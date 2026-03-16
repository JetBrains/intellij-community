// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util

import fleet.util.multiplatform.Actual
import platform.Foundation.NSProcessInfo

@Actual
internal fun getNameNative(): String = "ios"

@Actual
internal fun getVersionNative(): String = NSProcessInfo.processInfo.operatingSystemVersionString

@Actual
internal fun getArchNative(): String = "aarch64"
