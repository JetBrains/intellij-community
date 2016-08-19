/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.template.TemplateBuilderImpl;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Comparator;
import java.util.SortedSet;
import java.util.TreeSet;

/**
* @author Dmitry Batkovich
*/
public class AddMissingRequiredAnnotationParametersFix implements IntentionAction {
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

    final TObjectIntHashMap<String> annotationsOrderMap = getAnnotationsOrderMap();
    final SortedSet<Pair<String, PsiAnnotationMemberValue>>
      newParameters = new TreeSet<>(
      (o1, o2) -> annotationsOrderMap.get(o1.getFirst()) - annotationsOrderMap.get(o2.getFirst()));
    final boolean order = isAlreadyAddedOrdered(annotationsOrderMap, addedParameters);
    if (order) {
      if (addedParameters.length != 0) {
        final PsiAnnotationParameterList parameterList = myAnnotation.getParameterList();
        parameterList.deleteChildRange(addedParameters[0], addedParameters[addedParameters.length - 1]);
        for (final PsiNameValuePair addedParameter : addedParameters) {
          final String name = addedParameter.getName();
          final PsiAnnotationMemberValue value = addedParameter.getValue();
          if (name == null || value == null) {
            LOG.error(String.format("Invalid annotation parameter name = %s, value = %s", name, value));
            continue;
          }
          newParameters.add(Pair.create(name, value));
        }
      }
    }

    final PsiExpression nullValue = JavaPsiFacade.getElementFactory(project).createExpressionFromText(PsiKeyword.NULL, null);
    for (final String misssedParameter : myMissedElements) {
      newParameters.add(Pair.<String, PsiAnnotationMemberValue>create(misssedParameter, nullValue));
    }

    TemplateBuilderImpl builder = null;
    for (final Pair<String, PsiAnnotationMemberValue> newParameter : newParameters) {
      final PsiAnnotationMemberValue value =
        myAnnotation.setDeclaredAttributeValue(newParameter.getFirst(), newParameter.getSecond());
      if (myMissedElements.contains(newParameter.getFirst())) {
        if (builder == null) {
          builder = new TemplateBuilderImpl(myAnnotation.getParameterList());
        }
        builder.replaceElement(value, new EmptyExpression(), true);
      }
    }

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

  private TObjectIntHashMap<String> getAnnotationsOrderMap() {
    final TObjectIntHashMap<String> map = new TObjectIntHashMap<>();
    for (int i = 0; i < myAnnotationMethods.length; i++) {
      map.put(myAnnotationMethods[i].getName(), i);
    }
    return map;
  }

  private static boolean isAlreadyAddedOrdered(final TObjectIntHashMap<String> orderMap, final PsiNameValuePair[] addedParameters) {
    if (addedParameters.length <= 1) {
      return true;
    }
    int previousOrder = orderMap.get(addedParameters[0].getName());
    for (int i = 1; i < addedParameters.length; i++) {
      final int currentOrder = orderMap.get(addedParameters[i].getName());
      if (currentOrder < previousOrder) {
        return false;
      }
      previousOrder = currentOrder;
    }
    return true;
  }
}
