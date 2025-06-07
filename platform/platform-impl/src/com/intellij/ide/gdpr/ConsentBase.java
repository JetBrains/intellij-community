// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.gdpr;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public abstract class ConsentBase {
  private final String myId;
  private final Version myVersion;

  ConsentBase(String id, Version version) {
    myId = id;
    myVersion = version;
  }

  public String getId() {
    return myId;
  }

  public Version getVersion() {
    return myVersion;
  }

  public abstract boolean isAccepted();

  @Override
  public abstract String toString();

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final ConsentBase that = (ConsentBase)o;

    if (!myId.equals(that.myId)) {
      return false;
    }
    if (!myVersion.equals(that.myVersion)) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    int result = myId.hashCode();
    result = 31 * result + myVersion.hashCode();
    return result;
  }
}
