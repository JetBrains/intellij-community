// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree.mvcc.userData;

import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.UserDataHolderEx;
import com.intellij.util.keyFMap.KeyFMap;
import com.intellij.util.xmlb.annotations.Transient;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * A base class for implementors of {@link UserDataHolderEx} which are intended for usage in the versioned environment.
 * <p>
 * By default, user data keys behave as versioned references -- the values installed in future versions
 * are not visible to clients that operate in earlier versions.
 * <p>
 * This class works on top of {@link VersionedUserDataFMap} that stores versioned references to user data.
 * <p>
 */
@Transient
@ApiStatus.Experimental
public abstract class VersionedUserDataHolderBase extends UserDataHolderBase {

  public VersionedUserDataHolderBase() {
    super.setUserMap(VersionedUserDataFMap.empty());
  }

  @Override
  protected boolean changeUserMap(@NotNull KeyFMap oldMap, @NotNull KeyFMap newMap) {
    VersionedUserDataFMap newVersionedMap = VersionedUserDataFMap.from(newMap);
    return super.changeUserMap(oldMap, newVersionedMap);
  }

  @Override
  protected void setUserMap(@NotNull KeyFMap map) {
    VersionedUserDataFMap versionedMap = VersionedUserDataFMap.from(map);
    super.setUserMap(versionedMap);
  }
}
