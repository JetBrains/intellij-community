// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.introduceField;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.AbstractJavaInplaceIntroducer;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.ui.TypeSelectorManagerImpl;

public abstract class AbstractInplaceIntroduceFieldPopup extends AbstractJavaInplaceIntroducer {
  private final SmartPsiElementPointer<PsiClass> myParentClass;
  private final SmartPsiElementPointer<PsiElement> myAnchorElement;
  private int myAnchorIdx = -1;
  private final SmartPsiElementPointer<PsiElement> myAnchorElementIfAll;
  private int myAnchorIdxIfAll = -1;

  protected RangeMarker myFieldRangeStart;

  public AbstractInplaceIntroduceFieldPopup(Project project,
                                            Editor editor,
                                            PsiExpression expr,
                                            PsiVariable localVariable,
                                            PsiExpression[] occurrences,
                                            TypeSelectorManagerImpl typeSelectorManager,
                                            @NlsContexts.Command String title,
                                            PsiClass parentClass,
                                            final PsiElement anchorElement,
                                            final PsiElement anchorElementIfAll) {
    super(project, editor, expr, localVariable, occurrences, typeSelectorManager, title);
    SmartPointerManager smartPointerManager = SmartPointerManager.getInstance(project);
    myParentClass = smartPointerManager.createSmartPsiElementPointer(parentClass);
    myAnchorElement = anchorElement != null ? smartPointerManager.createSmartPsiElementPointer(anchorElement) : null;
    myAnchorElementIfAll = anchorElementIfAll != null ? smartPointerManager.createSmartPsiElementPointer(anchorElementIfAll) : null;
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
    return getParentClass();
  }

  @Override
  protected SearchScope getReferencesSearchScope(VirtualFile file) {
    return new LocalSearchScope(getParentClass());
  }

  @Override
  protected boolean startsOnTheSameElement(RefactoringActionHandler handler, PsiElement element) {
    return super.startsOnTheSameElement(handler, element) ||
           getParentClass() != null &&
           element instanceof PsiLocalVariable &&
           getParentClass().findFieldByName(((PsiLocalVariable)element).getName(), false) != null;
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
    PsiClass parentClass = getParentClass();
    if (parentClass == null || !parentClass.isValid()) return null;
    PsiElement element = parentClass.getContainingFile().findElementAt(myFieldRangeStart.getStartOffset());
    if (element instanceof PsiWhiteSpace) {
      element = PsiTreeUtil.skipWhitespacesForward(element);
    }
    if (element instanceof PsiField) return (PsiVariable)element;
    final PsiField field = PsiTreeUtil.getParentOfType(element, PsiField.class, false);
    if (field != null) return field;
    element = PsiTreeUtil.skipWhitespacesBackward(element);
    return PsiTreeUtil.getParentOfType(element, PsiField.class, false);
  }

  protected PsiClass getParentClass() {
    return myParentClass.getElement();
  }
}
