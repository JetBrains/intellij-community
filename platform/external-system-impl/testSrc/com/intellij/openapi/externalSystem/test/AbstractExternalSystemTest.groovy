/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.openapi.externalSystem.test

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.extensions.ExtensionPoint
import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.externalSystem.ExternalSystemManager
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.SkipInHeadlessEnvironment
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable

import java.lang.reflect.Field
import java.lang.reflect.Modifier

@SkipInHeadlessEnvironment
abstract class AbstractExternalSystemTest extends UsefulTestCase {
  static File tmpDir
  
  IdeaProjectTestFixture testFixture
  Project project
  File projectDir

  TestExternalSystemManager externalSystemManager
  ExtensionPoint externalSystemManagerEP

  @Override
  protected void setUp() throws Exception {
    super.setUp()
    
    ensureTempDirCreated()

    testFixture = IdeaTestFixtureFactory.fixtureFactory.createFixtureBuilder(name).fixture
    testFixture.setUp()
    project = testFixture.project

    projectDir = new File(tmpDir, getTestName(false))
    projectDir.mkdirs()
    
    externalSystemManager = new TestExternalSystemManager(project)
    def area = Extensions.getArea(null)
    externalSystemManagerEP = area.getExtensionPoint(ExternalSystemManager.EP_NAME)
    externalSystemManagerEP.registerExtension(externalSystemManager)
  }

  private static void ensureTempDirCreated() {
    if (tmpDir != null) {
      return
    }

    tmpDir = new File(FileUtil.tempDirectory, "externalSystemTests")
    FileUtil.delete(tmpDir)
    tmpDir.mkdirs()
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      project = null
      UIUtil.invokeAndWaitIfNeeded {
        try {
          externalSystemManagerEP.unregisterExtension(externalSystemManager)
          testFixture.tearDown()
          testFixture = null
        }
        catch (Exception e) {
          throw new RuntimeException(e)
        }
      }

      if (!FileUtil.delete(projectDir) && projectDir.exists()) {
        System.err.println("Cannot delete " + projectDir)
        //printDirectoryContent(myDir);
        projectDir.deleteOnExit()
      }
    }
    finally {
      super.tearDown()
      resetClassFields(getClass())
    }
  }

  private void resetClassFields(@Nullable Class<?> aClass) {
    if (aClass == null) {
      return
    }

    for (Field field : aClass.declaredFields) {
      final int modifiers = field.modifiers
      if ((modifiers & Modifier.FINAL) == 0 && (modifiers & Modifier.STATIC) == 0 && !field.getType().isPrimitive()) {
        field.setAccessible(true)
        try {
          field.set(this, null)
        }
        catch (IllegalAccessException e) {
          e.printStackTrace()
        }
      }
    }

    if (aClass != AbstractExternalSystemTest.class) {
      resetClassFields(aClass.getSuperclass())
    }
  }

  void setupExternalProject(@NotNull Closure c) {
    DataNode<ProjectData> node = buildExternalProjectInfo(c)
    applyProjectState([node])
  }
  
  @NotNull
  <T> DataNode<T> buildExternalProjectInfo(@NotNull Closure c) {
    ExternalProjectBuilder builder = new ExternalProjectBuilder(projectDir: projectDir)
    c.delegate = builder
    c.call()
  }

  protected void applyProjectState(@NotNull List<DataNode<ProjectData>> states) {
    def dataManager = ServiceManager.getService(ProjectDataManager.class)
    def settingsInitialized = false
    for (DataNode<ProjectData> node : states) {
      if (!settingsInitialized) {
        settingsInitialized = true
        def settings = ExternalSystemApiUtil.getSettings(project, ExternalSystemTestUtil.TEST_EXTERNAL_SYSTEM_ID)
        settings.linkedProjectsSettings = [new TestExternalProjectSettings(externalProjectPath: node.data.linkedExternalProjectPath)]
      }

      final Project myProject = project
      dataManager.importData(node, myProject, true)
    }
  }
}
