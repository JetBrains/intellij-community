package com.intellij.lang.customFolding;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;

/**
 * @author Rustam Vishnyakov
 */
@State(
    name = "CustomFolding",
    storages = {
        @Storage( file = "$PROJECT_FILE$"),
        @Storage( file = "$PROJECT_CONFIG_DIR$/customFolding.xml", scheme = StorageScheme.DIRECTORY_BASED)
})
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
  public void loadState(State state) {
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
