/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Dec 2, 2001
 * Time: 12:05:14 AM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.deadCode;

import com.intellij.codeInspection.reference.RefJavaElement;
import com.intellij.codeInspection.reference.RefParameter;
import com.intellij.codeInspection.util.RefFilter;

public class RefEntryPointFilter extends RefFilter {
  public int getElementProblemCount(RefJavaElement refElement) {
    if (refElement instanceof RefParameter) return 0;
    return refElement.isEntry() && !refElement.isSyntheticJSP() ? 1 : 0;
  }
}
