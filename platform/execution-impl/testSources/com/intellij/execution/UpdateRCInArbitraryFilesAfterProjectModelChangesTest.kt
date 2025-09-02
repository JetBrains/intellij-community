// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution

import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.testFramework.*
import com.intellij.testFramework.rules.ProjectModelRule
import com.intellij.testFramework.rules.TempDirectory
import org.assertj.core.api.Assertions.assertThat
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test

class UpdateRCInArbitraryFilesAfterProjectModelChangesTest {
  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()
  }

  @Rule
  @JvmField
  val projectModel = ProjectModelRule()

  @JvmField
  @Rule
  val baseNonProjectDir: TempDirectory = TempDirectory()

  @JvmField
  @Rule
  val logging = TestLoggerFactory.createTestWatcher()
  @Test
  fun `add and remove module`() {
    val file = baseNonProjectDir.newVirtualFile("m/a.run.xml", generateRunXmlFileText("a"))
    assertThat(runConfigurations).isEmpty()
    val module = projectModel.createModule("m", baseNonProjectDir.rootPath)
    ModuleRootModificationUtil.addContentRoot(module, file.parent.path)
    IndexingTestUtil.waitUntilIndexesAreReady(projectModel.project)
    assertThat(runConfigurations.single().name).isEqualTo("a")
    projectModel.removeModule(module)
    runInEdtAndWait { NonBlockingReadActionImpl.waitForAsyncTaskCompletion() }
    IndexingTestUtil.waitUntilIndexesAreReady(projectModel.project)
    assertThat(runConfigurations).isEmpty()
  }
  @Test
  fun `remove source root under excluded folder`() {
    val contentRoot = projectModel.baseProjectDir.newVirtualDirectory("m")
    val excludedRoot = projectModel.baseProjectDir.newVirtualDirectory("m/exc")
    val srcRoot = projectModel.baseProjectDir.newVirtualDirectory("m/exc/src")
    val module = projectModel.createModule("m")
    ModuleRootModificationUtil.addContentRoot(module, contentRoot)
    PsiTestUtil.addSourceRoot(module, srcRoot)
    PsiTestUtil.addExcludedRoot(module, excludedRoot)
    projectModel.baseProjectDir.newVirtualFile("m/exc/src/b.run.xml", generateRunXmlFileText("b"))
    IndexingTestUtil.waitUntilIndexesAreReady(projectModel.project)

    assertThat(runConfigurations.single().name).isEqualTo("b")
    
    PsiTestUtil.removeSourceRoot(module, srcRoot)
    runInEdtAndWait { NonBlockingReadActionImpl.waitForAsyncTaskCompletion() }
    IndexingTestUtil.waitUntilIndexesAreReady(projectModel.project)
    assertThat(runConfigurations).isEmpty()
  }
  
  private val runConfigurations: List<RunnerAndConfigurationSettings>
    get() = RunManager.getInstance(projectModel.project).allSettings

  private fun generateRunXmlFileText(rcName: String): ByteArray {
    return """
      |<component name='ProjectRunConfigurationManager'>
      |  <configuration name='$rcName' type='CompoundRunConfigurationType' factoryName='CompoundRunConfigurationType'/>
      |</component>
      |""".trimMargin().toByteArray()
  }

}