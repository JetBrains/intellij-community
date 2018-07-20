// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

import org.jetbrains.annotations.Nullable;

public class Developer {
  public static final Developer NULL = new Developer(-1, "<none>");

  private final int myId;
  private final String myName;

  public Developer(int id, String name) {
    myId = id;
    myName = name;
  }

  @Nullable
  public Integer getId() {
    return this == NULL ? null : myId;
  }

  public String getDisplayText() {
    return myName;
  }

  @Override
  public String toString() {
    return String.format("%d - %s", myId, myName);
  }
}