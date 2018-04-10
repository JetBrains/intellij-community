/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.psi.impl.java.stubs.impl;

import com.intellij.psi.PsiNameValuePair;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiNameValuePairStub;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 */
public class PsiNameValuePairStubImpl extends StubBase<PsiNameValuePair> implements PsiNameValuePairStub {

  @Nullable private final String myName;
  @Nullable private final String myValue;

  public PsiNameValuePairStubImpl(StubElement parent, @Nullable String name, @Nullable String value) {
    super(parent, JavaStubElementTypes.NAME_VALUE_PAIR);
    myName = name;
    myValue = value;
  }

  @Nullable
  @Override
  public String getName() {
    return myName;
  }

  @Nullable
  @Override
  public String getValue() {
    return myValue;
  }
}
