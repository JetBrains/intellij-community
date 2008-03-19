/*
 * @author max
 */
package com.intellij.psi.stubs;

import com.intellij.psi.PsiElement;
import com.intellij.util.io.PersistentEnumerator;

public interface StubIndexExtension<Key, Psi extends PsiElement> {
  StubIndexKey<Key, Psi> getKey();
  int getVersion();
  PersistentEnumerator.DataDescriptor<Key> getKeyDescriptor();
}