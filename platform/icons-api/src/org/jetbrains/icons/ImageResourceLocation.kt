// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons

/**
 * Represents a place from which image resource can be loaded.
 *
 * For example:
 * - path
 * - pluginId
 * - moduleId
 *
 * When creating new locations, ensure to register new ImageResourceLoader (rendering api) for the specific location.
 * Check current implementation on how to register extensions.
 */
@ExperimentalIconsApi
interface ImageResourceLocation