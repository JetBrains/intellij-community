// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.dev

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

internal class DevDistPatchedDescriptorsTest {
  @AfterEach
  fun unarm() {
    // process-wide state, so a test that arms it has to give it back
    DevDistPatchedDescriptors.stopForTest()
  }

  @Test
  fun `the first step carries no verdict and the rest compare with the step before`() {
    val stages = DevDistDescriptorStages()
    stages.add(DevDistDescriptorStage.SOURCE, "<idea-plugin/>")
    stages.add(DevDistDescriptorStage.RAW_TEXT_PATCHER, "<idea-plugin/>")
    stages.add(DevDistDescriptorStage.RESERIALIZED, "<idea-plugin />")

    val record = record(stages)

    assertThat(record.steps.map { it.stage to it.changed }).containsExactly(
      DevDistDescriptorStage.SOURCE to null,
      DevDistDescriptorStage.RAW_TEXT_PATCHER to false,
      DevDistDescriptorStage.RESERIALIZED to true,
    )
    assertThat(record.source).isEqualTo("<idea-plugin/>")
    assertThat(record.patched).isEqualTo("<idea-plugin />")
  }

  /**
   * Every stage's text is recoverable, and no text is stored twice.
   *
   * The three ways a step states no text are all here: the source, a step that changed nothing, and the last step
   * that changed the text.
   */
  @Test
  fun `a step states its text only when no other field holds that text`() {
    val stages = DevDistDescriptorStages()
    stages.add(DevDistDescriptorStage.SOURCE, "<idea-plugin/>")
    stages.add(DevDistDescriptorStage.RAW_TEXT_PATCHER, "<idea-plugin/>")
    stages.add(DevDistDescriptorStage.RESERIALIZED, "<idea-plugin />")
    stages.add(DevDistDescriptorStage.STAMPS, "<idea-plugin><version>1</version></idea-plugin>")

    val record = record(stages)

    assertThat(record.steps.map { it.stage to it.text }).containsExactly(
      // the record's own `source` field holds it
      DevDistDescriptorStage.SOURCE to null,
      // the step ahead of it holds it
      DevDistDescriptorStage.RAW_TEXT_PATCHER to null,
      // nothing else holds it, so the step states it
      DevDistDescriptorStage.RESERIALIZED to "<idea-plugin />",
      // the record's own `patched` field holds it
      DevDistDescriptorStage.STAMPS to null,
    )
  }

  /**
   * A step whose text is the source text states that text.
   *
   * The flags alone cannot tell such a step from the last step that changed the text, so the reader would answer
   * `patched` for it. No stage of this product undoes the stage ahead of it, and the rule is here for the one that
   * does.
   */
  @Test
  fun `a step that returns to the source text states it`() {
    val stages = DevDistDescriptorStages()
    stages.add(DevDistDescriptorStage.SOURCE, "<idea-plugin/>")
    stages.add(DevDistDescriptorStage.RAW_TEXT_PATCHER, "<idea-plugin />")
    stages.add(DevDistDescriptorStage.RESERIALIZED, "<idea-plugin/>")
    stages.add(DevDistDescriptorStage.STAMPS, "<idea-plugin><version>1</version></idea-plugin>")

    assertThat(record(stages).steps.map { it.text }).containsExactly(
      null,
      "<idea-plugin />",
      "<idea-plugin/>",
      null,
    )
  }

  /** A descriptor is UTF-8 in the jar, so the size of a non-ASCII one is not its character count. */
  @Test
  fun `a step is measured in the bytes the jar receives`() {
    val stages = DevDistDescriptorStages()
    stages.add(DevDistDescriptorStage.SOURCE, "<name>Kotlin®</name>")

    // 20 characters, and the registered-trademark sign is two bytes of them
    assertThat(record(stages).steps.single().bytes).isEqualTo(21)
  }

  @Test
  fun `a recording that does not start at the source is refused`() {
    val stages = DevDistDescriptorStages()
    stages.add(DevDistDescriptorStage.STAMPS, "<idea-plugin/>")

    assertThatThrownBy { record(stages) }
      .hasMessageContaining("was not recorded from its source")
      .hasMessageContaining("STAMPS")
  }

  @Test
  fun `nothing is recorded and nothing is written until it is armed`(@TempDir tempDir: Path) {
    val file = tempDir.resolve("unarmed.patched-descriptors.json")

    assertThat(DevDistPatchedDescriptors.stagesOrNull()).isNull()

    assertThat(DevDistPatchedDescriptors.write(file = file, fragment = "unarmed")).isNull()
    assertThat(Files.exists(file)).isFalse()
  }

  @Test
  fun `one plugin built twice is one record, and a disagreement is two`(@TempDir tempDir: Path) {
    DevDistPatchedDescriptors.start()
    recordPlugin("plugin.os.specific", "<idea-plugin>one</idea-plugin>")
    recordPlugin("plugin.os.specific", "<idea-plugin>one</idea-plugin>")
    recordPlugin("plugin.disagrees", "<idea-plugin>a</idea-plugin>")
    recordPlugin("plugin.disagrees", "<idea-plugin>b</idea-plugin>")

    val report = write(tempDir.resolve("two.patched-descriptors.json"), "plugins_rest")

    assertThat(report.fragment).isEqualTo("plugins_rest")
    assertThat(report.collapsedDuplicates).isEqualTo(1)
    assertThat(report.plugins.map { it.mainModule to it.patched }).containsExactly(
      "plugin.disagrees" to "<idea-plugin>a</idea-plugin>",
      "plugin.disagrees" to "<idea-plugin>b</idea-plugin>",
      "plugin.os.specific" to "<idea-plugin>one</idea-plugin>",
    )
  }

  @Test
  fun `an armed run carries what a reader needs to find the main jar`(@TempDir tempDir: Path) {
    DevDistPatchedDescriptors.start()
    val stages = DevDistPatchedDescriptors.stagesOrNull()!!
    stages.add(DevDistDescriptorStage.SOURCE, "<idea-plugin/>")
    DevDistPatchedDescriptors.record(
      mainModule = "intellij.java",
      directoryName = "java",
      mainJar = "java-impl.jar",
      embedsContentModules = false,
      stages = stages,
    )

    val file = tempDir.resolve("one.patched-descriptors.json")
    write(file, "plugins_java")

    assertThat(Files.readString(file))
      .contains("\"mainModule\": \"intellij.java\"")
      .contains("\"directoryName\": \"java\"")
      .contains("\"mainJar\": \"java-impl.jar\"")
      .contains("\"embedsContentModules\": false")
      .contains("\"source\": \"<idea-plugin/>\"")
      .contains("\"patched\": \"<idea-plugin/>\"")
  }

  /**
   * The names in the file are the artifact's contract, and a reader keys on every one of them.
   *
   * Spelled out rather than derived from the enum. A test that read the names off the code would pass after a rename
   * that breaks every reader. That is the failure this test exists to catch.
   */
  @Test
  fun `the file states every stage name and every step field`(@TempDir tempDir: Path) {
    DevDistPatchedDescriptors.start()
    val stages = DevDistPatchedDescriptors.stagesOrNull()!!
    for (stage in DevDistDescriptorStage.entries) {
      stages.add(stage, "<idea-plugin>${stage.name}</idea-plugin>")
    }
    DevDistPatchedDescriptors.record(
      mainModule = "intellij.every.stage",
      directoryName = "every-stage",
      mainJar = "every-stage.jar",
      embedsContentModules = true,
      stages = stages,
    )

    val file = tempDir.resolve("all.patched-descriptors.json")
    write(file, "plugins_rest")

    assertThat(Files.readString(file))
      .contains("\"plugins\"")
      .contains("\"steps\"")
      .contains("\"bytes\"")
      .contains("\"changed\"")
      .contains("\"text\": \"<idea-plugin>RESERIALIZED</idea-plugin>\"")
      .contains("\"stage\": \"source\"")
      .contains("\"stage\": \"rawTextPatcher\"")
      .contains("\"stage\": \"reserialized\"")
      .contains("\"stage\": \"stamps\"")
      .contains("\"stage\": \"includes\"")
      .contains("\"stage\": \"contentModules\"")
      .contains("\"stage\": \"textPatcher\"")
  }

  private fun record(stages: DevDistDescriptorStages): DevDistPluginDescriptor {
    return stages.toRecord(
      mainModule = "intellij.plugin",
      directoryName = "plugin",
      mainJar = "plugin.jar",
      embedsContentModules = true,
    )
  }

  private fun recordPlugin(mainModule: String, patched: String) {
    val stages = DevDistPatchedDescriptors.stagesOrNull()!!
    stages.add(DevDistDescriptorStage.SOURCE, "<idea-plugin/>")
    stages.add(DevDistDescriptorStage.TEXT_PATCHER, patched)
    DevDistPatchedDescriptors.record(
      mainModule = mainModule,
      directoryName = mainModule,
      mainJar = "$mainModule.jar",
      embedsContentModules = true,
      stages = stages,
    )
  }

  /**
   * Asserts what the file states, and answers with the report, which is the same object.
   *
   * The names are checked on the text, because they are the artifact's contract. The reader that classified the patch
   * keys on `stage` and on each stage's serial name. The tests module cannot decode the file, because it does not carry
   * kotlinx.serialization. An assertion on the text is the cheaper trade against adding that library to
   * `intellij.platform.buildScripts.tests.iml`.
   *
   * Three things no assertion on the file covers. The six other stage names, which one other test covers on the file.
   * The order of the `steps` list, which the first test covers on the record and nothing covers on the file. And the
   * absence of `changed` on the `source` step, which nothing checks at all.
   */
  private fun write(file: Path, fragment: String): DevDistDescriptorReport {
    val report = DevDistPatchedDescriptors.write(file = file, fragment = fragment)!!
    assertThat(Files.readString(file))
      .contains("\"fragment\": \"$fragment\"")
      .contains("\"stage\": \"source\"")
      .contains("\"collapsedDuplicates\": ${report.collapsedDuplicates}")
    return report
  }
}
