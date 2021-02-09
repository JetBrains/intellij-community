// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin Bulenkov
 */
final class DaemonCodeAnalyzerOptionDescription extends EditorOptionDescription {
  DaemonCodeAnalyzerOptionDescription(String fieldName, @Nls String option, String configurableId) {
    super(fieldName, option, configurableId);
  }

  @Override
  public @NotNull Object getInstance() {
    return DaemonCodeAnalyzerSettings.getInstance();
  }
}
