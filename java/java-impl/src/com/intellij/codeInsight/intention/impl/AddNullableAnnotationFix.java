/*
 * Created by IntelliJ IDEA.
 * User: cdr
 * Date: Jul 20, 2007
 * Time: 2:57:59 PM
 */
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.AnnotationUtil;

public class AddNullableAnnotationFix extends AddNullableNotNullAnnotationFix {
  public AddNullableAnnotationFix() {
    super(AnnotationUtil.NULLABLE, AnnotationUtil.NOT_NULL);
  }
}