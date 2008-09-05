/*
 * @author max
 */
package com.intellij.psi.stubs;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.util.io.KeyDescriptor;

public interface StubIndexExtension<Key, Psi extends PsiElement> {
  ExtensionPointName<StubIndexExtension> EP_NAME = ExtensionPointName.create("com.intellij.stubIndex");

  StubIndexKey<Key, Psi> getKey();
  int getVersion();
  KeyDescriptor<Key> getKeyDescriptor();
}