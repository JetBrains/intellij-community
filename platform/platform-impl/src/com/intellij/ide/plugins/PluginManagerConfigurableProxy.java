// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public class PluginManagerConfigurableProxy extends PluginManagerConfigurable {
  public static void showPluginConfigurable(@Nullable Component parent,
                                            @Nullable Project project,
                                            @NotNull IdeaPluginDescriptor... descriptors) {
    PluginManagerConfigurable configurable = new PluginManagerConfigurable();
    Runnable init = () -> configurable.select(descriptors);
    ShowSettingsUtil util = ShowSettingsUtil.getInstance();

    if (parent != null) {
      util.editConfigurable(parent, configurable, init);
    }
    else {
      util.editConfigurable(project, configurable, init);
    }
  }
}
