// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.SettingsCategory;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

@State(name = "ViewsSettings", storages = @Storage("debugger.xml"), category = SettingsCategory.TOOLS)
public final class ViewsGeneralSettings implements PersistentStateComponent<ViewsGeneralSettings> {
  public boolean HIDE_NULL_ARRAY_ELEMENTS = true;
  public boolean AUTOSCROLL_TO_NEW_LOCALS = true;
  public boolean USE_DFA_ASSIST = true;
  public boolean USE_DFA_ASSIST_GRAY_OUT = true;
  public boolean POPULATE_THROWABLE_STACKTRACE = true;

  public static ViewsGeneralSettings getInstance() {
    return ApplicationManager.getApplication().getService(ViewsGeneralSettings.class);
  }

  @Override
  public void loadState(@NotNull ViewsGeneralSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  @Override
  public ViewsGeneralSettings getState() {
    return this;
  }

  public boolean equals(Object object) {
    if (!(object instanceof ViewsGeneralSettings)) return false;
    ViewsGeneralSettings generalSettings = ((ViewsGeneralSettings)object);
    return HIDE_NULL_ARRAY_ELEMENTS == generalSettings.HIDE_NULL_ARRAY_ELEMENTS &&
           AUTOSCROLL_TO_NEW_LOCALS == generalSettings.AUTOSCROLL_TO_NEW_LOCALS &&
           POPULATE_THROWABLE_STACKTRACE == generalSettings.POPULATE_THROWABLE_STACKTRACE &&
           USE_DFA_ASSIST == generalSettings.USE_DFA_ASSIST &&
           USE_DFA_ASSIST_GRAY_OUT == generalSettings.USE_DFA_ASSIST_GRAY_OUT;
  }
}
