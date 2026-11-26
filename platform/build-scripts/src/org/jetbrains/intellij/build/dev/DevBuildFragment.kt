// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.dev

import org.jetbrains.annotations.ApiStatus

/**
 * Which part of the distribution one assembly produces.
 *
 * [COMPLETE] is the whole distribution in one assembly: what an in-process dev launch and a non-split standalone build
 * produce. [PLATFORM_RESOURCES] packs no jar, lays out no plugin and writes no runtime module repository. The launch
 * model test renders the platform resource files over a build context of it, see [renderSplitPlatformResources].
 */
@ApiStatus.Internal
enum class DevBuildFragment {
  COMPLETE,
  PLATFORM_RESOURCES;

  /**
   * Whether this assembly is the whole distribution. Only a complete assembly owns the `lib/` jars, `bin`, the product
   * metadata, the launchers, the copied product files, the bundled plugin directories and the runtime module
   * repository. It is the only shape that may be scrambled.
   */
  val isComplete: Boolean
    get() = this == COMPLETE
}
