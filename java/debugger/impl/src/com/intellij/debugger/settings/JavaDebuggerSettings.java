/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.debugger.settings;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.OptionsBundle;
import com.intellij.openapi.options.SimpleConfigurable;
import com.intellij.xdebugger.settings.DebuggerSettingsCategory;
import com.intellij.xdebugger.settings.XDebuggerSettings;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static java.util.Collections.singletonList;

/**
 * We cannot now transform DebuggerSettings to XDebuggerSettings: getState/loadState is not called for EP,
 * but we cannot use standard implementation to save our state, due to backward compatibility we must use own state spec.
 * <p/>
 * But we must implement createConfigurable as part of XDebuggerSettings otherwise java general settings will be before xdebugger general setting,
 * because JavaDebuggerSettingsPanelProvider has higher priority than XDebuggerSettingsPanelProviderImpl.
 */
public class JavaDebuggerSettings extends XDebuggerSettings<Element> {
  protected JavaDebuggerSettings() {
    super("java");
  }

  @NotNull
  @Override
  public Collection<? extends Configurable> createConfigurables(@NotNull DebuggerSettingsCategory category) {
    switch (category) {
      case GENERAL:
        return singletonList(SimpleConfigurable.create("reference.idesettings.debugger.launching", OptionsBundle.message("options.java.display.name"),
                                                       DebuggerLaunchingConfigurable.class, DebuggerSettings::getInstance));
      case DATA_VIEWS:
        return createDataViewsConfigurable();
      case STEPPING:
        return singletonList(SimpleConfigurable.create("reference.idesettings.debugger.stepping", OptionsBundle.message("options.java.display.name"),
                                                       DebuggerSteppingConfigurable.class, DebuggerSettings::getInstance));
      case HOTSWAP:
        return singletonList(SimpleConfigurable.create("reference.idesettings.debugger.hotswap", OptionsBundle.message("options.java.display.name"),
                                                       JavaHotSwapConfigurableUi.class, DebuggerSettings::getInstance));
      case ROOT:
        return singletonList(new CaptureConfigurable());
    }
    return Collections.emptyList();
  }

  @SuppressWarnings("SpellCheckingInspection")
  @NotNull
  public static List<Configurable> createDataViewsConfigurable() {
    return Arrays.asList(new DebuggerDataViewsConfigurable(null),
                         SimpleConfigurable.create("reference.idesettings.debugger.typerenderers", DebuggerBundle.message("user.renderers.configurable.display.name"),
                                                                 UserRenderersConfigurable.class, NodeRendererSettings::getInstance));
  }

  @Override
  public void generalApplied(@NotNull DebuggerSettingsCategory category) {
    if (category == DebuggerSettingsCategory.DATA_VIEWS) {
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