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
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.refactoring.JavaRenameRefactoring;
import com.intellij.refactoring.rename.naming.AutomaticInheritorRenamerFactory;
import com.intellij.refactoring.rename.naming.AutomaticVariableRenamerFactory;

/**
 * @author dsl
 */
public class JavaRenameRefactoringImpl extends RenameRefactoringImpl implements JavaRenameRefactoring {
  private static final AutomaticVariableRenamerFactory ourVariableRenamerFactory = new AutomaticVariableRenamerFactory();
  private static final AutomaticInheritorRenamerFactory ourInheritorRenamerFactory = new AutomaticInheritorRenamerFactory();

  public JavaRenameRefactoringImpl(Project project,
                                 PsiElement element,
                                 String newName,
                                 boolean toSearchInComments,
                                 boolean toSearchInNonJavaFiles) {
    this(project, element, newName, GlobalSearchScope.projectScope(project), toSearchInComments, toSearchInNonJavaFiles);
  }

  public JavaRenameRefactoringImpl(Project project,
                                   PsiElement element,
                                   String newName,
                                   SearchScope scope,
                                   boolean toSearchInComments,
                                   boolean toSearchInNonJavaFiles) {
    super(project, element, newName, scope, toSearchInComments, toSearchInNonJavaFiles);
  }

  @Override
  public void setShouldRenameVariables(boolean value) {
    if (value) {
      myProcessor.addRenamerFactory(ourVariableRenamerFactory);
    }
    else {
      myProcessor.removeRenamerFactory(ourVariableRenamerFactory);
    }
  }

  @Override
  public void setShouldRenameInheritors(boolean value) {
    if (value) {
      myProcessor.addRenamerFactory(ourInheritorRenamerFactory);
    }
    else {
      myProcessor.removeRenamerFactory(ourInheritorRenamerFactory);
    }
  }
}
