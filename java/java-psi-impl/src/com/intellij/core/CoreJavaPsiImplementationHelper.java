/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.core;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.JavaPsiImplementationHelper;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class CoreJavaPsiImplementationHelper extends JavaPsiImplementationHelper {
  private final Project myProject;

  public CoreJavaPsiImplementationHelper(@NotNull Project project) {
    myProject = project;
  }

  @NotNull
  @Override
  public PsiClass getOriginalClass(@NotNull PsiClass psiClass) {
    return psiClass;
  }

  @NotNull
  @Override
  public PsiJavaModule getOriginalModule(@NotNull PsiJavaModule module) {
    return module;
  }

  @NotNull
  @Override
  public PsiElement getClsFileNavigationElement(@NotNull PsiJavaFile clsFile) {
    return clsFile;
  }

  @NotNull
  @Override
  public LanguageLevel getEffectiveLanguageLevel(@Nullable VirtualFile virtualFile) {
    return PsiUtil.getLanguageLevel(myProject);
  }

  @Override
  public ASTNode getDefaultImportAnchor(@NotNull PsiImportList list, @NotNull PsiImportStatementBase statement) {
    throw new UnsupportedOperationException("TODO");
  }

  @Override
  public PsiElement getDefaultMemberAnchor(@NotNull PsiClass psiClass, @NotNull PsiMember firstPsi) {
    throw new UnsupportedOperationException("TODO");
  }

  @Override
  public void setupCatchBlock(@NotNull String exceptionName, @NotNull PsiType exceptionType, PsiElement context, @NotNull PsiCatchSection element) {
    throw new UnsupportedOperationException("TODO");
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }
}