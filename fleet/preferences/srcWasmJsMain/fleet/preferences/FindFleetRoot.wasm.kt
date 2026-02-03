// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.preferences

import fleet.util.multiplatform.Actual
import kotlinx.io.files.Path

@Actual
internal fun findRepositoryRootWasmJs(): String? {
  return MONOREPO_PATH
}

@JsName("MONOREPO_PATH")
external val MONOREPO_PATH: String?