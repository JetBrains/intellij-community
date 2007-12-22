/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Dec 2, 2001
 * Time: 12:14:37 AM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.deadCode;

import com.intellij.codeInspection.ex.InspectionTool;
import com.intellij.codeInspection.reference.*;
import com.intellij.psi.PsiDocCommentOwner;
import com.intellij.psi.PsiElement;

public class UnreferencedFilter extends RefUnreachableFilter {
  public UnreferencedFilter(final InspectionTool tool) {
    super(tool);
  }

  public int getElementProblemCount(RefJavaElement refElement) {
    if (refElement instanceof RefParameter) return 0;
    if (refElement.isEntry() || !((RefElementImpl)refElement).isSuspicious() || refElement.isSyntheticJSP()) return 0;

    final PsiElement element = refElement.getElement();
    if (!(element instanceof PsiDocCommentOwner) || !myTool.getContext().isToCheckMember(refElement, myTool)) return 0;

    if (refElement instanceof RefField) {
      RefField refField = (RefField) refElement;
      if (refField.isUsedForReading() && !refField.isUsedForWriting()) return 1;
      if (refField.isUsedForWriting() && !refField.isUsedForReading()) return 1;
    }

    if (refElement instanceof RefClass && ((RefClass)refElement).isAnonymous()) return 0;
    return -1;
  }
}
