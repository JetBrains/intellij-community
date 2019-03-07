// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.Nullable;

@Tag("developer")
public class Developer {
  public static final Developer NULL = new Developer(-1, "<none>");

  @Attribute("id")
  private final int myId;

  @Attribute("name")
  private final String myName;

  @SuppressWarnings("unused") // need for xml serialization
  private Developer() {
    this(0, "");
  }

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