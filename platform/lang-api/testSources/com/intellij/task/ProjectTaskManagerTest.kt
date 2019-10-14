// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.task

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectModelBuildableElement
import com.intellij.openapi.vfs.VirtualFile
import org.easymock.EasyMock
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeoutException
import java.util.function.BiPredicate

class ProjectTaskManagerTest {
  @Test
  @Throws(TimeoutException::class, ExecutionException::class)
  fun `test new api calls`() {
    doTestNewApi(PromiseProjectTaskManager()) { it.blockingGet(10) }
    doTestNewApi(DeprecatedProjectTaskManager()) { it.blockingGet(10) }
  }

  @Test
  fun `test deprecated api calls`() {
    doTestDeprecatedApi(PromiseProjectTaskManager()) { it.blockingGet(10) }
    doTestDeprecatedApi(DeprecatedProjectTaskManager()) { it.blockingGet(10) }
  }

  private abstract class TestProjectTaskManager : ProjectTaskManager(EasyMock.mock(Project::class.java)) {
    override fun createAllModulesBuildTask(isIncrementalBuild: Boolean, project: Project): ProjectTask {
      throw UnsupportedOperationException()
    }

    override fun createModulesBuildTask(module: Module,
                                        isIncrementalBuild: Boolean,
                                        includeDependentModules: Boolean,
                                        includeRuntimeDependencies: Boolean): ProjectTask {
      throw UnsupportedOperationException()
    }

    override fun createModulesBuildTask(modules: Array<Module>,
                                        isIncrementalBuild: Boolean,
                                        includeDependentModules: Boolean,
                                        includeRuntimeDependencies: Boolean): ProjectTask {
      throw UnsupportedOperationException()
    }

    override fun createBuildTask(isIncrementalBuild: Boolean, vararg artifacts: ProjectModelBuildableElement): ProjectTask {
      throw UnsupportedOperationException()
    }
  }

  private class PromiseProjectTaskManager : TestProjectTaskManager() {
    override fun run(projectTask: ProjectTask): Promise<Result> {
      return getSuccessPromise(ProjectTaskContext())
    }

    override fun run(context: ProjectTaskContext, projectTask: ProjectTask): Promise<Result> {
      return getSuccessPromise(context)
    }

    override fun buildAllModules(): Promise<Result> {
      return getSuccessPromise(ProjectTaskContext())
    }

    override fun rebuildAllModules(): Promise<Result> {
      return getSuccessPromise(ProjectTaskContext())
    }

    override fun build(vararg modules: Module): Promise<Result> {
      return getSuccessPromise(ProjectTaskContext())
    }

    override fun rebuild(vararg modules: Module): Promise<Result> {
      return getSuccessPromise(ProjectTaskContext())
    }

    override fun compile(vararg files: VirtualFile): Promise<Result> {
      return getSuccessPromise(ProjectTaskContext())
    }

    override fun build(vararg buildableElements: ProjectModelBuildableElement): Promise<Result> {
      return getSuccessPromise(ProjectTaskContext())
    }

    override fun rebuild(vararg buildableElements: ProjectModelBuildableElement): Promise<Result> {
      return getSuccessPromise(ProjectTaskContext())
    }

    companion object {
      private fun getSuccessPromise(context: ProjectTaskContext): AsyncPromise<Result> {
        val promise = AsyncPromise<Result>()
        promise.setResult(object : Result {
          override fun getContext(): ProjectTaskContext = context
          override fun hasErrors(): Boolean = false
          override fun isAborted(): Boolean = false
          override fun contains(predicate: BiPredicate<in ProjectTask, in ProjectTaskState>): Boolean = false
        })
        return promise
      }
    }
  }

  private class DeprecatedProjectTaskManager : TestProjectTaskManager() {
    override fun run(projectTask: ProjectTask, callback: ProjectTaskNotification?) {
      sendSuccessResult(callback)
    }

    override fun run(context: ProjectTaskContext, projectTask: ProjectTask, callback: ProjectTaskNotification?) {
      sendSuccessResult(callback)
    }

    override fun buildAllModules(callback: ProjectTaskNotification?) {
      sendSuccessResult(callback)
    }

    override fun rebuildAllModules(callback: ProjectTaskNotification?) {
      sendSuccessResult(callback)
    }

    override fun build(modules: Array<Module>, callback: ProjectTaskNotification?) {
      sendSuccessResult(callback)
    }

    override fun rebuild(modules: Array<Module>, callback: ProjectTaskNotification?) {
      sendSuccessResult(callback)
    }

    override fun compile(files: Array<VirtualFile>, callback: ProjectTaskNotification?) {
      sendSuccessResult(callback)
    }

    override fun build(buildableElements: Array<ProjectModelBuildableElement>, callback: ProjectTaskNotification?) {
      sendSuccessResult(callback)
    }

    override fun rebuild(buildableElements: Array<ProjectModelBuildableElement>, callback: ProjectTaskNotification?) {
      sendSuccessResult(callback)
    }

    companion object {
      private fun sendSuccessResult(callback: ProjectTaskNotification?) {
        callback?.finished(ProjectTaskResult(false, 0, 0))
      }
    }
  }

  private class DummyTask : ProjectTask {
    override fun getPresentableName(): String {
      return "dummy task"
    }
  }

  companion object {
    @JvmStatic
    @Throws(TimeoutException::class, ExecutionException::class)
    fun doTestNewApi(taskManager: ProjectTaskManager, promiseHandler: (Promise<ProjectTaskManager.Result?>) -> ProjectTaskManager.Result?) {
      val task = DummyTask()
      val context = ProjectTaskContext()
      assertNotNull(taskManager.run(task).run(promiseHandler))
      assertEquals(context, taskManager.run(context, task).run(promiseHandler)!!.context)
      assertNotNull(taskManager.buildAllModules().run(promiseHandler))
      assertNotNull(taskManager.rebuildAllModules().run(promiseHandler))
      assertNotNull(taskManager.build(*Module.EMPTY_ARRAY).run(promiseHandler))
      assertNotNull(taskManager.rebuild(*Module.EMPTY_ARRAY).run(promiseHandler))
      assertNotNull(taskManager.compile(*VirtualFile.EMPTY_ARRAY).run(promiseHandler))
      val emptyArray = emptyArray<ProjectModelBuildableElement>()
      assertNotNull(taskManager.build(*emptyArray).run(promiseHandler))
      assertNotNull(taskManager.rebuild(*emptyArray).run(promiseHandler))
    }

    @Suppress("DEPRECATION")
    fun doTestDeprecatedApi(taskManager: ProjectTaskManager, promiseHandler: (Promise<ProjectTaskResult?>) -> ProjectTaskResult?) {
      fun doTest(body: (ProjectTaskNotification) -> Unit) {
        val promise1 = AsyncPromise<ProjectTaskResult?>()
        body.invoke(object : ProjectTaskNotification {
          override fun finished(executionResult: ProjectTaskResult) {
            promise1.setResult(executionResult)
          }
        })
        assertNotNull(promise1.run(promiseHandler))

        val promise2 = AsyncPromise<ProjectTaskResult?>()
        body.invoke(object : ProjectTaskNotification {
          override fun finished(context: ProjectTaskContext, executionResult: ProjectTaskResult) {
            promise2.setResult(executionResult)
          }
        })
        assertNotNull(promise2.run(promiseHandler))
      }

      val task = DummyTask()
      val context = ProjectTaskContext()

      doTest { taskManager.run(task, it) }
      doTest { taskManager.run(context, task, it) }
      doTest { taskManager.buildAllModules(it) }
      doTest { taskManager.rebuildAllModules(it) }
      doTest { taskManager.build(Module.EMPTY_ARRAY, it) }
      doTest { taskManager.rebuild(Module.EMPTY_ARRAY, it) }
      doTest { taskManager.compile(VirtualFile.EMPTY_ARRAY, it) }
      doTest { taskManager.build(arrayOf<ProjectModelBuildableElement>(), it) }
      doTest { taskManager.rebuild(arrayOf<ProjectModelBuildableElement>(), it) }
    }
  }
}