// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.serviceContainer.ComponentManagerImpl
import org.jdom.Element
import java.io.Writer
import java.nio.file.Path

private const val FILE_SPEC = "${APP_CONFIG}/project.default.xml"

internal class DefaultProjectStoreImpl(override val project: Project) : ComponentStoreWithExtraComponents() {
  // see note about default state in project store
  override val loadPolicy: StateLoadPolicy
    get() = if (ApplicationManager.getApplication().isUnitTestMode) StateLoadPolicy.NOT_LOAD else StateLoadPolicy.LOAD

  private val storage by lazy {
    val file = ApplicationManager.getApplication().stateStore.storageManager.expandMacro(FILE_SPEC)
    DefaultProjectStorage(file, FILE_SPEC, PathMacroManager.getInstance(project))
  }

  override val serviceContainer: ComponentManagerImpl
    get() = project as ComponentManagerImpl
  
  override val storageManager = object : StateStorageManager {
    override val componentManager: ComponentManager?
      get() = null

    override fun addStreamProvider(provider: StreamProvider, first: Boolean) { }

    override fun removeStreamProvider(aClass: Class<out StreamProvider>) { }

    override fun getStateStorage(storageSpec: Storage): DefaultProjectStorage = storage

    override fun expandMacro(collapsedPath: String) = throw UnsupportedOperationException()

    override fun getOldStorage(component: Any, componentName: String, operation: StateStorageOperation): DefaultProjectStorage = storage
  }

  override fun isUseLoadedStateAsExisting(storage: StateStorage) = false

  // don't want to optimize and use already loaded data - it will add unnecessary complexity and implementation-lock
  // (currently we store loaded archived state in memory, but later implementation can be changed)
  fun getStateCopy(): Element? = storage.loadLocalData()

  override fun getPathMacroManagerForDefaults(): PathMacroManager = PathMacroManager.getInstance(project)

  override fun <T> getStorageSpecs(component: PersistentStateComponent<T>, stateSpec: State, operation: StateStorageOperation): List<FileStorageAnnotation> =
    listOf(PROJECT_FILE_STORAGE_ANNOTATION)

  override fun setPath(path: Path) { }

  override fun toString() = "default project"

  private class DefaultProjectStorage(file: Path, fileSpec: String, pathMacroManager: PathMacroManager)
    : FileBasedStorage(file, fileSpec, "defaultProject", pathMacroManager.createTrackingSubstitutor(), RoamingType.DISABLED)
  {
    @Suppress("RedundantVisibilityModifier")
    public override fun loadLocalData(): Element? =
      try {
        super.loadLocalData()?.getChild("component")?.getChild("defaultProject")
      }
      catch (_: NullPointerException) {
        LOG.warn("Cannot read default project")
        null
      }

    override fun createSaveSession(states: StateMap): FileSaveSessionProducer =
      object : FileSaveSessionProducer(states, this) {
        override fun saveLocally(dataWriter: DataWriter?) {
          super.saveLocally(if (dataWriter == null) null else object : StringDataWriter() {
            override fun hasData(filter: DataWriterFilter): Boolean = dataWriter.hasData(filter)

            override fun write(writer: Writer, lineSeparator: String, filter: DataWriterFilter?) {
              val lineSeparatorWithIndent = "${lineSeparator}    "
              writer.append("<application>").append(lineSeparator)
              writer.append("""  <component name="ProjectManager">""")
              writer.append(lineSeparatorWithIndent)
              (dataWriter as StringDataWriter).write(writer, lineSeparatorWithIndent, filter)
              writer.append(lineSeparator)
              writer.append("  </component>").append(lineSeparator)
              writer.append("</application>")
            }
          })
        }
      }
  }
}
