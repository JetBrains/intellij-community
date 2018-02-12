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
package com.intellij.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public abstract class JavaPsiImplementationHelper {
  public static JavaPsiImplementationHelper getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, JavaPsiImplementationHelper.class);
  }

  @NotNull
  public abstract PsiClass getOriginalClass(@NotNull PsiClass psiClass);

  @NotNull
  public abstract PsiJavaModule getOriginalModule(@NotNull PsiJavaModule module);

  @NotNull
  public abstract PsiElement getClsFileNavigationElement(@NotNull PsiJavaFile clsFile);

  @NotNull
  public abstract LanguageLevel getEffectiveLanguageLevel(@Nullable VirtualFile virtualFile);

  @Nullable
  public abstract ASTNode getDefaultImportAnchor(@NotNull PsiImportList list, @NotNull PsiImportStatementBase statement);

  @Nullable
  public abstract PsiElement getDefaultMemberAnchor(@NotNull PsiClass psiClass, @NotNull PsiMember firstPsi);

  public abstract void setupCatchBlock(@NotNull String exceptionName,
                                       @NotNull PsiType exceptionType,
                                       @Nullable PsiElement context,
                                       @NotNull PsiCatchSection element);
}