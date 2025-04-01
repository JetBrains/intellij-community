// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.maven

import com.intellij.testFramework.utils.io.createFile
import kotlinx.coroutines.runBlocking
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
        )
      }
    }
  }

  @TempDir
  lateinit var workDir: Path
  val publication: MavenCentralPublication by lazy { MavenCentralPublication(context, workDir, dryRun = true) }
  val coordinates = MavenCoordinates("foo", "bar", "1.0")
  fun createDistributionFiles(): List<Path> {
    return sequenceOf(
      "pom" to "",
      "jar" to "",
      "jar" to "sources",
      "jar" to "javadoc",
    ).map { (packaging, classifier) ->
      workDir.resolve(coordinates.getFileName(classifier = classifier, packaging = packaging)).createFile().apply {
        if (packaging == "pom") {
          writeText(
            """
            <project>
              <groupId>${coordinates.groupId}</groupId>
              <artifactId>${coordinates.artifactId}</artifactId>
              <version>${coordinates.version}</version>
            </project>
          """.trimIndent()
          )
        }
      }
    }.toList()
  }

  @Test
  fun `should fail upon an empty input directory`() {
    runBlocking {
      assertThrows<IllegalArgumentException> {
        publication.execute()
      }
    }
  }

  @Test
  fun `should generate a bundle zip`() {
    runBlocking {
      val files = createDistributionFiles().map { "${coordinates.directoryPath}/${it.name}" }
      publication.execute()
      val bundle = workDir.resolve("bundle.zip")
      assert(Files.exists(bundle)) {}
      val entries = buildList {
        suspendAwareReadZipFile(bundle) { entry, _ ->
          add(entry)
        }
      }
      assert(entries.containsAll(files))
      assert(entries.containsAll(files.map { "$it.sha1" }))
      assert(entries.containsAll(files.map { "$it.sha256" }))
      assert(entries.containsAll(files.map { "$it.sha512" }))
      assert(entries.containsAll(files.map { "$it.md5" }))
    }
  }

  @Test
  fun `should fail upon an invalid input checksum`() {
    runBlocking {
      val files = createDistributionFiles()
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