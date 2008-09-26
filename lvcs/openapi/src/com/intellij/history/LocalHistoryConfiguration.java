/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package com.intellij.history;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;

@State(
  name = "LocalHistoryConfiguration",
  storages = {
    @Storage(
      id ="other",
      file = "$APP_CONFIG$/other.xml"
    )}
)
public class LocalHistoryConfiguration implements PersistentStateComponent<LocalHistoryConfiguration> {
  private static final long DEFAULT_PURGING_PERIOD = 1000 * 60 * 60 * 24 * 3; // 3 days

  public long PURGE_PERIOD = DEFAULT_PURGING_PERIOD;

  public boolean ADD_LABEL_ON_PROJECT_OPEN = true;
  public boolean ADD_LABEL_ON_PROJECT_COMPILATION = true;
  public boolean ADD_LABEL_ON_FILE_PACKAGE_COMPILATION = true;
  public boolean ADD_LABEL_ON_PROJECT_MAKE = true;
  public boolean ADD_LABEL_ON_RUNNING = true;
  public boolean ADD_LABEL_ON_DEBUGGING = true;
  public boolean ADD_LABEL_ON_UNIT_TEST_PASSED = true;
  public boolean ADD_LABEL_ON_UNIT_TEST_FAILED = true;

  public boolean SHOW_CHANGES_ONLY = false;

  public static LocalHistoryConfiguration getInstance() {
    return ServiceManager.getService(LocalHistoryConfiguration.class);
  }

  public LocalHistoryConfiguration getState() {
    return this;
  }

  public void loadState(final LocalHistoryConfiguration state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}
