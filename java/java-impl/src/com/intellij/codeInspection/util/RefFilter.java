/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Dec 1, 2001
 * Time: 11:42:56 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.util;

import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.reference.RefJavaElement;
import com.intellij.codeInspection.reference.RefParameter;

public abstract class RefFilter {
  // Default accepts implementation accepts element if one under unaccepted one. Thus it will accept all and only upper level classes.
  public int getElementProblemCount(RefJavaElement refElement) {
    if (refElement instanceof RefParameter) return 0;
    RefEntity refOwner = refElement.getOwner();
    if (refOwner == null || !(refOwner instanceof RefJavaElement)) return 1;

    return 1 - getElementProblemCount((RefJavaElement)refOwner);
  }

  public final boolean accepts(RefJavaElement refElement) {
    return getElementProblemCount(refElement) > 0;
  }
}
