/*
 * @author max
 */
package com.intellij.psi.stubs;

import com.intellij.psi.PsiElement;
import com.intellij.util.io.EnumeratorIntegerDescriptor;
import com.intellij.util.io.KeyDescriptor;

public abstract class IntStubIndexExtension<Psi extends PsiElement> extends AbstractStubIndex<Integer, Psi> {
  public int getVersion() {
    return 1;
  }

  public KeyDescriptor<Integer> getKeyDescriptor() {
    return new EnumeratorIntegerDescriptor();
  }
}