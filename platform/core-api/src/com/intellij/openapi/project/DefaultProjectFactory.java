// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project;

import com.intellij.openapi.application.ApplicationManager;

public abstract class DefaultProjectFactory {
  public static DefaultProjectFactory getInstance() {
    return ApplicationManager.getApplication().getService(DefaultProjectFactory.class);
  }

  public abstract Project getDefaultProject();
}
