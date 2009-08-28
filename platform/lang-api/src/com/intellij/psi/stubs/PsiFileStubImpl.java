/*
 * @author max
 */
package com.intellij.psi.stubs;

import com.intellij.lang.Language;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IStubFileElementType;

public class PsiFileStubImpl<T extends PsiFile> extends StubBase<T> implements PsiFileStub<T> {
  public static final IStubFileElementType TYPE = new IStubFileElementType(Language.ANY);
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
    return null;
  }

  public IStubFileElementType getType() {
    return TYPE;
  }
}