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
package com.intellij.diff.tools.external;

import com.intellij.diff.util.DiffUtil;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diff.impl.external.DiffManagerImpl;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.config.AbstractProperty;
import com.intellij.util.config.BooleanProperty;
import com.intellij.util.config.StringProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@State(
  name = "ExternalDiffSettings",
  storages = @Storage(DiffUtil.DIFF_CONFIG)
)
public class ExternalDiffSettings implements PersistentStateComponent<ExternalDiffSettings.State> {
  private State myState = new State();

  @Override
  public State getState() {
    return myState;
  }

  @Override
  public void loadState(State state) {
    myState = state;
  }

  public static ExternalDiffSettings getInstance() {
    return ServiceManager.getService(ExternalDiffSettings.class);
  }

  //
  // Migration from the old settings container. To be removed.
  //

  @NotNull
  private static AbstractProperty.AbstractPropertyContainer getProperties() {
    return DiffManagerImpl.getInstanceEx().getProperties();
  }

  @NotNull
  private static String getProperty(@Nullable StringProperty oldProperty, @Nullable String newValue, @NotNull String defaultValue) {
    if (newValue != null) return newValue;
    if (oldProperty != null) {
      String oldValue = oldProperty.get(getProperties());
      if (!StringUtil.isEmptyOrSpaces(oldValue)) return oldValue;
    }
    return defaultValue;
  }

  private static boolean getProperty(@Nullable BooleanProperty oldProperty, @Nullable Boolean newValue, boolean defaultValue) {
    if (newValue != null) return newValue;
    if (oldProperty != null) {
      return oldProperty.value(getProperties());
    }
    return defaultValue;
  }

  private static void setProperty(@Nullable StringProperty oldProperty, @NotNull String value) {
    if (oldProperty != null) oldProperty.set(getProperties(), value);
  }

  private static void setProperty(@Nullable BooleanProperty oldProperty, boolean value) {
    if (oldProperty != null) oldProperty.set(getProperties(), value);
  }

  public static class State {
    @Nullable public Boolean DIFF_ENABLED = null;
    @Nullable public Boolean DIFF_DEFAULT = null;
    @Nullable public String DIFF_EXE_PATH = null;
    @Nullable public String DIFF_PARAMETERS = null;

    @Nullable public Boolean MERGE_ENABLED = null;
    @Nullable public String MERGE_EXE_PATH = null;
    @Nullable public String MERGE_PARAMETERS = null;
    public boolean MERGE_TRUST_EXIT_CODE = false;
  }

  public boolean isDiffEnabled() {
    return getProperty(DiffManagerImpl.ENABLE_FILES, myState.DIFF_ENABLED, false);
  }

  public void setDiffEnabled(boolean value) {
    myState.DIFF_ENABLED = value;
  }

  public boolean isDiffDefault() {
    return getProperty(DiffManagerImpl.ENABLE_FILES, myState.DIFF_DEFAULT, false);
  }

  public void setDiffDefault(boolean value) {
    myState.DIFF_DEFAULT = value;
    setProperty(DiffManagerImpl.ENABLE_FILES, value);
  }

  @NotNull
  public String getDiffExePath() {
    return getProperty(DiffManagerImpl.FILES_TOOL, myState.DIFF_EXE_PATH, "");
  }

  public void setDiffExePath(@NotNull String path) {
    myState.DIFF_EXE_PATH = path;
    setProperty(DiffManagerImpl.FILES_TOOL, path);
  }

  @NotNull
  public String getDiffParameters() {
    return getProperty(null, myState.DIFF_PARAMETERS, "%1 %2 %3");
  }

  public void setDiffParameters(@NotNull String path) {
    myState.DIFF_PARAMETERS = path;
  }


  public boolean isMergeEnabled() {
    return getProperty(DiffManagerImpl.ENABLE_MERGE, myState.MERGE_ENABLED, false);
  }

  public void setMergeEnabled(boolean value) {
    myState.MERGE_ENABLED = value;
    setProperty(DiffManagerImpl.ENABLE_MERGE, value);
  }

  @NotNull
  public String getMergeExePath() {
    return getProperty(DiffManagerImpl.MERGE_TOOL, myState.MERGE_EXE_PATH, "");
  }

  public void setMergeExePath(@NotNull String path) {
    myState.MERGE_EXE_PATH = path;
    setProperty(DiffManagerImpl.MERGE_TOOL, path);
  }

  @NotNull
  public String getMergeParameters() {
    return getProperty(DiffManagerImpl.MERGE_TOOL_PARAMETERS, myState.MERGE_PARAMETERS, "%1 %2 %3 %4");
  }

  public void setMergeParameters(@NotNull String path) {
    myState.MERGE_PARAMETERS = path;
    setProperty(DiffManagerImpl.MERGE_TOOL_PARAMETERS, path);
  }

  public boolean isMergeTrustExitCode() {
    return myState.MERGE_TRUST_EXIT_CODE;
  }

  public void setMergeTrustExitCode(boolean value) {
    myState.MERGE_TRUST_EXIT_CODE = value;
  }
}
