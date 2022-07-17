// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui;

import com.intellij.ide.plugins.PluginManager;
import com.intellij.ide.ui.search.OptionDescription;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.stream.Collectors;

/**
 * @author Konstantin Bulenkov
 */
final class PluginOptionsTopHitProvider implements OptionsTopHitProvider.ApplicationLevelProvider {
  @Override
  public @NotNull Collection<OptionDescription> getOptions() {
    return PluginManager.getVisiblePlugins(false)
      .map(PluginBooleanOptionDescriptor::new)
      .collect(Collectors.toList());
  }

  @Override
  public @NotNull String getId() {
    return "plugins";
  }
}
