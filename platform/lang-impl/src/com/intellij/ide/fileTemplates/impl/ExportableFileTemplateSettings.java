// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.fileTemplates.impl;

import com.intellij.openapi.components.SettingsCategory;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.ApiStatus;

/**
 * @author Dmitry Avdeev
 */
@State(
  name = "ExportableFileTemplateSettings",
  storages = @Storage(FileTemplateSettings.EXPORTABLE_SETTINGS_FILE),
  additionalExportDirectory = FileTemplatesLoader.TEMPLATES_DIR,
  category = SettingsCategory.CODE
)
@ApiStatus.Internal
public final class ExportableFileTemplateSettings extends FileTemplateSettings {
  ExportableFileTemplateSettings() {
    super(null);
  }
}
