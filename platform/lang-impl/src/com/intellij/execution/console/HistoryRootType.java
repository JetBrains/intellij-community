// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.console;

import com.intellij.ide.scratch.RootType;
import org.jetbrains.annotations.NotNull;

/**
 * @author gregsh
 */
public final class HistoryRootType extends RootType {

  public HistoryRootType() {
    super("consoles/.history", null);
  }

  public static @NotNull HistoryRootType getInstance() {
    return findByClass(HistoryRootType.class);
  }

}
