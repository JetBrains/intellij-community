// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package org.jetbrains.intellij.build.impl

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Path

internal class ModuleOutputProbeRootTest {
  @Test
  fun `the sibling of an ultimate target`() {
    assertThat(resourcesSiblingLabel("//platform/ide-impl:ide-impl.jar")).isEqualTo("//platform/ide-impl:ide-impl_resource_jar.jar")
  }

  @Test
  fun `the sibling of a community target keeps the repository`() {
    assertThat(resourcesSiblingLabel("@community//plugins/env-files-support:dotenv.jar"))
      .isEqualTo("@community//plugins/env-files-support:dotenv_resource_jar.jar")
  }

  @Test
  fun `the sibling of a test target`() {
    assertThat(resourcesSiblingLabel("@community//platform/core-api:core-api_test_lib.jar"))
      .isEqualTo("@community//platform/core-api:core-api_test_lib_resource_jar.jar")
  }

  @Test
  fun `a name with a slash keeps its directory`() {
    assertThat(resourcesSiblingLabel("//a/b:c/d.jar")).isEqualTo("//a/b:c/d_resource_jar.jar")
  }

  @Test
  fun `a label that is not a jar has no sibling`() {
    assertThat(resourcesSiblingLabel("//platform/ide-impl:ide-impl")).isNull()
    assertThat(resourcesSiblingLabel("//platform/ide-impl:.jar")).isNull()
    assertThat(resourcesSiblingLabel("ide-impl.jar")).isNull()
  }

  @Test
  fun `the sibling comes first`() {
    val files = HashMap<String, Path>()
    files.put(MODULE_TARGET, Path.of("module.jar"))
    files.put(SIBLING_TARGET, Path.of("sibling.jar"))
    val asked = ArrayList<String>()

    val root = resolveProbeRoot(MODULE_TARGET) { label ->
      asked.add(label)
      files.get(label)
    }

    assertThat(root).isEqualTo(Path.of("sibling.jar"))
    assertThat(asked).containsExactly(SIBLING_TARGET)
  }

  @Test
  fun `the module jar is the root when the sibling is absent`() {
    val asked = ArrayList<String>()

    val root = resolveProbeRoot(MODULE_TARGET) { label ->
      asked.add(label)
      if (label == MODULE_TARGET) Path.of("module.jar") else null
    }

    assertThat(root).isEqualTo(Path.of("module.jar"))
    assertThat(asked).containsExactly(SIBLING_TARGET, MODULE_TARGET)
  }

  @Test
  fun `no root when both are absent`() {
    assertThat(resolveProbeRoot(MODULE_TARGET) { null }).isNull()
  }
}

private const val MODULE_TARGET = "@community//plugins/env-files-support:dotenv.jar"
private const val SIBLING_TARGET = "@community//plugins/env-files-support:dotenv_resource_jar.jar"
