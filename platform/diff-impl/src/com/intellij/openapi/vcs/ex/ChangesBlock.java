// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.ex;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class ChangesBlock<T> {
  public final @NotNull List<ChangedLines<T>> changes = new ArrayList<>();
  public final @NotNull List<Range> ranges = new ArrayList<>();
}
