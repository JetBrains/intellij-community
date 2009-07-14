package com.intellij.execution;

import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.execution.util.StoringPropertyContainer;
import com.intellij.ide.util.PropertiesComponent;
import org.jetbrains.annotations.NonNls;

public class RunManagerConfig {
  public static final String MAKE = ExecutionBundle.message("before.run.property.make");
  private final StoringPropertyContainer myProperties;
  private final PropertiesComponent myPropertiesComponent;
  @NonNls private static final String RECENTS_LIMIT = "recentsLimit";

  public RunManagerConfig(PropertiesComponent propertiesComponent,
                          RunManagerImpl manager) {
    myPropertiesComponent = propertiesComponent;
    myProperties = new StoringPropertyContainer("RunManagerConfig.", propertiesComponent);
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
