// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.keymap;

import com.intellij.openapi.application.ApplicationManager;

import javax.swing.*;

/**
 * @author yole
 */
public abstract class KeymapGroupFactory {
  public static KeymapGroupFactory getInstance() {
    return ApplicationManager.getApplication().getService(KeymapGroupFactory.class);
  }

  public abstract KeymapGroup createGroup(String name);
  public abstract KeymapGroup createGroup(String name, Icon icon);
}
