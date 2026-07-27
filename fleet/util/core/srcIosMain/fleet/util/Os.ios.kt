// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util

import fleet.util.multiplatform.Actual
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSProcessInfo
import kotlin.experimental.ExperimentalNativeApi

@OptIn(ExperimentalNativeApi::class)
@Actual
internal fun getNameNative(): String = Platform.osFamily.name.lowercase()

@OptIn(ExperimentalForeignApi::class)
@Actual
internal fun getVersionNative(): String = NSProcessInfo.processInfo.operatingSystemVersionString

@OptIn(ExperimentalNativeApi::class)
@Actual
internal fun getArchNative(): String = Platform.cpuArchitecture.name.lowercase()
