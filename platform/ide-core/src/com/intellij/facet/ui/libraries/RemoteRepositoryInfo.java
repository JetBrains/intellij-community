// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.facet.ui.libraries;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class RemoteRepositoryInfo {
  private static final Logger LOG = Logger.getInstance(RemoteRepositoryInfo.class);
  private final String myId;
  private final String myPresentableName;
  private final String[] myMirrors;

  public RemoteRepositoryInfo(@NotNull @NonNls String id, final @NotNull @Nls String presentableName, final @NonNls String @NotNull [] mirrors) {
    myId = id;
    LOG.assertTrue(mirrors.length > 0);
    myPresentableName = presentableName;
    myMirrors = mirrors;
  }

  public String getId() {
    return myId;
  }

  public String getPresentableName() {
    return myPresentableName;
  }

  public String[] getMirrors() {
    return myMirrors;
  }

  public String getDefaultMirror() {
    return myMirrors[0];
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final RemoteRepositoryInfo that = (RemoteRepositoryInfo)o;
    return myId.equals(that.myId);

  }

  @Override
  public int hashCode() {
    return myId.hashCode();
  }
}