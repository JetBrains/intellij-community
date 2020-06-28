// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.build.output;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vladislav.Soroka
 */
public interface BuildOutputInstantReader {
  @NotNull
  Object getParentEventId();

  @Nullable
  String readLine();

  void pushBack();

  void pushBack(int numberOfLines);
}
