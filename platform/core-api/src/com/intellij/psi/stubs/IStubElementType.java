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
package com.intellij.psi.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class IStubElementType<StubT extends StubElement, PsiT extends PsiElement> extends IElementType implements StubSerializer<StubT> {
  public IStubElementType(@NotNull @NonNls final String debugName, @Nullable final Language language) {
    super(debugName, language);
  }

  public abstract PsiT createPsi(@NotNull StubT stub);

  public abstract StubT createStub(@NotNull PsiT psi, final StubElement parentStub);

  public boolean shouldCreateStub(ASTNode node) {
    return true;
  }

  public String getId(StubT stub) {
    assert stub.getStubType() == this;

    final StubElement parent = stub.getParentStub();
    int count = 0;
    for (Object child : parent.getChildrenStubs()) {
      if (((StubElement)child).getStubType() == this) count++;
      if (child == stub) {
        return '#' + String.valueOf(count);
      }
    }

    throw new RuntimeException("Parent/child relations corrupted");
  }
}