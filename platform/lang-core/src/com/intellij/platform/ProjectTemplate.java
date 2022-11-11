// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform;

import com.intellij.ide.util.projectWizard.AbstractModuleBuilder;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Dmitry Avdeev
 */
public interface ProjectTemplate {

  ProjectTemplate[] EMPTY_ARRAY = new ProjectTemplate[0];

  default String getId() {
    return getClass().getSimpleName();
  }

  @NotNull
  @NlsContexts.Label
  String getName();

  @Nullable
  @NlsContexts.DetailedDescription
  String getDescription();

  Icon getIcon();

  @NotNull
  AbstractModuleBuilder createModuleBuilder();

  /**
   * @return null if ok, error message otherwise
   * @deprecated unused API
   */
  @Deprecated
  @Nullable
  ValidationInfo validateSettings();
}
