// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle.arrangement;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class JavaArrangementOverriddenMethodsInfo {

  private final @NotNull List<JavaElementArrangementEntry> myMethodEntries = new ArrayList<>();
  // Name of base class, which contains overridden methods
  // Used only for debug purposes
  private final String myName;

  public JavaArrangementOverriddenMethodsInfo(String name) {
    myName = name;
  }

  public void addMethodEntry(@NotNull JavaElementArrangementEntry entry) {
    myMethodEntries.add(entry);
  }

  public @NotNull String getName() {
    return myName;
  }

  public @NotNull List<JavaElementArrangementEntry> getMethodEntries() {
    return myMethodEntries;
  }

  @Override
  public String toString() {
    return "methods from " + myName;
  }
}
