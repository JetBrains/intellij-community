// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.components;

/**
 * @deprecated Use {@link com.intellij.configurationStore.SettingsSavingComponent}.
 */
@Deprecated(forRemoval = true)
public interface SettingsSavingComponent {
  void save();
}
