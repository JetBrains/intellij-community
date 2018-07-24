// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.extension;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.vfs.VirtualFile;

public interface JsonSchemaEnabler {
  ExtensionPointName<JsonSchemaEnabler> EXTENSION_POINT_NAME = ExtensionPointName.create("Json.Schema.Enabler");

  boolean isEnabledForFile(VirtualFile file);

  default boolean shouldShowSwitcherWidget(VirtualFile file) {
    return true;
  }
}
