/*
 * @author max
 */
package com.intellij.psi.stubs;

import com.intellij.psi.PsiElement;
import com.intellij.util.io.EnumeratorIntegerDescriptor;
import com.intellij.util.io.PersistentEnumerator;

public abstract class IntStubIndexExtension<Psi extends PsiElement> implements StubIndexExtension<Integer, Psi> {
  public int getVersion() {
    return 1;
  }

  public PersistentEnumerator.DataDescriptor<Integer> getKeyDescriptor() {
    return new EnumeratorIntegerDescriptor();
  }
}