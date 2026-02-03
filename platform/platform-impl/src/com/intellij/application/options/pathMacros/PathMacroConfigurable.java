// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.pathMacros;

import com.intellij.configurationStore.StorageUtilKt;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public final class PathMacroConfigurable implements SearchableConfigurable, Configurable.NoScroll {
  public static final @NonNls String HELP_ID = "preferences.pathVariables";
  private PathMacroListEditor editor;

  @Override
  public JComponent createComponent() {
    editor = new PathMacroListEditor();
    return editor.getPanel();
  }

  @Override
  public void apply() throws ConfigurationException {
    editor.commit();

    StorageUtilKt.scheduleCheckUnknownMacros();
  }

  @Override
  public void reset() {
    editor.reset();
  }

  @Override
  public void disposeUIResources() {
    editor = null;
  }

  @Override
  public String getDisplayName() {
    return ApplicationBundle.message("title.path.variables");
  }

  @Override
  public @NotNull String getHelpTopic() {
    return HELP_ID;
  }

  @Override
  public boolean isModified() {
    return editor != null && editor.isModified();
  }

  @Override
  public @NotNull String getId() {
    return getHelpTopic();
  }
}
