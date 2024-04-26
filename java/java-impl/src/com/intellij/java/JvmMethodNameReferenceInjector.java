// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.injection.ReferenceInjector;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElementKt;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UastContextKt;
import org.jetbrains.uast.expressions.UInjectionHost;

import javax.swing.*;

public final class JvmMethodNameReferenceInjector extends ReferenceInjector {
  @Override
  public @NotNull String getId() {
    return "jvm-method-name";
  }

  @Override
  public @NotNull @NlsContexts.Label String getDisplayName() {
    return JavaBundle.message("label.jvm.method.name");
  }

  @Override
  public PsiReference @NotNull [] getReferences(@NotNull PsiElement element,
                                                @NotNull ProcessingContext context,
                                                @NotNull TextRange range) {
    UInjectionHost host = UastContextKt.toUElement(element, UInjectionHost.class);
    if (host == null) return PsiReference.EMPTY_ARRAY;
    String text = range.substring(element.getText());
    if (text.isEmpty()) return PsiReference.EMPTY_ARRAY;
    UClass uClass = UastContextKt.getUastParentOfType(element, UClass.class);
    if (uClass == null) return PsiReference.EMPTY_ARRAY;
    PsiClass containingClass = UElementKt.getAsJavaPsiElement(uClass, PsiClass.class);
    if (containingClass == null) return PsiReference.EMPTY_ARRAY;
    PsiMethod[] methods = containingClass.findMethodsByName(text, true);
    if (methods.length == 0) {
      return new JavaMethodReference[]{new JavaMethodReference(element, range, null, uClass)};
    }
    return ContainerUtil.map2Array(methods, JavaMethodReference.class, 
                                   m -> new JavaMethodReference(element, range, m, uClass));
  }

  @Override
  public @NotNull Icon getIcon() {
    return AllIcons.Nodes.Method;
  }

  private static final class JavaMethodReference extends PsiReferenceBase<PsiElement> {
    private final @Nullable PsiMethod myMethod;
    private final @NotNull UClass myContainingClass;

    JavaMethodReference(@NotNull PsiElement literal, @NotNull TextRange range,
                        @Nullable PsiMethod method, @NotNull UClass containingClass) {
      super(literal, range, method == null);
      myMethod = method;
      myContainingClass = containingClass;
    }

    @Override
    public Object @NotNull [] getVariants() {
      return StreamEx.of(myContainingClass.getJavaPsi().getAllMethods())
        .remove(method -> method.hasModifierProperty(PsiModifier.STATIC))
        .distinct(PsiMethod::getName).toArray(PsiMethod.EMPTY_ARRAY);
    }

    @Override
    public @Nullable PsiElement resolve() {
      UMethod uMethod = UastContextKt.toUElement(myMethod, UMethod.class);
      return uMethod == null ? null : uMethod.getSourcePsi();
    }
  }
}
