// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.stubs;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NotNull;

/**
 * Definition of the index. Implement the
 * {@link IStubElementType#indexStub(Stub, IndexSink)}) function
 * in your language's Stub Elements to fill the index with data.
 *
 * @see IStubElementType#indexStub(Stub, IndexSink)}
 */
public interface StubIndexExtension<Key, Psi extends PsiElement> {
  ExtensionPointName<StubIndexExtension<?, ?>> EP_NAME = ExtensionPointName.create("com.intellij.stubIndex");

  @NotNull
  StubIndexKey<Key, Psi> getKey();

  int getVersion();

  @NotNull
  KeyDescriptor<Key> getKeyDescriptor();

  int getCacheSize();
}
