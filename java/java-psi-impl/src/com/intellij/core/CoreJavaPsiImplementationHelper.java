/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.JavaPsiImplementationHelper;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class CoreJavaPsiImplementationHelper extends JavaPsiImplementationHelper {
  @Override
  public PsiClass getOriginalClass(PsiClass psiClass) {
    return psiClass;
  }

  @NotNull
  @Override
  public PsiElement getClsFileNavigationElement(PsiJavaFile clsFile) {
    return clsFile;
  }

  @Override
  public LanguageLevel getClassesLanguageLevel(VirtualFile virtualFile) {
    return null;
  }

  @Override
  public ASTNode getDefaultImportAnchor(PsiImportList list, PsiImportStatementBase statement) {
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
}
