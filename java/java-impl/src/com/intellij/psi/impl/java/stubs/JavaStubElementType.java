/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.psi.impl.java.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.PsiFileStub;
import com.intellij.psi.stubs.StubElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public abstract class JavaStubElementType<StubT extends StubElement, PsiT extends PsiElement> extends IStubElementType<StubT, PsiT> {
  private final boolean myLeftBound;

  protected JavaStubElementType(@NotNull @NonNls final String debugName) {
    this(debugName, false);
  }

  protected JavaStubElementType(@NotNull @NonNls final String debugName, final boolean leftBound) {
    super(debugName, StdFileTypes.JAVA != null ? StdFileTypes.JAVA.getLanguage() : null);
    myLeftBound = leftBound;
  }

  public String getExternalId() {
    return "java." + toString();
  }

  public boolean isCompiled(StubT stub) {
    StubElement parent = stub;
    while (!(parent instanceof PsiFileStub)) {
      parent = parent.getParentStub();
    }

    return ((PsiJavaFileStub)parent).isCompiled();
  }

  public abstract PsiT createPsi(ASTNode node);

  @Override
  public boolean isLeftBound() {
    return myLeftBound;
  }
}