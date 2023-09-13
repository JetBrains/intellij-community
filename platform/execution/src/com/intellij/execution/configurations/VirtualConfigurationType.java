// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.configurations;

/**
 * Marker interface for configuration types that are provided from contexts external to run configuration management UI.
 * Configuration of such type can't be manually added or removed by users in the Run/Debug Configurations panel,
 * and the configuration type template entry is hidden.
 */
public interface VirtualConfigurationType {
}
