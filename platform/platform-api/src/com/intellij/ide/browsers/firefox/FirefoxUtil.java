/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/**
 * @author nik
 */
public class FirefoxUtil {
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
  public static FirefoxProfile findProfileByNameOrDefault(@Nullable String name, @NotNull List<FirefoxProfile> profiles) {
    for (FirefoxProfile profile : profiles) {
      if (profile.getName().equals(name)) {
        return profile;
      }
    }
    return getDefaultProfile(profiles);
  }

  @Nullable
  public static FirefoxProfile getDefaultProfile(List<FirefoxProfile> profiles) {
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

    try {
      BufferedReader reader;
      reader = new BufferedReader(new FileReader(profilesFile));
      try {
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
      finally {
        reader.close();
      }
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
