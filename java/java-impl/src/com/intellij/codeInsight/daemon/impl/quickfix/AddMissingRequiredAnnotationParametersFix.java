// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.FileModifier;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.template.TemplateBuilderImpl;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.TextExpression;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.TypeUtils;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Comparator;
import java.util.SortedSet;
import java.util.TreeSet;

/**
* @author Dmitry Batkovich
*/
public final class AddMissingRequiredAnnotationParametersFix implements IntentionAction {
  private static final Logger LOG = Logger.getInstance(AddMissingRequiredAnnotationParametersFix.class);

  private final PsiAnnotation myAnnotation;
  private final PsiMethod[] myAnnotationMethods;
  private final Collection<String> myMissedElements;

  public AddMissingRequiredAnnotationParametersFix(final PsiAnnotation annotation,
                                                   final PsiMethod[] annotationMethods,
                                                   final Collection<String> missedElements) {
    if (missedElements.isEmpty()) {
      throw new IllegalArgumentException("missedElements can't be empty");
    }
    myAnnotation = annotation;
    myAnnotationMethods = annotationMethods;
    myMissedElements = missedElements;
  }

  @NotNull
  @Override
  public String getText() {
    return myMissedElements.size() == 1
           ? QuickFixBundle.message("add.missing.annotation.single.parameter.fix", ContainerUtil.getFirstItem(myMissedElements))
           : QuickFixBundle.message("add.missing.annotation.parameters.fix", StringUtil.join(myMissedElements, ", "));
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return QuickFixBundle.message("annotations.fix");
  }

  @Override
  public boolean isAvailable(@NotNull final Project project, final Editor editor, final PsiFile file) {
    return myAnnotation.isValid();
  }

  @Override
  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
    final PsiNameValuePair[] addedParameters = myAnnotation.getParameterList().getAttributes();

    final Object2IntMap<String> annotationsOrderMap = getAnnotationsOrderMap();
    final SortedSet<Pair<String, PsiAnnotationMemberValue>> newParameters =
      new TreeSet<>(Comparator.comparingInt(o -> annotationsOrderMap.getInt(o.getFirst())));

    final boolean order = isAlreadyAddedOrdered(annotationsOrderMap, addedParameters);
    if (order) {
      if (addedParameters.length != 0) {
        final PsiAnnotationParameterList parameterList = myAnnotation.getParameterList();
        parameterList.deleteChildRange(addedParameters[0], addedParameters[addedParameters.length - 1]);
        for (final PsiNameValuePair addedParameter : addedParameters) {
          String name = addedParameter.getName();
          final PsiAnnotationMemberValue value = addedParameter.getValue();
          if (name == null) {
            name = "value";
          }
          if (value == null) {
            LOG.error(String.format("Invalid annotation parameter name = %s, value = %s", name, null));
            continue;
          }
          newParameters.add(Pair.create(name, value));
        }
      }
    }

    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    for (PsiMethod method : myAnnotationMethods) {
      if (myMissedElements.contains(method.getName())) {
        PsiType type = method.getReturnType();
        String defaultValue;
        if (TypeUtils.isJavaLangString(type)) {
          defaultValue = "\"\"";
        }
        else if (type instanceof PsiArrayType) {
          defaultValue = "{}";
        } else {
          defaultValue = TypeUtils.getDefaultValue(type);
        }
        newParameters.add(Pair.create(method.getName(), factory.createExpressionFromText(defaultValue, null)));
      }
    }

    TemplateBuilderImpl builder = null;
    for (final Pair<String, PsiAnnotationMemberValue> newParameter : newParameters) {
      final PsiAnnotationMemberValue value =
        myAnnotation.setDeclaredAttributeValue(newParameter.getFirst(), newParameter.getSecond());
      if (myMissedElements.contains(newParameter.getFirst())) {
        if (builder == null) {
          builder = new TemplateBuilderImpl(myAnnotation.getParameterList());
        }
        builder.replaceElement(value, new TextExpression(newParameter.getSecond().getText()), true);
      }
    }
    
    if (!file.isPhysical()) return;

    editor.getCaretModel().moveToOffset(myAnnotation.getParameterList().getTextRange().getStartOffset());
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
    final Document document = documentManager.getDocument(file);
    if (document == null) {
      throw new IllegalStateException();
    }
    documentManager.doPostponedOperationsAndUnblockDocument(document);
    TemplateManager.getInstance(project).startTemplate(editor, builder.buildInlineTemplate(), null);
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }

  private Object2IntMap<String> getAnnotationsOrderMap() {
    final Object2IntMap<String> map = new Object2IntOpenHashMap<>();
    for (int i = 0; i < myAnnotationMethods.length; i++) {
      map.put(myAnnotationMethods[i].getName(), i);
    }
    return map;
  }

  private static boolean isAlreadyAddedOrdered(final Object2IntMap<String> orderMap, final PsiNameValuePair[] addedParameters) {
    if (addedParameters.length <= 1) {
      return true;
    }
    int previousOrder = orderMap.getInt(addedParameters[0].getName());
    for (int i = 1; i < addedParameters.length; i++) {
      final int currentOrder = orderMap.getInt(addedParameters[i].getName());
      if (currentOrder < previousOrder) {
        return false;
      }
      previousOrder = currentOrder;
    }
    return true;
  }

  @Override
  public @NotNull FileModifier getFileModifierForPreview(@NotNull PsiFile target) {
    return new AddMissingRequiredAnnotationParametersFix(PsiTreeUtil.findSameElementInCopy(myAnnotation, target), myAnnotationMethods,
                                                         myMissedElements);
  }
}
