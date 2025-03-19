// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.editor;

import com.intellij.ide.ui.OptionsSearchTopHitProvider;
import com.intellij.ide.ui.search.OptionDescription;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

final class EditorOptionsTopHitProvider implements OptionsSearchTopHitProvider.ApplicationLevelProvider {
  @Override
  public @NotNull String getId() {
    return EditorOptionsPanel.ID;
  }

  @Override
  public @NotNull Collection<OptionDescription> getOptions() {
    return EditorOptionsPanelKt.getOptionDescriptors();
  }
}
