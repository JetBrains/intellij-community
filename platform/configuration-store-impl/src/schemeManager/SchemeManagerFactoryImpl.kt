// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore.schemeManager

import com.intellij.configurationStore.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.impl.stores.IProjectStore
import com.intellij.openapi.components.impl.stores.stateStore
import com.intellij.openapi.options.Scheme
import com.intellij.openapi.options.SchemeManager
import com.intellij.openapi.options.SchemeManagerFactory
import com.intellij.openapi.options.SchemeProcessor
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.RefreshQueue
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.addSuppressed
import com.intellij.util.containers.ContainerUtil
import kotlinx.coroutines.CancellationException
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.nio.file.Path

const val ROOT_CONFIG: String = "\$ROOT_CONFIG\$"

internal typealias FileChangeSubscriber = (schemeManager: SchemeManagerImpl<*, *>) -> Unit

sealed class SchemeManagerFactoryBase : SchemeManagerFactory(), SettingsSavingComponent {
  private val managers = ContainerUtil.createLockFreeCopyOnWriteList<SchemeManagerImpl<Scheme, Scheme>>()

  protected open val componentManager: ComponentManager? = null

  protected open fun createFileChangeSubscriber(): FileChangeSubscriber? = null

  @ApiStatus.Internal
  final override fun <T: Scheme, MutableT : T> create(
    directoryName: String,
    processor: SchemeProcessor<T, MutableT>,
    presentableName: String?,
    roamingType: RoamingType,
    schemeNameToFileName: SchemeNameToFileName,
    streamProvider: StreamProvider?,
    directoryPath: Path?,
    isAutoSave: Boolean,
    settingsCategory: SettingsCategory
  ): SchemeManager<T> {
    val path = checkPath(directoryName)
    val fileChangeSubscriber = when {
      streamProvider != null && streamProvider.isApplicable(path, roamingType) -> null
      else -> createFileChangeSubscriber()
    }
    val manager = SchemeManagerImpl(
      path,
      processor,
      streamProvider ?: componentManager?.stateStore?.storageManager?.streamProvider,
      ioDirectory = directoryPath ?: pathToFile(path),
      roamingType,
      presentableName,
      schemeNameToFileName,
      fileChangeSubscriber,
      settingsCategory,
    )
    if (isAutoSave) {
      @Suppress("UNCHECKED_CAST")
      managers.add(manager as SchemeManagerImpl<Scheme, Scheme>)
    }
    return manager
  }

  override fun dispose(schemeManager: SchemeManager<*>) {
    managers.remove(schemeManager)
  }

  internal open fun checkPath(originalPath: String): String {
    when {
      originalPath.contains('\\') -> LOG.error("Path must be system-independent, use forward slash instead of backslash")
      originalPath.isEmpty() -> LOG.error("Path must not be empty")
    }
    return originalPath
  }

  internal abstract fun pathToFile(path: String): Path

  fun process(processor: (SchemeManagerImpl<Scheme, Scheme>) -> Unit) {
    for (manager in managers) {
      try {
        processor(manager)
      }
      catch (e: CancellationException) { throw e }
      catch (e: ProcessCanceledException) { throw e }
      catch (e: Throwable) {
        LOG.error("Cannot reload settings for ${manager.javaClass.name}", e)
      }
    }
  }

  final override suspend fun save() {
    var error: Throwable? = null
    val events = mutableListOf<VFileEvent>()

    for (registeredManager in managers) {
      try {
        registeredManager.saveImpl(events)
      }
      catch (e: CancellationException) { throw e }
      catch (e: ProcessCanceledException) { throw e }
      catch (e: Throwable) {
        error = addSuppressed(error, e)
      }
    }

    if (events.isNotEmpty()) {
      blockingContext {
        RefreshQueue.getInstance().processEvents(false, events)
      }
    }

    error?.let {
      throw it
    }
  }

  @Suppress("unused")
  private class ApplicationSchemeManagerFactory : SchemeManagerFactoryBase() {
    override val componentManager: ComponentManager
      get() = ApplicationManager.getApplication()

    override fun checkPath(originalPath: String): String {
      var path = super.checkPath(originalPath)
      if (path.startsWith(ROOT_CONFIG)) {
        path = path.substring(ROOT_CONFIG.length + 1)
        val message = "Path must not contains ROOT_CONFIG macro, corrected: $path"
        if (ApplicationManager.getApplication().isUnitTestMode) {
          throw AssertionError(message)
        }
        else {
          LOG.warn(message)
        }
      }
      return path
    }

    override fun pathToFile(path: String): Path =
      ApplicationManager.getApplication().stateStore.storageManager.expandMacro(ROOT_CONFIG).resolve(path)
  }

  @Suppress("unused")
  private class ProjectSchemeManagerFactory(private val project: Project) : SchemeManagerFactoryBase() {
    override val componentManager = project

    override fun createFileChangeSubscriber(): FileChangeSubscriber = { schemeManager ->
      if (!ApplicationManager.getApplication().isUnitTestMode || project.getUserData(LISTEN_SCHEME_VFS_CHANGES_IN_TEST_MODE) == true) {
        project.messageBus.simpleConnect().subscribe(VirtualFileManager.VFS_CHANGES, SchemeFileTracker(schemeManager, project))
      }
    }

    override fun pathToFile(path: String): Path {
      if (project.isDefault) {
        // no idea how to solve this issue (run SingleInspectionProfilePanelTest) in a quick and safe way
        return Path.of("__not_existent_path__")
      }

      val projectStore = project.stateStore as? IProjectStore
      val projectFileDir = projectStore?.directoryStorePath
      return when {
        projectFileDir != null -> projectFileDir.resolve(path)
        projectStore != null -> projectStore.projectBasePath.resolve(".$path")
        else -> Path.of(project.basePath!!, ".${path}")
      }
    }
  }

  @TestOnly
  @ApiStatus.Internal
  class TestSchemeManagerFactory(private val basePath: Path) : SchemeManagerFactoryBase() {
    override fun pathToFile(path: String): Path = basePath.resolve(path)
  }
}
