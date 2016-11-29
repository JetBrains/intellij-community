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
package com.intellij.refactoring.introduceParameterObject;

import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.ui.UsageViewDescriptorAdapter;
import com.intellij.usageView.UsageViewBundle;
import org.jetbrains.annotations.NotNull;

public class IntroduceParameterObjectUsageViewDescriptor extends UsageViewDescriptorAdapter {

  private final PsiElement method;

  public IntroduceParameterObjectUsageViewDescriptor(PsiElement method) {
    this.method = method;
  }

  @NotNull
  public PsiElement[] getElements() {
    return new PsiElement[]{method};
  }

  public String getProcessedElementsHeader() {
    return RefactoringBundle.message("refactoring.introduce.parameter.object.method.whose.parameters.are.to.wrapped");
  }

  public String getCodeReferencesText(int usagesCount, int filesCount) {
    return RefactoringBundle.message("refactoring.introduce.parameter.object.references.to.be.modified") +
           UsageViewBundle.getReferencesString(usagesCount, filesCount);
  }
}
