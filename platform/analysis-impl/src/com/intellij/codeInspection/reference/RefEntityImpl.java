// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInspection.reference;

import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.util.BitUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public abstract class RefEntityImpl extends UserDataHolderBase implements RefEntity, WritableRefEntity {
  private volatile WritableRefEntity myOwner;
  private List<RefEntity> myChildren;  // guarded by this
  private final String myName;
  protected long myFlags; // guarded by this
  protected final RefManagerImpl myManager;

  protected RefEntityImpl(@NotNull String name, @NotNull RefManager manager) {
    myManager = (RefManagerImpl)manager;
    myName = myManager.internName(name);
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @NotNull
  @Override
  public String getQualifiedName() {
    return myName;
  }

  @NotNull
  @Override
  public synchronized List<RefEntity> getChildren() {
    return ObjectUtils.notNull(myChildren, ContainerUtil.emptyList());
  }

  @Override
  public WritableRefEntity getOwner() {
    return myOwner;
  }

  @Override
  public void setOwner(@Nullable final WritableRefEntity owner) {
    myOwner = owner;
  }

  @Override
  public synchronized void add(@NotNull final RefEntity child) {
    addChild(child);
    ((RefEntityImpl)child).setOwner(this);
  }

  protected synchronized void addChild(@NotNull RefEntity child) {
    List<RefEntity> children = myChildren;
    if (children == null) {
      myChildren = children = new ArrayList<>(1);
    }
    children.add(child);
  }

  @Override
  public synchronized void removeChild(@NotNull final RefEntity child) {
    if (myChildren != null) {
      myChildren.remove(child);
      ((WritableRefEntity)child).setOwner(null);
    }
  }

  public String toString() {
    return getName();
  }

  @Override
  public void accept(@NotNull final RefVisitor refVisitor) {
    DumbService.getInstance(myManager.getProject()).runReadActionInSmartMode(() -> refVisitor.visitElement(this));
  }

  public synchronized boolean checkFlag(long mask) {
    return BitUtil.isSet(myFlags, mask);
  }

  public synchronized void setFlag(final boolean value, final long mask) {
    myFlags = BitUtil.set(myFlags, mask, value);
  }

  @Override
  public String getExternalName() {
    return myName;
  }

  @NotNull
  @Override
  public RefManagerImpl getRefManager() {
    return myManager;
  }
}
