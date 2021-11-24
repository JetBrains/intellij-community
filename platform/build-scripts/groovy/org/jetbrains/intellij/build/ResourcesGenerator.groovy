// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build

import groovy.transform.CompileStatic
import org.jetbrains.annotations.Nullable

/**
 * @deprecated Use {@link org.jetbrains.intellij.build.impl.PluginLayout.PluginLayoutSpec#withGeneratedResources(java.util.function.BiConsumer)}
 */
@Deprecated
@CompileStatic
interface ResourcesGenerator {
  /**
   * Generate files which need to be included into the product distribution somewhere under {@code context.paths.temp} directory.
   * @return path to the generated file or directory or {@code null} if nothing was generated
   */
  @Nullable File generateResources(BuildContext context)
}