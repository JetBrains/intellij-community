/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.openapi.util.BuildNumber;

import java.awt.*;
import java.util.Calendar;

public abstract class ApplicationInfo {
  public abstract Calendar getBuildDate();
  public abstract BuildNumber getBuild();
  public abstract String getApiVersion();
  public abstract String getMajorVersion();
  public abstract String getMinorVersion();
  public abstract String getVersionName();
  public abstract String getHelpURL();
  public abstract String getCompanyName();
  public abstract String getCompanyURL();
  public abstract String getThirdPartySoftwareURL();
  public abstract Rectangle getAboutLogoRect();
  public abstract boolean hasHelp();
  public abstract boolean hasContextHelp();

  public String getFullVersion() {
    final String majorVersion = getMajorVersion();
    if (majorVersion != null && majorVersion.trim().length() > 0) {
      final String minorVersion = getMinorVersion();
      if (minorVersion != null && minorVersion.trim().length() > 0) {
        return majorVersion + "." + minorVersion;
      }
      else {
        return majorVersion + ".0";
      }
    }
    else {
      return getVersionName();
    }
  }

  public static ApplicationInfo getInstance() {
    return ApplicationManager.getApplication().getComponent(ApplicationInfo.class);
  }

  public static boolean helpAvailable() {
    return ApplicationManager.getApplication() != null && getInstance() != null && getInstance().hasHelp();
  }

  public static boolean contextHelpAvailable() {
    return ApplicationManager.getApplication() != null && getInstance() != null && getInstance().hasContextHelp();
  }

  /** @deprecated use {@link #getBuild()} instead (to remove in IDEA 14) */
  @SuppressWarnings("UnusedDeclaration")
  public String getBuildNumber() {
    return getBuild().asString();
  }
}
