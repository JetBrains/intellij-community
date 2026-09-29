// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.lightProducts

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/**
 * Maps the store directory of a light project to the project path that the user opened.
 *
 * The platform checks trust on the store directory (see [openProjectForLightProduct]).
 * A trust provider of the product uses this map to check the project path instead.
 * The map lives in memory only, because [openProjectForLightProduct] registers both paths each time it opens a project.
 */
@ApiStatus.Internal
@Service(Service.Level.APP)
class LightProjectTrustTargets {
  private val targets = ConcurrentHashMap<Path, Path>()

  /** Records that the trust of [storeDir] is the trust of [projectPath]. */
  fun register(storeDir: Path, projectPath: Path) {
    targets[storeDir] = projectPath
  }

  /** @return the project path registered for [storeDir], or `null` when [storeDir] is not a light project store directory. */
  fun getProjectPath(storeDir: Path): Path? {
    if (targets.isEmpty()) {
      return null
    }
    return targets[storeDir]
  }

  companion object {
    @JvmStatic
    fun getInstance(): LightProjectTrustTargets = service()
  }
}
