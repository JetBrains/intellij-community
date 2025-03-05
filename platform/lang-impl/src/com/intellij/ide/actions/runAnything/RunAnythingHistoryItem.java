// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.runAnything;

final class RunAnythingHistoryItem {
  final String pattern, type, fqn;

  RunAnythingHistoryItem(String pattern, String type, String fqn) {
    this.pattern = pattern;
    this.type = type;
    this.fqn = fqn;
  }

  @Override
  public String toString() {
    return pattern + "\t" + type + "\t" + fqn;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    RunAnythingHistoryItem item = (RunAnythingHistoryItem)o;

    if (!pattern.equals(item.pattern)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return pattern.hashCode();
  }
}