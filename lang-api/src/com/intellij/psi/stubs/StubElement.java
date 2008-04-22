/*
 * @author max
 */
package com.intellij.psi.stubs;

import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface StubElement<T extends PsiElement> {
  IStubElementType getStubType();
  StubElement getParentStub();
  List<StubElement> getChildrenStubs();

  @Nullable
  <P extends PsiElement> StubElement<P> findChildStubByType(IStubElementType<?, P> elementType);

  T getPsi();

  <E extends PsiElement> E[] getChildrenByType(final IElementType elementType, final E[] array);
  <E extends PsiElement> E[] getChildrenByType(final TokenSet filter, final E[] array);

  @Nullable
  <E extends PsiElement> E getParentStubOfType(final Class<E> parentClass);
}