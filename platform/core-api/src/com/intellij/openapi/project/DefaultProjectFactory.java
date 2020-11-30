// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project;

import com.intellij.openapi.application.ApplicationManager;

/**
 * @author yole
 */
public abstract class DefaultProjectFactory {
  public static DefaultProjectFactory getInstance() {
    return ApplicationManager.getApplication().getService(DefaultProjectFactory.class);
  }

  public abstract Project getDefaultProject();
}
