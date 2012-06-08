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
package com.intellij.codeInsight.intention.impl;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * User: anna
 * Date: 9/5/11
 */
public class RunRefactoringAction extends BaseRunRefactoringAction<RefactoringActionHandler> {
  private final RefactoringActionHandler myHandler;
  private final String myCommandName;

  public RunRefactoringAction(RefactoringActionHandler handler, String commandName) {
    myHandler = handler;
    myCommandName = commandName;
  }

  @NotNull
  @Override
  public String getText() {
    return myCommandName;
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return true;
  }

  @Override
  public final void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    myHandler.invoke(project, editor, file, null);
  }
}
