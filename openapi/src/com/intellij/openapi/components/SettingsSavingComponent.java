/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.components;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.project.Project;

/**
 * Component which implements this interfaces will be asked to save ({@link #save}) custom settings (in their own custom way)
 *  when {@link Application#saveSettings()} (for Application level components) or {@link Project#save()}
 * (for Project level compoents) is invoked.
 * @see BaseComponent
 */
public interface SettingsSavingComponent {
  void save();
}
