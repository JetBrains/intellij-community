/*
 * @author max
 */
package com.intellij.psi.stubs;

import com.intellij.psi.PsiFile;

public class PsiFileStubImpl extends StubBase<PsiFile> implements PsiFileStub {
  public PsiFileStubImpl() {
    super(null);
  }

  public IStubElementType getStubType() {
    throw new UnsupportedOperationException("getStubType is not implemented");
  }
}