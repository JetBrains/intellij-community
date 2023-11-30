package org.jetbrains.jps.model.serialization;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class JpsMavenSettingsTest {
  @Rule
  public TemporaryFolder tempFolder = new TemporaryFolder();

  @Test
  public void testLoadAuthenticationSettingsNoFilesExist() throws Exception {
    File nonExistingFile = new File(tempFolder.newFolder(), "non-existing-file");
    var result = JpsMavenSettings.loadAuthenticationSettings(nonExistingFile, nonExistingFile);
    assertTrue(result.isEmpty());
  }

  @Test
  public void testLoadAuthenticationSettingsEmptyFile() throws Exception {
    File emptyFile = tempFolder.newFile();
    var result = JpsMavenSettings.loadAuthenticationSettings(emptyFile, emptyFile);
    assertTrue(result.isEmpty());
  }

  @Test
  public void testLoadAuthenticationSettingsUnknownNamespace() throws Exception {
    File wrongNsFile = tempFolder.newFile();
    String wrongNsXml = """
      <settings xmlns="http://maven.apache.org/UNKNOWN">
          <servers>
              <server>
                <id>id1</id>
                <username>user1</username>
                <password>pass1</password>
              </server>
          </servers>
      </settings>
      """;
    Files.writeString(wrongNsFile.toPath(), wrongNsXml, StandardCharsets.UTF_8);

    var result = JpsMavenSettings.loadAuthenticationSettings(wrongNsFile, wrongNsFile);
    assertTrue(result.isEmpty());
  }

  @Test
  public void testLoadAuthenticationSettingsUserSettingsHasHigherPriority() throws Exception {
    String globalXml = """
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

    String userXml = """
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

    File globalSettings = tempFolder.newFile();
    File userSettings = tempFolder.newFile();

    Files.writeString(globalSettings.toPath(), globalXml, StandardCharsets.UTF_8);
    Files.writeString(userSettings.toPath(), userXml, StandardCharsets.UTF_8);

    var result = JpsMavenSettings.loadAuthenticationSettings(globalSettings, userSettings);
    assertNotNull(result.get("id1"));
    assertEquals("user2", result.get("id1").getUsername());
    assertEquals("pass2", result.get("id1").getPassword());
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

    File settingsFile = tempFolder.newFile();
    Files.writeString(settingsFile.toPath(), xmlWithIncompleteCredentials, StandardCharsets.UTF_8);

    Map<String, JpsMavenSettings.RemoteRepositoryAuthentication> result =
      JpsMavenSettings.loadAuthenticationSettings(settingsFile, settingsFile);

    assertNull(result.get("id1"));
    assertNull(result.get("id2"));
  }

  @Test
  public void testLoadAuthenticationSettings_SupportsAllKnownNamespaces() throws Exception {
    List<String> knownNamespaces = List.of("http://maven.apache.org/SETTINGS/1.0.0",
                                           "http://maven.apache.org/SETTINGS/1.1.0",
                                           "http://maven.apache.org/SETTINGS/1.2.0");
    for (String namespace : knownNamespaces) {
      String xml = """
        <settings xmlns="%s">
            <servers>
                <server>
                  <id>id1</id>
                  <username>user1</username>
                  <password>pass1</password>
                </server>
            </servers>
        </settings>
        """.formatted(namespace);

      File settingsFile = tempFolder.newFile();
      Files.writeString(settingsFile.toPath(), xml, StandardCharsets.UTF_8);

      var result = JpsMavenSettings.loadAuthenticationSettings(settingsFile, settingsFile);
      assertNotNull(result.get("id1"));
      assertEquals("user1", result.get("id1").getUsername());
      assertEquals("pass1", result.get("id1").getPassword());
    }
  }
}