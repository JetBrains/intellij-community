// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.stubs;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.psi.StubBasedPsiElement;
import org.jetbrains.annotations.ApiStatus;

/**
 * @author Dmitry Avdeev
 */
public abstract class ObjectStubBase<T extends Stub> extends UserDataHolderBase implements Stub {
  private static final Key<Boolean> DANGLING_STUB = Key.create("DIRECT_PARENT_IS_STUBBED");

  protected final T myParent;
  @ApiStatus.Internal
  public int id;

  public ObjectStubBase(T parent) {
    myParent = parent;
  }

  @Override
  public T getParentStub() {
    return myParent;
  }

  /**
   * @return whether the parent stub is not immediate, i.e., doesn't correspond to the actual AST parent node.
   * In this case, {@link StubBasedPsiElement#getParent()} should switch to AST.
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