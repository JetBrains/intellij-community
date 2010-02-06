/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

/*
 * User: anna
 * Date: 12-Jan-2010
 */
package com.intellij.refactoring.rename.naming;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.usageView.UsageInfo;

import java.util.Collection;

public class AutomaticParametersRenamerFactory implements AutomaticRenamerFactory{
  public boolean isApplicable(PsiElement element) {
    return element instanceof PsiParameter && ((PsiParameter)element).getDeclarationScope() instanceof PsiMethod;
  }

  public String getOptionName() {
    return RefactoringBundle.message("rename.parameters.hierarchy");
  }

  public boolean isEnabled() {
    return JavaRefactoringSettings.getInstance().isRenameParameterInHierarchy();
  }

  public void setEnabled(boolean enabled) {
    JavaRefactoringSettings.getInstance().setRenameParameterInHierarchy(enabled);
  }

  public AutomaticRenamer createRenamer(PsiElement element, String newName, Collection<UsageInfo> usages) {
    return new AutomaticParametersRenamer((PsiParameter)element, newName);
  }
}