
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

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.psi.*;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usageView.UsageViewDescriptor;
import org.jetbrains.annotations.NotNull;

class InlineViewDescriptor implements UsageViewDescriptor{

  private final PsiElement myElement;

  InlineViewDescriptor(PsiElement element) {
    myElement = element;
  }

  @Override
  public PsiElement @NotNull [] getElements() {
    return new PsiElement[] {myElement};
  }

  @Override
  public String getProcessedElementsHeader() {
    if (myElement instanceof PsiField) {
      return JavaRefactoringBundle.message("inline.field.elements.header");
    }
    if (myElement instanceof PsiVariable) {
      return JavaRefactoringBundle.message("inline.vars.elements.header");
    }
    if (myElement instanceof PsiClass) {
      return JavaRefactoringBundle.message("inline.class.elements.header");
    }
    if (myElement instanceof PsiMethod) {
      return JavaRefactoringBundle.message("inline.method.elements.header");
    }
    return JavaRefactoringBundle.message("inline.element.unknown.header");
  }

  @NotNull
  @Override
  public String getCodeReferencesText(int usagesCount, int filesCount) {
    return JavaRefactoringBundle.message("invocations.to.be.inlined", UsageViewBundle.getReferencesString(usagesCount, filesCount));
  }

  @Override
  public String getCommentReferencesText(int usagesCount, int filesCount) {
    return JavaRefactoringBundle.message("comments.elements.header",
                                     UsageViewBundle.getOccurencesString(usagesCount, filesCount));
  }

}
