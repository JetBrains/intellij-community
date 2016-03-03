/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
           AUTOSCROLL_TO_NEW_LOCALS == generalSettings.AUTOSCROLL_TO_NEW_LOCALS;
  }
}
