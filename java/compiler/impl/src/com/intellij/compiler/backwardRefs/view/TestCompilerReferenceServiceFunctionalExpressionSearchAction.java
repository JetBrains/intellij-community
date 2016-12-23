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
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.psi.LambdaUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopeUtil;
import org.jetbrains.annotations.NotNull;

public class TestCompilerReferenceServiceFunctionalExpressionSearchAction extends TestCompilerReferenceServiceAction {

  public TestCompilerReferenceServiceFunctionalExpressionSearchAction() {
    super("Compiler Reference Functional Expression Search");
  }

  @Override
  protected void startActionFor(@NotNull PsiElement element) {
    if (element instanceof PsiClass && LambdaUtil.isFunctionalClass((PsiClass)element)) {
      final Project project = element.getProject();
      final CompilerReferenceServiceImpl compilerReferenceService = (CompilerReferenceServiceImpl)CompilerReferenceService.getInstance(
        project);
      final GlobalSearchScope scope = GlobalSearchScopeUtil.toGlobalSearchScope(element.getUseScope(), project);
      final CompilerReferenceHierarchyTestInfo hierarchyTestInfo =
        compilerReferenceService.getTestFunExpressions((PsiNamedElement)element, scope, StdFileTypes.JAVA);
      InternalCompilerRefServiceView.showHierarchyInfo(hierarchyTestInfo, element);
    }
  }
}
