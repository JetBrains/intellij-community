/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Aug 23, 2006
 * Time: 3:56:42 PM
 */
package com.intellij.formatting;

import com.intellij.psi.PsiElement;

public interface CustomFormattingModelBuilder extends FormattingModelBuilder {
  /**
   * Implementors of the method must decide if this particular builder is responsible to format a <code>context</code> passed.
   * @param context a PSI context for the builder to decide if it is responsible to format these kind of things.
   * @return <code>true</code> if this particular builder shall be used to format <code>context</code>
   */
  boolean isEngagedToFormat(PsiElement context);
}