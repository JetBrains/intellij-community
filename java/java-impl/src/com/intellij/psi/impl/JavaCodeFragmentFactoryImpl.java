// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl;

import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaCodeFragment;
import com.intellij.psi.JavaCodeFragmentFactory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpressionCodeFragment;
import com.intellij.psi.PsiJavaCodeReferenceCodeFragment;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeCodeFragment;
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
  public @NotNull PsiExpressionCodeFragment createExpressionCodeFragment(@NotNull String text,
                                                                         @Nullable PsiElement context,
                                                                         @Nullable PsiType expectedType,
                                                                         boolean isPhysical) {
    return new PsiExpressionCodeFragmentImpl(myProject, isPhysical, "fragment.java", text, expectedType, context);
  }

  @Override
  public @NotNull JavaCodeFragment createCodeBlockCodeFragment(@NotNull String text, @Nullable PsiElement context, boolean isPhysical) {
    return new PsiCodeFragmentImpl(myProject, JavaElementType.STATEMENTS, isPhysical, "fragment.java", text, context);
  }

  @Override
  public @NotNull PsiTypeCodeFragment createTypeCodeFragment(@NotNull String text, @Nullable PsiElement context, boolean isPhysical) {
    return createTypeCodeFragment(text, context, isPhysical, 0);
  }

  @Override
  public @NotNull PsiTypeCodeFragment createTypeCodeFragment(@NotNull String text,
                                                             @Nullable PsiElement context,
                                                             boolean isPhysical,
                                                             int flags) {
    return new PsiTypeCodeFragmentImpl(myProject, isPhysical, "fragment.java", text, flags, context);
  }

  @Override
  public @NotNull PsiJavaCodeReferenceCodeFragment createReferenceCodeFragment(@NotNull String text,
                                                                               @Nullable PsiElement context,
                                                                               boolean isPhysical,
                                                                               boolean isClassesAccepted) {
    return new PsiJavaCodeReferenceCodeFragmentImpl(myProject, isPhysical, "fragment.java", text, isClassesAccepted, context);
  }

  @Override
  public @NotNull PsiJavaCodeReferenceCodeFragment createReferenceCodeFragmentInPackage(@NotNull String text,
                                                                                        @NotNull String packageName,
                                                                                        boolean isClassesAccepted) {
    return new PsiJavaCodeReferenceCodeFragmentImpl(myProject, true, "fragment.java", text, isClassesAccepted, packageName);
  }

  @Override
  public JavaCodeFragment createMemberCodeFragment(@NotNull String text, @Nullable PsiElement context, boolean isPhysical) {
    return new PsiCodeFragmentImpl(myProject,  JavaElementType.MEMBERS, isPhysical, "fragment.java", text, context);
  }
}
