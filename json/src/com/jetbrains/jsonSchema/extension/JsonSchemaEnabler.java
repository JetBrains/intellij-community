// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.extension;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * This API provides a mechanism to enable JSON schemas in particular files
 * This interface should be implemented if you want a particular kind of virtual files to have access to JsonSchemaService APIs
 *
 * This API is new in IntelliJ IDEA Platform 2018.2
 */
public interface JsonSchemaEnabler {
  ExtensionPointName<JsonSchemaEnabler> EXTENSION_POINT_NAME = ExtensionPointName.create("com.intellij.json.jsonSchemaEnabler");

  /**
   * This method should return true if JSON schema mechanism should become applicable to corresponding file.
   * This method SHOULD NOT ADDRESS INDEXES.
   * @param file Virtual file to check for
   * @return true if available, false otherwise
   */
  boolean isEnabledForFile(VirtualFile file);

  /**
   * This method enables/disables JSON schema selection widget
   * This method SHOULD NOT ADDRESS INDEXES
   */
  default boolean shouldShowSwitcherWidget(VirtualFile file) {
    return true;
  }
}
