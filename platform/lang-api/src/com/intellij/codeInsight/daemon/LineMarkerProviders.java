// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon;

import com.intellij.lang.LanguageExtension;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import org.jetbrains.annotations.NotNull;

@Service
public final class LineMarkerProviders extends LanguageExtension<LineMarkerProvider> {
  /**
   * @deprecated Use {@link #getInstance()}.
   */
  @Deprecated
  public static final LineMarkerProviders INSTANCE = ApplicationManager.getApplication() == null || ApplicationManager.getApplication().isUnitTestMode() ? null : getInstance();

  public static final String EP_NAME = "com.intellij.codeInsight.lineMarkerProvider";

  @NotNull
  public static LineMarkerProviders getInstance() {
    return ApplicationManager.getApplication().getService(LineMarkerProviders.class);
  }

  private LineMarkerProviders() {
    super(EP_NAME);
  }
}
