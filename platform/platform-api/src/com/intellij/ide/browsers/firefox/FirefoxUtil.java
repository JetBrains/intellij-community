// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.browsers.firefox;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SmartList;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

public final class FirefoxUtil {
  private static final Logger LOG = Logger.getInstance(FirefoxUtil.class);
  @NonNls public static final String PROFILES_INI_FILE = "profiles.ini";

  private FirefoxUtil() {
  }

  @Nullable
  public static File getDefaultProfileIniPath() {
    File[] roots = getProfilesDirs();
    for (File profilesDir : roots) {
      File profilesFile = new File(profilesDir, PROFILES_INI_FILE);
      if (profilesFile.isFile()) {
        return profilesFile;
      }
    }
    return null;
  }

  @Nullable
  public static File getFirefoxExtensionsDir(FirefoxSettings settings) {
    File profilesFile = settings.getProfilesIniFile();
    if (profilesFile != null && profilesFile.exists()) {
      List<FirefoxProfile> profiles = computeProfiles(profilesFile);
      FirefoxProfile profile = findProfileByNameOrDefault(settings.getProfile(), profiles);
      if (profile != null) {
        File profileDir = profile.getProfileDirectory(profilesFile);
        if (profileDir.isDirectory()) {
          return new File(profileDir, "extensions");
        }
      }
    }
    return null;
  }

  @Nullable
  public static FirefoxProfile findProfileByNameOrDefault(@Nullable String name, @NotNull List<? extends FirefoxProfile> profiles) {
    for (FirefoxProfile profile : profiles) {
      if (profile.getName().equals(name)) {
        return profile;
      }
    }
    return getDefaultProfile(profiles);
  }

  @Nullable
  public static FirefoxProfile getDefaultProfile(List<? extends FirefoxProfile> profiles) {
    if (profiles.isEmpty()) {
      return null;
    }

    for (FirefoxProfile profile : profiles) {
      if (profile.isDefault()) {
        return profile;
      }
    }
    return profiles.get(0);
  }

  @NotNull
  public static List<FirefoxProfile> computeProfiles(@Nullable File profilesFile) {
    if (profilesFile == null || !profilesFile.isFile()) {
      return Collections.emptyList();
    }

      try (BufferedReader reader = new BufferedReader(new FileReader(profilesFile))) {
        final List<FirefoxProfile> profiles = new SmartList<>();
        boolean insideProfile = false;
        String currentName = null;
        String currentPath = null;
        boolean isDefault = false;
        boolean isRelative = false;
        boolean eof = false;
        while (!eof) {
          String line = reader.readLine();
          if (line == null) {
            eof = true;
            line = "[]";
          }
          else {
            line = line.trim();
          }

          if (line.startsWith("[") && line.endsWith("]")) {
            if (!StringUtil.isEmpty(currentPath) && !StringUtil.isEmpty(currentName)) {
              profiles.add(new FirefoxProfile(currentName, currentPath, isDefault, isRelative));
            }
            currentName = null;
            currentPath = null;
            isDefault = false;
            isRelative = false;
            insideProfile = StringUtil.startsWithIgnoreCase(line, "[Profile");
            continue;
          }

          final int i = line.indexOf('=');
          if (i != -1 && insideProfile) {
            String name = line.substring(0, i).trim();
            String value = line.substring(i + 1).trim();
            if (name.equalsIgnoreCase("path")) {
              currentPath = value;
            }
            else if (name.equalsIgnoreCase("name")) {
              currentName = value;
            }
            else if (name.equalsIgnoreCase("default") && value.equals("1")) {
              isDefault = true;
            }
            else //noinspection SpellCheckingInspection
              if (name.equalsIgnoreCase("isrelative") && value.equals("1")) {
                isRelative = true;
              }
          }
        }
        return profiles;
      }
    catch (IOException e) {
      LOG.info(e);
      return Collections.emptyList();
    }
  }

  private static File[] getProfilesDirs() {
    final String userHome = SystemProperties.getUserHome();
    if (SystemInfo.isMac) {
      return new File[] {
        new File(userHome, "Library" + File.separator + "Mozilla" + File.separator + "Firefox"),
        new File(userHome, "Library" + File.separator + "Application Support" + File.separator + "Firefox"),
      };
    }
    if (SystemInfo.isUnix) {
      return new File[] {new File(userHome, ".mozilla" + File.separator + "firefox")};
    }

    String localPath = "Mozilla" + File.separator + "Firefox";
    return new File[] {
      new File(System.getenv("APPDATA"), localPath),
      new File(userHome, "AppData" + File.separator + "Roaming" + File.separator + localPath),
      new File(userHome, "Application Data" + File.separator + localPath)
    };
  }
}
