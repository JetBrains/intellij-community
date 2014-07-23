/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.debugger.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.OptionsBundle;
import com.intellij.openapi.options.SimpleConfigurable;
import com.intellij.openapi.util.Getter;
import com.intellij.xdebugger.settings.XDebuggerSettings;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * We cannot now transform DebuggerSettings to XDebuggerSettings: getState/loadState is not called for EP,
 * but we cannot use standard implementation to save our state, due to backward compatibility we must use own state spec.
 *
 * But we must implement createConfigurable as part of XDebuggerSettings otherwise java general settings will be before xdebugger general setting,
 * because JavaDebuggerSettingsPanelProvider has higher priority than XDebuggerSettingsPanelProviderImpl.
 */
class JavaDebuggerSettings extends XDebuggerSettings<Element> {
  protected JavaDebuggerSettings() {
    super("java");
  }

  @Nullable
  @Override
  public Configurable createConfigurable(@NotNull Category category) {
    final Getter<DebuggerSettings> debuggerSettingsGetter = new Getter<DebuggerSettings>() {
      @Override
      public DebuggerSettings get() {
        return DebuggerSettings.getInstance();
      }
    };

    switch (category) {
      case GENERAL:
        return SimpleConfigurable.create("reference.idesettings.debugger.launching", OptionsBundle.message("options.java.display.name"),
                                         DebuggerLaunchingConfigurable.class, debuggerSettingsGetter);
      case DATA_VIEWS:
        return new DebuggerDataViewsConfigurable(null);
      case STEPPING:
        return SimpleConfigurable.create("reference.idesettings.debugger.stepping", OptionsBundle.message("options.java.display.name"), DebuggerSteppingConfigurable.class, debuggerSettingsGetter);
      case HOTSWAP:
        return SimpleConfigurable.create("reference.idesettings.debugger.hotswap", OptionsBundle.message("options.java.display.name"), JavaHotSwapConfigurableUi.class, debuggerSettingsGetter);
    }
    return null;
  }

  @Override
  public void generalApplied(@NotNull XDebuggerSettings.Category category) {
    if (category == XDebuggerSettings.Category.DATA_VIEWS) {
      NodeRendererSettings.getInstance().fireRenderersChanged();
    }
  }

  @Nullable
  @Override
  public Element getState() {
    return null;
  }

  @Override
  public void loadState(Element state) {
  }
}