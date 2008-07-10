/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
package com.intellij.openapi.application;

import java.util.Calendar;

public abstract class ApplicationInfo {
  public abstract Calendar getBuildDate();
  public abstract String getBuildNumber();
  public abstract String getMajorVersion();
  public abstract String getMinorVersion();
  public abstract String getVersionName();
  public abstract String getHelpURL();

  public static ApplicationInfo getInstance() {
    return ApplicationManager.getApplication().getComponent(ApplicationInfo.class);
  }
}
