/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.refactoring.openapi.impl;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.refactoring.RefactoringImpl;
import com.intellij.refactoring.TurnRefsToSuperRefactoring;
import com.intellij.refactoring.turnRefsToSuper.TurnRefsToSuperProcessor;

/**
 * @author dsl
 */
public class TurnRefsToSuperRefactoringImpl extends RefactoringImpl<TurnRefsToSuperProcessor> implements TurnRefsToSuperRefactoring {
  TurnRefsToSuperRefactoringImpl(Project project, PsiClass aClass, PsiClass aSuper, boolean replaceInstanceOf) {
    super(new TurnRefsToSuperProcessor(project, aClass, aSuper, replaceInstanceOf));
  }

  @Override
  public PsiClass getSuper() {
    return myProcessor.getSuper();
  }

  @Override
  public PsiClass getTarget() {
    return myProcessor.getTarget();
  }

  @Override
  public boolean isReplaceInstanceOf() {
    return myProcessor.isReplaceInstanceOf();
  }
}
