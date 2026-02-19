// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.bazelEnvironment

import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import java.nio.file.Files
import kotlin.io.path.absolutePathString
import kotlin.io.path.writeText

class BazelManifestTest {
  @Test
  fun smoke() {
    val manifestFile = Files.createTempFile("manifest", "bazel")
    manifestFile.writeText("""
      _main/dbe/dialects/_core/database-dialects-core.jar /execroot/_main/bazel-out/jvm-fastbuild/bin/dbe/dialects/_core/database-dialects-core.jar
      _main/dbe/dialects/_engine/0/engine.jar /execroot/_main/bazel-out/jvm-fastbuild/bin/dbe/dialects/_engine/0/engine.jar
      _main/dbe/dialects/_engine/1/engine.jar /execroot/_main/bazel-out/jvm-fastbuild/bin/dbe/dialects/_engine/1/engine.jar
      _main/dbe/dialects/base/base.jar /execroot/_main/bazel-out/jvm-fastbuild/bin/dbe/dialects/base/base.jar
      _repo_mapping /execroot/_main/bazel-out/darwin_arm64-fastbuild/bin/language-server/community/api.features/test/test_test.repo_mapping
      community+/build/build-javac-rt.jar /execroot/_main/bazel-out/jvm-fastbuild/bin/external/community+/build/build-javac-rt.jar
      community+/build/dependency-graph.jar /execroot/_main/bazel-out/jvm-fastbuild/bin/external/community+/build/dependency-graph.jar
      community+/build/deps/dependencies.jar /execroot/_main/bazel-out/jvm-fastbuild/bin/external/community+/build/deps/dependencies.jar
    """.trimIndent())
    val manifest = BazelRunfilesManifest(manifestFile.absolutePathString())

    assertEquals("/execroot/_main/bazel-out/jvm-fastbuild/bin/dbe/dialects",
                 manifest.get("_main/dbe/dialects"))
    assertEquals("/execroot/_main/bazel-out/jvm-fastbuild/bin/dbe/dialects/_engine",
                 manifest.get("_main/dbe/dialects/_engine"))
    assertEquals("/execroot/_main/bazel-out/jvm-fastbuild/bin/dbe/dialects/_engine/1/engine.jar",
                 manifest.get("_main/dbe/dialects/_engine/1/engine.jar"))
    assertEquals("/execroot/_main/bazel-out/jvm-fastbuild/bin/dbe",
                 manifest.get("_main/dbe"))
    assertEquals("/execroot/_main/bazel-out/darwin_arm64-fastbuild/bin/language-server/community/api.features/test/test_test.repo_mapping",
                 manifest.get("_repo_mapping"))
    assertEquals("/execroot/_main/bazel-out/jvm-fastbuild/bin/external/community+/build/build-javac-rt.jar",
                 manifest.get("community+/build/build-javac-rt.jar"))
    assertEquals("/execroot/_main/bazel-out/jvm-fastbuild/bin/external/community+/build",
                 manifest.get("community+/build"))
    assertEquals("/execroot/_main/bazel-out/jvm-fastbuild/bin/external/community+",
                 manifest.get("community+"))
  }
}
