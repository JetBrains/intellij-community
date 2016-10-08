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
package com.intellij.codeInsight.daemon.impl;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@State(name = "ParameterNameHintsSettings", storages = @Storage("parameter.name.hints.xml"))
public class ParameterNameHintsSettings implements PersistentStateComponent<ParameterNameHintsSettings.State> {
  
  public static final String[] defaultBlackList = {
    "(begin*, end*)",
    "(start*, end*)",
    "(first*, last*)",
    "(first*, second*)",
    "(from*, to*)",
    "(min*, max*)",
    "(key, value)",
    "(format, arg*)",
    "(message)",
    "(message, error)",
    
    "*.set(*,*)",
    "*.print(*)",
    "*.println(*)",
    "*.get(*)",
    "*.append(*)",
    "*.charAt(*)",
    "*.indexOf(*)",
    "*.contains(*)",
    "*.startsWith(*)",
    "*.endsWith(*)"
  };

  public static class State {
    public int version = 1;
    public List<String> blackList = ContainerUtil.newArrayList(defaultBlackList);
  }

  private ParameterNameHintsSettings.State myState = new State();

  public static ParameterNameHintsSettings getInstance() {
    return ServiceManager.getService(ParameterNameHintsSettings.class);
  }

  @Nullable
  @Override
  public ParameterNameHintsSettings.State getState() {
    return myState;
  }

  @Override
  public void loadState(ParameterNameHintsSettings.State state) {
    myState = state;
  }
  
  
  public int getVersion() {
    return myState.version;
  }

  public List<String> getBlacklist() {
    return myState.blackList;
  }


  public void setVersion(int newVersion) {
    myState.version = newVersion;
  }

  public void setBlacklist(@NotNull List<String> newBlacklist) {
    myState.blackList = newBlacklist;
  }
  
}