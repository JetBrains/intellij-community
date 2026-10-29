// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.fs

import com.intellij.platform.eel.fs.EelPrefetchApi
import com.intellij.platform.eel.fs.EelFileSystemApi
import com.intellij.platform.eel.fs.EelFileSystemPosixApi
import com.intellij.platform.eel.fs.EelFileSystemWindowsApi
import com.intellij.platform.eel.fs.EelSearchApi
import com.intellij.platform.util.annotations.VisibleToClasses

@VisibleToClasses(
  "com.intellij.docker.ijent.DockerMountsAwareFileSystemProvider",
  "com.intellij.platform.ijent.community.impl.DelegateHolder",
  "com.intellij.platform.ijent.community.impl.IjentFailSafeFileSystemApiKt",
  "com.intellij.platform.ijent.community.impl.IjentFailSafeFileSystemApiTest",
  "com.intellij.platform.ijent.community.impl.IjentFailSafeFileSystemPosixApiImpl",
  "com.intellij.platform.ijent.community.impl.IjentFailSafeFileSystemWindowsApiImpl",
  "com.intellij.platform.ijent.community.impl.nio.IjentNioBasicFileAttributeView",
  "com.intellij.platform.ijent.community.impl.nio.IjentNioFileStore",
  "com.intellij.platform.ijent.community.impl.nio.IjentNioFileSystem",
  "com.intellij.platform.ijent.community.impl.nio.IjentNioFileSystemProvider",
  "com.intellij.platform.ijent.community.impl.nio.IjentNioWatchService",
  "com.intellij.platform.ijent.testFramework.functional.fixture.EelFixtureOverridingServiceKt",
  "com.intellij.platform.ijent.testFramework.functional.fs.IjentFileSystemTestUtilKt",
)
sealed interface IjentFileSystemApi : EelFileSystemApi, EelPrefetchApi, EelSearchApi

interface IjentFileSystemPosixApi : IjentFileSystemApi, EelFileSystemPosixApi

interface IjentFileSystemWindowsApi : IjentFileSystemApi, EelFileSystemWindowsApi