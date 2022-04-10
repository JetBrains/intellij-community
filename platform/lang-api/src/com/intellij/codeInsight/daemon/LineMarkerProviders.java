// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon;

import com.intellij.lang.LanguageExtension;
import com.intellij.lang.LanguageExtensionPoint;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;

@Service
public final class LineMarkerProviders extends LanguageExtension<LineMarkerProvider> {
  public static final ExtensionPointName<LanguageExtensionPoint<LineMarkerProvider>> EP_NAME = ExtensionPointName.create("com.intellij.codeInsight.lineMarkerProvider");

  /**
   * @deprecated Use {@link #getInstance()}.
   */
  @Deprecated(forRemoval = true)
  public static final LineMarkerProviders INSTANCE = ApplicationManager.getApplication() == null || ApplicationManager.getApplication().isUnitTestMode() ? null : getInstance();

  @NotNull
  public static LineMarkerProviders getInstance() {
    return ApplicationManager.getApplication().getService(LineMarkerProviders.class);
  }

  private LineMarkerProviders() {
    super(EP_NAME);
  }
}
