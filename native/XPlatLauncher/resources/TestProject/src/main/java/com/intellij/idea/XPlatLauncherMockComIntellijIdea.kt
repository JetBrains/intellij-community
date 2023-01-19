// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.idea

object AppExitCodes {
  const val NO_GRAPHICS = 1
  const val RESTART_FAILED = 2
  const val STARTUP_EXCEPTION = 3

  // reserved: public static final int JDK_CHECK_FAILED = 4;
  const val DIR_CHECK_FAILED = 5
  const val INSTANCE_CHECK_FAILED = 6
  const val LICENSE_ERROR = 7
  const val PLUGIN_ERROR = 8

  // reserved (doesn't seem to ever be used): public static final int OUT_OF_MEMORY = 9;
  // reserved (permanently if launchers will perform the check): public static final int UNSUPPORTED_JAVA_VERSION = 10;
  const val PRIVACY_POLICY_REJECTION = 11
  const val INSTALLATION_CORRUPTED = 12
  const val ACTIVATE_WRONG_TOKEN_CODE = 13
  const val ACTIVATE_NOT_INITIALIZED = 14
  const val ACTIVATE_ERROR = 15
  const val ACTIVATE_DISPOSING = 16
}
