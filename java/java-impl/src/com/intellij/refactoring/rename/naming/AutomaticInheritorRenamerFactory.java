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
package com.intellij.refactoring.rename.naming;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.usageView.UsageInfo;

import java.util.Collection;

/**
 * @author yole
 */
public class AutomaticInheritorRenamerFactory implements AutomaticRenamerFactory {
  public boolean isApplicable(final PsiElement element) {
    return element instanceof PsiClass;
  }

  public String getOptionName() {
    return RefactoringBundle.message("rename.inheritors");
  }

  public boolean isEnabled() {
    return JavaRefactoringSettings.getInstance().isToRenameInheritors();
  }

  public void setEnabled(final boolean enabled) {
    JavaRefactoringSettings.getInstance().setRenameInheritors(enabled);
  }

  public AutomaticRenamer createRenamer(final PsiElement element, final String newName, final Collection<UsageInfo> usages) {
    return new InheritorRenamer((PsiClass)element, newName);
  }
}
