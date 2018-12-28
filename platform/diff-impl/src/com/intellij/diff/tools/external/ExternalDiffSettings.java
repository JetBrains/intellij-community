// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.tools.external;

import com.intellij.diff.util.DiffUtil;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;

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
    public boolean DIFF_ENABLED = false;
    public boolean DIFF_DEFAULT = false;
    @NotNull public String DIFF_EXE_PATH = "";
    @NotNull public String DIFF_PARAMETERS = "%1 %2 %3";

    public boolean MERGE_ENABLED = false;
    @NotNull public String MERGE_EXE_PATH = "";
    @NotNull public String MERGE_PARAMETERS = "%1 %2 %3 %4";
    public boolean MERGE_TRUST_EXIT_CODE = false;
  }

  public boolean isDiffEnabled() {
    return myState.DIFF_ENABLED;
  }

  public void setDiffEnabled(boolean value) {
    myState.DIFF_ENABLED = value;
  }

  public boolean isDiffDefault() {
    return myState.DIFF_DEFAULT;
  }

  public void setDiffDefault(boolean value) {
    myState.DIFF_DEFAULT = value;
  }

  @NotNull
  public String getDiffExePath() {
    return myState.DIFF_EXE_PATH;
  }

  public void setDiffExePath(@NotNull String path) {
    myState.DIFF_EXE_PATH = path;
  }

  @NotNull
  public String getDiffParameters() {
    return myState.DIFF_PARAMETERS;
  }

  public void setDiffParameters(@NotNull String path) {
    myState.DIFF_PARAMETERS = path;
  }


  public boolean isMergeEnabled() {
    return myState.MERGE_ENABLED;
  }

  public void setMergeEnabled(boolean value) {
    myState.MERGE_ENABLED = value;
  }

  @NotNull
  public String getMergeExePath() {
    return myState.MERGE_EXE_PATH;
  }

  public void setMergeExePath(@NotNull String path) {
    myState.MERGE_EXE_PATH = path;
  }

  @NotNull
  public String getMergeParameters() {
    return myState.MERGE_PARAMETERS;
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
