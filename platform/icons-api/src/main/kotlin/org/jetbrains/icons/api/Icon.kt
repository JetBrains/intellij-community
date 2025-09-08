// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.api

interface Icon {
  val identifier: IconIdentifier
  val state: IconState

  fun render(api: PaintingApi)
}