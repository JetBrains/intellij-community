// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.remote;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Disables JSON schema download from schema store for particular files.
 * Not intended to be used except to suppress JSON schema for JSCS
 */
@ApiStatus.Experimental
public interface JsonSchemaCatalogExclusion {

  ExtensionPointName<JsonSchemaCatalogExclusion> EP_NAME = ExtensionPointName.create("com.intellij.json.catalog.exclusion");

  boolean isExcluded(@NotNull VirtualFile file);
}
