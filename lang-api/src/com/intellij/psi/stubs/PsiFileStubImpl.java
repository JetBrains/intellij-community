/*
 * @author max
 */
package com.intellij.psi.stubs;

import com.intellij.psi.PsiFile;

public class PsiFileStubImpl<T extends PsiFile> extends StubBase<T> implements PsiFileStub<T> {
  private T myFile;

  public PsiFileStubImpl(final T file) {
    super(null, null);
    myFile = file;
  }

  public T getPsi() {
    return myFile;
  }

  public void setPsi(final T psi) {
    myFile = psi;
  }

  public IStubElementType getStubType() {
    throw new UnsupportedOperationException("getStubType is not implemented");
  }
}