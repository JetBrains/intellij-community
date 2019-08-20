// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.incremental.relativizer;

import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.SystemProperties;
import org.jdom.Element;
import org.jdom.Namespace;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Arrays;

import static com.intellij.openapi.util.text.StringUtil.*;

class MavenPathRelativizer implements PathRelativizer {
  private static final String IDENTIFIER = "$MAVEN_REPOSITORY$";
  private static final String M2_DIR = ".m2";
  private static final String CONF_DIR = "conf";
  private static final String SETTINGS_XML = "settings.xml";
  private static final String REPOSITORY_PATH = "repository";
  private static final Namespace SETTINGS_NAMESPACE = Namespace.getNamespace("http://maven.apache.org/SETTINGS/1.0.0");

  private boolean myPathInitialized;
  private String myMavenRepositoryPath;

  @Nullable
  @Override
  public String toRelativePath(@NotNull String path) {
    initializeMavenRepositoryPath();
    if (myMavenRepositoryPath == null || !FileUtil.startsWith(path, myMavenRepositoryPath)) return null;
    return IDENTIFIER + path.substring(myMavenRepositoryPath.length());

  }

  @Nullable
  @Override
  public String toAbsolutePath(@NotNull String path) {
    initializeMavenRepositoryPath();
    if (myMavenRepositoryPath == null || !path.startsWith(IDENTIFIER)) return null;
    return myMavenRepositoryPath + path.substring(IDENTIFIER.length());
  }

  private void initializeMavenRepositoryPath() {
    if (myPathInitialized) return;

    String defaultMavenFolder = SystemProperties.getUserHome() + File.separator + M2_DIR;
    // Check user local settings
    File userSettingsFile = new File(defaultMavenFolder, SETTINGS_XML);
    if (userSettingsFile.exists()) {
      String fromUserSettings = getRepositoryFromSettings(userSettingsFile);
      if (isNotEmpty(fromUserSettings) && new File(fromUserSettings).exists()) {
        myMavenRepositoryPath = PathRelativizerService.normalizePath(fromUserSettings);
        myPathInitialized = true;
        return;
      }
    }

    // Check global maven local settings
    File globalSettingsFile = new File(resolveMavenHomeDirectory() + File.separator + CONF_DIR, SETTINGS_XML);
    if (globalSettingsFile.exists()) {
      String fromGlobalSettings = getRepositoryFromSettings(globalSettingsFile);
      if (isNotEmpty(fromGlobalSettings) && new File(fromGlobalSettings).exists()) {
        myMavenRepositoryPath = PathRelativizerService.normalizePath(fromGlobalSettings);
        myPathInitialized = true;
        return;
      }
    }

    String defaultMavenRepository = defaultMavenFolder + File.separator + REPOSITORY_PATH;
    if (FileUtil.exists(defaultMavenFolder)) {
      myMavenRepositoryPath = PathRelativizerService.normalizePath(defaultMavenRepository);
      myPathInitialized = true;
    }
  }

  @Nullable
  private static String getRepositoryFromSettings(final File file) {
    try {
      Element repository = JDOMUtil.load(file).getChild("localRepository", SETTINGS_NAMESPACE);
      if (repository == null) return null;

      String text = repository.getText();
      if (isEmpty(text)) return null;
      return text;
    }
    catch (Exception e) {
      return null;
    }
  }

  @Nullable
  private static String resolveMavenHomeDirectory() {
    String m2home = System.getenv("M2_HOME");
    if (isValidMavenHome(m2home)) return m2home;

    String mavenHome = System.getenv("MAVEN_HOME");
    if (isValidMavenHome(mavenHome)) return mavenHome;

    String m2UserHome = SystemProperties.getUserHome() + File.separator + M2_DIR;
    if (isValidMavenHome(m2UserHome)) return m2UserHome;

    if (SystemInfoRt.isMac) {
      String mavenFromBrew = fromBrew();
      if (isValidMavenHome(mavenFromBrew)) return mavenFromBrew;
    }

    if (SystemInfoRt.isLinux || SystemInfoRt.isMac) {
      String defaultHome = "/usr/share/maven";
      if (isValidMavenHome(defaultHome)) return defaultHome;
    }
    return null;
  }

  @Nullable
  private static String fromBrew() {
    final File brewDir = new File("/usr/local/Cellar/maven");
    final String[] list = brewDir.list();
    if (list == null || list.length == 0) return null;

    Arrays.sort(list, (o1, o2) -> compareVersionNumbers(o2, o1));

    return brewDir + File.separator + list[0] + "/libexec";
  }

  private static boolean isValidMavenHome(@Nullable String path) {
    return isNotEmpty(path) && FileUtil.exists(path);
  }
}
