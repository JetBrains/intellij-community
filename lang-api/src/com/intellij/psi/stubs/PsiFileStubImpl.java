/*
 * @author max
 */
package com.intellij.psi.stubs;

import com.intellij.psi.PsiFile;

public class PsiFileStubImpl extends StubBase<PsiFile> implements PsiFileStub {
  private PsiFile myFile;

  public PsiFileStubImpl(final PsiFile file) {
    super(null, null);
    myFile = file;
  }

  public PsiFile getPsi() {
    return myFile;
  }

  public void setPsi(final PsiFile psi) {
    myFile = psi;
  }

  public IStubElementType getStubType() {
    throw new UnsupportedOperationException("getStubType is not implemented");
  }
}