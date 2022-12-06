// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public interface GeometryService {

  @NotNull
  static GeometryService getInstance() {
    return ApplicationManager.getApplication().getService(GeometryService.class);
  }

  @NotNull Dimension getSize(@NotNull Key<Dimension> key);

}
