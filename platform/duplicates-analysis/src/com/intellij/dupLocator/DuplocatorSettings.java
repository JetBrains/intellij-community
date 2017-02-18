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
package com.intellij.dupLocator;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;

import java.util.HashSet;
import java.util.Set;

@State(
  name = "DuplocatorSettings",
  storages = {
    @Storage("duplocatorSettings.xml"),
    @Storage(value = "other.xml", deprecated = true)
  }
)
public class DuplocatorSettings implements PersistentStateComponent<DuplocatorSettings> {
  public boolean DISTINGUISH_VARIABLES = false;
  public boolean DISTINGUISH_FIELDS = false;
  public boolean DISTINGUISH_METHODS = true;
  public boolean DISTINGUISH_TYPES = true;
  public boolean DISTINGUISH_LITERALS = true;
  public boolean CHECK_VALIDITY = true;
  public int LOWER_BOUND = 10;
  public int  DISCARD_COST = 0;
  public Set<String> SELECTED_PROFILES = new HashSet<>();
  public String LAST_SELECTED_LANGUAGE = null;

  public static DuplocatorSettings getInstance() {
    return ServiceManager.getService(DuplocatorSettings.class);
  }

  @Override
  public DuplocatorSettings getState() {
    return this;
  }

  @Override
  public void loadState(DuplocatorSettings object) {
    XmlSerializerUtil.copyBean(object, this);
  }
}
