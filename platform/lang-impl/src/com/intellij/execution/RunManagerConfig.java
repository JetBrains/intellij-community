/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

package com.intellij.execution;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NonNls;

public class RunManagerConfig {
  public static final String MAKE = ExecutionBundle.message("before.run.property.make");

  public static final int MIN_RECENT_LIMIT = 0;
  public static final int DEFAULT_RECENT_LIMIT = 5;

  private final PropertiesComponent myPropertiesComponent;

  @NonNls private static final String RECENTS_LIMIT = "recentsLimit";
  @NonNls private static final String RESTART_REQUIRES_CONFIRMATION = "restartRequiresConfirmation";
  @NonNls private static final String STOP_INCOMPATIBLE_REQUIRES_CONFIRMATION = "stopIncompatibleRequiresConfirmation";

  public RunManagerConfig(PropertiesComponent propertiesComponent) {
    myPropertiesComponent = propertiesComponent;
  }

  public int getRecentsLimit() {
    return Math.max(MIN_RECENT_LIMIT, StringUtil.parseInt(myPropertiesComponent.getValue(RECENTS_LIMIT), DEFAULT_RECENT_LIMIT));
  }

  public void setRecentsLimit(int recentsLimit) {
    myPropertiesComponent.setValue(RECENTS_LIMIT, recentsLimit, DEFAULT_RECENT_LIMIT);
  }

  public boolean isRestartRequiresConfirmation() {
    return myPropertiesComponent.getBoolean(RESTART_REQUIRES_CONFIRMATION, true);
  }

  public void setRestartRequiresConfirmation(boolean restartRequiresConfirmation) {
    myPropertiesComponent.setValue(RESTART_REQUIRES_CONFIRMATION, restartRequiresConfirmation, true);
  }

  public boolean isStopIncompatibleRequiresConfirmation() {
    return myPropertiesComponent.getBoolean(STOP_INCOMPATIBLE_REQUIRES_CONFIRMATION, true);
  }

  public void setStopIncompatibleRequiresConfirmation(boolean stopIncompatibleRequiresConfirmation) {
    myPropertiesComponent.setValue(STOP_INCOMPATIBLE_REQUIRES_CONFIRMATION, stopIncompatibleRequiresConfirmation, true);
  }
}
