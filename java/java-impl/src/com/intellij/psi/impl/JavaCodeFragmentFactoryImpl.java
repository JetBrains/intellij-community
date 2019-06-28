// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

public class JavaCodeFragmentFactoryImpl extends JavaCodeFragmentFactory {
  private final Project myProject;

  public JavaCodeFragmentFactoryImpl(Project project) {
    myProject = project;
  }

  @NotNull
  @Override
  public PsiExpressionCodeFragment createExpressionCodeFragment(@NotNull final String text,
                                                                @Nullable final PsiElement context,
                                                                @Nullable final PsiType expectedType,
                                                                final boolean isPhysical) {
    return new PsiExpressionCodeFragmentImpl(myProject, isPhysical, "fragment.java", text, expectedType, context);
  }

  @NotNull
  @Override
  public JavaCodeFragment createCodeBlockCodeFragment(@NotNull final String text, @Nullable final PsiElement context, final boolean isPhysical) {
    return new PsiCodeFragmentImpl(myProject, JavaElementType.STATEMENTS, isPhysical, "fragment.java", text, context);
  }

  @NotNull
  @Override
  public PsiTypeCodeFragment createTypeCodeFragment(@NotNull final String text, @Nullable final PsiElement context, final boolean isPhysical) {
    return createTypeCodeFragment(text, context, isPhysical, 0);
  }

  @NotNull
  @Override
  public PsiTypeCodeFragment createTypeCodeFragment(@NotNull final String text, @Nullable final PsiElement context, final boolean isPhysical, final int flags) {
    return new PsiTypeCodeFragmentImpl(myProject, isPhysical, "fragment.java", text, flags, context);
  }

  @NotNull
  @Override
  public PsiJavaCodeReferenceCodeFragment createReferenceCodeFragment(@NotNull final String text,
                                                                      @Nullable final PsiElement context,
                                                                      final boolean isPhysical,
                                                                      final boolean isClassesAccepted) {
    return new PsiJavaCodeReferenceCodeFragmentImpl(myProject, isPhysical, "fragment.java", text, isClassesAccepted, context);
  }

  @Override
  public JavaCodeFragment createMemberCodeFragment(@NotNull String text, @Nullable PsiElement context, boolean isPhysical) {
    return new PsiCodeFragmentImpl(myProject,  JavaElementType.MEMBERS, isPhysical, "fragment.java", text, context);
  }
}
