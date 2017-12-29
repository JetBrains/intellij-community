// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.settings;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;

@State(
  name = "ViewsSettings",
  storages = @Storage("debugger.frameview.xml")
)
public class ViewsGeneralSettings implements PersistentStateComponent<ViewsGeneralSettings> {
  public boolean SHOW_OBJECTID = true;
  public boolean HIDE_NULL_ARRAY_ELEMENTS = true;
  public boolean AUTOSCROLL_TO_NEW_LOCALS = true;
  public boolean POPULATE_THROWABLE_STACKTRACE = true;

  public static ViewsGeneralSettings getInstance() {
    return ServiceManager.getService(ViewsGeneralSettings.class);
  }

  @Override
  public void loadState(ViewsGeneralSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  @Override
  public ViewsGeneralSettings getState() {
    return this;
  }

  public boolean equals(Object object) {
    if (!(object instanceof ViewsGeneralSettings)) return false;
    ViewsGeneralSettings generalSettings = ((ViewsGeneralSettings)object);
    return SHOW_OBJECTID == generalSettings.SHOW_OBJECTID &&
           HIDE_NULL_ARRAY_ELEMENTS == generalSettings.HIDE_NULL_ARRAY_ELEMENTS &&
           AUTOSCROLL_TO_NEW_LOCALS == generalSettings.AUTOSCROLL_TO_NEW_LOCALS &&
           POPULATE_THROWABLE_STACKTRACE == generalSettings.POPULATE_THROWABLE_STACKTRACE;
  }
}
