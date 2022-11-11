// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.frameworkSupport;

import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 */
public class FrameworkRole {
  public static final FrameworkRole[] UNKNOWN = new FrameworkRole[0];

  private final String myId;

  public FrameworkRole(@NotNull String id) {
    myId = id;
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof FrameworkRole && myId.equals(((FrameworkRole)obj).myId);
  }

  @Override
  public String toString() {
    return myId;
  }
}
