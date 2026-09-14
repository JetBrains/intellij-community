// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.ide.SaveAndSyncHandler
import com.intellij.ide.SaveAndSyncHandlerListener
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.impl.stores.ComponentStoreOwner
import com.intellij.openapi.components.impl.stores.IComponentStore
import com.intellij.openapi.components.impl.stores.stateStore
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.rules.ProjectModelExtension
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.readText
import kotlin.time.Duration.Companion.seconds

@TestApplication
@Timeout(30)
internal class SaveAndSyncHandlerApplicationSaveTest {
  @RegisterExtension
  private val firstModel = ProjectModelExtension()

  @RegisterExtension
  private val secondModel = ProjectModelExtension()

  @Test
  fun `the batch saves the application and both project workspaces`(): Unit = timeoutRunBlocking {
    val projects = listOf(firstModel.project, secondModel.project)
    for (project in projects) {
      PropertiesComponent.getInstance(project).setValue("exit.save.test", project.name)
    }
    withHandler { handler ->
      val saved = withContext(Dispatchers.EDT) {
        handler.saveSettingsUnderModalProgress(listOf(ApplicationManager.getApplication()) + projects)
      }
      assertThat(saved).isTrue()
    }
    for (project in projects) {
      assertThat(project.stateStore.storageManager.expandMacro(StoragePathMacros.WORKSPACE_FILE).readText()).contains("exit.save.test")
    }
  }

  @Test
  fun `the batch attempts each store once after another store fails`(): Unit = timeoutRunBlocking {
    val failedSaves = AtomicInteger()
    val successfulSaves = AtomicInteger()
    val failed = target(firstModel.project) {
      failedSaves.incrementAndGet()
      throw IOException("expected save failure")
    }
    val successful = target(secondModel.project) { successfulSaves.incrementAndGet() }
    val errors = AtomicInteger()
    LoggedErrorProcessor.executeWith(object : LoggedErrorProcessor() {
      override fun processError(category: String, message: String, details: Array<out String?>, t: Throwable?): Set<Action> {
        if (t is IOException && t.message == "expected save failure") {
          errors.incrementAndGet()
          return Action.NONE
        }
        return super.processError(category, message, details, t)
      }
    }).use {
      withHandler { handler ->
        val saved = withContext(Dispatchers.EDT) {
          handler.saveSettingsUnderModalProgress(listOf(failed, successful, successful))
        }
        assertThat(saved).isFalse()
      }
    }
    assertThat(failedSaves.get()).isEqualTo(1)
    assertThat(successfulSaves.get()).isEqualTo(1)
    assertThat(errors.get()).isEqualTo(1)
  }

  @Test
  fun `the headless handler uses the same batch save`(): Unit = timeoutRunBlocking {
    val saves = AtomicInteger()
    val store = target(firstModel.project) { saves.incrementAndGet() }
    val saved = withContext(Dispatchers.EDT) {
      HeadlessSaveAndSyncHandler().saveSettingsUnderModalProgress(listOf(store, store))
    }
    assertThat(saved).isTrue()
    assertThat(saves.get()).isEqualTo(1)
  }

  @Test
  fun `saving one manager does not expand its targets`(): Unit = timeoutRunBlocking {
    val saves = AtomicInteger()
    withHandler { handler ->
      val saved = withContext(Dispatchers.EDT) {
        handler.saveSettingsUnderModalProgress(target(firstModel.project) { saves.incrementAndGet() })
      }
      assertThat(saved).isTrue()
    }
    assertThat(saves.get()).isEqualTo(1)
  }

  @Test
  fun `the explicit save joins interrupted autosave and resumes its uncovered task`(): Unit = timeoutRunBlocking {
    val started = CompletableDeferred<Unit>()
    val cleanupStarted = CompletableDeferred<Unit>()
    val releaseCleanup = CompletableDeferred<Unit>()
    val cleanupFinished = AtomicBoolean()
    val resumed = CompletableDeferred<Unit>()
    val attempts = AtomicInteger()
    ExtensionPointName.create<SaveAndSyncHandlerListener>("com.intellij.saveAndSyncHandlerListener").point.registerExtension(
      object : SaveAndSyncHandlerListener {
        override suspend fun beforeSave(task: SaveAndSyncHandler.SaveTask, forceExecuteImmediately: Boolean) {
          if (attempts.getAndIncrement() != 0) {
            resumed.complete(Unit)
            return
          }
          started.complete(Unit)
          try {
            awaitCancellation()
          }
          finally {
            withContext(NonCancellable) {
              cleanupStarted.complete(Unit)
              releaseCleanup.await()
              cleanupFinished.set(true)
            }
          }
        }
      }, firstModel.disposableRule.disposable)
    withHandler { handler ->
      handler.scheduleSave(SaveAndSyncHandler.SaveTask(firstModel.project), forceExecuteImmediately = true)
      started.await()
      launch {
        cleanupStarted.await()
        releaseCleanup.complete(Unit)
      }
      withContext(Dispatchers.EDT) {
        handler.saveSettingsUnderModalProgress(target(secondModel.project) {
          assertThat(cleanupFinished.get()).isTrue()
        })
      }
      resumed.await()
    }
  }

  @Test
  fun `a queued save resumes when the pause ends`(): Unit = timeoutRunBlocking {
    val saved = CompletableDeferred<Unit>()
    ExtensionPointName.create<SaveAndSyncHandlerListener>("com.intellij.saveAndSyncHandlerListener").point.registerExtension(
      object : SaveAndSyncHandlerListener {
        override suspend fun beforeSave(task: SaveAndSyncHandler.SaveTask, forceExecuteImmediately: Boolean) {
          saved.complete(Unit)
        }
      }, firstModel.disposableRule.disposable)
    withHandler { handler ->
      handler.disableAutoSave().use {
        handler.scheduleSave(SaveAndSyncHandler.SaveTask(firstModel.project), forceExecuteImmediately = true)
        assertThat(saved.isCompleted).isFalse()
      }
      saved.await()
    }
  }

  private suspend fun withHandler(action: suspend CoroutineScope.(SaveAndSyncHandlerImpl) -> Unit) {
    coroutineScope {
      try {
        action(SaveAndSyncHandlerImpl(this, listenDelay = 0.seconds))
      }
      finally {
        coroutineContext.cancelChildren()
      }
    }
  }

  private fun target(project: Project, saveAction: suspend () -> Unit): ComponentManager {
    return object : ComponentManager by project, ComponentStoreOwner {
      override val componentStore: IComponentStore = object : IComponentStore by project.stateStore {
        override suspend fun save(forceSavingAllSettings: Boolean) {
          assertThat(forceSavingAllSettings).isTrue()
          saveAction()
        }
      }
    }
  }
}
