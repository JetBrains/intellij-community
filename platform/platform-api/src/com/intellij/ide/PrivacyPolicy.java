/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.ide;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;

/**
 * @author Eugene Zhuravlev
 *         Date: 09-Mar-16
 */
public final class PrivacyPolicy {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.PrivacyPolicy");
  private static final String CACHED_RESOURCE_NAME = "Cached";
  private static final String RELATIVE_RESOURCE_PATH = "JetBrains/PrivacyPolicy";
  private static final String VERSION_COMMENT_START = "<!--";
  private static final String VERSION_COMMENT_END = "-->";
  private static final String ACCEPTED_VERSION_KEY = "JetBrains.privacy_policy.accepted_version";
  private static final Version EMBEDDED_VERSION = new Version(1, 0);
  private static final Version MAGIC_VERSION = new Version(999, 999);
  @Nullable
  private static volatile Version ourLatestVersion;

  private static final File ourCachedPolicyFile;
  static {
    File dataDir = null;
    if (SystemInfo.isWindows) {
      final String appdata = System.getenv("APPDATA");
      if (appdata != null) {
        dataDir = new File(appdata, RELATIVE_RESOURCE_PATH);
      }
    }
    else {
      final String userHome = System.getProperty("user.home");
      if (userHome != null) {
        if (SystemInfo.isMac) {
          final File dataRoot = new File(userHome, "/Library/Application Support");
          if (dataRoot.exists()) {
            dataDir = new File(dataRoot, RELATIVE_RESOURCE_PATH);
          }
        }
        else if (SystemInfo.isUnix) {
          final String dataHome = System.getenv("XDG_DATA_HOME");
          final File dataRoot = dataHome == null ? new File(userHome, ".local/share") : new File(dataHome);
          if (dataRoot.exists()) {
            dataDir = new File(dataRoot, RELATIVE_RESOURCE_PATH);
          }
        }
      }
    }
    if (dataDir == null)  {
      // default location
      dataDir = new File(PathManager.getSystemPath(), "PrivacyPolicy");
    }
    dataDir.mkdirs();
    ourCachedPolicyFile = new File(dataDir, CACHED_RESOURCE_NAME);
  }

  public static boolean isLatestVersionAccepted() {
    final Version latest = getLatestVersion();
    return getAcceptedVersion().equals(latest) || MAGIC_VERSION.equals(latest);
  }

  public static void setVersionAccepted(@NotNull Version version) {
    if (version.isUnknown()) {
      Prefs.remove(ACCEPTED_VERSION_KEY);
    }
    else {
      Prefs.put(ACCEPTED_VERSION_KEY, version.toString());
    }
  }

  @NotNull
  public static Version getLatestVersion() {
    final Version cached = ourLatestVersion;
    if (cached != null) {
      return cached;
    }
    Version latest = EMBEDDED_VERSION;
    if (ourCachedPolicyFile.exists()) {
      try {
        final Version version = loadVersion(new FileInputStream(ourCachedPolicyFile));
        if (!version.isUnknown() && version.compareTo(latest) > 0) {
          latest = version;
        }
      }
      catch (FileNotFoundException ignored) {
      }
    }
    ourLatestVersion = latest;
    return latest;
  }

  @NotNull
  public static Version getAcceptedVersion() {
    return Version.fromString(Prefs.get(ACCEPTED_VERSION_KEY, null));
  }

  public static String getText() {
    return getText(getLatestVersion());
  }

  @NotNull
  public static String getText(@NotNull Version version) {
    String text = null;
    try {
      if (EMBEDDED_VERSION.equals(version)) {
        text = loadText(PrivacyPolicy.class.getResourceAsStream("/PrivacyPolicy-" + EMBEDDED_VERSION + ".html"));
      }
      else {
        text = loadText(new FileInputStream(ourCachedPolicyFile));
      }
    }
    catch (Exception ignored) {
    }
    return text == null? "" : text;
  }

  public static void updateText(String text) {
    try {
      FileUtil.writeToFile(ourCachedPolicyFile, text);
    }
    catch (IOException e) {
      LOG.info(e);
    }
    finally {
      ourLatestVersion = null; // clear cache
    }
  }

  @Nullable
  private static String loadText(InputStream stream) {
    try {
      if (stream != null) {
        final Reader reader = new InputStreamReader(stream, "utf-8");
        try {
          return new String(FileUtil.adaptiveLoadText(reader));
        }
        finally {
          reader.close();
        }
      }
    }
    catch (IOException e) {
      LOG.info(e);
    }
    return null;
  }

  @NotNull
  private static Version loadVersion(final InputStream is) {
    try {
      final BufferedReader reader = new BufferedReader(new InputStreamReader(is, "utf-8"));
      try {
        final String line = reader.readLine();
        if (line != null) {
          final int startComment = line.indexOf(VERSION_COMMENT_START);
          if (startComment >= 0 ) {
            final int endComment = line.indexOf(VERSION_COMMENT_END);
            if (endComment > startComment) {
              return Version.fromString(line.substring(startComment + VERSION_COMMENT_START.length(), endComment).trim());
            }
          }
        }
      }
      finally {
        reader.close();
      }
    }
    catch (IOException e) {
      LOG.info(e);
    }
    return Version.UNKNOWN;
  }

  public static final class Version implements Comparable<PrivacyPolicy.Version>{
    public static final Version UNKNOWN = new Version(-1, -1);

    private final int myMajor;
    private final int myMinor;

    private Version(int major, int minor) {
      myMajor = major;
      myMinor = minor;
    }

    /**
     * @param ver string in format "[major].[minor]"
     */
    public static Version fromString(@Nullable String ver) {
      int major = -1, minor = -1;
      final int dot = ver == null ? -1 : ver.indexOf('.');
      if (dot > 0) {
        major = Integer.parseInt(ver.substring(0, dot));
        minor = Integer.parseInt(ver.substring(dot + 1));
      }
      return major < 0 || minor < 0? UNKNOWN : new Version(major, minor);
    }

    public boolean isUnknown() {
      return myMajor < 0 || myMinor < 0;
    }

    @Override
    public int compareTo(Version other) {
      if (isUnknown()) {
        return other.isUnknown()? 0 : -1;
      }
      final int majorDiff = myMajor - other.myMajor;
      return majorDiff != 0? majorDiff : myMinor - other.myMinor;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Version version = (Version)o;

      if (myMajor != version.myMajor) return false;
      if (myMinor != version.myMinor) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = myMajor;
      result = 31 * result + myMinor;
      return result;
    }

    @Override
    public String toString() {
      return isUnknown()? "unknown" : myMajor + "." + myMinor;
    }
  }
}
