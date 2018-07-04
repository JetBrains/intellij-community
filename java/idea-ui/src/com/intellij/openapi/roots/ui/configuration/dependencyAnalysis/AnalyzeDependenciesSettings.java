// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui.configuration.dependencyAnalysis;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * The default mode for classpath details settings
 */
@State(name = "AnalyzeDependenciesSettings", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public class AnalyzeDependenciesSettings implements PersistentStateComponent<AnalyzeDependenciesSettings.State> {
  /**
   * The current state
   */
  private State myState = new State();

  /**
   * {@inheritDoc}
   */
  @Override
  public State getState() {
    return myState;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void loadState(@NotNull State state) {
    if (state == null) {
      state = new State();
    }
    myState = state;
  }

  /**
   * Set runtime flag
   *
   * @param value the value to se
   */
  public void setRuntime(boolean value) {
    myState.IS_RUNTIME = value;
  }

  /**
   * Set test flag
   *
   * @param value the value to se
   */
  public void setTest(boolean value) {
    myState.IS_TEST = value;
  }

  /**
   * Set include sdk flag
   *
   * @param value the value to se
   */
  public void setIncludeSdk(boolean value) {
    myState.INCLUDE_SDK = value;
  }

  /**
   * @return true, if runtime classpath should be analyzed
   */
  public boolean isRuntime() {
    return myState.IS_RUNTIME;
  }

  /**
   * @return true, if sdk classes should be also checked
   */
  public boolean isSdkIncluded() {
    return myState.INCLUDE_SDK;
  }

  /**
   * @return true, if test classes should be included
   */
  public boolean isTest() {
    return myState.IS_TEST;
  }

  /**
   * @return true, if classpath analysis is done in term or urls. false if in term of order entries
   */
  public boolean isUrlMode() {
    return myState.IS_URL_MODE;
  }

  /**
   * Set url mode
   *
   * @param value true, if classpath analysis is done in term or urls. false if in term of order entries
   */
  public void setUrlMode(boolean value) {
    myState.IS_URL_MODE = value;
  }


  /**
   * Get project instance
   *
   * @param project the context project
   * @return the created instance
   */
  public static AnalyzeDependenciesSettings getInstance(Project project) {
    return ServiceManager.getService(project, AnalyzeDependenciesSettings.class);
  }

  /**
   * The state object for settings
   */
  public static class State {
    /**
     * If true, runtime dependencies are shown, otherwise compile-time
     */
    boolean IS_RUNTIME = true;
    /**
     * If true, test dependencies are shown, otherwise production dependencies only
     */
    boolean IS_TEST = true;
    /**
     * If true, the JDK entries are included as well
     */
    boolean INCLUDE_SDK = false;
    /**
     * Are urls or order entry used
     */
    boolean IS_URL_MODE = true;
  }
}
