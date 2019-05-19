// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.build.output;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vladislav.Soroka
 */
@ApiStatus.Experimental
public interface BuildOutputInstantReader {
  @NotNull
  Object getParentEventId();

  @Nullable
  String readLine();

  void pushBack();

  /**
   * Push back the given number of lines.
   */
  void pushBack(int numberOfLines);

  String getCurrentLine();
}
