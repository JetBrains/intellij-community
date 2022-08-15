// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.idea;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class AppExitCodes {
  public static final int NO_GRAPHICS = 1;
  public static final int RESTART_FAILED = 2;
  public static final int STARTUP_EXCEPTION = 3;
  // reserved: public static final int JDK_CHECK_FAILED = 4;
  public static final int DIR_CHECK_FAILED = 5;
  public static final int INSTANCE_CHECK_FAILED = 6;
  public static final int LICENSE_ERROR = 7;
  public static final int PLUGIN_ERROR = 8;
  // reserved (doesn't seem to ever be used): public static final int OUT_OF_MEMORY = 9;
  // reserved (permanently if launchers will perform the check): public static final int UNSUPPORTED_JAVA_VERSION = 10;
  public static final int PRIVACY_POLICY_REJECTION = 11;
  public static final int INSTALLATION_CORRUPTED = 12;
  public static final int ACTIVATE_WRONG_TOKEN_CODE = 13;
  public static final int ACTIVATE_NOT_INITIALIZED = 14;
  public static final int ACTIVATE_ERROR = 15;
  public static final int ACTIVATE_DISPOSING = 16;
}
