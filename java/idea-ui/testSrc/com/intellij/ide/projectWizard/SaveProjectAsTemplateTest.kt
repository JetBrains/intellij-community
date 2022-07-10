// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectWizard

import com.intellij.configurationStore.runInAllowSaveMode
import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.ide.fileTemplates.impl.FileTemplateManagerImpl
import com.intellij.ide.util.PropertiesComponent
import com.intellij.ide.util.projectWizard.ProjectTemplateParameterFactory
import com.intellij.ide.wizard.Step
import com.intellij.mock.MockProgressIndicator
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.Experiments
import com.intellij.openapi.components.StorageScheme
import com.intellij.openapi.module.BasePackageParameterFactory
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.platform.ProjectTemplatesFactory
import com.intellij.platform.templates.ArchivedTemplatesFactory
import com.intellij.platform.templates.LocalArchivedTemplate
import com.intellij.platform.templates.SaveProjectAsTemplateAction
import com.intellij.project.stateStore
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.testFramework.OpenProjectTaskBuilder
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.util.SystemProperties
import com.intellij.util.io.createFile
import com.intellij.util.text.DateFormatUtil
import org.assertj.core.api.Assertions.assertThat
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

private const val FOO_BAR_JAVA = "foo/Bar.java"
private val TEST_DATE = Date(0)

class SaveProjectAsTemplateTest : NewProjectWizardTestCase() {
  fun testSaveProject() {
    doTest(shouldEscape = true, replaceParameters = true, initialText = """/** No comments */
    
    /**
     * Created by Dmitry.Avdeev on 1/22/13.
     */
    package foo;
    public class Bar {
    }""", expected = """/** No comments */
    
    /**
     * Created by ${SystemProperties.getUserName()} on ${DateFormatUtil.formatDate(TEST_DATE)}.
     */
    
    package foo;
    public class Bar {
    }""")
  }

  fun testSaveProjectUnescaped() {
    doTest(false, false, """/** No comments */

/**
 * Created by Dmitry.Avdeev on 1/22/13.
 */
package foo;
public class Bar {
}""", """/** No comments */

/**
 * Created by ${SystemProperties.getUserName()} on ${DateFormatUtil.formatDate(TEST_DATE)}.
 */

package foo;
public class Bar {
}""")
  }

  private fun doTest(shouldEscape: Boolean, replaceParameters: Boolean, initialText: String, expected: String) {
    assertThat(project.stateStore.storageScheme).isEqualTo(StorageScheme.DIRECTORY_BASED)
    val root = ProjectRootManager.getInstance(project).contentRoots[0]
    val rootFile = root.toNioPath().resolve(FOO_BAR_JAVA)
    rootFile.createFile()
    val file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(rootFile)
    assertNotNull(file)
    HeavyPlatformTestCase.setFileText(file!!, initialText)
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    val basePackage = BasePackageParameterFactory().detectParameterValue(project)
    assertEquals("foo", basePackage)
    val zipFile = ArchivedTemplatesFactory.getTemplateFile("foo")
    runInAllowSaveMode(true) {
      SaveProjectAsTemplateAction.saveProject(project, zipFile, null, "bar", replaceParameters, MockProgressIndicator(), shouldEscape)
    }
    assertThat(zipFile.fileName.toString()).isEqualTo("foo.zip")
    assertThat(Files.size(zipFile)).isGreaterThan(0)
    val fromTemplate = if (Experiments.getInstance().isFeatureEnabled("new.project.wizard")) createProject { step: Step? ->
      if (step is ProjectTypeStep) {
        assertTrue(step.setSelectedTemplate("foo", null))
      }
    }
    else createProjectFromTemplate(ProjectTemplatesFactory.CUSTOM_GROUP, "foo", null)
    val descriptionFile = SaveProjectAsTemplateAction.getDescriptionFile(fromTemplate, LocalArchivedTemplate.DESCRIPTION_PATH)
    assertNotNull(descriptionFile)
    assertEquals("bar", VfsUtilCore.loadText(descriptionFile))
    val roots = ProjectRootManager.getInstance(fromTemplate).contentRoots
    val child = roots[0].findFileByRelativePath(FOO_BAR_JAVA)
    assertNotNull(Arrays.asList(*roots[0].children).toString(), child)
    assertEquals(expected, StringUtil.convertLineSeparators(VfsUtilCore.loadText(
      child!!)))
    assertThat(Path.of(fromTemplate.basePath, ".idea/workspace.xml")).isRegularFile
  }

  override fun setUp() {
    super.setUp()
    (FileTemplateManager.getDefaultInstance() as FileTemplateManagerImpl).setTestDate(TEST_DATE)
    PropertiesComponent.getInstance().unsetValue(ProjectTemplateParameterFactory.IJ_BASE_PACKAGE)
    PlatformTestUtil.setLongMeaninglessFileIncludeTemplateTemporarilyFor(project, project)
  }

  override fun tearDown() {
    try {
      (FileTemplateManager.getDefaultInstance() as FileTemplateManagerImpl).setTestDate(null)
      PropertiesComponent.getInstance().unsetValue(ProjectTemplateParameterFactory.IJ_BASE_PACKAGE)
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }

  override fun doCreateAndOpenProject(): Project {
    val projectFile = getProjectDirOrFile(true)
    Files.createDirectories(projectFile.parent.resolve(Project.DIRECTORY_STORE_FOLDER))
    return ProjectManagerEx.getInstanceEx().openProject(projectFile.parent, OpenProjectTaskBuilder().build())!!
  }

  override fun createMainModule(): Module {
    val module = super.createMainModule()
    ApplicationManager.getApplication().runWriteAction {
      val model = ModuleRootManager.getInstance(module).modifiableModel
      val baseDir = PlatformTestUtil.getOrCreateProjectBaseDir(module.project)
      val entry = model.addContentEntry(baseDir)
      entry.addSourceFolder(baseDir, false)
      model.commit()
    }
    return module
  }
}