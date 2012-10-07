/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
}
