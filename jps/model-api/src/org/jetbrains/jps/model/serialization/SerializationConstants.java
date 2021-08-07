// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.model.serialization;

public final class SerializationConstants {
  /**
   * Specifies where an entity is imported from in configuration files stored under external_build_system directory
   */
  public static final String EXTERNAL_SYSTEM_ID_ATTRIBUTE = "__external-system-id";

  /**
   * Specifies where an entity is imported from in configuration files stored under .idea directory. This attribute is different from
   * {@link #EXTERNAL_SYSTEM_ID_ATTRIBUTE} because the latter is used to filter out tags from internal storage in
   * {@link com.intellij.openapi.components.StateSplitterEx}.
   */
  public static final String EXTERNAL_SYSTEM_ID_IN_INTERNAL_STORAGE_ATTRIBUTE = "external-system-id";
}
