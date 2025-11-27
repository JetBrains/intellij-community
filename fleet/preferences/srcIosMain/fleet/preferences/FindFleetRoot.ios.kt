// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.preferences

import fleet.util.multiplatform.Actual

@Actual
internal fun findRepositoryRootNative(): String? {
  return MONOREPO_PATH
}

val MONOREPO_PATH = null // TODO
