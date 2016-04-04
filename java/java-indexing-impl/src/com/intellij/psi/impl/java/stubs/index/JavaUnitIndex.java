/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.impl.java.stubs.index;

import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.impl.java.stubs.hierarchy.IndexTree;
import com.intellij.psi.stubs.AbstractStubIndex;
import com.intellij.psi.stubs.StubIndexKey;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NotNull;

public class JavaUnitIndex extends AbstractStubIndex<IndexTree.Unit, PsiClassOwner> {

  @NotNull
  @Override
  public StubIndexKey<IndexTree.Unit, PsiClassOwner> getKey() {
    return JavaStubIndexKeys.UNITS;
  }

  @Override
  public int getVersion() {
    return 1;
  }

  @NotNull
  @Override
  public KeyDescriptor<IndexTree.Unit> getKeyDescriptor() {
    return JavaUnitKeyDescriptor.INSTANCE;
  }
}
