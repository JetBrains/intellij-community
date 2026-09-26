// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.fs

import com.intellij.platform.eel.fs.DirectoryPrefetcher
import com.intellij.platform.eel.fs.EelFileSystemApi
import com.intellij.platform.eel.fs.EelFileSystemPosixApi
import com.intellij.platform.eel.fs.EelFileSystemWindowsApi
import com.intellij.platform.eel.fs.EelSearchApi

sealed interface IjentFileSystemApi : EelFileSystemApi, DirectoryPrefetcher, EelSearchApi

interface IjentFileSystemPosixApi : IjentFileSystemApi, EelFileSystemPosixApi

interface IjentFileSystemWindowsApi : IjentFileSystemApi, EelFileSystemWindowsApi