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

/*
 * @author max
 */
package com.intellij.psi.stubs;

import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.ArrayFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface StubElement<T extends PsiElement> extends Stub {
  @Override
  IStubElementType getStubType();
  @Override
  StubElement getParentStub();
  @Override
  @NotNull
  List<StubElement> getChildrenStubs();

  @Nullable
  <P extends PsiElement> StubElement<P> findChildStubByType(@NotNull IStubElementType<?, P> elementType);

  T getPsi();

  @NotNull
  <E extends PsiElement> E[] getChildrenByType(@NotNull IElementType elementType, final E[] array);
  @NotNull
  <E extends PsiElement> E[] getChildrenByType(@NotNull TokenSet filter, final E[] array);

  @NotNull
  <E extends PsiElement> E[] getChildrenByType(@NotNull IElementType elementType, @NotNull ArrayFactory<E> f);
  @NotNull
  <E extends PsiElement> E[] getChildrenByType(@NotNull TokenSet filter, @NotNull ArrayFactory<E> f);

  @Nullable
  <E extends PsiElement> E getParentStubOfType(@NotNull Class<E> parentClass);
}