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
package com.intellij.psi.stubs;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.psi.StubBasedPsiElement;

/**
 * @author Dmitry Avdeev
 * @since 3.08.2012
 */
public abstract class ObjectStubBase<T extends Stub> extends UserDataHolderBase implements Stub {
  private static final Key<Boolean> DANGLING_STUB = Key.create("DIRECT_PARENT_IS_STUBBED");

  protected final T myParent;
  int id;

  public ObjectStubBase(T parent) {
    myParent = parent;
  }

  @Override
  public T getParentStub() {
    return myParent;
  }

  /**
   * @return whether the parent stub is not immediate, i.e. doesn't correspond to the actual AST parent node. In this case,
   * {@link StubBasedPsiElement#getParent()} should switch to AST.
   */
  public boolean isDangling() {
    return Boolean.TRUE.equals(getUserData(DANGLING_STUB));
  }

  /**
   * @see #isDangling()
   */
  public void markDangling() {
    putUserData(DANGLING_STUB, true);
  }

  /**
   * @return the index of this stub in {@code ObjectStubTree#getPlainList}.
   */
  public int getStubId() {
    return id;
  }
}