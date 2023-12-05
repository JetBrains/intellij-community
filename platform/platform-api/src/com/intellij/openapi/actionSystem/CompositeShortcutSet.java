// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CompositeShortcutSet implements ShortcutSet {
  private final ShortcutSet[] sets;

  public CompositeShortcutSet(ShortcutSet... sets) {
    this.sets = sets;
  }

  @Override
  public Shortcut @NotNull [] getShortcuts() {
    List<Shortcut> result = new ArrayList<>();
    for (ShortcutSet each : sets) {
      Collections.addAll(result, each.getShortcuts());
    }
    return result.toArray(Shortcut.EMPTY_ARRAY);
  }

  @Override
  public boolean hasShortcuts() {
    for (ShortcutSet each : sets) {
      if (each.hasShortcuts()) {
        return true;
      }
    }
    return false;
  }
}
