/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package com.intellij.psi.stubs;

import com.intellij.psi.PsiNamedElement;
import com.intellij.util.io.StringRef;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public abstract class NamedStubBase<T extends PsiNamedElement> extends StubBase<T> implements NamedStub<T> {
  private final StringRef myName;

  protected NamedStubBase(StubElement parent, @NotNull IStubElementType elementType, StringRef name) {
    super(parent, elementType);
    myName = name;
  }

  protected NamedStubBase(final StubElement parent, @NotNull IStubElementType elementType, final String name) {
    this(parent, elementType, StringRef.fromString(name));
  }

  @Override
  public String getName() {
    return myName.getString();
  }
}
