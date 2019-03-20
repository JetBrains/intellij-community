// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.largeFilesEditor.accessGettingPageTokens;

import org.jetbrains.annotations.NotNull;

public abstract class TokenLockBase implements ITokenLock {

  private AccessGettingPageToken activeToken = null;

  @Override
  public void release() {
    activeToken = null;
  }

  @Override
  public boolean trySetNewToken(AccessGettingPageToken token) {
    if (canBeSet(token)) {
      activeToken = token;
      return true;
    }
    return false;
  }

  @Override
  public AccessGettingPageToken getActiveToken() {
    return activeToken;
  }

  @Override
  public String toString() {
    return "active token = {" + activeToken.toString() + "}";
  }

  @Override
  public abstract boolean canBeSet(@NotNull AccessGettingPageToken token);
}
