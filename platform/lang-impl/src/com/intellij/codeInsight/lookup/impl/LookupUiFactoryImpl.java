// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.lookup.impl;

import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBList;
import org.jetbrains.annotations.NotNull;

public class LookupUiFactoryImpl extends LookupUiFactory {
  @NotNull
  @Override
  public LookupUi createLookupUi(@NotNull LookupImpl lookup, @NotNull Advertiser advertiser, @NotNull JBList list, @NotNull Project project) {
    return new LookupUiImpl(lookup, advertiser, list, project);
  }
}
