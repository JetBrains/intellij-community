// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReferenceProvider;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReferenceSet;
import com.intellij.psi.injection.ReferenceInjector;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UastContextKt;
import org.jetbrains.uast.expressions.UInjectionHost;

import javax.swing.*;

final class JvmClassNameReferenceInjector extends ReferenceInjector {
  @Override
  public @NotNull String getId() {
    return "jvm-class-name";
  }

  @Override
  public @NotNull Icon getIcon() {
    return AllIcons.Nodes.Class;
  }

  @Override
  public @NotNull String getDisplayName() {
    return JavaBundle.message("label.jvm.class.name");
  }

  @Override
  public PsiReference @NotNull [] getReferences(@NotNull PsiElement element,
                                                @NotNull ProcessingContext context,
                                                @NotNull TextRange range) {
    UInjectionHost host = UastContextKt.toUElement(element, UInjectionHost.class);
    if (host == null) return PsiReference.EMPTY_ARRAY;

    String className = host.evaluateToString();
    if (className == null) className = "";

    return new JavaClassReferenceSet(className, element, range.getStartOffset(), false, new JavaClassReferenceProvider())
      .getReferences();
  }
}
