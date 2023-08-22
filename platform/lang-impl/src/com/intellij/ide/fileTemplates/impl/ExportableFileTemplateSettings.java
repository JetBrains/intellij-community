// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.fileTemplates.impl;

import com.intellij.openapi.components.SettingsCategory;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;

/**
 * @author Dmitry Avdeev
 */
@State(
  name = "ExportableFileTemplateSettings",
  storages = @Storage(FileTemplateSettings.EXPORTABLE_SETTINGS_FILE),
  additionalExportDirectory = FileTemplatesLoader.TEMPLATES_DIR,
  category = SettingsCategory.CODE
)
final class ExportableFileTemplateSettings extends FileTemplateSettings {
  ExportableFileTemplateSettings() {
    super(null);
  }
}
