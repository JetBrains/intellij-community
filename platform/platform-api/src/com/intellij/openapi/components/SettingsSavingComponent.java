// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.components;

import com.intellij.openapi.application.Application;

/**
 * Component or service which implements this interfaces will be asked to save ({@link #save}) custom settings (in their own custom way)
 *  when {@link Application#saveSettings()} (for Application level components) or {@link com.intellij.openapi.project.Project#save()}
 * (for Project level components) is invoked.
 */
public interface SettingsSavingComponent {
  void save();
}
