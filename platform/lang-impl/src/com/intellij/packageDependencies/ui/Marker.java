/*
 * User: anna
 * Date: 16-Jan-2008
 */
package com.intellij.packageDependencies.ui;

import com.intellij.psi.PsiFile;

public interface Marker {
  boolean isMarked(PsiFile file);
}