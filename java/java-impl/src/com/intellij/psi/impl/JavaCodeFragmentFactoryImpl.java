// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiCodeFragmentImpl;
import com.intellij.psi.impl.source.PsiExpressionCodeFragmentImpl;
import com.intellij.psi.impl.source.PsiJavaCodeReferenceCodeFragmentImpl;
import com.intellij.psi.impl.source.PsiTypeCodeFragmentImpl;
import com.intellij.psi.impl.source.tree.JavaElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class JavaCodeFragmentFactoryImpl extends JavaCodeFragmentFactory {
  private final Project myProject;

  public JavaCodeFragmentFactoryImpl(Project project) {
    myProject = project;
  }

  @Override
  public @NotNull PsiExpressionCodeFragment createExpressionCodeFragment(final @NotNull String text,
                                                                         final @Nullable PsiElement context,
                                                                         final @Nullable PsiType expectedType,
                                                                         final boolean isPhysical) {
    return new PsiExpressionCodeFragmentImpl(myProject, isPhysical, "fragment.java", text, expectedType, context);
  }

  @Override
  public @NotNull JavaCodeFragment createCodeBlockCodeFragment(final @NotNull String text, final @Nullable PsiElement context, final boolean isPhysical) {
    return new PsiCodeFragmentImpl(myProject, JavaElementType.STATEMENTS, isPhysical, "fragment.java", text, context);
  }

  @Override
  public @NotNull PsiTypeCodeFragment createTypeCodeFragment(final @NotNull String text, final @Nullable PsiElement context, final boolean isPhysical) {
    return createTypeCodeFragment(text, context, isPhysical, 0);
  }

  @Override
  public @NotNull PsiTypeCodeFragment createTypeCodeFragment(final @NotNull String text, final @Nullable PsiElement context, final boolean isPhysical, final int flags) {
    return new PsiTypeCodeFragmentImpl(myProject, isPhysical, "fragment.java", text, flags, context);
  }

  @Override
  public @NotNull PsiJavaCodeReferenceCodeFragment createReferenceCodeFragment(final @NotNull String text,
                                                                               final @Nullable PsiElement context,
                                                                               final boolean isPhysical,
                                                                               final boolean isClassesAccepted) {
    return new PsiJavaCodeReferenceCodeFragmentImpl(myProject, isPhysical, "fragment.java", text, isClassesAccepted, context);
  }

  @Override
  public JavaCodeFragment createMemberCodeFragment(@NotNull String text, @Nullable PsiElement context, boolean isPhysical) {
    return new PsiCodeFragmentImpl(myProject,  JavaElementType.MEMBERS, isPhysical, "fragment.java", text, context);
  }
}
