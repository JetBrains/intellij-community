// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.ex;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class ChangesBlock<T> {
  @NotNull public final List<ChangedLines<T>> changes = new ArrayList<>();
  @NotNull public final List<Range> ranges = new ArrayList<>();
}
