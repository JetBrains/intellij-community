// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;

/**
 * Extension that implements this interface will be automatically queried for inspection tool classes.
 */
public interface InspectionToolProvider {
  ExtensionPointName<InspectionToolProvider> EXTENSION_POINT_NAME = ExtensionPointName.create("com.intellij.inspectionToolProvider");

  /**
   * Query method for inspection tools provided by a plugin.
   * @return classes that extend {@link InspectionProfileEntry}
   */
  @NotNull
  Class[] getInspectionClasses();
}
