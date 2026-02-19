// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.settings.fileBasedSync

import com.intellij.openapi.extensions.ExtensionPointName

/**
 * Allows specifying settings that should be synced between the monolith and backend as raw files
 */
interface FileBasedSettingsSyncListProvider {

  companion object {
    val EP_NAME: ExtensionPointName<FileBasedSettingsSyncListProvider> = ExtensionPointName.Companion.create("com.intellij.rdserver.fileBasedSettingsSyncListProvider")
  }

  /**
   * Returns the list of the settings files that should be synced.
   *
   * **Note**: This method is called when the list is first required and every time the list of extensions changes.
   */
  fun getSettingsFileList(): List<String>

}
