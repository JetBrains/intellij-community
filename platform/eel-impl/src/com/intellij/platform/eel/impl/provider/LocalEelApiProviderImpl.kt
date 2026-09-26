// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl.provider

import com.intellij.openapi.util.SystemInfo
import com.intellij.platform.eel.LocalEelApi
import com.intellij.platform.eel.impl.local.LocalPosixEelApiImpl
import com.intellij.platform.eel.impl.local.LocalWindowsEelApiImpl
import com.intellij.platform.eel.provider.LocalEelApiProvider

internal class LocalEelApiProviderImpl : LocalEelApiProvider {
  override fun getLocalEelApi(): LocalEelApi =
    if (SystemInfo.isWindows) LocalWindowsEelApiImpl() else LocalPosixEelApiImpl()
}
