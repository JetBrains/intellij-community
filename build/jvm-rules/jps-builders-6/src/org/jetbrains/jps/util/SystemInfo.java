// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.util;

import java.util.Locale;

public final class SystemInfo {
  private static final String _OS_NAME = System.getProperty("os.name").toLowerCase(Locale.ENGLISH);
  public static final boolean isMac = _OS_NAME.startsWith("mac");
  public static final boolean isWindows = _OS_NAME.startsWith("windows");
  public static final boolean isOS2 = _OS_NAME.startsWith("os/2") || _OS_NAME.startsWith("os2");
  public static final boolean isFileSystemCaseSensitive = !isWindows && !isOS2 && !isMac;
}
