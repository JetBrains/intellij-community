
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
package com.intellij.refactoring.inline;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usageView.UsageViewDescriptor;
import org.jetbrains.annotations.NotNull;

class InlineViewDescriptor implements UsageViewDescriptor{

  private final PsiElement myElement;

  public InlineViewDescriptor(PsiElement element) {
    myElement = element;
  }

  @NotNull
  public PsiElement[] getElements() {
    return new PsiElement[] {myElement};
  }

  public String getProcessedElementsHeader() {
    if (myElement instanceof PsiClass) {
      return RefactoringBundle.message("inline.class.elements.header");
    }
    return myElement instanceof PsiMethod ?
           RefactoringBundle.message("inline.method.elements.header") :
           RefactoringBundle.message("inline.field.elements.header");
  }

  public String getCodeReferencesText(int usagesCount, int filesCount) {
    return RefactoringBundle.message("invocations.to.be.inlined", UsageViewBundle.getReferencesString(usagesCount, filesCount));
  }

  public String getCommentReferencesText(int usagesCount, int filesCount) {
    return RefactoringBundle.message("comments.elements.header",
                                     UsageViewBundle.getOccurencesString(usagesCount, filesCount));
  }

}
