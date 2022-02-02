// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.externalProcessAuthHelper;

/**
 * Flag to get user credentials in different interaction modes:
 * NONE: no authentication will be performed if password is requested
 * (Also, native credential helper should be disabled manually as a command parameter);
 * SILENT: the IDE will look for passwords in the common password storages. if no password is found, no authentication will be performed;
 * FULL: the IDE will look for passwords in the common password storages. If no password is found, an authentication dialog will be displayed.
 */
public enum GitAuthenticationMode {
  NONE, SILENT, FULL
}
