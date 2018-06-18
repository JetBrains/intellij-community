// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.extension;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public interface JsonWidgetSuppressor {
  ExtensionPointName<JsonWidgetSuppressor> EXTENSION_POINT_NAME = ExtensionPointName.create("com.intellij.json.Json.Widget.Suppressor");

  /**
   * Allows to suppress JSON widget for particular files
   * This method can access indexes and PSI
   */
  boolean suppressSwitcherWidget(@NotNull VirtualFile file, @NotNull Project project);
}
