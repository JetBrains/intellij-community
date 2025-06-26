// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.serialization;

import com.intellij.openapi.diagnostic.Logger;
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
  private static final Logger LOG = Logger.getInstance(JpsMavenSettings.class);
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
    String pathFromBrew = fromBrew();
    if (pathFromBrew != null && getMavenSettingsFromHome(pathFromBrew).exists()) {
      LOG.debug("Maven home is read from brew: " + pathFromBrew);
      return getMavenSettingsFromHome(pathFromBrew);
    }

    if (SystemInfoRt.isLinux || SystemInfoRt.isMac) {
      String defaultGlobalPath = "/usr/share/maven";
      if (isValidMavenHome(defaultGlobalPath) && getMavenSettingsFromHome(defaultGlobalPath).exists()) {
        LOG.debug("Maven home is read from default global directory: " + defaultGlobalPath);
        return getMavenSettingsFromHome(defaultGlobalPath);
      }
    }
    return null;
  }

  private static @NotNull File getMavenSettingsFromHome(String homePath) {
    return new File(homePath + File.separator + CONF_DIR, SETTINGS_XML);
  }

  public static @NotNull String getMavenRepositoryPath() {
    String property = findMavenRepositoryProperty(EnvironmentUtil.getValue(MAVEN_OPTS));
    if (isNotEmpty(property)) {
      LOG.debug("Maven repository path is read from " + MAVEN_OPTS + " environment variable: " + property);
      return property;
    }

    try {
      File userSettingsFile = getUserMavenSettingsXml();
      File settingsFile = userSettingsFile.exists() ? userSettingsFile : getGlobalMavenSettingsXml();

      if (settingsFile != null && settingsFile.exists()) {
        String fromSettings = getRepositoryFromSettings(settingsFile);
        if (isNotEmpty(fromSettings)) {
          LOG.debug("Maven repository path is read from " + settingsFile + " - " + fromSettings);
          return fromSettings;
        }
      }
    } catch (Exception e) {
      LOG.warn("Cannot read Maven settings.xml", e);
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
    if (!SystemInfoRt.isMac) {
      return null;
    }
    String defaultBrewPath = "/opt/homebrew/Cellar/maven/Current/libexec";
    return isValidMavenHome(defaultBrewPath) ? defaultBrewPath : null;
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
