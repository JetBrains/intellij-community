// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectWizard

import com.intellij.configurationStore.runInAllowSaveMode
import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.ide.fileTemplates.impl.FileTemplateManagerImpl
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.util.PropertiesComponent
import com.intellij.ide.util.projectWizard.ProjectTemplateParameterFactory
import com.intellij.mock.MockProgressIndicator
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.components.StorageScheme
import com.intellij.openapi.module.BasePackageParameterFactory
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.platform.templates.ArchivedTemplatesFactory
import com.intellij.platform.templates.LocalArchivedTemplate
import com.intellij.platform.templates.SaveProjectAsTemplateAction
import com.intellij.project.stateStore
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.util.SystemProperties
import com.intellij.util.io.createParentDirectories
import com.intellij.util.text.DateFormatUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.io.path.createFile

private const val FOO_BAR_JAVA = "foo/Bar.java"
private val TEST_DATE = Date(0)

class SaveProjectAsTemplateTest : NewProjectWizardTestCase() {
  override fun runInDispatchThread() = false

  fun testSaveProject() = runBlocking {
    doTest(
      shouldEscape = true,
      replaceParameters = true,
      initialText = """/** No comments */

/**
 * Created by Dmitry.Avdeev on 1/22/13.
 */
package foo;
public class Bar {
}""",
      expected = """/** No comments */

/**
 * Created by ${SystemProperties.getUserName()} on ${DateFormatUtil.formatDate(TEST_DATE)}.
 */

package foo;
public class Bar {
}""")
  }

  fun testSaveProjectUnescaped() = runBlocking {
    doTest(
      shouldEscape = false,
      replaceParameters = false,
      initialText = """/** No comments */

/**
 * Created by Dmitry.Avdeev on 1/22/13.
 */
package foo;
public class Bar {
}
    """,
      expected = """
/** No comments */

/**
 * Created by ${SystemProperties.getUserName()} on ${DateFormatUtil.formatDate(TEST_DATE)}.
 */

package foo;
public class Bar {
}""")
  }

  private suspend fun doTest(shouldEscape: Boolean, replaceParameters: Boolean, initialText: String, expected: String) {
    assertThat(project.stateStore.storageScheme).isEqualTo(StorageScheme.DIRECTORY_BASED)
    val root = ProjectRootManager.getInstance(project).contentRoots[0]
    val rootFile = root.toNioPath().resolve(FOO_BAR_JAVA)
    rootFile.createParentDirectories().createFile()
    val file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(rootFile)
    withContext(Dispatchers.EDT) {
      writeIntentReadAction {
        assertThat(file).isNotNull
        HeavyPlatformTestCase.setFileText(file!!, initialText)
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        val basePackage = BasePackageParameterFactory().detectParameterValue(project)
        assertEquals("foo", basePackage)
      }
    }

    val zipFile = ArchivedTemplatesFactory.getTemplateFile("foo")
    runInAllowSaveMode(true) {
      SaveProjectAsTemplateAction.saveProject(project, zipFile, null, "bar", replaceParameters, MockProgressIndicator(), shouldEscape)
    }
    assertThat(zipFile.fileName.toString()).isEqualTo("foo.zip")
    assertThat(Files.size(zipFile)).isGreaterThan(0)
    val fromTemplate = withContext(Dispatchers.EDT) {
      writeIntentReadAction {
        createProject { step ->
          if (step is ProjectTypeStep) {
            assertTrue(step.setSelectedTemplate("foo", null))
          }
        }
      }
    }
    val descriptionFile = SaveProjectAsTemplateAction.getDescriptionFile(fromTemplate, LocalArchivedTemplate.DESCRIPTION_PATH)
    assertThat(descriptionFile).isNotNull
    assertThat(VfsUtilCore.loadText(descriptionFile)).isEqualTo("bar")
    val roots = ProjectRootManager.getInstance(fromTemplate).contentRoots
    val child = roots[0].findFileByRelativePath(FOO_BAR_JAVA)
    assertNotNull(listOf(*roots[0].children).toString(), child)
    assertThat(VfsUtilCore.loadText(child!!).trim()).isEqualToIgnoringNewLines(expected.trim())
    assertThat(Path.of(fromTemplate.basePath!!, ".idea/workspace.xml")).isRegularFile
  }

  override fun setUp() {
    super.setUp()
    (FileTemplateManager.getDefaultInstance() as FileTemplateManagerImpl).setTestDate(TEST_DATE)
    PropertiesComponent.getInstance().unsetValue(ProjectTemplateParameterFactory.IJ_BASE_PACKAGE)
    PlatformTestUtil.setLongMeaninglessFileIncludeTemplateTemporarilyFor(project, project)
  }

  override fun tearDown() {
    try {
      ProjectJdkTable.getInstance().apply {
        allJdks.forEach { removeJdk(it) }
      }
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
    return runBlocking {
      ProjectManagerEx.getInstanceEx().openProjectAsync(projectStoreBaseDir = projectFile.parent, options = OpenProjectTask {})!!
    }
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
