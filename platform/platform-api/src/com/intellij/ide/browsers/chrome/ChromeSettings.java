// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.browsers.chrome;

import com.intellij.ide.browsers.BrowserSpecificSettings;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.PathUtil;
import com.intellij.util.execution.ParametersListUtil;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.XMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class ChromeSettings extends BrowserSpecificSettings {
  public static final String USER_DATA_DIR_ARG = "--user-data-dir=";
  public static final String NO_FIRST_RUN_ARG = "--no-first-run";
  public static final String NO_DEFAULT_BROWSER_CHECK_ARG = "--no-default-browser-check";
  public static final String DISABLE_FIRST_RUN_EXPERIENCE_ARG = "--disable-fre";
  private @Nullable String myCommandLineOptions;
  private @Nullable String myUserDataDirectoryPath;
  private boolean myUseCustomProfile;
  private @NotNull Map<String, String> myEnvironmentVariables = new HashMap<>();

  public ChromeSettings() {
  }

  @Nullable
  @Tag("user-data-dir")
  public String getUserDataDirectoryPath() {
    return myUserDataDirectoryPath;
  }

  @Tag("use-custom-profile")
  public boolean isUseCustomProfile() {
    return myUseCustomProfile;
  }

  @Nullable
  @Tag("command-line-options")
  public String getCommandLineOptions() {
    return myCommandLineOptions;
  }

  public void setCommandLineOptions(@Nullable String value) {
    myCommandLineOptions = StringUtil.nullize(value);
  }

  public void setUserDataDirectoryPath(@Nullable String value) {
    myUserDataDirectoryPath = PathUtil.toSystemIndependentName(StringUtil.nullize(value));
  }

  public void setUseCustomProfile(boolean useCustomProfile) {
    myUseCustomProfile = useCustomProfile;
  }

  @NotNull
  @Override
  public List<String> getAdditionalParameters() {
    if (myCommandLineOptions == null) {
      if (myUseCustomProfile && myUserDataDirectoryPath != null) {
        return Collections.singletonList(USER_DATA_DIR_ARG + FileUtilRt.toSystemDependentName(myUserDataDirectoryPath));
      }
      else {
        return Collections.emptyList();
      }
    }

    List<String> cliOptions = ParametersListUtil.parse(myCommandLineOptions);
    if (myUseCustomProfile && myUserDataDirectoryPath != null) {
      cliOptions.add(USER_DATA_DIR_ARG + FileUtilRt.toSystemDependentName(myUserDataDirectoryPath));
    }
    return cliOptions;
  }

  @Override
  @NotNull
  @XMap(propertyElementName = ("environment-variables"))
  public Map<String, String> getEnvironmentVariables() {
    return myEnvironmentVariables;
  }

  public void setEnvironmentVariables(@NotNull final Map<String, String> environmentVariables) {
    myEnvironmentVariables = environmentVariables;
  }

  @NotNull
  @Override
  public ChromeSettingsConfigurable createConfigurable() {
    return new ChromeSettingsConfigurable(this);
  }

  @Override
  public ChromeSettings clone() {
    ChromeSettings clone = (ChromeSettings)super.clone();
    clone.myEnvironmentVariables = new HashMap<>(myEnvironmentVariables);
    return clone;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    ChromeSettings settings = (ChromeSettings)o;
    return myUseCustomProfile == settings.myUseCustomProfile &&
           Objects.equals(myCommandLineOptions, settings.myCommandLineOptions) &&
           (!myUseCustomProfile || Objects.equals(myUserDataDirectoryPath, settings.myUserDataDirectoryPath)) &&
           myEnvironmentVariables.equals(settings.myEnvironmentVariables);
  }
}
