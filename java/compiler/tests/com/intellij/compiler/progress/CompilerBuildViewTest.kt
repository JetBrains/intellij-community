// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.progress

import com.intellij.build.BuildWorkspaceConfiguration
import com.intellij.compiler.BaseCompilerTestCase
import com.intellij.compiler.CompilerWorkspaceConfiguration
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.compiler.CompilationStatusListener
import com.intellij.openapi.compiler.CompileContext
import com.intellij.openapi.compiler.CompileStatusNotification
import com.intellij.openapi.compiler.CompilerMessageCategory.INFORMATION
import com.intellij.openapi.compiler.CompilerMessageCategory.WARNING
import com.intellij.openapi.compiler.CompilerTopics
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.PsiTestUtil.addSourceRoot
import com.intellij.testFramework.RunAll
import com.intellij.testFramework.fixtures.BuildViewTestFixture
import com.intellij.util.ThrowableRunnable
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.jps.model.java.JavaResourceRootType

class CompilerBuildViewTest : BaseCompilerTestCase() {
  private lateinit var buildViewTestFixture: BuildViewTestFixture
  private val testDisposable: Disposable = Disposer.newDisposable()

  @Throws(Exception::class)
  public override fun setUp() {
    super.setUp()
    buildViewTestFixture = BuildViewTestFixture(project)
    buildViewTestFixture.setUp()
  }

  public override fun tearDown() {
    RunAll(
      ThrowableRunnable { if (::buildViewTestFixture.isInitialized) buildViewTestFixture.tearDown() },
      ThrowableRunnable { Disposer.dispose(testDisposable) },
      ThrowableRunnable { super.tearDown() }
    ).run()
  }

  fun `test empty build`() {
    make(compilerManager.createProjectCompileScope(myProject))
    buildViewTestFixture.assertBuildViewTreeEquals("-\n build finished")
    buildViewTestFixture.assertBuildViewSelectedNode("build finished", "", false)

    rebuildProject(false)
    buildViewTestFixture.assertBuildViewTreeEquals("-\n rebuild finished")
    buildViewTestFixture.assertBuildViewSelectedNode("rebuild finished", "", false)

    compile(compilerManager.createProjectCompileScope(myProject), true)
    buildViewTestFixture.assertBuildViewTreeEquals("-\n recompile finished")
    buildViewTestFixture.assertBuildViewSelectedNode("recompile finished", "", false)
  }

  fun `test successful build`() {
    val file = createFile("src/A.java", "public class A {}")
    val srcRoot = file.parent
    val module = addModule("a", srcRoot)
    val propFile = createFile("resources/foo.properties", "bar=baz")
    runWriteAction { addSourceRoot(module, propFile.parent, JavaResourceRootType.RESOURCE) }

    build(module)
    buildViewTestFixture.assertBuildViewTreeEquals("-\n build finished")

    runWithProgressExIndicatorSupport { rebuildProject() }
    buildViewTestFixture.assertBuildViewTreeEquals("-\n rebuild finished")
    buildViewTestFixture.assertBuildViewSelectedNode("rebuild finished", false) { output: String? ->
      assertThat(output).startsWith("Clearing build system data...\n" +
                                    "Executing pre-compile tasks...\n" +
                                    "Loading Ant configuration...\n" +
                                    "Running Ant tasks...\n" +
                                    "Cleaning output directories…\n" +
                                    "Running 'before' tasks\n" +
                                    "Checking sources\n" +
                                    "Copying resources… [a]\n" +
                                    "Parsing java… [a]\n" +
                                    "Writing classes… [a]\n" +
                                    "Updating dependency information… [a]\n" +
                                    "Adding @NotNull assertions… [a]\n" +
                                    "Adding pattern assertions… [a]\n" +
                                    "Adding the Threading Model assertions… [a]\n" +
                                    "Running 'after' tasks\n")
      assertThat(output).contains("Finished, saving caches…\n" +
                                  "Executing post-compile tasks...\n" +
                                  "Loading Ant configuration...\n" +
                                  "Running Ant tasks...\n" +
                                  "Synchronizing output directories...")
    }

    runWithProgressExIndicatorSupport { rebuild(module) }
    buildViewTestFixture.assertBuildViewTreeEquals("-\n recompile finished")
    buildViewTestFixture.assertBuildViewSelectedNode("recompile finished", false) { output: String? ->
      assertThat(output).startsWith("Executing pre-compile tasks...\n" +
                                    "Loading Ant configuration...\n" +
                                    "Running Ant tasks...\n" +
                                    "Cleaning output directories…\n" +
                                    "Running 'before' tasks\n" +
                                    "Checking sources\n" +
                                    "Copying resources… [a]\n" +
                                    "Parsing java… [a]\n" +
                                    "Writing classes… [a]\n" +
                                    "Updating dependency information… [a]\n" +
                                    "Adding @NotNull assertions… [a]\n" +
                                    "Adding pattern assertions… [a]\n" +
                                    "Adding the Threading Model assertions… [a]\n" +
                                    "Running 'after' tasks")
      assertThat(output).contains("Finished, saving caches…\n" +
                                  "Executing post-compile tasks...\n" +
                                  "Loading Ant configuration...\n" +
                                  "Running Ant tasks...\n" +
                                  "Synchronizing output directories...")
    }
  }

  fun `test build with compile error`() {
    val file = createFile("src/A.java", "public class A a{}foo")
    val srcRoot = file.parent
    val module = addModule("a", srcRoot)

    build(module, true)
    buildViewTestFixture.assertBuildViewTreeEquals(
      "-\n" +
      " -build failed\n" +
      "  -A.java\n" +
      "   '{' expected\n" +
      "   reached end of file while parsing"
    )
    rebuildProject(true)
    buildViewTestFixture.assertBuildViewTreeEquals(
      "-\n" +
      " -rebuild failed\n" +
      "  -A.java\n" +
      "   '{' expected\n" +
      "   reached end of file while parsing"
    )

    rebuild(module, true)
    buildViewTestFixture.assertBuildViewTreeEquals(
      "-\n" +
      " -recompile failed\n" +
      "  -A.java\n" +
      "   '{' expected\n" +
      "   reached end of file while parsing"
    )
  }

  fun `test build workspace settings sync`() {
    val workspaceConfiguration = myProject.service<CompilerWorkspaceConfiguration>()
    val oldAutoShowErrorsInEditor = workspaceConfiguration.AUTO_SHOW_ERRORS_IN_EDITOR

    assertEquals(oldAutoShowErrorsInEditor, myProject.service<BuildWorkspaceConfiguration>().isShowFirstErrorInEditor)
    try {
      workspaceConfiguration.AUTO_SHOW_ERRORS_IN_EDITOR = false
      assertFalse(myProject.service<BuildWorkspaceConfiguration>().isShowFirstErrorInEditor)

      workspaceConfiguration.AUTO_SHOW_ERRORS_IN_EDITOR = true
      assertTrue(myProject.service<BuildWorkspaceConfiguration>().isShowFirstErrorInEditor)
    } finally {
      workspaceConfiguration.AUTO_SHOW_ERRORS_IN_EDITOR = oldAutoShowErrorsInEditor
    }
  }

  fun `test build autoShowFirstError`() {
    val workspaceConfiguration = myProject.service<CompilerWorkspaceConfiguration>()
    val oldAutoShowErrorsInEditor = workspaceConfiguration.AUTO_SHOW_ERRORS_IN_EDITOR

    try {
      val file = createFile("src/A.java", "public class A a{}foo")
      val srcRoot = file.parent
      val module = addModule("a", srcRoot)

      workspaceConfiguration.AUTO_SHOW_ERRORS_IN_EDITOR = false
      build(module, true)

      val fileEditorManager = FileEditorManager.getInstance(myProject)
      assertThat(fileEditorManager.selectedFiles).isEmpty()
      assertNull(fileEditorManager.selectedEditor)

      workspaceConfiguration.AUTO_SHOW_ERRORS_IN_EDITOR = true
      rebuildProject(true)

      assertThat(fileEditorManager.selectedFiles).containsExactly(file)
      val logicalPosition = (fileEditorManager.selectedEditor as TextEditor).editor.caretModel.logicalPosition
      assertEquals(0, logicalPosition.line)
      assertEquals(14, logicalPosition.column)
    } finally {
      workspaceConfiguration.AUTO_SHOW_ERRORS_IN_EDITOR = oldAutoShowErrorsInEditor
    }
  }

  @Suppress("DialogTitleCapitalization")
  fun `test build messages started with carriage return`() {
    val module = addModule("a", null)
    myProject.messageBus.connect(testRootDisposable).subscribe(CompilerTopics.COMPILATION_STATUS, object : CompilationStatusListener {
      override fun compilationFinished(aborted: Boolean, errors: Int, warnings: Int, compileContext: CompileContext) {
        compileContext.addMessage(INFORMATION, "some progress 0%", null, -1, -1)
        compileContext.addMessage(INFORMATION, "\rsome progress 30%", null, -1, -1)
        compileContext.addMessage(INFORMATION, "\rsome progress 60%", null, -1, -1)
        compileContext.addMessage(INFORMATION, "another message\n", null, -1, -1)
        compileContext.addMessage(WARNING, "another yellow message\n", null, -1, -1)
        compileContext.addMessage(INFORMATION, "\rsome progress 90%", null, -1, -1)
        compileContext.addMessage(INFORMATION, "\rsome progress 95%", null, -1, -1)
        compileContext.addMessage(INFORMATION, "this message will be dropped because of subsequent message started with CR", null, -1, -1)
        compileContext.addMessage(INFORMATION, "\rsome progress 99%", null, -1, -1)
        compileContext.addMessage(INFORMATION, "\rsome progress 100%", null, -1, -1)
      }
    })
    build(module)
    buildViewTestFixture.assertBuildViewTreeEquals("-\n" +
                                                   " -build finished\n" +
                                                   "  another yellow message")
    buildViewTestFixture.assertBuildViewSelectedNode("build finished", "some progress 60%\n" +
                                                                       "another message\n" +
                                                                       "another yellow message\n" +
                                                                       "some progress 95%\n" +
                                                                       "some progress 100%", false)
  }

  private fun build(module: Module, errorsExpected: Boolean = false): CompilationLog? {
    val compileScope = compilerManager.createModuleCompileScope(module, false)
    return compile(compileScope, false, errorsExpected)
  }

  private fun rebuild(module: Module, errorsExpected: Boolean = false): CompilationLog? {
    val compileScope = compilerManager.createModuleCompileScope(module, false)
    return compile(compileScope, true, errorsExpected)
  }

  private fun rebuildProject(errorsExpected: Boolean = false): CompilationLog? {
    return compile(errorsExpected) { compileStatusNotification: CompileStatusNotification? ->
      compilerManager.rebuild(compileStatusNotification)
    }
  }

  private fun runWithProgressExIndicatorSupport(action: () -> Unit) {
    PlatformTestUtil.withSystemProperty<Nothing>("intellij.progress.task.ignoreHeadless", "true", action)
  }
}
