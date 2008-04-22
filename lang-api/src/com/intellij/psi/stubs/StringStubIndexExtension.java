/*
 * @author max
 */
package com.intellij.psi.stubs;

import com.intellij.psi.PsiElement;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.PersistentEnumerator;

public abstract class StringStubIndexExtension<Psi extends PsiElement> extends AbstractStubIndex<String, Psi> {
  public int getVersion() {
    return 2;
  }

  public PersistentEnumerator.DataDescriptor<String> getKeyDescriptor() {
    return new EnumeratorStringDescriptor();
  }
}