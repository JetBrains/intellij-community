// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.maven

import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.utils.io.createFile
import kotlinx.coroutines.runBlocking
import org.apache.maven.model.Developer
import org.apache.maven.model.License
import org.apache.maven.model.Model
import org.apache.maven.model.Scm
import org.apache.maven.model.io.xpp3.MavenXpp3Writer
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuildPaths.Companion.COMMUNITY_ROOT
import org.jetbrains.intellij.build.IdeaCommunityProperties
import org.jetbrains.intellij.build.impl.BuildContextImpl
import org.jetbrains.intellij.build.impl.maven.MavenCentralPublication.DeploymentState
import org.jetbrains.intellij.build.io.suspendAwareReadZipFile
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText

class MavenCentralPublicationTest {
  companion object {
    val context: BuildContext by lazy {
      runBlocking {
        BuildContextImpl.createContext(
          COMMUNITY_ROOT.communityRoot,
          IdeaCommunityProperties(COMMUNITY_ROOT.communityRoot),
          setupTracer = false,
        )
      }
    }
  }

  @TempDir
  lateinit var workDir: Path
  val publication: MavenCentralPublication by lazy { MavenCentralPublication(context, workDir, dryRun = true) }

  class Result(val workDirPath: Path, val zipPath: String)

  fun createDistributionFiles(flatLayout: Boolean = false): List<Result> {
    return sequenceOf(
      MavenCoordinates("org.jetbrains", "bar", "1.0"),
      MavenCoordinates("org.jetbrains", "foo", "2.0"),
    ).flatMap { coordinates ->
      sequenceOf(
        "pom" to "",
        "jar" to "",
        "jar" to "sources",
        "jar" to "javadoc",
      ).map { (packaging, classifier) ->
        val name = coordinates.getFileName(classifier = classifier, packaging = packaging)
        val zipPath = "${coordinates.directoryPath}/$name"
        val file = workDir.resolve(if (flatLayout) name else zipPath).createFile()
        if (packaging == "pom") {
          writePom(coordinates, file)
        }
        Result(file, zipPath = zipPath)
      }
    }.toList()
  }

  private fun writePom(coordinates: MavenCoordinates, file: Path) {
    val pom = Model()
    pom.groupId = coordinates.groupId
    pom.artifactId = coordinates.artifactId
    pom.version = coordinates.version
    pom.name = coordinates.artifactId
    pom.description = coordinates.artifactId
    pom.url = "https://github.com/JetBrains/intellij-community"
    pom.addDeveloper(Developer())
    pom.scm = Scm()
    pom.addLicense(License())
    Files.newBufferedWriter(file).use {
      MavenXpp3Writer().write(it, pom)
    }
  }

  @Test
  fun `should fail upon an empty input directory`() {
    runBlocking {
      assertThrows<IllegalArgumentException> {
        publication.execute()
      }
    }
  }

  private fun `should generate a bundle zip for artifacts`(flatLayout: Boolean) {
    runBlocking {
      val files = createDistributionFiles(flatLayout = flatLayout).map { it.zipPath }
      publication.execute()
      val bundle = workDir.resolve("bundle.zip")
      assertThat(bundle).exists()
      val entries = buildList {
        suspendAwareReadZipFile(bundle) { entry, _ ->
          add(entry)
        }
      }.sorted()
      Assertions.assertEquals(
        files.asSequence()
          .plus(files.asSequence().map { "$it.sha1" })
          .plus(files.asSequence().map { "$it.sha256" })
          .plus(files.asSequence().map { "$it.sha512" })
          .plus(files.asSequence().map { "$it.md5" })
          .sorted().toList(),
        entries,
      )
    }
  }


  @Test
  fun `should generate a bundle zip for artifacts`() {
    `should generate a bundle zip for artifacts`(flatLayout = false)
  }

  @Test
  fun `should generate a bundle zip for flat artifacts layout`() {
    `should generate a bundle zip for artifacts`(flatLayout = true)
  }

  @Test
  fun `should fail upon an invalid input checksum`() {
    runBlocking {
      val files = createDistributionFiles().map { it.workDirPath }
      val malformedChecksum = files.first()
        .resolveSibling("${files.first().fileName}.sha1")
        .createFile()
      malformedChecksum.writeText("not a checksum")
      assertThrows<MavenCentralPublication.ChecksumMismatch> {
        publication.execute()
      }
    }
  }

  @Test
  fun `deployment state parsing test`() {
    Assertions.assertEquals(
      DeploymentState.PUBLISHED, publication.parseDeploymentState(
      """
            {
              "deploymentId": "28570f16-da32-4c14-bd2e-c1acc0782365",
              "deploymentName": "central-bundle.zip",
              "deploymentState": "PUBLISHED",
              "purls": [
                "pkg:maven/com.sonatype.central.example/example_java_project@0.0.7"
              ]
            }
          """.trimIndent()
    )
    )
  }
}