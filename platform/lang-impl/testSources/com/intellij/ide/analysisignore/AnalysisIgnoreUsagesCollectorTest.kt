// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.analysisignore

import com.intellij.internal.statistic.FUCollectorTestCase
import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.rules.ProjectModelExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

private const val ENABLED = "ide.analysisignore.file.enabled"

@TestApplication
class AnalysisIgnoreUsagesCollectorTest {
  @JvmField
  @RegisterExtension
  val projectModel: ProjectModelExtension = ProjectModelExtension()

  private val service get() = AnalysisIgnoreService.getInstance(projectModel.project)

  private lateinit var module: Module
  private lateinit var projectRoot: VirtualFile

  @BeforeEach
  fun setUp() {
    projectRoot = projectModel.baseProjectDir.newVirtualDirectory("projectRoot")
    module = projectModel.createModule()
    // The service reads a new file only in indexable content, and a module content root is what makes the tree indexable content.
    PsiTestUtil.addSourceContentToRoots(module, projectRoot)
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  fun `reports the enabled state and counts the files that hold a pattern`() {
    discover(
      writeAnalysisIgnoreFile("projectRoot", "build"),
      writeAnalysisIgnoreFile("projectRoot/sub", "out/"),
      writeAnalysisIgnoreFile("projectRoot/other", "# a comment only"),
    )

    val metrics = collect()

    assertEquals(true, metrics.dataOf("feature.enabled")["enabled"])
    // The file with the comment only has no pattern, and thus no entity, and thus it is not counted.
    assertEquals(2, metrics.dataOf("files.found")["count"])
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  fun `reports zero files when the project has none`() {
    val metrics = collect()

    assertEquals(true, metrics.dataOf("feature.enabled")["enabled"])
    assertEquals(0, metrics.dataOf("files.found")["count"])
  }

  @Test
  @RegistryKey(key = ENABLED, value = "false")
  fun `reports the disabled state and no count while the feature is off`() {
    val metrics = collect()

    assertEquals(false, metrics.dataOf("feature.enabled")["enabled"])
    assertNull(metrics.firstOrNull { it.eventId == "files.found" })
  }

  private fun collect(): Set<MetricEvent> =
    FUCollectorTestCase.collectProjectStateCollectorEvents(AnalysisIgnoreUsagesCollector::class.java, projectModel.project)

  private fun Set<MetricEvent>.dataOf(eventId: String): Map<String, Any> = single { it.eventId == eventId }.data.build()

  /** Reads [files] and updates the entities at once, as the scanning of the project does. */
  private fun discover(vararg files: VirtualFile) {
    for (file in files) {
      service.applyNow(file)
    }
  }

  private fun writeAnalysisIgnoreFile(relativeDirPath: String, vararg lines: String): VirtualFile =
    projectModel.baseProjectDir.newVirtualFile("$relativeDirPath/$ANALYSIS_IGNORE_FILE_NAME", lines.joinToString("\n").toByteArray())
}
