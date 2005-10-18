package com.intellij.execution;

import com.intellij.execution.util.StoringPropertyContainer;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.util.config.BooleanProperty;

public class RunManagerConfig {
  private static final BooleanProperty SHOW_SETTINGS = new BooleanProperty("showSettingsBeforeRunnig", true);
  private static final BooleanProperty COMPILE_BERFORE_RUNNING = new BooleanProperty("compileBeforeRunning", true);
  private StoringPropertyContainer myProperties;

  public RunManagerConfig(PropertiesComponent propertiesComponent) {
    myProperties = new StoringPropertyContainer("RunManagerConfig.", propertiesComponent);
  }

  public boolean isShowSettingsBeforeRun() {
    return SHOW_SETTINGS.value(myProperties);
  }

  public void setShowSettingsBeforeRun(final boolean value) {
    SHOW_SETTINGS.primSet(myProperties, value);
  }

  public boolean isCompileBeforeRunning() {
    return COMPILE_BERFORE_RUNNING.value(myProperties);
  }

  public void setCompileBeforeRunning(final boolean value) {
    COMPILE_BERFORE_RUNNING.primSet(myProperties, value);
  }
}
