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
package com.intellij.refactoring.introduceField;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.refactoring.introduceParameter.AbstractJavaInplaceIntroducer;
import com.intellij.refactoring.ui.TypeSelectorManagerImpl;
import com.intellij.refactoring.util.occurences.OccurenceManager;

/**
 * User: anna
 */
public abstract class AbstractInplaceIntroduceFieldPopup extends AbstractJavaInplaceIntroducer {
  protected final PsiClass myParentClass;
  protected final OccurenceManager myOccurenceManager;

  private SmartPsiElementPointer<PsiElement> myAnchorElement;
  private int myAnchorIdx = -1;
  private SmartPsiElementPointer<PsiElement> myAnchorElementIfAll;
  private int myAnchorIdxIfAll = -1;

  private final SmartPointerManager mySmartPointerManager;

  public AbstractInplaceIntroduceFieldPopup(Project project,
                                            Editor editor,
                                            PsiExpression expr,
                                            PsiVariable localVariable,
                                            PsiExpression[] occurrences,
                                            TypeSelectorManagerImpl typeSelectorManager,
                                            String title,
                                            PsiClass parentClass,
                                            final PsiElement anchorElement,
                                            final OccurenceManager occurenceManager,
                                            final PsiElement anchorElementIfAll) {
    super(project, editor, expr, localVariable, occurrences, typeSelectorManager, title);
    myParentClass = parentClass;
    myOccurenceManager = occurenceManager;
    mySmartPointerManager = SmartPointerManager.getInstance(project);
    myAnchorElement = anchorElement != null ? mySmartPointerManager.createSmartPsiElementPointer(anchorElement) : null;
    myAnchorElementIfAll = anchorElementIfAll != null ? mySmartPointerManager.createSmartPsiElementPointer(anchorElementIfAll) : null;
    for (int i = 0, occurrencesLength = occurrences.length; i < occurrencesLength; i++) {
      PsiExpression occurrence = occurrences[i];
      PsiElement parent = occurrence.getParent();
      if (parent == anchorElement) {
        myAnchorIdx = i;
      }
      if (parent == anchorElementIfAll) {
        myAnchorIdxIfAll = i;
      }
    }
  }

  @Override
  protected PsiElement checkLocalScope() {
    return myParentClass;
  }

  protected PsiElement getAnchorElement() {
    if (myAnchorIdx != -1 && myOccurrences[myAnchorIdx] != null) {
      return myOccurrences[myAnchorIdx].getParent();
    }
    else {
      return myAnchorElement != null ? myAnchorElement.getElement() : null;
    }
  }

  protected PsiElement getAnchorElementIfAll() {
    if (myAnchorIdxIfAll != -1 && myOccurrences[myAnchorIdxIfAll] != null) {
      return myOccurrences[myAnchorIdxIfAll].getParent();
    }
    else {
      return myAnchorElementIfAll != null ? myAnchorElementIfAll.getElement() : null;
    }
  }
}
