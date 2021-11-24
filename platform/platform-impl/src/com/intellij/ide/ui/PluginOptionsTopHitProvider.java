// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui;

import com.intellij.ide.plugins.PluginManagerConfigurable;
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
    return PluginManagerConfigurable.getVisiblePlugins()
      .map(PluginBooleanOptionDescriptor::new)
      .collect(Collectors.toUnmodifiableList());
  }

  @Override
  public @NotNull String getId() {
    return "plugins";
  }
}
