// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui;

import com.intellij.ide.ui.laf.PluggableLafInfo;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;

public interface LafProvider {
  ExtensionPointName<LafProvider> EP_NAME = new ExtensionPointName<>("com.intellij.lafProvider");

  @NotNull PluggableLafInfo getLookAndFeelInfo();
}
