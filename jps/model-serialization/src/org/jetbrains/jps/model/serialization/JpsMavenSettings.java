// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.serialization;

import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.text.Strings;
import com.intellij.util.EnvironmentUtil;
import com.intellij.util.SystemProperties;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.Namespace;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.openapi.util.text.StringUtil.*;

@ApiStatus.Internal
public final class JpsMavenSettings {
  private static final String REPOSITORY_PATH = "repository";
  private static final String M2_DIR = ".m2";
  private static final String CONF_DIR = "conf";
  private static final String SETTINGS_XML = "settings.xml";
  /**
   * [<a href="https://maven.apache.org/configure.html">https://maven.apache.org/configure.html</a>]
   */
  private static final String MAVEN_OPTS = "MAVEN_OPTS";

  /**
   * [<a href="https://maven.apache.org/ref/4.0.0-rc-1/api/maven-api-core/apidocs/constant-values.html#org.apache.maven.api.Constants.MAVEN_REPO_LOCAL">https://maven.apache.org/ref/4.0.0-rc-1/api/maven-api-core/apidocs</a>]
   */
  private static final String MAVEN_REPO_LOCAL = "maven.repo.local";

  @SuppressWarnings("HttpUrlsUsage")
  private static final List<Namespace> KNOWN_NAMESPACES = List.of(
    Namespace.getNamespace("http://maven.apache.org/SETTINGS/1.0.0"),
    Namespace.getNamespace("http://maven.apache.org/SETTINGS/1.1.0"),
    Namespace.getNamespace("http://maven.apache.org/SETTINGS/1.2.0")
  );

  public static @NotNull File getUserMavenSettingsXml() {
    String defaultMavenFolder = SystemProperties.getUserHome() + File.separator + M2_DIR;
    return new File(defaultMavenFolder, SETTINGS_XML);
  }

  public static @Nullable File getGlobalMavenSettingsXml() {
    String mavenHome = resolveMavenHomeDirectory();
    if (mavenHome == null) {
      return null;
    }
    return new File(mavenHome + File.separator + CONF_DIR, SETTINGS_XML);
  }

  private static @Nullable String resolveMavenHomeDirectory() {
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

  public static @NotNull String getMavenRepositoryPath() {
    String property = findMavenRepositoryProperty(EnvironmentUtil.getValue(MAVEN_OPTS));
    if (isNotEmpty(property)) {
      return property;
    }

    File userSettingsFile = getUserMavenSettingsXml();
    File settingsFile = userSettingsFile.exists() ? userSettingsFile : getGlobalMavenSettingsXml();

    if (settingsFile != null && settingsFile.exists()) {
      String fromSettings = getRepositoryFromSettings(settingsFile);
      if (isNotEmpty(fromSettings)) {
        return fromSettings;
      }
    }

    return SystemProperties.getUserHome() + File.separator + M2_DIR + File.separator + REPOSITORY_PATH;
  }

  private static String findMavenRepositoryProperty(String mavenOpts) {
    if (mavenOpts == null) {
      return null;
    }

    // -Dmaven.repo.local=/path/to/repo     -> [1]:"maven.repo.local" [2]:"" [3]:"/path/to/repo"
    // -Dmaven.repo.local="/my custom/path" -> [1]:"maven.repo.local" [2]:"/my custom/path" [3]:""
    Pattern propertyPattern = Pattern.compile("-D([^=\\s]+)(?:=(?:\"([^\"]+)\"|(\\S+)))?");
    Matcher matcher = propertyPattern.matcher(mavenOpts);
    Map<String, String> properties = new HashMap<>();

    while (matcher.find()) {
      String key = matcher.group(1);
      String quotedValue = matcher.group(2);
      String unquotedValue = matcher.group(3);
      String value = quotedValue != null && !quotedValue.isEmpty() ? quotedValue : unquotedValue;
      properties.put(key, value);
    }

    return properties.get(MAVEN_REPO_LOCAL);
  }


  /**
   * Load remote repositories authentication settings from Maven's settings.xml.
   *
   * @param globalMavenSettingsXml global settings.xml path, has lower priority.
   * @param userMavenSettingsXml user's local settings.xml path, has higher priority.
   * @return Map of Remote Repository ID to Authentication Data elements.
   */
  @ApiStatus.Internal
  public static @NotNull Map<String, RemoteRepositoryAuthentication> loadAuthenticationSettings(
    @Nullable File globalMavenSettingsXml,
    @NotNull File userMavenSettingsXml
  ) {
    if ((globalMavenSettingsXml == null || !globalMavenSettingsXml.exists()) && !userMavenSettingsXml.exists()) {
      return Collections.emptyMap();
    }

    Map<String, RemoteRepositoryAuthentication> result = new HashMap<>();
    if (globalMavenSettingsXml != null && globalMavenSettingsXml.exists()) {
      loadAuthenticationFromSettings(globalMavenSettingsXml, result);
    }
    if (userMavenSettingsXml.exists()) {
      loadAuthenticationFromSettings(userMavenSettingsXml, result);
    }
    return result;
  }

  private static @Nullable String fromBrew() {
    final File brewDir = new File("/usr/local/Cellar/maven");
    final String[] list = brewDir.list();
    if (list == null || list.length == 0) return null;

    Arrays.sort(list, (o1, o2) -> compareVersionNumbers(o2, o1));

    return brewDir + File.separator + list[0] + "/libexec";
  }

  private static @Nullable String getRepositoryFromSettings(final File file) {
    Element settingsXmlRoot;
    try {
      settingsXmlRoot = JDOMUtil.load(file);
    }
    catch (JDOMException | IOException e) {
      return null;
    }

    Optional<String> maybeRepository = KNOWN_NAMESPACES.stream()
      .map(it -> settingsXmlRoot.getChildText("localRepository", it))
      .filter(it -> it != null && !isEmpty(it))
      .findFirst();
    return maybeRepository.orElse(null);
  }

  private static boolean isValidMavenHome(@Nullable String path) {
    return Strings.isNotEmpty(path) && Files.exists(Path.of(path));
  }

  private static void loadAuthenticationFromSettings(@NotNull File settingsXml,
                                                     @NotNull Map<String, RemoteRepositoryAuthentication> output) {
    Element settingsXmlRoot;
    try {
      settingsXmlRoot = JDOMUtil.load(settingsXml);
    }
    catch (JDOMException | IOException e) {
      return;
    }

    Optional<Namespace> maybeNamespace = KNOWN_NAMESPACES.stream()
      .filter(it -> settingsXmlRoot.getChild("servers", it) != null)
      .findFirst();
    if (maybeNamespace.isEmpty()) {
      return;
    }

    Namespace namespace = maybeNamespace.get();
    Element serversElement = settingsXmlRoot.getChild("servers", namespace);

    for (Element serverElement : serversElement.getChildren("server", namespace)) {
      String id = serverElement.getChildText("id", namespace);
      String username = serverElement.getChildText("username", namespace);
      String password = serverElement.getChildText("password", namespace);

      if (id != null && username != null && password != null) {
        output.put(id, new RemoteRepositoryAuthentication(username, password));
      }
    }
  }

  public static final class RemoteRepositoryAuthentication {
    private final String username;
    private final String password;

    public RemoteRepositoryAuthentication(String username, String password) {
      this.username = username;
      this.password = password;
    }

    public String getUsername() {
      return username;
    }

    public String getPassword() {
      return password;
    }
  }
}
