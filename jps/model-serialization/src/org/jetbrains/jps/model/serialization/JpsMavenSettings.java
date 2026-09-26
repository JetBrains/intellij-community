// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.serialization;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.util.EnvironmentUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.system.OS;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.Namespace;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static com.intellij.openapi.util.text.StringUtil.nullize;

@ApiStatus.Internal
public final class JpsMavenSettings {
  private static final Logger LOG = Logger.getInstance(JpsMavenSettings.class);
  private static final String REPOSITORY_PATH = "repository";
  private static final String M2_DIR = ".m2";
  private static final String CONF_DIR = "conf";
  private static final String SETTINGS_XML = "settings.xml";

  // https://maven.apache.org/configure.html
  private static final String MAVEN_OPTS = "MAVEN_OPTS";
  // https://maven.apache.org/ref/4.0.0-rc-1/api/maven-api-core/apidocs/constant-values.html#org.apache.maven.api.Constants.MAVEN_REPO_LOCAL
  private static final String MAVEN_REPO_LOCAL = "maven.repo.local";

  public static @NotNull Path getUserMavenSettingsXml() {
    return Path.of(SystemProperties.getUserHome(), M2_DIR, SETTINGS_XML);
  }

  public static @Nullable Path getGlobalMavenSettingsXml() {
    if (OS.CURRENT == OS.macOS) {
      var defaultBrewPath = Path.of("/opt/homebrew/Cellar/maven/Current/libexec", CONF_DIR, SETTINGS_XML);
      if (Files.exists(defaultBrewPath)) {
        LOG.debug("Maven home is read from HomeBrew: " + defaultBrewPath);
        return defaultBrewPath;
      }
    }

    if (OS.CURRENT == OS.Linux || OS.CURRENT == OS.macOS) {
      var defaultGlobalPath = Path.of("/usr/share/maven", CONF_DIR, SETTINGS_XML);
      if (Files.exists(defaultGlobalPath)) {
        LOG.debug("Maven home is read from the default global directory: " + defaultGlobalPath);
        return defaultGlobalPath;
      }
    }
    return null;
  }

  public static @NotNull String getMavenRepositoryPath() {
    var property = findMavenRepositoryProperty(EnvironmentUtil.getValue(MAVEN_OPTS));
    if (isNotEmpty(property)) {
      LOG.debug("Maven repository path is read from " + MAVEN_OPTS + " environment variable: " + property);
      return property;
    }

    try {
      var userSettingsFile = getUserMavenSettingsXml();
      var settingsFile = Files.exists(userSettingsFile) ? userSettingsFile : getGlobalMavenSettingsXml();

      if (settingsFile != null && Files.exists(settingsFile)) {
        var fromSettings = getRepositoryFromSettings(settingsFile);
        if (isNotEmpty(fromSettings)) {
          LOG.debug("Maven repository path is read from " + settingsFile + " - " + fromSettings);
          return fromSettings;
        }
      }
    }
    catch (Exception e) {
      LOG.warn("Cannot read Maven settings.xml", e);
    }

    return Path.of(SystemProperties.getUserHome(), M2_DIR, REPOSITORY_PATH).toString();
  }

  private static String findMavenRepositoryProperty(String mavenOpts) {
    if (mavenOpts == null) {
      return null;
    }

    // -Dmaven.repo.local=/path/to/repo     -> [1]:"maven.repo.local" [2]:"" [3]:"/path/to/repo"
    // -Dmaven.repo.local="/my custom/path" -> [1]:"maven.repo.local" [2]:"/my custom/path" [3]:""
    var propertyPattern = Pattern.compile("-D([^=\\s]+)(?:=(?:\"([^\"]+)\"|(\\S+)))?");
    var matcher = propertyPattern.matcher(mavenOpts);
    Map<String, String> properties = new HashMap<>();

    while (matcher.find()) {
      var key = matcher.group(1);
      var quotedValue = matcher.group(2);
      var unquotedValue = matcher.group(3);
      var value = quotedValue != null && !quotedValue.isEmpty() ? quotedValue : unquotedValue;
      properties.put(key, value);
    }

    return properties.get(MAVEN_REPO_LOCAL);
  }

  /**
   * Load remote repositories authentication settings from Maven's settings.xml.
   *
   * @param globalMavenSettingsXml the global 'settings.xml' path (lower priority)
   * @param userMavenSettingsXml user's local 'settings.xml' path (higher priority)
   * @return Map of Remote Repository ID to Authentication Data elements.
   */
  public static @NotNull Map<String, RemoteRepositoryAuthentication> loadAuthenticationSettings(
    @Nullable Path globalMavenSettingsXml,
    @NotNull Path userMavenSettingsXml
  ) {
    var result = new HashMap<String, RemoteRepositoryAuthentication>();
    if (globalMavenSettingsXml != null) {
      loadAuthenticationFromSettings(globalMavenSettingsXml, result);
    }
    loadAuthenticationFromSettings(userMavenSettingsXml, result);
    return result;
  }

  public static @Nullable String getRepositoryFromSettings(@NotNull Path file) {
    Element settingsXmlRoot;
    try {
      settingsXmlRoot = JDOMUtil.load(file);
    }
    catch (JDOMException | IOException e) {
      return null;
    }

    var child = settingsXmlRoot.getChild("localRepository", null);
    if (child == null || isUnknownNamespace(child.getNamespace())) {
      return null;
    }
    return nullize(child.getText());
  }

  private static void loadAuthenticationFromSettings(Path settingsXml, Map<String, RemoteRepositoryAuthentication> output) {
    Element settingsXmlRoot;
    try {
      settingsXmlRoot = JDOMUtil.load(settingsXml);
    }
    catch (JDOMException | IOException ignored) {
      return;
    }

    var serversElement = settingsXmlRoot.getChild("servers", null);
    if (serversElement == null || isUnknownNamespace(serversElement.getNamespace())) {
      return;
    }

    for (var serverElement : serversElement.getChildren("server", null)) {
      var id = serverElement.getChildText("id", null);
      var username = serverElement.getChildText("username", null);
      var password = serverElement.getChildText("password", null);
      if (id != null && username != null && password != null) {
        output.put(id, new RemoteRepositoryAuthentication(username, password));
      }
    }
  }

  private static boolean isUnknownNamespace(@Nullable Namespace namespace) {
    //noinspection HttpUrlsUsage
    return namespace != null && isNotEmpty(namespace.getURI()) && !namespace.getURI().startsWith("http://maven.apache.org/SETTINGS/");
  }

  public static final class RemoteRepositoryAuthentication {
    public final String username;
    public final String password;

    public RemoteRepositoryAuthentication(String username, String password) {
      this.username = username;
      this.password = password;
    }
  }
}
