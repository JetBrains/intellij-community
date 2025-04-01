// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import org.jetbrains.annotations.ApiStatus

@Deprecated("This interface will eventually be removed, it is only needed for incremental migration. " +
            "A migration guide will be provided when alternatives will become available")
@ApiStatus.Experimental
interface IdeaPluginDescriptorImplPublic : IdeaPluginDescriptor {
  val moduleName: String?

  /**
   * aka `<dependencies>` element from plugin.xml
   *
   * Note that it's different from [getDependencies] (which is for `<depends>`)
   */
  val moduleDependencies: ModuleDependencies
}