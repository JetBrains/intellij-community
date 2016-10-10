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
package com.intellij.codeInsight.hints.settings;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.List;
import java.util.Set;

@State(name = "ParameterNameHintsSettings", storages = @Storage("parameter.name.hints.xml"))
public class ParameterNameHintsSettings implements PersistentStateComponent<ParameterNameHintsSettings.State> {
  private static final int VERSION = 1;
  private static final Set<String> DEFAULT = ContainerUtil.newHashSet(
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
  );

  private ParameterNameHintsSettings.State state = new State();
  private Set<String> defaultIgnoreSet = DEFAULT;
  
  @TestOnly
  protected void setDefaultSet(@NotNull Set<String> ignoreSet) {
    defaultIgnoreSet = ignoreSet;
  }

  public static ParameterNameHintsSettings getInstance() {
    return ServiceManager.getService(ParameterNameHintsSettings.class);
  }

  @Nullable
  @Override
  public ParameterNameHintsSettings.State getState() {
    return state;
  }

  @Override
  public void loadState(ParameterNameHintsSettings.State state) {
    this.state = state;
  }

  public void addIgnorePattern(@NotNull String pattern) {
    state.diff.add('+' + pattern);
  }
  
  public int getVersion() {
    return state.version;
  }

  public Set<String> getIgnorePatternSet() {
    Set<String> ignoreSet = ContainerUtil.newHashSet(defaultIgnoreSet);
    state.diff.forEach((item) -> {
      if (item.startsWith("+")) {
        ignoreSet.add(item.substring(1));
      }
      else if (item.startsWith("-")) {
        ignoreSet.remove(item.substring(1));
      }
    });
    return ignoreSet;
  }

  public void setVersion(int newVersion) {
    state.version = newVersion;
  }

  public void setIgnorePatternSet(@NotNull Set<String> updatedBlackList) {
    Set<String> addedItems = ContainerUtil.newHashSet(updatedBlackList);
    defaultIgnoreSet.forEach((pattern) -> addedItems.remove(pattern));

    Set<String> removedItems = ContainerUtil.newHashSet(defaultIgnoreSet);
    updatedBlackList.forEach((pattern) -> removedItems.remove(pattern));

    List<String> diff = ContainerUtil.newArrayList();
    addedItems.forEach((item) -> diff.add('+' + item));
    removedItems.forEach((item) -> diff.add('-' + item));

    state.diff = diff;
  }

  public static class State {
    public int version = VERSION;
    public List<String> diff = ContainerUtil.newArrayList();
  }
  
}