// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.lookup.impl;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBList;
import org.jetbrains.annotations.NotNull;

public abstract class LookupUiFactory {

  public abstract @NotNull LookupUi createLookupUi(@NotNull LookupImpl lookup, @NotNull Advertiser advertiser, @NotNull JBList list, @NotNull Project project);

  public static LookupUiFactory getInstance() {
    return ServiceManager.getService(LookupUiFactory.class);
  }
}
