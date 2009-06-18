/*
 * @author max
 */
package com.intellij.psi.stubs;

import com.intellij.openapi.util.UserDataHolder;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IStubFileElementType;

public interface PsiFileStub<T extends PsiFile> extends StubElement<T>, UserDataHolder {
  IStubFileElementType getType();
}
