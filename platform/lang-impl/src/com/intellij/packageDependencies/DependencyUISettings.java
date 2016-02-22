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
package com.intellij.packageDependencies;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.packageDependencies.ui.PatternDialectProvider;
import com.intellij.util.xmlb.XmlSerializerUtil;

@State(
  name = "DependencyUISettings",
  storages = {
    @Storage("ui.lnf.xml"),
    @Storage(value = "other.xml", deprecated = true)
  }
)
public class DependencyUISettings implements PersistentStateComponent<DependencyUISettings> {
  public boolean UI_FLATTEN_PACKAGES = true;
  public boolean UI_SHOW_FILES = true;
  public boolean UI_SHOW_MODULES = true;
  public boolean UI_SHOW_MODULE_GROUPS = true;
  public boolean UI_FILTER_LEGALS = false;
  public boolean UI_FILTER_OUT_OF_CYCLE_PACKAGES = true;
  public boolean UI_GROUP_BY_SCOPE_TYPE = true;
  public boolean UI_COMPACT_EMPTY_MIDDLE_PACKAGES = true;
  public String SCOPE_TYPE = Extensions.getExtensions(PatternDialectProvider.EP_NAME)[0].getShortName();

  public static DependencyUISettings getInstance() {
    return ServiceManager.getService(DependencyUISettings.class);
  }

  @Override
  public DependencyUISettings getState() {
    return this;
  }

  @Override
  public void loadState(DependencyUISettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}