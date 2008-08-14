/*
 * User: anna
 * Date: 07-Aug-2008
 */
package com.intellij.codeInspection;

import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.psi.PsiFile;

public class LocalInspectionToolSession extends UserDataHolderBase {
  private PsiFile myFile;
  private int myStartOffset;
  private int myEndOffset;

  public LocalInspectionToolSession(final PsiFile file, final int startOffset, final int endOffset) {
    myFile = file;
    myStartOffset = startOffset;
    myEndOffset = endOffset;
  }

  public PsiFile getFile() {
    return myFile;
  }

  public int getStartOffset() {
    return myStartOffset;
  }

  public int getEndOffset() {
    return myEndOffset;
  }
}