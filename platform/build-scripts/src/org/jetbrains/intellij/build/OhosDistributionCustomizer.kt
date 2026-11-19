// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import java.nio.file.Path

/**
 * An OHOS distribution has no launcher and no bundled runtime. A host application gives the runtime and runs the IDE.
 * See [org.jetbrains.intellij.build.impl.OhosDistributionBuilder] for what the host application gets.
 */
open class OhosDistributionCustomizer {
  /**
   * Additional options to add to the `*.vmoptions` file of the OHOS distribution, see [ProductProperties.additionalVmOptions].
   */
  var additionalVmOptions: PersistentList<String> = persistentListOf()

  /**
   * Override this method to copy additional files to the OHOS distribution of the product.
   */
  open fun copyAdditionalFiles(targetDir: Path, arch: JvmArchitecture, context: BuildContext) {}
}
