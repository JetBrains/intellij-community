/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.ui;

import com.intellij.ide.plugins.PluginManager;
import com.intellij.ide.plugins.PluginManagerConfigurable;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.ui.search.BooleanOptionDescription;
import com.intellij.openapi.extensions.PluginId;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public class PluginBooleanOptionDescriptor extends BooleanOptionDescription {
  private final PluginId myId;

  public PluginBooleanOptionDescriptor(PluginId id) {
    //noinspection ConstantConditions
    super(PluginManager.getPlugin(id).getName(), PluginManagerConfigurable.ID);
    myId = id;
  }

  @Override
  public boolean isOptionEnabled() {
    //noinspection ConstantConditions
    return PluginManager.getPlugin(myId).isEnabled();
  }

  @Override
  public void setOptionState(boolean enabled) {
    List<String> disabledPlugins = new ArrayList<>(PluginManagerCore.getDisabledPlugins());
    if (enabled) {
      disabledPlugins.remove(myId.getIdString());
    } else {
      disabledPlugins.add(myId.getIdString());
    }
    try {
      PluginManagerCore.saveDisabledPlugins(disabledPlugins, false);
      PluginManagerConfigurable.shutdownOrRestartApp();
    }
    catch (IOException e) {//
    }
  }
}
