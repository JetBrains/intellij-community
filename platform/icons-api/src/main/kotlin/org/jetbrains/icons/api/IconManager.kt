// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.api

interface IconManager {
  fun loadIcon(id: IconIdentifier, path: String, aClass: Class<*>): Icon?
  fun registerIcon(id: IconIdentifier, icon: Icon)
}

interface IconIdentifier