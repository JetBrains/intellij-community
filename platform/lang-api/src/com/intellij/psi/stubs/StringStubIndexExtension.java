/*
 * @author max
 */
package com.intellij.psi.stubs;

import com.intellij.psi.PsiElement;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;

public abstract class StringStubIndexExtension<Psi extends PsiElement> extends AbstractStubIndex<String, Psi> {
  public int getVersion() {
    return 2;
  }

  public KeyDescriptor<String> getKeyDescriptor() {
    return new EnumeratorStringDescriptor();
  }
}