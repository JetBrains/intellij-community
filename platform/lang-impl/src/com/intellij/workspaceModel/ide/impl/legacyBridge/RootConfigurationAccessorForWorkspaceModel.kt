// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl.legacyBridge

import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder
import org.jetbrains.annotations.ApiStatus

/**
 * Provides access to the global modifiable model builder via [com.intellij.openapi.roots.impl.RootConfigurationAccessor]'s implementation.
 * This interface is supposed to be used for Project Structure dialog only.
 */
@ApiStatus.Internal
interface RootConfigurationAccessorForWorkspaceModel {
  val actualDiffBuilder: WorkspaceEntityStorageBuilder?
}