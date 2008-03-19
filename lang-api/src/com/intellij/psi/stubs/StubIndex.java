/*
 * @author max
 */
package com.intellij.psi.stubs;

import com.intellij.psi.PsiElement;

import java.util.Collection;

public abstract class StubIndex {
  public abstract <Key, Psi extends PsiElement> Collection<StubElement<Psi>> get(StubIndexKey<Key, Psi> index, Key key);
}