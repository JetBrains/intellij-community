package com.intellij.execution;

import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.util.StoringPropertyContainer;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.util.config.BooleanProperty;
import com.intellij.util.config.ExternalizablePropertyContainer;
import com.intellij.util.config.StringProperty;

import java.util.HashMap;
import java.util.Map;

public class RunManagerConfig {
  public static final String MAKE = ExecutionBundle.message("before.run.property.make");
  public static final String NONE = ExecutionBundle.message("before.run.property.none");
  public static final String MAKE_ANT = ExecutionBundle.message("before.run.property.make.ant");
  public static final String ANT = ExecutionBundle.message("before.run.property.ant");
  public static final String [] METHODS = new String[]{NONE, MAKE, ANT, MAKE_ANT};

  private static final BooleanProperty SHOW_SETTINGS = new BooleanProperty("showSettingsBeforeRunnig", true);
  private static final BooleanProperty COMPILE_BERFORE_RUNNING = new BooleanProperty("compileBeforeRunning", true);
  private Map<RunProfile, StringProperty> myCompileBeforeRunning = new HashMap<RunProfile, StringProperty>();
  private Map<RunProfile, BooleanProperty> myStoreProperty = new HashMap<RunProfile, BooleanProperty>();
  private StoringPropertyContainer myProperties;
  private ExternalizablePropertyContainer myContainer;

  public RunManagerConfig(PropertiesComponent propertiesComponent) {
    myProperties = new StoringPropertyContainer("RunManagerConfig.", propertiesComponent);
    myContainer = new ExternalizablePropertyContainer();
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

  public boolean isCompileBeforeRunning(RunProfile runProfile){
    return isCompileBeforeRunning() &&
           (getCompileMethodBeforeRunning(runProfile) == MAKE || getCompileMethodBeforeRunning(runProfile) == MAKE_ANT);
  }

  public String getCompileMethodBeforeRunning(final RunProfile runConfiguration) {
    final StringProperty property = myCompileBeforeRunning.get(runConfiguration);
    return property != null ? property.get(myContainer) : MAKE;
  }

  public void setCompileMethodBeforeRunning(final RunProfile runConfiguration, final String method) {
    StringProperty property = myCompileBeforeRunning.get(runConfiguration);
    if (property != null){
      property.set(myContainer, method);
    } else {
      property = new StringProperty("compileMethodBeforeRunning." + runConfiguration.getName(), method);
      myCompileBeforeRunning.put(runConfiguration, property);
    }
  }

  public boolean isStoreProjectConfiguration(final RunProfile runProfile) {
    final BooleanProperty booleanProperty = myStoreProperty.get(runProfile);
    return booleanProperty != null && booleanProperty.value(myProperties);
  }

  public void setStoreProjectConfiguration(final RunProfile runConfiguration, final boolean value) {
    BooleanProperty property = myStoreProperty.get(runConfiguration);
    if (property != null){
      property.set(myProperties, value);
    } else {
      property = new BooleanProperty("storeProjectConfiguration." + (runConfiguration instanceof RunConfiguration ? (((RunConfiguration)runConfiguration).getType().getDisplayName() + ".") : "") + runConfiguration.getName(), value);
      myStoreProperty.put(runConfiguration, property);
    }
  }
}
