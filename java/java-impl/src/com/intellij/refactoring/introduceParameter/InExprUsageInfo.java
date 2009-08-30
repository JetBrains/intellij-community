/*
* Created by IntelliJ IDEA.
* User: dsl
* Date: 06.05.2002
* Time: 15:02:15
* To change template for new class use
* Code Style | Class Templates options (Tools | IDE Options).
*/
package com.intellij.refactoring.introduceParameter;

import com.intellij.psi.PsiElement;
import com.intellij.usageView.UsageInfo;

/**
 * Usage inside an expression
 */
public class InExprUsageInfo extends UsageInfo {
  InExprUsageInfo(PsiElement elem) {
    super(elem);
  }

}
