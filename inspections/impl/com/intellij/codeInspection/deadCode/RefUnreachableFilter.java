/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Dec 2, 2001
 * Time: 12:07:30 AM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.deadCode;

import com.intellij.codeInspection.ex.InspectionTool;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefElementImpl;
import com.intellij.codeInspection.reference.RefParameter;
import com.intellij.codeInspection.util.RefFilter;
import com.intellij.psi.PsiDocCommentOwner;
import com.intellij.psi.PsiElement;

public class RefUnreachableFilter extends RefFilter {
  protected InspectionTool myTool;

  public RefUnreachableFilter(final InspectionTool tool) {
    myTool = tool;
  }

  public int getElementProblemCount(RefElement refElement) {
    if (refElement instanceof RefParameter) return 0;
    if (refElement.isSyntheticJSP()) return 0;
    final PsiElement element = refElement.getElement();
    if (!(element instanceof PsiDocCommentOwner) || !myTool.getContext().isToCheckMember(refElement, myTool)) return 0;
    return ((RefElementImpl)refElement).isSuspicious() ? 1 : 0;
  }
}
