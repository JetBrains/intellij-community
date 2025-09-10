// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.components.impl.stores.stateStore
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.platform.settings.SettingsController
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import java.io.InputStream
import java.io.Writer
import java.nio.file.Path

@ApiStatus.Internal
const val PROJECT_DEFAULT_FILE_NAME: String = StoragePathMacros.PROJECT_DEFAULT_FILE
@ApiStatus.Internal
const val PROJECT_DEFAULT_FILE_SPEC: String = "${APP_CONFIG}/${PROJECT_DEFAULT_FILE_NAME}"

internal class DefaultProjectStoreImpl(override val project: Project) : ComponentStoreWithExtraComponents() {
  // see note about default state in project store
  private val compoundStreamProvider = CompoundStreamProvider()

  override val allowSavingWithoutModifications: Boolean
    get() = true

  override val loadPolicy: StateLoadPolicy
    get() = if (ApplicationManager.getApplication().isUnitTestMode) StateLoadPolicy.NOT_LOAD else StateLoadPolicy.LOAD

  private val storage by lazy {
    val file = ApplicationManager.getApplication().stateStore.storageManager.expandMacro(PROJECT_DEFAULT_FILE_SPEC)
    DefaultProjectStorage(file, PROJECT_DEFAULT_FILE_SPEC, PathMacroManager.getInstance(project), compoundStreamProvider)
  }

  override val serviceContainer: ComponentManagerEx
    get() = project as ComponentManagerEx

  override val storageManager: StateStorageManager = object : StateStorageManager {
    override val componentManager: ComponentManager?
      get() = null

    override fun addStreamProvider(provider: StreamProvider, first: Boolean) {
      compoundStreamProvider.addStreamProvider(provider = provider, first = first)
    }

    override fun removeStreamProvider(aClass: Class<out StreamProvider>) {
      compoundStreamProvider.removeStreamProvider(aClass)
    }

    override fun getStateStorage(storageSpec: Storage) = storage

    override fun expandMacro(collapsedPath: String) = throw UnsupportedOperationException()

    override fun collapseMacro(path: String): String = throw UnsupportedOperationException()

    override val streamProvider: StreamProvider
      get() = compoundStreamProvider

    override fun getOldStorage(component: Any, componentName: String, operation: StateStorageOperation) = storage
  }

  override fun isUseLoadedStateAsExisting(storage: StateStorage) = false

  // don't want to optimize and use already loaded data - it will add unnecessary complexity and implementation-lock
  // (currently we store loaded archived state in memory, but later implementation can be changed)
  fun getStateCopy(): Element? = storage.loadLocalData()

  override fun getPathMacroManagerForDefaults(): PathMacroManager = PathMacroManager.getInstance(project)

  override fun <T> getStorageSpecs(component: PersistentStateComponent<T>, stateSpec: State, operation: StateStorageOperation): List<FileStorageAnnotation> {
    return listOf(PROJECT_FILE_STORAGE_ANNOTATION)
  }

  override fun setPath(path: Path) {}

  override fun toString(): String = "default project"

  private class DefaultProjectStorage(file: Path, fileSpec: String, pathMacroManager: PathMacroManager, streamProvider: StreamProvider) :
    FileBasedStorage(
      file = file,
      fileSpec = fileSpec,
      rootElementName = "defaultProject",
      pathMacroManager = pathMacroManager.createTrackingSubstitutor(),
      roamingType = RoamingType.DISABLED,
      provider = streamProvider,
    ) {
    override val controller: SettingsController?
      get() = null

    @Suppress("RedundantVisibilityModifier")
    public override fun loadLocalData(): Element? = postProcessLoadedData { super.loadLocalData() }

    override fun loadFromStreamProvider(stream: InputStream): Element? = postProcessLoadedData { super.loadFromStreamProvider(stream) }

    private fun postProcessLoadedData(elementProvider: () -> Element?): Element? {
      try {
        return elementProvider()?.getChild("component")?.getChild("defaultProject")
      }
      catch (_: NullPointerException) {
        LOG.warn("Cannot read default project")
        return null
      }
    }

    override fun createSaveSession(states: StateMap): FileSaveSessionProducer =
      object : FileSaveSessionProducer(storageData = states, storage = this) {
        override fun saveLocally(dataWriter: DataWriter?, useVfs: Boolean, events: MutableList<VFileEvent>?) {
          val dataWriter = if (dataWriter == null) null else object : StringDataWriter() {
            override fun hasData(filter: DataWriterFilter): Boolean = dataWriter.hasData(filter)

            override fun writeTo(writer: Writer, lineSeparator: String, filter: DataWriterFilter?) {
              val lineSeparatorWithIndent = "${lineSeparator}    "
              writer.append("<application>").append(lineSeparator)
              writer.append("""  <component name="ProjectManager">""").append(lineSeparatorWithIndent)
              (dataWriter as StringDataWriter).writeTo(writer, lineSeparatorWithIndent, filter)
              writer.append(lineSeparator)
              writer.append("  </component>").append(lineSeparator)
              writer.append("</application>")
            }
          }
          super.saveLocally(dataWriter, useVfs, events)
        }
      }
  }
}
