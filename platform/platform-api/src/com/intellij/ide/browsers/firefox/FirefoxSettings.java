// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.browsers.firefox;

import com.intellij.ide.browsers.BrowserSpecificSettings;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.PathUtil;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class FirefoxSettings extends BrowserSpecificSettings {
  private String myProfilesIniPath;
  private @NlsSafe String myProfile;

  public FirefoxSettings() {
  }

  public FirefoxSettings(@Nullable String profilesIniPath, @NlsSafe @Nullable String profile) {
    myProfilesIniPath = StringUtil.nullize(profilesIniPath);
    myProfile = StringUtil.nullize(profile);
  }

  @Nullable
  @Tag("profiles-ini-path")
  public String getProfilesIniPath() {
    return myProfilesIniPath;
  }

  public void setProfilesIniPath(@Nullable String value) {
    myProfilesIniPath = PathUtil.toSystemIndependentName(StringUtil.nullize(value));
  }

  @Nullable
  @Tag("profile")
  public @NlsSafe String getProfile() {
    return myProfile;
  }

  public void setProfile(@Nullable String value) {
    myProfile = StringUtil.nullize(value);
  }

  @NotNull
  @Override
  public Configurable createConfigurable() {
    return new FirefoxSettingsConfigurable(this);
  }

  @Nullable
  public File getProfilesIniFile() {
    if (myProfilesIniPath != null) {
      return new File(FileUtil.toSystemDependentName(myProfilesIniPath));
    }
    return FirefoxUtil.getDefaultProfileIniPath();
  }

  @NotNull
  @Override
  public List<String> getAdditionalParameters() {
    List<FirefoxProfile> profiles = FirefoxUtil.computeProfiles(getProfilesIniFile());
    if (profiles.size() >= 2) {
      FirefoxProfile profile = FirefoxUtil.findProfileByNameOrDefault(myProfile, profiles);
      if (profile != null && !profile.isDefault()) {
        return Arrays.asList("-P", profile.getName());
      }
    }
    return Collections.emptyList();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    FirefoxSettings settings = (FirefoxSettings)o;
    return Objects.equals(myProfilesIniPath, settings.myProfilesIniPath) &&
           Objects.equals(myProfile, settings.myProfile);
  }
}
