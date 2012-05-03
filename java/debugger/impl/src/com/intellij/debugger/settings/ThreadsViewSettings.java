/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.openapi.components.*;
import com.intellij.util.xmlb.XmlSerializerUtil;

@State(
  name="ThreadsViewSettings",
  storages= {
    @Storage(
      file = StoragePathMacros.APP_CONFIG + "/debugger.threadsview.xml"
    )}
)
public class ThreadsViewSettings implements PersistentStateComponent<ThreadsViewSettings> {
  public boolean SHOW_THREAD_GROUPS = false;
  public boolean SHOW_LINE_NUMBER = true;
  public boolean SHOW_CLASS_NAME = true;
  public boolean SHOW_SOURCE_NAME = false;
  public boolean SHOW_SYNTHETIC_FRAMES = true;
  public boolean SHOW_CURRENT_THREAD = true;

  public static ThreadsViewSettings getInstance() {
    return ServiceManager.getService(ThreadsViewSettings.class);
 }

  public ThreadsViewSettings getState() {
    return this;
  }

  public void loadState(final ThreadsViewSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}