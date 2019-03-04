// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.tools.external;

import com.intellij.diff.util.DiffUtil;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.util.ObjectUtils.notNull;

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
  public void loadState(@NotNull State state) {
    myState = state;
  }

  public static ExternalDiffSettings getInstance() {
    return ServiceManager.getService(ExternalDiffSettings.class);
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
    return notNull(myState.DIFF_ENABLED, false);
  }

  public void setDiffEnabled(boolean value) {
    myState.DIFF_ENABLED = value;
  }

  public boolean isDiffDefault() {
    return notNull(myState.DIFF_DEFAULT, false);
  }

  public void setDiffDefault(boolean value) {
    myState.DIFF_DEFAULT = value;
  }

  @NotNull
  public String getDiffExePath() {
    return notNull(myState.DIFF_EXE_PATH, "");
  }

  public void setDiffExePath(@NotNull String path) {
    myState.DIFF_EXE_PATH = path;
  }

  @NotNull
  public String getDiffParameters() {
    return notNull(myState.DIFF_PARAMETERS, "%1 %2 %3");
  }

  public void setDiffParameters(@NotNull String path) {
    myState.DIFF_PARAMETERS = path;
  }


  public boolean isMergeEnabled() {
    return notNull(myState.MERGE_ENABLED, false);
  }

  public void setMergeEnabled(boolean value) {
    myState.MERGE_ENABLED = value;
  }

  @NotNull
  public String getMergeExePath() {
    return notNull(myState.MERGE_EXE_PATH, "");
  }

  public void setMergeExePath(@NotNull String path) {
    myState.MERGE_EXE_PATH = path;
  }

  @NotNull
  public String getMergeParameters() {
    return notNull(myState.MERGE_PARAMETERS, "%1 %2 %3 %4");
  }

  public void setMergeParameters(@NotNull String path) {
    myState.MERGE_PARAMETERS = path;
  }

  public boolean isMergeTrustExitCode() {
    return myState.MERGE_TRUST_EXIT_CODE;
  }

  public void setMergeTrustExitCode(boolean value) {
    myState.MERGE_TRUST_EXIT_CODE = value;
  }
}
