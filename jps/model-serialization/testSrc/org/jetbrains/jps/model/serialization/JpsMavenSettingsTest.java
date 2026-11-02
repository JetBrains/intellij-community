// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.serialization;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JpsMavenSettingsTest {
  @TempDir Path tempDir;

  @Test
  public void testLoadAuthenticationSettingsNoFilesExist() {
    var nonExistingFile = tempDir.resolve("non-existing-file");
    var result = JpsMavenSettings.loadAuthenticationSettings(nonExistingFile, nonExistingFile);
    assertTrue(result.isEmpty());
  }

  @Test
  public void testLoadAuthenticationSettingsEmptyFile() throws Exception {
    var emptyFile = Files.createFile(tempDir.resolve("empty-file"));
    var result = JpsMavenSettings.loadAuthenticationSettings(emptyFile, emptyFile);
    assertTrue(result.isEmpty());
  }

  @Test
  public void testLoadSettingsUnknownNamespace() throws Exception {
    var wrongNsFile = Files.createFile(tempDir.resolve("wrong-namespace"));
    var wrongNsXml = """
      <settings xmlns="http://maven.apache.org/UNKNOWN">
          <localRepository>some/path</localRepository>
          <servers>
              <server>
                <id>id1</id>
                <username>user1</username>
                <password>pass1</password>
              </server>
          </servers>
      </settings>
      """;
    Files.writeString(wrongNsFile, wrongNsXml, StandardCharsets.UTF_8);

    var settings = JpsMavenSettings.loadAuthenticationSettings(wrongNsFile, wrongNsFile);
    assertTrue(settings.isEmpty());

    var localRepo = JpsMavenSettings.getRepositoryFromSettings(wrongNsFile);
    assertNull(localRepo);
  }

  @Test
  public void testLoadAuthenticationSettingsUserSettingsHasHigherPriority() throws Exception {
    var globalXml = """
      <settings xmlns="http://maven.apache.org/SETTINGS/1.0.0">
          <servers>
              <server>
                <id>id1</id>
                <username>user1</username>
                <password>pass1</password>
              </server>
          </servers>
      </settings>
      """;

    var userXml = """
      <settings xmlns="http://maven.apache.org/SETTINGS/1.0.0">
          <servers>
              <server>
                <id>id1</id>
                <username>user2</username>
                <password>pass2</password>
              </server>
          </servers>
      </settings>
      """;

    var globalSettings = Files.createFile(tempDir.resolve("global-settings"));
    var userSettings = Files.createFile(tempDir.resolve("user-settings"));

    Files.writeString(globalSettings, globalXml, StandardCharsets.UTF_8);
    Files.writeString(userSettings, userXml, StandardCharsets.UTF_8);

    var result = JpsMavenSettings.loadAuthenticationSettings(globalSettings, userSettings);
    assertNotNull(result.get("id1"));
    assertEquals("user2", result.get("id1").username);
    assertEquals("pass2", result.get("id1").password);
  }

  @Test
  public void testLoadAuthenticationSettingsIncompleteCredentials() throws Exception {
    var xmlWithIncompleteCredentials = """
      <settings xmlns="http://maven.apache.org/SETTINGS/1.0.0">
          <servers>
              <server>
                <id>id1</id>
                <username>user1</username>
              </server>
              <server>
                <id>id2</id>
                <password>pass1</password>
              </server>
          </servers>
      </settings>
      """;

    var settingsFile = Files.createFile(tempDir.resolve("settings"));
    Files.writeString(settingsFile, xmlWithIncompleteCredentials, StandardCharsets.UTF_8);

    var result = JpsMavenSettings.loadAuthenticationSettings(settingsFile, settingsFile);

    assertNull(result.get("id1"));
    assertNull(result.get("id2"));
  }

  @Test
  public void testLoadSettingsAllKnownNamespaces() throws Exception {
    var knownNamespaces = List.of(
      "", // no namespace is OK
      "http://maven.apache.org/SETTINGS/1.0.0",
      "http://maven.apache.org/SETTINGS/1.1.0",
      "http://maven.apache.org/SETTINGS/1.2.0"
    );
    for (var namespace : knownNamespaces) {
      var xmlns = namespace.isEmpty() ? "" : " xmlns=\"" + namespace + "\"";
      var xml = """
        <settings%s>
            <localRepository>some/path</localRepository>
            <servers>
                <server>
                  <id>id1</id>
                  <username>user1</username>
                  <password>pass1</password>
                </server>
            </servers>
        </settings>
        """.formatted(xmlns);

      var settingsFile = Files.createFile(tempDir.resolve("settings-" + Integer.toHexString(namespace.hashCode()) + ".xml"));
      Files.writeString(settingsFile, xml, StandardCharsets.UTF_8);

      var settings = JpsMavenSettings.loadAuthenticationSettings(settingsFile, settingsFile);
      assertNotNull(settings.get("id1"));
      assertEquals("user1", settings.get("id1").username);
      assertEquals("pass1", settings.get("id1").password);

      var localRepo = JpsMavenSettings.getRepositoryFromSettings(settingsFile);
      assertEquals("some/path", localRepo);
    }
  }
}
