// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.console;

import com.intellij.ide.scratch.RootType;
import com.intellij.openapi.util.text.StringHash;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author gregsh
 */
public abstract class ConsoleRootType extends RootType {

  public static final String SEPARATOR = "-. . -..- - / . -. - .-. -.--";
  private static final String PATH_PREFIX = "consoles/";

  protected ConsoleRootType(@NonNls @NotNull String consoleTypeId, @Nls @Nullable String displayName) {
    super(PATH_PREFIX + consoleTypeId, displayName);
  }

  public final String getConsoleTypeId() {
    return getId().substring(PATH_PREFIX.length());
  }

  public @NotNull String getEntrySeparator() {
    return "\n" + SEPARATOR + "\n";
  }

  public @NotNull String getContentPathName(@NotNull String id) {
    return Long.toHexString(StringHash.calc(id));
  }

  public @NotNull String getHistoryPathName(@NotNull String id) {
    return Long.toHexString(StringHash.calc(id));
  }

  public @NotNull String getDefaultFileExtension() {
    return "txt";
  }
}
