/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.psi.PsiRequiresStatement;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiRequiresStatementStub;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;

public class PsiRequiresStatementStubImpl extends StubBase<PsiRequiresStatement> implements PsiRequiresStatementStub {
  private static final byte PUBLIC = 0x01;
  private static final byte STATIC = 0x02;

  private final String myModuleName;
  private final byte myFlags;

  public PsiRequiresStatementStubImpl(StubElement parent, String refText, boolean isPublic, boolean isStatic) {
    this(parent, refText, (byte)((isPublic ? PUBLIC : 0) | (isStatic ? STATIC : 0)));
  }

  public PsiRequiresStatementStubImpl(StubElement parent, String refText, byte flags) {
    super(parent, JavaStubElementTypes.REQUIRES_STATEMENT);
    myModuleName = refText;
    myFlags = flags;
  }

  @Override
  public String getModuleName() {
    return myModuleName;
  }

  @Override
  public byte getFlags() {
    return myFlags;
  }

  @Override
  public boolean isPublic() {
    return (myFlags & PUBLIC) != 0;
  }

  @Override
  public boolean isStatic() {
    return (myFlags & STATIC) != 0;
  }

  @Override
  public String toString() {
    return "PsiRequiresStatementStub:" + myFlags + ":" + myModuleName;
  }
}