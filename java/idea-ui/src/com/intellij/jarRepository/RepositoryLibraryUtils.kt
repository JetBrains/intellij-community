// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.jarRepository

import com.intellij.configurationStore.deserialize
import com.intellij.configurationStore.serialize
import com.intellij.ide.JavaUiBundle
import com.intellij.ide.actions.ShowStructureSettingsAction
import com.intellij.jarRepository.RepositoryLibraryType.REPOSITORY_LIBRARY_KIND
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.JavadocOrderRootType
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.ui.OrderRoot
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryEditor
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsContexts.NotificationContent
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.util.containers.ContainerUtil
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.storage.EntityStorage
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.bridgeEntities.*
import com.intellij.workspaceModel.storage.toBuilder
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.concurrency.*
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryDescription
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryProperties
import org.jetbrains.jps.model.library.JpsMavenRepositoryLibraryDescriptor
import org.jetbrains.jps.model.library.JpsMavenRepositoryLibraryDescriptor.ArtifactVerification
import org.jetbrains.jps.model.serialization.library.JpsLibraryTableSerializer
import org.jetbrains.jps.util.JpsChecksumUtil
import org.jetbrains.jps.util.JpsPathUtil
import java.io.IOException
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * [RepositoryLibraryType] libraries utils.
 */
@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class RepositoryLibraryUtils(private val project: Project, private val cs: CoroutineScope) {
  companion object {
    private val logger = logger<RepositoryLibraryUtils>()

    @JvmStatic
    fun getInstance(project: Project): RepositoryLibraryUtils = project.service()

    @JvmStatic
    fun isVerifiableRootsChanged(editor: LibraryEditor, newRoots: Collection<OrderRoot>): Boolean {
      val oldPaths = editor.getUrls(OrderRootType.CLASSES).asSequence().map { JpsPathUtil.urlToOsPath(it) }.toSet()
      val newPaths = newRoots.asSequence().filter { it.type == OrderRootType.CLASSES }.map { JpsPathUtil.urlToOsPath(it.file.url) }.toSet()
      return oldPaths != newPaths
    }

    /* Reuse notifications group from [JarRepositoryManager] */
    private val NOTIFICATIONS_GROUP = JarRepositoryManager.GROUP

    private val CoroutineScope.progressSinkOrError: ProgressSink get() = requireNotNull(progressSink)
  }

  private var testCoroutineScope: CoroutineScope? = null
  private val myCoroutineScope: CoroutineScope get() = testCoroutineScope ?: cs

  /**
   * Should be used only in tests. Required to promote errors to tests via [testScope].
   */
  @TestOnly
  fun setTestCoroutineScope(testScope: CoroutineScope) {
    testCoroutineScope = testScope
  }

  @TestOnly
  fun resetTestCoroutineScope() {
    testCoroutineScope = null
  }

  /**
   * Tries to guess remote jar repository for each [RepositoryLibraryType] library and bind it to
   * the library if found. Global libraries are excluded.
   *
   * See [JpsMavenRepositoryLibraryDescriptor.getJarRepositoryId], [RemoteRepositoriesConfiguration].
   */
  fun guessAndBindRemoteRepositoriesModal() = BuildExtendedLibraryPropertiesJob(guessAndBindRemoteRepository = true,
                                                                                buildMissingChecksums = false,
                                                                                rebuildExistingChecksums = false,
                                                                                silentIfNoChanges = false)
    .runModal(JavaUiBundle.message("repository.library.utils.progress.title.guessing.remote.repos"))

  /**
   * Unbinds remote repositories from all the [RepositoryLibraryType] libraries of project. Global libraries are excluded.
   *
   * See [JpsMavenRepositoryLibraryDescriptor.getJarRepositoryId], [RemoteRepositoriesConfiguration].
   */
  fun unbindRemoteRepositoriesModal() = ClearExtendedLibraryPropertiesJob(unbindRemoteRepositories = true,
                                                                          disableEnabledChecksums = false)
    .runModal(JavaUiBundle.message("repository.library.utils.progress.title.unbind.remote.repos"))

  /**
   * Rebuild all existing SHA256 checksums for all [RepositoryLibraryType] libraries.
   *
   * Libraries with disabled checksums are excluded.
   * Global libraries are excluded.
   *
   * See [JpsMavenRepositoryLibraryDescriptor.ArtifactVerification], [JpsMavenRepositoryLibraryDescriptor.isVerifySha256Checksum].
   */
  fun rebuildExistingSha256ChecksumsBackground() = BuildExtendedLibraryPropertiesJob(guessAndBindRemoteRepository = false,
                                                                                     buildMissingChecksums = false,
                                                                                     rebuildExistingChecksums = true)
    .runBackground(JavaUiBundle.message("repository.library.utils.progress.title.building.sha256sum"))

  /**
   * Enable and build SHA256 checksums for all [RepositoryLibraryType] libraries for which checksums were not enabled.
   *
   * Global libraries are excluded.
   *
   * See [JpsMavenRepositoryLibraryDescriptor.ArtifactVerification], [JpsMavenRepositoryLibraryDescriptor.isVerifySha256Checksum].
   */
  fun buildMissingSha256ChecksumsBackground() = BuildExtendedLibraryPropertiesJob(guessAndBindRemoteRepository = false,
                                                                                  buildMissingChecksums = true,
                                                                                  rebuildExistingChecksums = false)
    .runBackground(JavaUiBundle.message("repository.library.utils.progress.title.building.sha256sum"))

  /**
   * Disable SHA256 checksum for all [RepositoryLibraryType] libraries. Global libraries are excluded.
   *
   * See [JpsMavenRepositoryLibraryDescriptor.ArtifactVerification], [JpsMavenRepositoryLibraryDescriptor.isVerifySha256Checksum].
   */
  fun removeSha256ChecksumsBackground() = ClearExtendedLibraryPropertiesJob(unbindRemoteRepositories = false,
                                                                            disableEnabledChecksums = true)
    .runBackground(JavaUiBundle.message("repository.library.utils.progress.title.removing.sha256sum"))

  /**
   * Check whether all libraries can be resolved in background, on finish shows notification with details.
   *
   * @return [Deferred] of `true` if all libraries can be resolved, [Deferred] of `false` if not.
   */
  fun checkAllLibrariesCanBeResolvedBackground(): Deferred<Boolean> = myCoroutineScope.async {
    withBackgroundProgressIndicator(
      project,
      JavaUiBundle.message("repository.library.utils.progress.title.all.libraries.resolution.check")
    ) {
      val snapshot = WorkspaceModel.getInstance(project).currentSnapshot
      checkLibrariesCanBeResolved(snapshot.entities(LibraryEntity::class.java), snapshot)
    }
  }

  /**
   * Build SHA256 checksums if [buildSha256Checksum]` == true`. Checksums are always rebuilt, event if ones exist.
   * Guess and bind remote repository if [guessAndBindRemoteRepository]` == true`. If bind repository already exists, it won't be changed.
   *
   * Expected to be used when new library is added/existing library is modified (ex. version) and its properties should be filled.
   *
   * Does nothing if [library] is not [RepositoryLibraryType], if [library] is global.
   */
  fun computePropertiesForLibraries(libraries: Set<LibraryEntity>,
                                    buildSha256Checksum: Boolean,
                                    guessAndBindRemoteRepository: Boolean): Job? {
    if (!buildSha256Checksum && !guessAndBindRemoteRepository) return null
    return BuildExtendedLibraryPropertiesJob(
      guessAndBindRemoteRepository = guessAndBindRemoteRepository,
      buildMissingChecksums = buildSha256Checksum,
      rebuildExistingChecksums = buildSha256Checksum,
      silentIfNoChanges = true,
      customFilter = { it in libraries }
    ).runBackground(JavaUiBundle.message("repository.library.utils..progress.title.libraries.changed"))
  }

  private fun LibrariesModificationJob.runBackground(title: @NlsContexts.ProgressTitle String) = myCoroutineScope.launch {
    withBackgroundProgressIndicator(project, title) { apply() }
  }

  private fun LibrariesModificationJob.runModal(title: @NlsContexts.ProgressTitle String) = myCoroutineScope.launch {
    withModalProgressIndicator(project, title) { apply() }
  }

  /**
   * Checks whether [libraries] can be resolved, show notification with result. Libraries properties used while check are resolved via
   * [LibraryEntity.symbolicId] with [EntityStorage.resolve].
   */
  private suspend fun checkLibrariesCanBeResolved(libraries: Sequence<LibraryEntity>, snapshot: EntityStorage) = coroutineScope {
    val failedList: MutableList<LibraryEntity> = CopyOnWriteArrayList()
    val result = libraries.mapNotNull { snapshot.resolve(it.symbolicId) }
      .map { lib -> lib.tryResolve().onError { failedList.add(lib) } }
      .toList()
      .collectResults(ignoreErrors = true).await() // We're collecting errors manually, fail silently

    if (failedList.isNotEmpty()) {
      val showStructureSettingsAction = ShowStructureSettingsAction().apply {
        templatePresentation.text = JavaUiBundle.message("repository.library.utils.notification.action.open.project.structure")
      }
      showNotification(JavaUiBundle.message("repository.library.utils.notification.content.libraries.resolve.check.fail",
                                            failedList.joinToString("") { "<br/>- ${it.getDisplayName(snapshot)}" }),
                       NotificationType.ERROR,
                       showStructureSettingsAction)
      return@coroutineScope false
    }

    showNotification(JavaUiBundle.message("repository.library.utils.notification.content.libraries.resolve.check.ok", result.size))
    return@coroutineScope true
  }

  /**
   * Returns [LibraryEntity.name] with library [ModuleEntity.name] appended if [library] is module library,
   * otherwise [LibraryEntity.name].
   */
  @NlsSafe
  private fun LibraryEntity.getDisplayName(snapshot: EntityStorage): String {
    val moduleId = (this.tableId as? LibraryTableId.ModuleLibraryTableId)?.moduleId
    val module = if (moduleId != null) snapshot.resolve(moduleId) else null
    return if (module == null) this.name else "${this.name} (${module.name})"
  }

  private fun LibraryEntity.tryResolve(): Promise<List<OrderRoot>> {
    val propertiesEntity = libraryProperties ?: return resolvedPromise()
    val properties = propertiesEntity.getPropertiesIfRepositoryLibrary() ?: return resolvedPromise()

    val downloadSources = roots.any { it.type == LibraryRootTypeId.SOURCES }
    val downloadJavadoc = roots.any { it.type.name == JavadocOrderRootType.getInstance().name() }

    return JarRepositoryManager.loadDependenciesAsync(project, properties, downloadSources, downloadJavadoc, null, null).thenAsync {
      if (it == null || it.isEmpty()) rejectedPromise() else resolvedPromise(it)
    }
  }

  private fun LibraryPropertiesEntity.getPropertiesIfRepositoryLibrary(): RepositoryLibraryProperties? {
    val propertiesXmlTag = this.propertiesXmlTag ?: return null
    if (REPOSITORY_LIBRARY_KIND.kindId == libraryType) {
      return deserialize(JDOMUtil.load(propertiesXmlTag))
    }
    return null
  }

  private fun LibraryPropertiesEntity.modifyProperties(builder: MutableEntityStorage, newProperties: RepositoryLibraryProperties) {
    val propertiesElement = serialize(newProperties)!!
    propertiesElement.name = JpsLibraryTableSerializer.PROPERTIES_TAG
    val xmlTag = JDOMUtil.writeElement(propertiesElement)

    if (xmlTag != propertiesXmlTag) {
      builder.modifyEntity(this) {
        propertiesXmlTag = xmlTag
      }
    }
  }

  private fun showNotification(content: @NotificationContent String,
                               type: NotificationType = NotificationType.INFORMATION,
                               action: AnAction? = null) {
    val notification = NOTIFICATIONS_GROUP.createNotification(JavaUiBundle.message("repository.library.utils.library.update.title"),
                                                              content, type)
    if (action != null) {
      notification.addAction(action)
    }
    Notifications.Bus.notify(notification, project)
  }

  private inner class BuildExtendedLibraryPropertiesJob(
    val guessAndBindRemoteRepository: Boolean,
    val buildMissingChecksums: Boolean,
    val rebuildExistingChecksums: Boolean,
    val silentIfNoChanges: Boolean = false,
    val customFilter: (entity: LibraryEntity) -> Boolean = { true }
  ) : LibrariesModificationJob() {
    private val availableRepositories = RemoteRepositoriesConfiguration.getInstance(project).repositories
    private val unresolvedLibrariesNames = mutableListOf<@NlsSafe String>()

    private val progressCounter = AtomicInteger(0)
    private val checksumsBuiltFlag = AtomicInteger(0)
    private val repositoriesChangedFlag = AtomicInteger(0)

    override fun filter(entity: LibraryPropertiesEntity, properties: RepositoryLibraryProperties): Boolean {
      if (properties.isEnableSha256Checksum && rebuildExistingChecksums || !properties.isEnableSha256Checksum && buildMissingChecksums) {
        // Save lib with problems, will cancel coroutine in afterFilter() with readable message
        if (!entity.library.isCompiledArtifactsResolved()) {
          unresolvedLibrariesNames.add(entity.library.getDisplayName(stateSnapshot))
        }
      }
      return customFilter(entity.library)
    }

    override suspend fun afterFilter() = coroutineScope {
      if (filteredProperties.isEmpty()) {
        if (!silentIfNoChanges) {
          showNotification(JavaUiBundle.message("repository.library.utils.notification.content.nothing.to.update"))
        }
        cancel()
      }

      if (unresolvedLibrariesNames.isNotEmpty()) {
        showLibraryArtifactsNotResolvedAndCancel()
      }
    }

    override suspend fun transform() = coroutineScope {
      progressSinkOrError.text(JavaUiBundle.message("repository.library.utils.progress.text.updating.repository.libraries"))
      progressSinkOrError.update(details = JavaUiBundle.message("repository.library.utils.progress.details.complete.for",
                                                                0, filteredProperties.size),
                                 fraction = 0.0)
      filteredProperties.map { (entity, properties) ->
        async(Dispatchers.IO) {
          if (properties.isEnableSha256Checksum && rebuildExistingChecksums ||
              !properties.isEnableSha256Checksum && buildMissingChecksums) {
            if (properties.rebuildChecksum(entity)) {
              checksumsBuiltFlag.set(1)
            }
          }

          if (guessAndBindRemoteRepository && properties.jarRepositoryId == null) {
            if (properties.tryGuessAndBindRemoteRepository()) {
              repositoriesChangedFlag.set(1)
            }
          }

          val currentCounter = progressCounter.incrementAndGet()
          progressSinkOrError.update(details = JavaUiBundle.message("repository.library.utils.progress.details.complete.for",
                                                                    currentCounter, filteredProperties.size),
                                     fraction = currentCounter.toDouble() / filteredProperties.size)
          entity to properties
        }
      }.awaitAll().forEach { (entity, properties) ->
        entity.modifyProperties(properties)
      }
    }

    override suspend fun onFinish() = coroutineScope {
      if (!builder.hasChanges()) {
        if (!silentIfNoChanges) {
          showNotification(JavaUiBundle.message("repository.library.utils.notification.content.nothing.to.update"))
        }
        return@coroutineScope
      }
      showNotification(JavaUiBundle.message("repository.library.utils.notification.content.library.properties.built",
                                            filteredProperties.size, checksumsBuiltFlag.get(), repositoriesChangedFlag.get()))

      if (repositoriesChangedFlag.get() != 0) {
        progressSinkOrError.update(JavaUiBundle.message("repository.library.utils.progress.checking.resolution",
                                                        filteredProperties.size, 1), "")
        checkLibrariesCanBeResolved(filteredProperties.asSequence().map { it.first.library }, builder)
      }
    }

    private fun CoroutineScope.showLibraryArtifactsNotResolvedAndCancel() {
      val displayed = ContainerUtil.getFirstItems(unresolvedLibrariesNames, 10)
      showNotification(JavaUiBundle.message("repository.library.utils.notification.content.unresolved.artifacts",
                                            displayed.joinToString { "<br/> - $it" },
                                            unresolvedLibrariesNames.size - displayed.size),
                       NotificationType.ERROR)
      cancel()
    }

    private fun LibraryEntity.getHashableRoots() = roots.asSequence().filter { it.type == LibraryRootTypeId.COMPILED }
    private fun LibraryEntity.isCompiledArtifactsResolved() = getHashableRoots().all { JpsPathUtil.urlToFile(it.url.url).exists() }

    private fun RepositoryLibraryProperties.rebuildChecksum(propertiesEntity: LibraryPropertiesEntity): Boolean {
      var verification = emptyList<ArtifactVerification>()
      if (version != RepositoryLibraryDescription.LatestVersionId &&
          version != RepositoryLibraryDescription.ReleaseVersionId &&
          !version.endsWith(RepositoryLibraryDescription.SnapshotVersionSuffix)) {

        val verifiableJars = propertiesEntity.library.getHashableRoots()
          .filter { it.type == LibraryRootTypeId.COMPILED }
          .map { JpsPathUtil.urlToFile(it.url.url) }

        verification = verifiableJars.map {
          require(it.exists()) { "Verifiable JAR not exists: $it" }
          val artifactFileUrl = VfsUtilCore.fileToUrl(it)
          val sha256sum = try {
            JpsChecksumUtil.getSha256Checksum(it.toPath())
          }
          catch (e: IOException) {
            logger.error(e)
            throw RuntimeException(e)
          }
          ArtifactVerification(artifactFileUrl, sha256sum)
        }.toList()
      }

      if (verification == artifactsVerification) {
        return false
      }
      artifactsVerification = verification
      return true
    }

    private suspend fun RepositoryLibraryProperties.tryGuessAndBindRemoteRepository(): Boolean {
      val description = RepositoryLibraryDescription.findDescription(this)
      val remoteRepositoryId = availableRepositories.firstOrNull {
        val versions = JarRepositoryManager.getAvailableVersions(project, description, Collections.singletonList(it)).await()
        versions.isNotEmpty() && versions.contains(this.version)
      }?.id
      jarRepositoryId = remoteRepositoryId
      return remoteRepositoryId != null
    }
  }

  private inner class ClearExtendedLibraryPropertiesJob(
    val unbindRemoteRepositories: Boolean,
    val disableEnabledChecksums: Boolean,
  ) : LibrariesModificationJob() {
    private val checksumsRemovedFlag = AtomicInteger(0)
    private val repositoriesUnbindFlag = AtomicInteger(0)

    override fun filter(entity: LibraryPropertiesEntity, properties: RepositoryLibraryProperties) =
      properties.isEnableSha256Checksum && disableEnabledChecksums || properties.jarRepositoryId != null && unbindRemoteRepositories

    override suspend fun transform() = coroutineScope {
      progressSinkOrError.text(JavaUiBundle.message("repository.library.utils.progress.text.updating.repository.libraries"))

      filteredProperties.forEach { (entity, properties) ->
        if (disableEnabledChecksums && properties.isEnableSha256Checksum) {
          properties.artifactsVerification = emptyList()
          checksumsRemovedFlag.set(1)
        }
        if (unbindRemoteRepositories && properties.jarRepositoryId != null) {
          properties.jarRepositoryId = null
          repositoriesUnbindFlag.set(1)
        }
        entity.modifyProperties(properties)
      }
    }

    override suspend fun onFinish() {
      if (!builder.hasChanges()) {
        showNotification(JavaUiBundle.message("repository.library.utils.notification.content.nothing.to.update"))
        return
      }
      showNotification(JavaUiBundle.message("repository.library.utils.notification.content.library.properties.cleared",
                                            filteredProperties.size, checksumsRemovedFlag.get(), repositoriesUnbindFlag.get()))
    }
  }

  private abstract inner class LibrariesModificationJob(private val includeGlobalLibs: Boolean = false) {
    protected val filteredProperties: MutableList<Pair<LibraryPropertiesEntity, RepositoryLibraryProperties>> = mutableListOf()

    private val workspaceModel = WorkspaceModel.getInstance(project)
    protected lateinit var stateSnapshot: EntityStorage
    protected lateinit var builder: MutableEntityStorage

    /**
     * Should return `true` if [entity] with [properties] are suitable for further [transform].
     * Filtered properties are stored in [filteredProperties].
     */
    protected abstract fun filter(entity: LibraryPropertiesEntity, properties: RepositoryLibraryProperties): Boolean

    /**
     * Invoked after [filter] and before [transform]. Can be used to cancel running process on some errors found while [filter].
     * By default, cancels job with message if [filteredProperties] is empty.
     */
    protected open suspend fun afterFilter(): Unit = coroutineScope {
      if (filteredProperties.isEmpty()) {
        showNotification(JavaUiBundle.message("repository.library.utils.notification.content.nothing.to.update"))
        cancel()
      }
    }

    /**
     * Invoked after [afterFilter]. It's expected it uses [filteredProperties] to perform changes under library properties and writes
     * these changes with [modifyProperties].
     */
    protected abstract suspend fun transform()

    /**
     * Invoked after all the modifications from [transform] made by [modifyProperties] are committed into project [WorkspaceModel].
     * Can be used to show some messages to user.
     */
    protected abstract suspend fun onFinish()

    protected fun LibraryPropertiesEntity.modifyProperties(newProperties: RepositoryLibraryProperties) =
      modifyProperties(builder, newProperties)

    /**
     * Starts a coroutine of all the steps from other methods. Should not be reused, create a new instance of [LibrariesModificationJob]
     * for each new use.
     */
    suspend fun apply() = coroutineScope {
      val job = launch {
        progressSinkOrError.text(JavaUiBundle.message("repository.library.utils.progress.text.collecting.libraries"))
        stateSnapshot = workspaceModel.currentSnapshot

        for (libraryPropertiesEntity in stateSnapshot.entities(LibraryPropertiesEntity::class.java)) {
          checkCancelled()

          if (includeGlobalLibs || libraryPropertiesEntity.library.tableId !is LibraryTableId.GlobalLibraryTableId) {
            val properties = libraryPropertiesEntity.getPropertiesIfRepositoryLibrary()
            if (properties != null && filter(libraryPropertiesEntity, properties)) {
              filteredProperties.add(libraryPropertiesEntity to properties)
            }
          }
        }

        progressSinkOrError.text(JavaUiBundle.message("repository.library.utils.progress.updating.libraries", 1))
        afterFilter()

        builder = stateSnapshot.toBuilder()
        transform()

        progressSinkOrError.text(JavaUiBundle.message("repository.library.utils.progress.saving.changes", 1))
        if (builder.hasChanges()) {
          withContext(Dispatchers.EDT) {
            WriteAction.run<RuntimeException> {
              workspaceModel.updateProjectModel("RepositoryLibraryUtils update") {
                it.addDiff(builder)
              }
            }
          }
        }
        onFinish()
      }
      job.invokeOnCompletion {
        when (it) {
          null, is CancellationException -> return@invokeOnCompletion
          else -> {
            showNotification(JavaUiBundle.message("repository.library.utils.notification.content.update.failed", it.localizedMessage),
                             NotificationType.ERROR)
            logger.error(it)
          }
        }
      }
      return@coroutineScope job
    }
  }
}
