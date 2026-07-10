// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.fs

import com.intellij.platform.eel.EelExecPosixApi
import com.intellij.platform.eel.EelExecWindowsApi
import com.intellij.platform.eel.LoginShellSpawner

interface IjentExecPosixApi : EelExecPosixApi, LoginShellSpawner

interface IjentExecWindowsApi : EelExecWindowsApi, LoginShellSpawner