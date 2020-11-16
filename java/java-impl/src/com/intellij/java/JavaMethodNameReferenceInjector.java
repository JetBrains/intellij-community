// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.injection.ReferenceInjector;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.ClassUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class JavaMethodNameReferenceInjector extends ReferenceInjector {
  @Override
  public @NotNull String getId() {
    return "java-method-name";
  }

  @Override
  public @NotNull @NlsContexts.Label String getDisplayName() {
    return JavaBundle.message("label.java.method.name");
  }

  @Override
  public PsiReference @NotNull [] getReferences(@NotNull PsiElement element,
                                                @NotNull ProcessingContext context,
                                                @NotNull TextRange range) {
    if (!(element instanceof PsiLiteralExpression)) return PsiReference.EMPTY_ARRAY;
    String text = range.substring(element.getText());
    if (text.isEmpty()) return PsiReference.EMPTY_ARRAY;
    PsiClass containingClass = ClassUtils.getContainingClass(element);
    if (containingClass == null) return PsiReference.EMPTY_ARRAY;
    PsiMethod[] methods = containingClass.findMethodsByName(text, true);
    PsiMethod[] allMethods = StreamEx.of(containingClass.getAllMethods()).distinct(PsiMethod::getName).toArray(PsiMethod.EMPTY_ARRAY);
    if (methods.length == 0) {
      return new JavaMethodReference[]{new JavaMethodReference((PsiLiteralExpression)element, range, null, allMethods)};
    }
    return ContainerUtil.map2Array(methods, JavaMethodReference.class, 
                                   m -> new JavaMethodReference((PsiLiteralExpression)element, range, m, allMethods));
  }

  @Override
  public @NotNull Icon getIcon() {
    return AllIcons.Nodes.Method;
  }

  private static final class JavaMethodReference extends PsiReferenceBase<PsiLiteralExpression> {
    private final @Nullable PsiMethod myMethod;
    private final @NotNull PsiMethod @NotNull [] myVariants;

    JavaMethodReference(@NotNull PsiLiteralExpression literal, @NotNull TextRange range,
                        @Nullable PsiMethod method, @NotNull PsiMethod @NotNull [] variants) {
      super(literal, range, method == null);
      myMethod = method;
      myVariants = variants;
    }

    @Override
    public Object @NotNull [] getVariants() {
      return myVariants;
    }

    @Override
    public @Nullable PsiMethod resolve() {
      return myMethod;
    }
  }
}
