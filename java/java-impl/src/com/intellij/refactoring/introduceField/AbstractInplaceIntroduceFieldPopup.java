/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.introduceParameter.AbstractJavaInplaceIntroducer;
import com.intellij.refactoring.ui.TypeSelectorManagerImpl;
import com.intellij.refactoring.util.occurrences.OccurrenceManager;

/**
 * User: anna
 */
public abstract class AbstractInplaceIntroduceFieldPopup extends AbstractJavaInplaceIntroducer {
  protected final PsiClass myParentClass;
  protected final OccurrenceManager myOccurrenceManager;

  private final SmartPsiElementPointer<PsiElement> myAnchorElement;
  private int myAnchorIdx = -1;
  private final SmartPsiElementPointer<PsiElement> myAnchorElementIfAll;
  private int myAnchorIdxIfAll = -1;

  private final SmartPointerManager mySmartPointerManager;
  protected RangeMarker myFieldRangeStart;

  public AbstractInplaceIntroduceFieldPopup(Project project,
                                            Editor editor,
                                            PsiExpression expr,
                                            PsiVariable localVariable,
                                            PsiExpression[] occurrences,
                                            TypeSelectorManagerImpl typeSelectorManager,
                                            String title,
                                            PsiClass parentClass,
                                            final PsiElement anchorElement,
                                            final OccurrenceManager occurrenceManager,
                                            final PsiElement anchorElementIfAll) {
    super(project, editor, expr, localVariable, occurrences, typeSelectorManager, title);
    myParentClass = parentClass;
    myOccurrenceManager = occurrenceManager;
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

  @Override
  protected SearchScope getReferencesSearchScope(VirtualFile file) {
    return new LocalSearchScope(myParentClass);
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

  @Override
  protected PsiVariable getVariable() {
    if (myFieldRangeStart == null) return null;
    if (!myParentClass.isValid()) return null;
    PsiElement element = myParentClass.getContainingFile().findElementAt(myFieldRangeStart.getStartOffset());
    if (element instanceof PsiWhiteSpace) {
      element = PsiTreeUtil.skipSiblingsForward(element, PsiWhiteSpace.class);
    }
    if (element instanceof PsiField) return (PsiVariable)element;
    final PsiField field = PsiTreeUtil.getParentOfType(element, PsiField.class, false);
    if (field != null) return field;
    element = PsiTreeUtil.skipSiblingsBackward(element, PsiWhiteSpace.class);
    return PsiTreeUtil.getParentOfType(element, PsiField.class, false);
  }
}
