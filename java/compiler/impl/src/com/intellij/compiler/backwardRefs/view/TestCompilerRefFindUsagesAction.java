/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.compiler.backwardRefs.view;

import com.intellij.compiler.CompilerReferenceService;
import com.intellij.compiler.backwardRefs.CompilerReferenceServiceImpl;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;

public class TestCompilerRefFindUsagesAction extends TestCompilerReferenceServiceAction {
  public TestCompilerRefFindUsagesAction() {
    super("Compiler Reference Find Usages");
  }

  @Override
  protected void startActionFor(@NotNull PsiElement element) {
    final CompilerReferenceServiceImpl compilerReferenceService =
      (CompilerReferenceServiceImpl)CompilerReferenceService.getInstance(element.getProject());
    final CompilerReferenceFindUsagesTestInfo compilerReferenceTestInfo = compilerReferenceService.getTestFindUsages(element);
    InternalCompilerRefServiceView.showFindUsages(compilerReferenceTestInfo, element);
  }

  @Override
  protected boolean canBeAppliedFor(@NotNull PsiElement element) {
    return element instanceof PsiMethod || element instanceof PsiField || element instanceof PsiClass;
  }
}
