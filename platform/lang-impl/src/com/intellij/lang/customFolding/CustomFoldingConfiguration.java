// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.customFolding;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * @author Rustam Vishnyakov
 */
@State(name = "CustomFolding", storages = @Storage("customFolding.xml"))
public class CustomFoldingConfiguration implements PersistentStateComponent<CustomFoldingConfiguration.State> {
  
  private State myState = new State();
  
  public static CustomFoldingConfiguration getInstance(Project project) {
    return ServiceManager.getService(project, CustomFoldingConfiguration.class);
  }

  @Override
  public State getState() {
    return myState;
  }

  @Override
  public void loadState(@NotNull State state) {
    myState = state;
  }

  public static class State {

    private String startFoldingPattern = "";
    private String endFoldingPattern = "";
    private String defaultCollapsedStatePattern = "";
    private boolean isEnabled = false;

    public String getDefaultCollapsedStatePattern() {
      return defaultCollapsedStatePattern;
    }

    public void setDefaultCollapsedStatePattern(String defaultCollapsedStatePattern) {
      this.defaultCollapsedStatePattern = defaultCollapsedStatePattern == null ? "" : defaultCollapsedStatePattern.trim();
    }

    public String getStartFoldingPattern() {
      return startFoldingPattern;
    }

    public void setStartFoldingPattern(String startFoldingPattern) {
      this.startFoldingPattern = startFoldingPattern == null ? "" : startFoldingPattern.trim();
    }

    public String getEndFoldingPattern() {
      return endFoldingPattern;
    }

    public void setEndFoldingPattern(String endFoldingPattern) {
      this.endFoldingPattern = endFoldingPattern == null ? "" : endFoldingPattern.trim();
    }

    public boolean isEnabled() {
      return isEnabled;
    }

    public void setEnabled(boolean enabled) {
      isEnabled = enabled;
    }
  }
}
