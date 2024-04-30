// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.dependencies

import org.jetbrains.intellij.build.BuildDependenciesJps
import org.jetbrains.intellij.build.IdeaProjectLoaderUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.jupiter.api.assertThrows
import java.nio.file.Path
import kotlin.io.path.*

@OptIn(ExperimentalPathApi::class)
class BuildDependenciesJpsTest {
  @Test
  fun getModuleLibrarySingleRoot() {
    val iml = getTestDataRoot().resolve("jps_library_test_iml.xml")
    val root = BuildDependenciesJps.getModuleLibrarySingleRoot(
      iml,
      "debugger-agent",
      BuildDependenciesConstants.INTELLIJ_DEPENDENCIES_URL,
      communityRoot,
      null, null
    )
    assertTrue(root.pathString, root.pathString.endsWith("debugger-agent-1.9.jar"))
  }

  @Test
  fun getModuleLibrarySingleRoot_snapshot_version() {
    val snapshotDir = BuildDependenciesJps.getLocalArtifactRepositoryRoot().resolve("org/jetbrains/intellij/deps/debugger-agent/1.0-SNAPSHOT")
    snapshotDir.deleteRecursively()

    val localFile = snapshotDir.resolve("debugger-agent-1.0-SNAPSHOT.jar")
    try {
      localFile.parent.createDirectories()
      localFile.writeText("some data")
      val iml = getTestDataRoot().resolve("jps_library_test_iml_snapshot_version.xml")
      val resolved = BuildDependenciesJps.getModuleLibrarySingleRoot(
        iml,
        "debugger-agent",
        BuildDependenciesConstants.INTELLIJ_DEPENDENCIES_URL,
        communityRoot,
        null, null
      )
      assertEquals("must resolve to a local file from .m2/repository", localFile.pathString, resolved.pathString)
    }
    finally {
      snapshotDir.deleteRecursively()
    }
  }

  @Test
  fun getModuleLibrarySingleRoot_wrong_checksum() {
    val iml = getTestDataRoot().resolve("jps_library_test_iml_wrong_checksum.xml")
    val ex = assertThrows<IllegalStateException> {
      BuildDependenciesJps.getModuleLibrarySingleRoot(
        iml,
        "debugger-agent",
        BuildDependenciesConstants.INTELLIJ_DEPENDENCIES_URL,
        communityRoot,
        null, null
      )
    }
    assertTrue(ex.cause!!.message,
               ex.cause!!.message!!.contains(
                 "On disk: 90b9b0f5bfd078c9942b4f85388fb4b1f765905064f22317eec5a488ae10224e. " +
                 "Expected: 29a31d2122fb753a7b5e94dcb90fffffffffffffffffffffffffffffffffffff"))
  }

  @Test
  fun getModuleLibrarySingleRoot_missing_checksum() {
    val iml = getTestDataRoot().resolve("jps_library_test_iml_missing_checksum.xml")
    val ex = assertThrows<IllegalStateException> {
      BuildDependenciesJps.getModuleLibrarySingleRoot(
        iml,
        "debugger-agent",
        BuildDependenciesConstants.INTELLIJ_DEPENDENCIES_URL,
        communityRoot,
        null, null
      )
    }
    assertTrue(ex.cause!!.message, ex.cause!!.message!!.contains("SHA256 checksum is missing"))
  }

  @Test
  fun getModuleLibrarySingleRoot_use_local_file() {
    val localFile = BuildDependenciesJps.getLocalArtifactRepositoryRoot()
      .resolve("org/jetbrains/intellij/deps/debugger-agent/1.0/debugger-agent-1.0.jar")
    localFile.deleteIfExists()
    try {
      localFile.parent.createDirectories()
      localFile.writeText("some data")
      val iml = getTestDataRoot().resolve("jps_library_test_iml_local_file.xml")
      val resolved = BuildDependenciesJps.getModuleLibrarySingleRoot(
        iml,
        "debugger-agent",
        BuildDependenciesConstants.INTELLIJ_DEPENDENCIES_URL,
        communityRoot,
        null, null
      )
      assertEquals("must resolve to a local file from .m2/repository", localFile.pathString, resolved.pathString)
    }
    finally {
      localFile.deleteIfExists()
    }
  }

  private val communityRoot by lazy {
    IdeaProjectLoaderUtil.guessCommunityHome(javaClass)
  }

  private fun getTestDataRoot(): Path {
    val testData = communityRoot.communityRoot.resolve("platform/build-scripts/tests/testData")
    check(testData.isDirectory()) {
      "not a directory: $testData"
    }
    return testData
  }
}
