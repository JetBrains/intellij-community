/*
 * Created by IntelliJ IDEA.
 * User: cdr
 * Date: Jul 20, 2007
 * Time: 2:57:38 PM
 */
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.psi.PsiModifierListOwner;

public class AddNotNullAnnotationFix extends AddNullableNotNullAnnotationFix {
  public AddNotNullAnnotationFix() {
    super(AnnotationUtil.NOT_NULL, AnnotationUtil.NULLABLE);
  }
  public AddNotNullAnnotationFix(PsiModifierListOwner owner) {
    super(AnnotationUtil.NOT_NULL, owner, AnnotationUtil.NULLABLE);
  }
}