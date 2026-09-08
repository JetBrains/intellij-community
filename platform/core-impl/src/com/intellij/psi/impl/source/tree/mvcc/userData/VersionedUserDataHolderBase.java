// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree.mvcc.userData;

import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.UserDataHolderEx;
import com.intellij.psi.impl.source.tree.mvcc.InternalPsiVersioning;
import com.intellij.psi.impl.source.tree.mvcc.PsiVersionCleanable;
import com.intellij.util.keyFMap.KeyFMap;
import com.intellij.util.xmlb.annotations.Transient;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

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
public abstract class VersionedUserDataHolderBase extends UserDataHolderBase implements PsiVersionCleanable {

  public VersionedUserDataHolderBase() {
    super.setUserMap(VersionedUserDataFMap.empty());
  }

  @Override
  protected boolean changeUserMap(@NotNull KeyFMap oldMap, @NotNull KeyFMap newMap) {
    VersionedUserDataFMap newVersionedMap = VersionedUserDataFMap.from(newMap);
    boolean changed = super.changeUserMap(oldMap, newVersionedMap);
    if (!changed) return false;
    if (newVersionedMap.isEligibleForAsyncCleanup()) {
      InternalPsiVersioning.recordVersionedChange(this);
    }
    return true;
  }

  @Override
  protected void setUserMap(@NotNull KeyFMap map) {
    VersionedUserDataFMap versionedMap = VersionedUserDataFMap.from(map);
    super.setUserMap(versionedMap);
    if (versionedMap.isEligibleForAsyncCleanup()) {
      InternalPsiVersioning.recordVersionedChange(this);
    }
  }

  @Override
  public void liveVersionChanged(long minVersion, @NotNull Set<Long> liveVersions) {
    KeyFMap oldMap = getUserMap();
    VersionedUserDataFMap oldVersionedMap = VersionedUserDataFMap.from(oldMap);
    VersionedUserDataFMap cleanedMap = oldVersionedMap.cleanup(minVersion);
    if (cleanedMap == oldVersionedMap) return;
    // We are not doing regular CAS loop here. If someone else succeeded, they will submit the garbage collection separately
    super.changeUserMap(oldMap, cleanedMap);
  }
}
