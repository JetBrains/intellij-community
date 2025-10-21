// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("IO_FILE_USAGE")

package org.jetbrains.intellij.build

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito
import java.io.File
import java.nio.file.Path
import kotlin.io.path.Path

class MavenRepositoryPathUtilTest {

  companion object {
    private val mockUtil = Mockito.mockStatic(SystemPropertiesHelper::class.java, Mockito.CALLS_REAL_METHODS)
  }

  private val defaultRepositoryPath
    get() = Path(SystemPropertiesHelper.getUserHome(), ".m2/repository").toString()

  @AfterEach
  fun afterEach() {
    mockUtil.reset()
  }

  @Test
  fun `test getMavenRepositoryPath returns default path when no settings exist`() {
    mockUtil.`when`<String> { SystemPropertiesHelper.getMavenOptsEnvVariable() }
      .thenReturn(null)

    val path = getMavenRepositoryPath()
    Assertions.assertEquals(defaultRepositoryPath, path)
  }

  @Test
  fun `test getMavenRepositoryPath with MAVEN_OPTS env`(@TempDir tempDir: Path) {
    val testPathMvn = "/custom/maven/re.po"
    val testPathXml = "/fail/test/path"
    val mavenOpts = "-Dmaven.repo.local=$testPathMvn"

    mockUtil.`when`<String> { SystemPropertiesHelper.getMavenOptsEnvVariable() }
      .thenReturn(mavenOpts)

    val settingsContent = """
        <settings xmlns="http://maven.apache.org/SETTINGS/1.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 https://maven.apache.org/xsd/settings-1.0.0.xsd">
            <localRepository>${testPathXml}</localRepository>
        </settings>
    """.trimIndent()
    val settingsFile = tempDir.resolve(".m2/settings.xml").toFile()
    settingsFile.parentFile.mkdirs()
    settingsFile.writeText(settingsContent)

    mockUtil.`when`<String> { SystemPropertiesHelper.getUserHome() }
      .thenReturn(settingsFile.parentFile.parentFile.path)

    val path = getMavenRepositoryPath()
    Assertions.assertEquals(testPathMvn, path)
  }

  @Test
  fun `test getMavenRepositoryPath with no maven_repo_local in MAVEN_OPTS env`() {
    mockUtil.`when`<String> { SystemPropertiesHelper.getMavenOptsEnvVariable() }
      .thenReturn("-Xmx2g -Dother.property=value")

    val result = getMavenRepositoryPath()
    Assertions.assertEquals(defaultRepositoryPath, result)
  }

  @Test
  fun `test getMavenRepositoryPath with incorrect maven_repo_local in MAVEN_OPTS env`() {
    val mavenOpts = "-Dmaven.repo.local= /test/path -Dproperty=value"
    mockUtil.`when`<String> { SystemPropertiesHelper.getMavenOptsEnvVariable() }
      .thenReturn(mavenOpts)

    val result = getMavenRepositoryPath()
    Assertions.assertEquals(defaultRepositoryPath, result)
  }

  @Test
  fun `test getMavenRepositoryPath from settings xml`(@TempDir tempDir: Path) {
    val settingsFile = tempDir.resolve(".m2/settings.xml").toFile()
    val customRepoPath = tempDir.resolve("custom-repo").toString()

    getMavenRepositoryPathFromSettings(settingsFile, customRepoPath)
  }

  @Disabled
  @Test
  fun `test getMavenRepositoryPath from settings xml with property`(@TempDir tempDir: Path) {
    val settingsFile = tempDir.resolve(".m2/settings.xml").toFile()
    val customRepoSubpath = "/m2/custom-repo"
    val customRepoPath = "\${user.home}$customRepoSubpath"
    val expectedPath = Path(System.getProperty("user.home"), customRepoSubpath)

    getMavenRepositoryPathFromSettings(settingsFile, customRepoPath, expectedPath.toString())
  }

  @Test
  fun `test getMavenRepositoryPath from settings xml unix path`(@TempDir tempDir: Path) {
    val settingsFile = tempDir.resolve(".m2/settings.xml").toFile()
    val customRepoPath = Path("/usr/local/maven/repository").toString()

    getMavenRepositoryPathFromSettings(settingsFile, customRepoPath)
  }

  @Test
  fun `test getMavenRepositoryPath from settings xml windows path`(@TempDir tempDir: Path) {
    val settingsFile = tempDir.resolve(".m2/settings.xml").toFile()
    val customRepoPath = Path("C:\\Users\\user name\\maven\\repository").toString()

    getMavenRepositoryPathFromSettings(settingsFile, customRepoPath)
  }

  @Test
  fun `test getMavenRepositoryPath from settings xml empty file`(@TempDir tempDir: Path) {
    mockUtil.`when`<String> { SystemPropertiesHelper.getMavenOptsEnvVariable() }
      .thenReturn(null)

    val settingsFile = tempDir.resolve(".m2/settings.xml").toFile()

    settingsFile.parentFile.mkdirs()
    settingsFile.writeText("")

    mockUtil.`when`<String> { SystemPropertiesHelper.getUserHome() }
      .thenReturn(settingsFile.parentFile.parentFile.path)

    val path = getMavenRepositoryPath()
    Assertions.assertEquals(defaultRepositoryPath, path)
  }

  @Test
  fun `test findMavenRepositoryProperty extracts repository path unix`() {
    val testPath = "/test/repo/path"
    val mavenOpts = "-Xmx2g -Dtest=1 -Dmaven.repo.local=$testPath -Dproperty=value -Dprop"
    mockUtil.`when`<String> { SystemPropertiesHelper.getMavenOptsEnvVariable() }
      .thenReturn(mavenOpts)

    val result = getMavenRepositoryPath()
    Assertions.assertEquals(testPath, result)
  }

  @Test
  fun `test findMavenRepositoryProperty extracts repository path windows`() {
    val testPath = "C:\\user test\\repo\\path"
    val mavenOpts = "-Xmx2g -Dprop -Dtest=1 -Dmaven.repo.local=\"$testPath\" -Dproperty=value"
    mockUtil.`when`<String> { SystemPropertiesHelper.getMavenOptsEnvVariable() }
      .thenReturn(mavenOpts)

    val result = getMavenRepositoryPath()
    Assertions.assertEquals(testPath, result)
  }

  private fun getMavenRepositoryPathFromSettings(settingsFile: File, repositoryPath: String, expectedPath: String = repositoryPath) {
    mockUtil.`when`<String> { SystemPropertiesHelper.getMavenOptsEnvVariable() }
      .thenReturn(null)

    val settingsContent = """
        <settings xmlns="http://maven.apache.org/SETTINGS/1.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 https://maven.apache.org/xsd/settings-1.0.0.xsd">
            <localRepository>${repositoryPath}</localRepository>
        </settings>
    """.trimIndent()

    val settingsFile = settingsFile
    settingsFile.parentFile.mkdirs()
    settingsFile.writeText(settingsContent)

    mockUtil.`when`<String> { SystemPropertiesHelper.getUserHome() }
      .thenReturn(settingsFile.parentFile.parentFile.path)

    val path = getMavenRepositoryPath()
    Assertions.assertEquals(expectedPath, path)
  }
}