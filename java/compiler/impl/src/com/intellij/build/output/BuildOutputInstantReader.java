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
  Object getBuildId();

  @Nullable
  String readLine();

  void pushBack();

  /***
   * Push back the given number of lines.
   * @param numberOfLines
   */
  void pushBack(int numberOfLines);

  String getCurrentLine();

  /***
   * Read lines until a given line is found or there are no more lines. The match is done by {@link String#equals(Object)} if endLine is not null.
   * @param endLine
   * @return All lines read until endLine is found or no more lines exist
   */
  @NotNull
  String readUntil(@Nullable String endLine);
}
