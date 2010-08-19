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
package com.intellij.usageView;

import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 */
public class BaseUsageViewDescriptor implements UsageViewDescriptor {

  private PsiElement[] myElements;

  public BaseUsageViewDescriptor(PsiElement... elements) {
    myElements = elements;
  }

  @NotNull
  @Override
  public PsiElement[] getElements() {
    return myElements;
  }

  @Override
  public String getProcessedElementsHeader() {
    return "Element(s) to be refactored:";
  }

  @Override
  public String getCodeReferencesText(int usagesCount, int filesCount) {
    return RefactoringBundle.message("references.to.be.changed", UsageViewBundle.getReferencesString(usagesCount, filesCount));
  }

  @Nullable
  @Override
  public String getCommentReferencesText(int usagesCount, int filesCount) {
    return null;
  }
}
