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

/**
 * created at Sep 11, 2001
 * @author Jeka
 */
package com.intellij.refactoring.move;

import com.intellij.psi.PsiElement;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.refactoring.RefactoringBundle;
import org.jetbrains.annotations.NotNull;

public class MoveMemberViewDescriptor implements UsageViewDescriptor {
  private final PsiElement[] myElementsToMove;

  public MoveMemberViewDescriptor(PsiElement[] elementsToMove) {
    myElementsToMove = elementsToMove;
  }

  @Override
  @NotNull
  public PsiElement[] getElements() {
    return myElementsToMove;
  }

  @Override
  public String getProcessedElementsHeader() {
    return RefactoringBundle.message("move.members.elements.header");
  }

  @Override
  public String getCodeReferencesText(int usagesCount, int filesCount) {
    return RefactoringBundle.message("references.to.be.changed", UsageViewBundle.getReferencesString(usagesCount, filesCount));
  }

  @Override
  public String getCommentReferencesText(int usagesCount, int filesCount) {
    return null;
  }

}
