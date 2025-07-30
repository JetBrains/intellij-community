// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.maven

import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.utils.io.createFile
import com.intellij.util.io.DigestUtil.md5
import kotlinx.coroutines.runBlocking
import org.apache.maven.model.Developer
import org.apache.maven.model.License
import org.apache.maven.model.Model
import org.apache.maven.model.Scm
import org.apache.maven.model.io.xpp3.MavenXpp3Writer
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuildPaths.Companion.COMMUNITY_ROOT
import org.jetbrains.intellij.build.IdeaCommunityProperties
import org.jetbrains.intellij.build.ProprietaryBuildTools
import org.jetbrains.intellij.build.SignTool
import org.jetbrains.intellij.build.impl.BuildContextImpl
import org.jetbrains.intellij.build.impl.Checksums
import org.jetbrains.intellij.build.impl.maven.MavenCentralPublication.DeploymentState
import org.jetbrains.intellij.build.io.suspendAwareReadZipFile
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.io.path.writeText

class MavenCentralPublicationTest {
  companion object {
    val context: BuildContext by lazy {
      runBlocking {
        BuildContextImpl.createContext(
          COMMUNITY_ROOT.communityRoot,
          IdeaCommunityProperties(COMMUNITY_ROOT.communityRoot),
          setupTracer = false,
          ProprietaryBuildTools.DUMMY.copy(signTool = object : SignTool by (ProprietaryBuildTools.DUMMY.signTool) {
            override suspend fun signFilesWithGpg(files: List<Path>, context: BuildContext) {
              for (file in files) {
                assert(file.extension != "asc")
                file.resolveSibling("${file.name}.asc").createFile()
              }
            }
          }),
        )
      }
    }
  }

  @TempDir
  lateinit var workDir: Path
  val publication: MavenCentralPublication by lazy { MavenCentralPublication(context, workDir, dryRun = true) }

  class Result(val workDirPath: Path, zipPath: String) {
    val expectedZipEntries: Sequence<String> =
      sequenceOf(zipPath)
        .plus("$zipPath.asc")
        .plus("$zipPath.sha1")
        .plus("$zipPath.sha256")
        .plus("$zipPath.sha512")
        .plus("$zipPath.md5")
  }

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
      val files = createDistributionFiles(flatLayout = flatLayout)
      publication.execute()
      Assertions.assertEquals(
        files.asSequence().flatMap { it.expectedZipEntries }.sorted().toList(),
        `bundle zip sorted entries`(),
      )
    }
  }

  private suspend fun `bundle zip sorted entries`(): List<String> {
    val bundle = workDir.resolve("bundle.zip")
    assertThat(bundle).exists()
    return buildList {
      suspendAwareReadZipFile(bundle) { entry, _ ->
        add(entry)
      }
    }.sorted()
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

  @Test
  fun `supplied signatures are not supported`() {
    runBlocking {
      createDistributionFiles().forEach {
        it.workDirPath.resolveSibling("${it.workDirPath.name}.asc").createFile()
      }
      assertThrows<MavenCentralPublication.SuppliedSignatures> {
        publication.execute()
      }
    }
  }

  @Test
  fun `should use supplied checksums`() {
    runBlocking {
      val files = createDistributionFiles().asSequence().onEach {
        it.workDirPath.resolveSibling("${it.workDirPath.name}.md5")
          .createFile()
          .writeText(Checksums(it.workDirPath, md5()).md5sum)
      }.toList()
      publication.execute()
      Assertions.assertEquals(
        files.asSequence().flatMap { it.expectedZipEntries }.sorted().toList(),
        `bundle zip sorted entries`(),
      )
    }
  }
}