// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.impl.JavaCodeInsightTestFixtureImpl
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.TestOnly
import java.nio.file.Path

/**
 * Creates [JavaCodeInsightTestFixture] fixture that can be used when running JUnit5 tests.
 */
@TestOnly
fun javaCodeInsightFixture(
  projectFixture: TestFixture<Project>,
  tempDirFixture: TestFixture<Path>,
): TestFixture<JavaCodeInsightTestFixture> = codeInsightFixture(projectFixture, tempDirFixture, ::JavaCodeInsightTestFixtureImpl)


/**
 * Configures jdk for the project and the module inside JUnit5 test.
 * This method should be called inside method annotated with [org.junit.jupiter.api.BeforeAll].
 *
 * @param level version of the JDK to be set
 */
@TestOnly
@RequiresEdt
fun setUpJdk(level: LanguageLevel, project: Project, module: Module, disposable: Disposable) {
  val jdk = IdeaTestUtil.getMockJdk(level)
  WriteAction.runAndWait<Exception> {
    ProjectJdkTable.getInstance().addJdk(jdk, disposable)
    ProjectRootManager.getInstance(project).setProjectSdk(jdk)
    ModuleRootModificationUtil.setModuleSdk(module, jdk)
  }
  IndexingTestUtil.waitUntilIndexesAreReady(project)
}