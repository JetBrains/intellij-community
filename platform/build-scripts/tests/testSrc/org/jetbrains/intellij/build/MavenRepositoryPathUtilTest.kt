// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import com.intellij.util.EnvironmentUtil
import com.intellij.util.SystemProperties
import org.jetbrains.jps.model.serialization.JpsMavenSettings.getMavenRepositoryPath
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.MockedStatic
import org.mockito.Mockito
import java.io.File
import java.nio.file.Path
import kotlin.io.path.Path

class MavenRepositoryPathUtilTest {

  companion object {
    private val mockEnv: MockedStatic<EnvironmentUtil> = Mockito.mockStatic(EnvironmentUtil::class.java)
  }

  private val defaultRepositoryPath
    get() = Path(SystemProperties.getUserHome(), ".m2/repository").toString()

  @AfterEach
  fun afterEach() {
    mockEnv.reset()
  }

  @Test
  fun `test getMavenRepositoryPath returns default path when no settings exist`() {
    val mockFile = Mockito.mock(File::class.java)
    mockEnv.`when`<String> { EnvironmentUtil.getValue("MAVEN_OPTS") }
      .thenReturn(null)

    Mockito.`when`(mockFile.exists())
      .thenReturn(false)

    val path = getMavenRepositoryPath()
    Assertions.assertEquals(defaultRepositoryPath, path)
  }

  @Test
  fun `test getMavenRepositoryPath with MAVEN_OPTS env`(@TempDir tempDir: Path) {
    val testPathMvn = "/custom/maven/re.po"
    val testPathXml = "/fail/test/path"
    val mavenOpts = "-Dmaven.repo.local=$testPathMvn"

    mockEnv.`when`<String> { EnvironmentUtil.getValue("MAVEN_OPTS") }
      .thenReturn(mavenOpts)

    val settingsContent = """
        <settings xmlns="http://maven.apache.org/SETTINGS/1.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 https://maven.apache.org/xsd/settings-1.0.0.xsd">
            <localRepository>${testPathXml}</localRepository>
        </settings>
    """.trimIndent()
    val settingsFile = tempDir.resolve(".m2/settings.xml").toFile()
    settingsFile.parentFile.mkdirs()
    settingsFile.writeText(settingsContent)

    Mockito.mockStatic(SystemProperties::class.java).use { mockSettings ->
      mockSettings.`when`<String> { SystemProperties.getUserHome() }
        .thenReturn(settingsFile.parentFile.path)

      val path = getMavenRepositoryPath()
      Assertions.assertEquals(testPathMvn, path)
    }
  }

  @Test
  fun `test getMavenRepositoryPath with no maven_repo_local in MAVEN_OPTS env`() {
    val mockFile = Mockito.mock(File::class.java)
    mockEnv.`when`<String> { EnvironmentUtil.getValue("MAVEN_OPTS") }
      .thenReturn("-Xmx2g -Dother.property=value")

    Mockito.`when`(mockFile.exists())
      .thenReturn(false)

    val result = getMavenRepositoryPath()
    Assertions.assertEquals(defaultRepositoryPath, result)
  }

  @Test
  fun `test getMavenRepositoryPath with incorrect maven_repo_local in MAVEN_OPTS env`() {
    val mockFile = Mockito.mock(File::class.java)
    val mavenOpts = "-Dmaven.repo.local= /test/path -Dproperty=value"
    mockEnv.`when`<String> { EnvironmentUtil.getValue("MAVEN_OPTS") }
      .thenReturn(mavenOpts)

    Mockito.`when`(mockFile.exists())
      .thenReturn(false)

    val result = getMavenRepositoryPath()
    Assertions.assertEquals(defaultRepositoryPath, result)
  }

  @Test
  fun `test getMavenRepositoryPath from settings xml`(@TempDir tempDir: Path) {
    val settingsFile = tempDir.resolve(".m2/settings.xml").toFile()
    val customRepoPath = tempDir.resolve("custom-repo")

    getMavenRepositoryPathFromSettings(settingsFile, customRepoPath)
  }

  @Test
  fun `test getMavenRepositoryPath from settings xml unix path`(@TempDir tempDir: Path) {
    val settingsFile = tempDir.resolve(".m2/settings.xml").toFile()
    val customRepoPath = Path("/usr/local/maven/repository")

    getMavenRepositoryPathFromSettings(settingsFile, customRepoPath)
  }

  @Test
  fun `test getMavenRepositoryPath from settings xml windows path`(@TempDir tempDir: Path) {
    val settingsFile = tempDir.resolve(".m2/settings.xml").toFile()
    val customRepoPath = Path("C:\\Users\\user name\\maven\\repository")

    getMavenRepositoryPathFromSettings(settingsFile, customRepoPath)
  }

  @Test
  fun `test getMavenRepositoryPath from settings xml empty file`(@TempDir tempDir: Path) {
    mockEnv.`when`<String> { EnvironmentUtil.getValue("MAVEN_OPTS") }
      .thenReturn(null)

    val settingsFile = tempDir.resolve(".m2/settings.xml").toFile()

    settingsFile.parentFile.mkdirs()
    settingsFile.writeText("")

    Mockito.mockStatic(SystemProperties::class.java).use { mockSettings ->
      mockSettings.`when`<String> { SystemProperties.getUserHome() }
        .thenReturn(settingsFile.parentFile.path)

      val path = getMavenRepositoryPath()
      Assertions.assertEquals(defaultRepositoryPath, path)
    }
  }

  @Test
  fun `test findMavenRepositoryProperty extracts repository path unix`() {
    val testPath = "/test/repo/path"
    val mavenOpts = "-Xmx2g -Dtest=1 -Dmaven.repo.local=$testPath -Dproperty=value -Dprop"
    mockEnv.`when`<String> { EnvironmentUtil.getValue("MAVEN_OPTS") }
      .thenReturn(mavenOpts)

    val result = getMavenRepositoryPath()
    Assertions.assertEquals(testPath, result)
  }

  @Test
  fun `test findMavenRepositoryProperty extracts repository path windows`() {
    val testPath = "C:\\user test\\repo\\path"
    val mavenOpts = "-Xmx2g -Dprop -Dtest=1 -Dmaven.repo.local=\"$testPath\" -Dproperty=value"
    mockEnv.`when`<String> { EnvironmentUtil.getValue("MAVEN_OPTS") }
      .thenReturn(mavenOpts)

    val result = getMavenRepositoryPath()
    Assertions.assertEquals(testPath, result)
  }

  private fun getMavenRepositoryPathFromSettings(settingsFile: File, repositoryPath: Path) {
    mockEnv.`when`<String> { EnvironmentUtil.getValue("MAVEN_OPTS") }
      .thenReturn(null)

    val settingsContent = """
        <settings xmlns="http://maven.apache.org/SETTINGS/1.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 https://maven.apache.org/xsd/settings-1.0.0.xsd">
            <localRepository>${repositoryPath}</localRepository>
        </settings>
    """.trimIndent()

    val settingsFile = settingsFile
    settingsFile.parentFile.mkdirs()
    settingsFile.writeText(settingsContent)

    Mockito.mockStatic(SystemProperties::class.java).use { mockSettings ->
      mockSettings.`when`<String> { SystemProperties.getUserHome() }
        .thenReturn(settingsFile.parentFile.parentFile.path)

      val path = getMavenRepositoryPath()
      Assertions.assertEquals(repositoryPath.toString(), path)
    }
  }
}