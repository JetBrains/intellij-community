/*
 * @author max
 */
package com.intellij.psi.stubs;

import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface StubElement<T extends PsiElement> {
  IStubElementType getStubType();
  StubElement getParentStub();
  List<StubElement> getChildrenStubs();
  @Nullable
  StubElement findChildStubByType(IElementType elementType);

  T getPsi();
}