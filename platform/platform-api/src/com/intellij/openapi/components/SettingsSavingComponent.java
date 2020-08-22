// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.components;

/**
 * @deprecated Use {@link com.intellij.configurationStore.SettingsSavingComponent}.
 */
@Deprecated
public interface SettingsSavingComponent {
  void save();
}
