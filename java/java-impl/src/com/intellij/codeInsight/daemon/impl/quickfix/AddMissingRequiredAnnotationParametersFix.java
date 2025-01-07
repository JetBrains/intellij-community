// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.template.impl.TextExpression;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
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
public final class AddMissingRequiredAnnotationParametersFix extends PsiUpdateModCommandAction<PsiAnnotation> {
  private static final Logger LOG = Logger.getInstance(AddMissingRequiredAnnotationParametersFix.class);

  private final Collection<String> myMissedElements;

  public AddMissingRequiredAnnotationParametersFix(final PsiAnnotation annotation, final Collection<String> missedElements) {
    super(annotation);
    if (missedElements.isEmpty()) {
      throw new IllegalArgumentException("missedElements can't be empty");
    }
    myMissedElements = missedElements;
  }

  @Override
  protected @NotNull Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiAnnotation element) {
    return Presentation.of(myMissedElements.size() == 1
           ? QuickFixBundle.message("add.missing.annotation.single.parameter.fix", ContainerUtil.getFirstItem(myMissedElements))
           : QuickFixBundle.message("add.missing.annotation.parameters.fix", StringUtil.join(myMissedElements, ", ")));
  }

  @Override
  public @NotNull String getFamilyName() {
    return QuickFixBundle.message("annotations.fix");
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiAnnotation annotation, @NotNull ModPsiUpdater updater) {
    final PsiNameValuePair[] addedParameters = annotation.getParameterList().getAttributes();

    PsiClass aClass = annotation.resolveAnnotationType();
    if (aClass == null) {
      updater.cancel(JavaBundle.message("error.no.annotation.class.found"));
      return;
    }
    PsiMethod[] methods = aClass.getMethods();
    final Object2IntMap<String> annotationsOrderMap = getAnnotationsOrderMap(methods);
    final SortedSet<Pair<String, PsiAnnotationMemberValue>> newParameters =
      new TreeSet<>(Comparator.comparingInt(o -> annotationsOrderMap.getInt(o.getFirst())));

    final boolean order = isAlreadyAddedOrdered(annotationsOrderMap, addedParameters);
    if (order) {
      if (addedParameters.length != 0) {
        final PsiAnnotationParameterList parameterList = annotation.getParameterList();
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

    PsiElementFactory factory = JavaPsiFacade.getElementFactory(context.project());
    for (PsiMethod method : methods) {
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

    ModTemplateBuilder builder = updater.templateBuilder();
    for (final Pair<String, PsiAnnotationMemberValue> newParameter : newParameters) {
      final PsiAnnotationMemberValue value =
        annotation.setDeclaredAttributeValue(newParameter.getFirst(), newParameter.getSecond());
      if (myMissedElements.contains(newParameter.getFirst())) {
        builder.field(value, new TextExpression(newParameter.getSecond().getText()));
      }
    }
  }

  private Object2IntMap<String> getAnnotationsOrderMap(PsiMethod[] methods) {
    final Object2IntMap<String> map = new Object2IntOpenHashMap<>();
    for (int i = 0; i < methods.length; i++) {
      map.put(methods[i].getName(), i);
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
}
