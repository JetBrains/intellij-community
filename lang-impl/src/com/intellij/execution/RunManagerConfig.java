package com.intellij.execution;

import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.execution.util.StoringPropertyContainer;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.util.config.BooleanProperty;
import org.jetbrains.annotations.NonNls;

import java.util.Map;

public class RunManagerConfig {
  public static final String MAKE = ExecutionBundle.message("before.run.property.make");
  private static final BooleanProperty SHOW_SETTINGS = new BooleanProperty("showSettingsBeforeRunnig", true);
  private final StoringPropertyContainer myProperties;
  private final PropertiesComponent myPropertiesComponent;
  private final RunManagerImpl myManager;
  @NonNls private static final String RECENTS_LIMIT = "recentsLimit";

  public RunManagerConfig(PropertiesComponent propertiesComponent,
                          RunManagerImpl manager) {
    myPropertiesComponent = propertiesComponent;
    myManager = manager;
    myProperties = new StoringPropertyContainer("RunManagerConfig.", propertiesComponent);
  }

  public boolean isShowSettingsBeforeRun() {
    return SHOW_SETTINGS.value(myProperties);
  }

  public void setShowSettingsBeforeRun(final boolean value) {
    SHOW_SETTINGS.primSet(myProperties, value);
  }

  public boolean isCompileBeforeRunning(RunProfile runProfile){
    if (runProfile instanceof RunConfiguration){
      final Map<String,Boolean> beforeRun = myManager.getStepsBeforeLaunch((RunConfiguration)runProfile);
      final Boolean makeBeforeRun = beforeRun.get(MAKE);
      return Boolean.TRUE.equals(makeBeforeRun);
    }
    return false;
  }

  public int getRecentsLimit() {
    try {
      return Integer.valueOf(myPropertiesComponent.getOrInit(RECENTS_LIMIT, "5")).intValue();
    }
    catch (NumberFormatException e) {
      return 5;
    }
  }

  public void setRecentsLimit(int recentsLimit) {
    myPropertiesComponent.setValue(RECENTS_LIMIT, Integer.toString(recentsLimit));
  }
}
