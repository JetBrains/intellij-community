/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

package com.intellij.pom.java.events;

import com.intellij.psi.PsiFile;

public class JavaTreeChanged implements PomJavaChange {
  private PsiFile myFile;

  public JavaTreeChanged(final PsiFile file) {
    myFile = file;
  }

  public PsiFile getFile() {
    return myFile;
  }

}
