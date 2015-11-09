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
package com.intellij.openapi.application;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.util.BuildNumber;

import java.awt.*;
import java.util.Calendar;

public abstract class ApplicationInfo {
  public abstract Calendar getBuildDate();
  public abstract BuildNumber getBuild();
  public abstract String getApiVersion();
  public abstract String getMajorVersion();
  public abstract String getMinorVersion();
  public abstract String getMicroVersion();
  public abstract String getPatchVersion();
  public abstract String getVersionName();
  public abstract String getHelpURL();
  public abstract String getCompanyName();
  public abstract String getCompanyURL();
  public abstract String getThirdPartySoftwareURL();
  public abstract String getJetbrainsTvUrl();
  public abstract String getEvalLicenseUrl();
  public abstract String getKeyConversionUrl();

  public abstract Rectangle getAboutLogoRect();
  public abstract boolean hasHelp();
  public abstract boolean hasContextHelp();

  public abstract String getFullVersion();
  public abstract String getStrictVersion();

  public static ApplicationInfo getInstance() {
    return ServiceManager.getService(ApplicationInfo.class);
  }

  public static boolean helpAvailable() {
    return ApplicationManager.getApplication() != null && getInstance() != null && getInstance().hasHelp();
  }

  public static boolean contextHelpAvailable() {
    return ApplicationManager.getApplication() != null && getInstance() != null && getInstance().hasContextHelp();
  }

  /** @deprecated use {@link #getBuild()} instead (to remove in IDEA 16) */
  @SuppressWarnings("UnusedDeclaration")
  public String getBuildNumber() {
    return getBuild().asString();
  }
}
