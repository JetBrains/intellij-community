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
package com.intellij.refactoring.rename.naming;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.refactoring.RefactoringBundle;

/**
 * @author dsl
 */
public class InheritorRenamer extends AutomaticRenamer {
  public InheritorRenamer(PsiClass aClass, String newClassName) {
    for (final PsiClass inheritor : ClassInheritorsSearch.search(aClass).findAll()) {
      if (inheritor.getName() != null) {
        myElements.add(inheritor);
      }
    }

    suggestAllNames(aClass.getName(), newClassName);
  }

  @Override
  public String getDialogTitle() {
    return RefactoringBundle.message("rename.inheritors.title");
  }

  @Override
  public String getDialogDescription() {
    return JavaRefactoringBundle.message("rename.inheritors.with.the.following.names.to.title");
  }

  @Override
  public String entityName() {
    return JavaRefactoringBundle.message("entity.name.inheritor");
  }
}
