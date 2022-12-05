// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.jarRepository

import com.intellij.configurationStore.deserialize
import com.intellij.configurationStore.serialize
import com.intellij.ide.JavaUiBundle
import com.intellij.jarRepository.RepositoryLibraryType.REPOSITORY_LIBRARY_KIND
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.checkCanceled
import com.intellij.openapi.progress.progressSink
import com.intellij.openapi.progress.withBackgroundProgressIndicator
import com.intellij.openapi.progress.withModalProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.LibraryKindRegistry
import com.intellij.openapi.roots.libraries.ui.OrderRoot
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.Strings.join
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
import org.jetbrains.concurrency.await
import org.jetbrains.concurrency.collectResults
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryDescription
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryProperties
import org.jetbrains.idea.maven.utils.library.RepositoryUtils
import org.jetbrains.jps.model.library.JpsMavenRepositoryLibraryDescriptor
import org.jetbrains.jps.model.library.JpsMavenRepositoryLibraryDescriptor.ArtifactVerification
import org.jetbrains.jps.model.serialization.library.JpsLibraryTableSerializer
import org.jetbrains.jps.util.JpsChecksumUtil
import org.jetbrains.jps.util.JpsPathUtil
import java.io.File
import java.io.IOException
import java.io.StringReader
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext

/**
 * [RepositoryLibraryType] libraries utils.
 */
@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class RepositoryLibraryUtils private constructor(private val project: Project, context: CoroutineContext) : Disposable {
  companion object {
    private val logger = logger<RepositoryLibraryUtils>()

    @JvmStatic
    fun getInstance(project: Project): RepositoryLibraryUtils = project.service()

    /**
     * Should be used only in tests. Required to promote errors to tests via [context].
     */
    @TestOnly
    fun createWithCustomContext(project: Project, context: CoroutineContext): RepositoryLibraryUtils = RepositoryLibraryUtils(project, context)

    @JvmStatic
    fun buildRepositoryLibraryArtifactsVerification(descriptor: JpsMavenRepositoryLibraryDescriptor,
                                                    roots: Collection<OrderRoot>): List<ArtifactVerification> {
      val usefulJars = roots.asSequence()
        .filter { it.type == OrderRootType.CLASSES }
        .map { JpsPathUtil.urlToFile(it.file.url) }
        .toList()

      return buildRepositoryLibraryArtifactsVerificationInternal(descriptor, usefulJars)
    }

    /* Reuse notifications group from [JarRepositoryManager] */
    private val NOTIFICATIONS_GROUP = JarRepositoryManager.GROUP

    private fun buildRepositoryLibraryArtifactsVerificationInternal(descriptor: JpsMavenRepositoryLibraryDescriptor,
                                                                    verifiableJars: List<File>): List<ArtifactVerification> {
      /* Build verification only for libraries with stable version and if verification is enabled */
      if (descriptor.version == RepositoryLibraryDescription.LatestVersionId ||
          descriptor.version == RepositoryLibraryDescription.ReleaseVersionId ||
          descriptor.version.endsWith(RepositoryLibraryDescription.SnapshotVersionSuffix) ||
          !descriptor.isVerifySha256Checksum) {
        return emptyList()
      }

      return verifiableJars.map {
        require(it.exists()) { "Verifiable JAR not exists" }
        val artifactFileUrl = VfsUtilCore.fileToUrl(it)
        val sha256sum = try {
          JpsChecksumUtil.getSha256Checksum(it.toPath())
        }
        catch (e: IOException) {
          logger.error(e)
          throw RuntimeException(e)
        }

        ArtifactVerification(artifactFileUrl, sha256sum)
      }
    }
  }

  private val myCoroutineScope: CoroutineScope = CoroutineScope(context)

  @Suppress("unused") // Used by getInstance() to create Project service
  constructor(project: Project) : this(project, SupervisorJob() + Dispatchers.Default)

  override fun dispose() = myCoroutineScope.cancel()

  /**
   * Tries to guess remote jar repository for each [RepositoryLibraryType] library and bind it to
   * the library if found. Global libraries are excluded.
   *
   * After finish, it reloads
   *
   * See [JpsMavenRepositoryLibraryDescriptor.getJarRepositoryId], [RemoteRepositoriesConfiguration].
   */
  fun guessAndBindRemoteRepositoriesModal() = GuessAndBindRemoteRepositoriesJob().runModal(
    JavaUiBundle.message("repository.library.utils.progress.title.guessing.remote.repos")
  )

  /**
   * Unbinds remote repositories from all the [RepositoryLibraryType] libraries of project. Global libraries are excluded.
   *
   * See [JpsMavenRepositoryLibraryDescriptor.getJarRepositoryId], [RemoteRepositoriesConfiguration].
   */
  fun unbindRemoteRepositoriesModal() = UnbindRemoteRepositoriesJob()
    .runModal(JavaUiBundle.message("repository.library.utils.progress.title.unbind.remote.repos"))

  /**
   * Rebuild all existing SHA256 checksums for all [RepositoryLibraryType] libraries.
   *
   * Libraries with disabled checksums are excluded.
   * Global libraries are excluded.
   *
   * See [JpsMavenRepositoryLibraryDescriptor.ArtifactVerification], [JpsMavenRepositoryLibraryDescriptor.myVerifySha256Checksum].
   */
  fun rebuildExistingSha256ChecksumsBackground() = BuildSha256SumJob(true, false).runBackground(
    JavaUiBundle.message("repository.library.utils.progress.title.building.sha256sum")
  )

  /**
   * Enable and build SHA256 checksums for all [RepositoryLibraryType] libraries for which checksums were not enabled.
   *
   * Global libraries are excluded.
   *
   * See [JpsMavenRepositoryLibraryDescriptor.ArtifactVerification], [JpsMavenRepositoryLibraryDescriptor.myVerifySha256Checksum].
   */
  fun buildMissingSha256ChecksumsBackground() = BuildSha256SumJob(false, true).runBackground(
    JavaUiBundle.message("repository.library.utils.progress.title.building.sha256sum")
  )


  /**
   * Disable SHA256 checksum for all [RepositoryLibraryType] libraries. Global libraries are excluded.
   *
   * See [JpsMavenRepositoryLibraryDescriptor.ArtifactVerification], [JpsMavenRepositoryLibraryDescriptor.myVerifySha256Checksum].
   */
  fun removeSha256ChecksumsBackground() = RemoveSha256ChecksumsJob().runBackground(
    JavaUiBundle.message("repository.library.utils.progress.title.removing.sha256sum")
  )

  /**
   * Reload all project libraries in background.
   */
  fun reloadAllRepositoryLibrariesBackground() = myCoroutineScope.launch {
    withBackgroundProgressIndicator(
      project,
      JavaUiBundle.message("repository.library.utils.progress.title.delete.and.reload.all.libs")
    ) { reloadAllRepositoryLibraries() }
  }

  private fun LibrariesModificationJob.runBackground(title: @NlsContexts.ProgressTitle String) = myCoroutineScope.launch {
    withBackgroundProgressIndicator(project, title) { apply() }
  }

  private fun LibrariesModificationJob.runModal(title: @NlsContexts.ProgressTitle String) =
    myCoroutineScope.launch {
      withModalProgressIndicator(project, title) { apply() }
    }

  private suspend fun reloadAllRepositoryLibraries() = coroutineScope {
    val failedList: MutableList<LibraryEx> = CopyOnWriteArrayList()
    val result = RepositoryLibrarySynchronizer.collectLibraries(project) {
      (it as? LibraryEx)?.properties is RepositoryLibraryProperties
    }
      .filterIsInstance<LibraryEx>()
      .map { lib -> RepositoryUtils.reloadDependencies(project, lib).onError { failedList.add(lib) } }
      .collectResults(ignoreErrors = true).await() // We're collecting errors manually, collect silently

    if (failedList.isNotEmpty()) {
      val failedLibrariesNames = failedList.map {
        val module = it.module
        if (module == null) "- ${it.name}" else "- ${it.name} (${module.name})"
      }
      Notifications.Bus.notify(NOTIFICATIONS_GROUP.createNotification(
        JavaUiBundle.message("repository.library.utils.notification.title"),
        JavaUiBundle.message("notification.content.libraries.reload.failed", join(failedLibrariesNames, "<p/>")),
        NotificationType.ERROR), project)
    }
    else {
      Notifications.Bus.notify(NOTIFICATIONS_GROUP.createNotification(
        JavaUiBundle.message("repository.library.utils.notification.title"),
        JavaUiBundle.message("notification.content.libraries.reloaded", result.size),
        NotificationType.INFORMATION), project)
    }
  }

  private inner class GuessAndBindRemoteRepositoriesJob : LibrariesModificationJob() {
    private val availableRepositories = RemoteRepositoriesConfiguration.getInstance(project).repositories
    private val progressCounter = AtomicInteger(0)
    override suspend fun filter(entity: LibraryPropertiesEntity, properties: RepositoryLibraryProperties) =
      properties.jarRepositoryId == null

    override suspend fun transform(): Unit = coroutineScope {
      progressSink!!.update(details = JavaUiBundle.message("repository.library.utils.progress.details.complete.for",
                                                           0, filteredProperties.size),
                            fraction = 0.0)
      filteredProperties.map { (entity, properties) ->
        async(Dispatchers.IO) {
          properties.jarRepositoryId = tryGuessRemoteRepositoryId(project, properties, availableRepositories)
          val currentCounter = progressCounter.incrementAndGet()
          progressSink!!.update(details = JavaUiBundle.message("repository.library.utils.progress.details.complete.for",
                                                               currentCounter, filteredProperties.size),
                                fraction = currentCounter.toDouble() / filteredProperties.size)
          entity to properties
        }
      }.awaitAll().forEach { (entity, properties) ->
        entity.modifyProperties(properties)
      }
    }

    override suspend fun onFinish() {
      val notification = NOTIFICATIONS_GROUP.createNotification(
        JavaUiBundle.message("repository.library.utils.notification.title"),
        JavaUiBundle.message("repository.library.utils.notification.content.repositories.guessed", filteredProperties.size),
        NotificationType.INFORMATION)

      notification.addAction(NotificationAction.createExpiring(
        JavaUiBundle.message("repository.library.utils.notification.action.title.reload.all.libraries")) { _, _ ->
        reloadAllRepositoryLibrariesBackground()
      })
      Notifications.Bus.notify(notification, project)
    }

    private suspend fun tryGuessRemoteRepositoryId(project: Project,
                                                   properties: RepositoryLibraryProperties,
                                                   remoteRepositories: List<RemoteRepositoryDescription>): String? {
      val description = RepositoryLibraryDescription.findDescription(properties)
      return remoteRepositories.firstOrNull {
        val versions = JarRepositoryManager.getAvailableVersions(project, description, Collections.singletonList(it)).await()
        versions.isNotEmpty() && versions.contains(properties.version)
      }?.id
    }
  }

  private inner class UnbindRemoteRepositoriesJob : LibrariesModificationJob() {
    override suspend fun filter(entity: LibraryPropertiesEntity, properties: RepositoryLibraryProperties) =
      properties.jarRepositoryId != null

    override suspend fun afterFilter(): Unit = coroutineScope {
      super.afterFilter()
      progressSink!!.details(details = JavaUiBundle.message("repository.library.utils.progress.details.updating.libraries"))
    }

    override suspend fun transform() = filteredProperties.forEach { (propertiesEntity, properties) ->
      properties.unbindRemoteRepository()
      propertiesEntity.modifyProperties(properties)
    }
  }

  private inner class BuildSha256SumJob(private val rebuildExistingChecksums: Boolean,
                                        private val enableIfChecksumDisabled: Boolean) : LibrariesModificationJob() {
    private val unresolvedLibrariesIds = mutableListOf<@NlsSafe String>()

    override suspend fun filter(entity: LibraryPropertiesEntity, properties: RepositoryLibraryProperties): Boolean {
      if (properties.isEnableSha256Checksum && rebuildExistingChecksums ||
          !properties.isEnableSha256Checksum && enableIfChecksumDisabled) {
        if (!entity.library.isCompiledArtifactsResolved()) {
          unresolvedLibrariesIds.add(properties.mavenId) // Save problem lib, will cancel coroutine in afterFilter() with readable message
        }
        return true
      }
      return false
    }

    override suspend fun afterFilter(): Unit = coroutineScope {
      super.afterFilter()
      if (unresolvedLibrariesIds.isNotEmpty()) {
        showLibraryArtifactsNotResolvedAndCancel()
      }
      progressSink!!.details(details = JavaUiBundle.message("repository.library.utils.progress.details.building.checksums"))
    }

    override suspend fun transform() = coroutineScope {
      filteredProperties.map { (propertiesEntity, properties) ->
        async(Dispatchers.IO) { propertiesEntity to rebuildChecksum(propertiesEntity, properties) }
      }.awaitAll().forEach { (propertiesEntity, properties) ->
        propertiesEntity.modifyProperties(properties)
      }
    }

    private fun LibraryEntity.getHashableRoots() = roots.asSequence().filter { it.type == LibraryRootTypeId.COMPILED }
    private fun LibraryEntity.isCompiledArtifactsResolved() = getHashableRoots().all { JpsPathUtil.urlToFile(it.url.url).exists() }

    private fun CoroutineScope.showLibraryArtifactsNotResolvedAndCancel() {
      val displayed = ContainerUtil.getFirstItems(unresolvedLibrariesIds, 10)
      val leftNotDisplayed = unresolvedLibrariesIds.size - displayed.size
      val displayedString = displayed.joinToString(separator = "<p/>")

      Notifications.Bus.notify(NOTIFICATIONS_GROUP.createNotification(
        JavaUiBundle.message("repository.library.utils.notification.title"),
        JavaUiBundle.message("repository.library.utils.notification.content.unresolved.artifacts", displayedString, leftNotDisplayed),
        NotificationType.ERROR), project)
      cancel()
    }

    /**
     * Updates properties in-place
     */
    private fun rebuildChecksum(propertiesEntity: LibraryPropertiesEntity,
                                properties: RepositoryLibraryProperties): RepositoryLibraryProperties {
      val verifiableJars = propertiesEntity.library.getHashableRoots()
        .filter { it.type == LibraryRootTypeId.COMPILED }
        .map { JpsPathUtil.urlToFile(it.url.url) }
        .toList()

      properties.isEnableSha256Checksum = true
      properties.artifactsVerification = buildRepositoryLibraryArtifactsVerificationInternal(properties.repositoryLibraryDescriptor,
                                                                                             verifiableJars)
      return properties
    }
  }

  private inner class RemoveSha256ChecksumsJob : LibrariesModificationJob() {
    override suspend fun filter(entity: LibraryPropertiesEntity, properties: RepositoryLibraryProperties) =
      properties.isEnableSha256Checksum

    override suspend fun afterFilter(): Unit = coroutineScope {
      super.afterFilter()
      progressSink!!.details(details = JavaUiBundle.message("repository.library.utils.progress.details.updating.libraries"))
    }

    override suspend fun transform() {
      filteredProperties.forEach { (entity, properties) ->
        properties.isEnableSha256Checksum = false
        properties.artifactsVerification = emptyList()
        entity.modifyProperties(properties)
      }
    }
  }

  private abstract inner class LibrariesModificationJob(private val includeGlobalLibs: Boolean = false) {
    protected val filteredProperties: MutableList<Pair<LibraryPropertiesEntity, RepositoryLibraryProperties>> = mutableListOf()

    private val workspaceModel = WorkspaceModel.getInstance(project)
    private val libraryKindRegistry = LibraryKindRegistry.getInstance()
    private lateinit var stateSnapshot: EntityStorage
    private lateinit var builder: MutableEntityStorage

    /**
     * Should return `true` if [entity] with [properties] are suitable for further [transform].
     * Filtered properties are stored in [filteredProperties].
     */
    protected abstract suspend fun filter(entity: LibraryPropertiesEntity, properties: RepositoryLibraryProperties): Boolean

    /**
     * Invoked after [filter] and before [transform]. Can be used to cancel running process on some errors found while [filter].
     * By default, cancels job with message if [filteredProperties] is empty.
     */
    protected open suspend fun afterFilter(): Unit = coroutineScope {
      if (filteredProperties.isEmpty()) {
        Notifications.Bus.notify(NOTIFICATIONS_GROUP.createNotification(
          JavaUiBundle.message("repository.library.utils.notification.title"),
          JavaUiBundle.message("repository.library.utils.notification.content.nothing.to.update"),
          NotificationType.INFORMATION), project)
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
    protected open suspend fun onFinish() {
      Notifications.Bus.notify(NOTIFICATIONS_GROUP.createNotification(
        JavaUiBundle.message("repository.library.utils.notification.title"),
        JavaUiBundle.message("repository.library.utils.notification.content.libraries.updated", filteredProperties.size),
        NotificationType.INFORMATION), project)
    }

    /**
     *
     */
    protected fun LibraryPropertiesEntity.modifyProperties(newProperties: RepositoryLibraryProperties) {
      val propertiesElement = serialize(newProperties)!!
      propertiesElement.name = JpsLibraryTableSerializer.PROPERTIES_TAG
      val xmlTag = JDOMUtil.writeElement(propertiesElement)

      builder.modifyEntity(this) {
        propertiesXmlTag = xmlTag
      }
    }

    /**
     * Starts a coroutine of all the steps from other methods. Should not be reused, create a new instance of [LibrariesModificationJob]
     * for each new use.
     */
    suspend fun apply() = coroutineScope {
      val job = launch {
        progressSink!!.text(JavaUiBundle.message("repository.library.utils.progress.text.collecting.libraries"))
        stateSnapshot = workspaceModel.entityStorage.current

        for (libraryPropertiesEntity in stateSnapshot.entities(LibraryPropertiesEntity::class.java)) {
          checkCanceled()

          if (includeGlobalLibs || !libraryPropertiesEntity.library.isGlobal()) {
            val properties = libraryPropertiesEntity.getPropertiesIfRepositoryLibrary()
            if (properties != null && filter(libraryPropertiesEntity, properties)) {
              filteredProperties.add(libraryPropertiesEntity to properties)
            }
          }
        }

        progressSink!!.text(JavaUiBundle.message("repository.library.utils.progress.text.updating.libraries"))
        afterFilter()

        builder = stateSnapshot.toBuilder()
        transform()

        progressSink!!.text(JavaUiBundle.message("repository.library.utils.progress.text.saving.changes"))
        withContext(Dispatchers.EDT) {
          WriteAction.run<RuntimeException> {
            workspaceModel.updateProjectModel("Build SHA256SUM for each library") {
              it.addDiff(builder)
            }
          }
        }
        onFinish()
      }
      job.invokeOnCompletion {
        when (it) {
          null, is CancellationException -> return@invokeOnCompletion
          else -> {
            Notifications.Bus.notify(NOTIFICATIONS_GROUP.createNotification(
              JavaUiBundle.message("repository.library.utils.notification.title"),
              JavaUiBundle.message("repository.library.utils.notification.content.update.failed", it.localizedMessage),
              NotificationType.ERROR), project)
            logger.error(it)
          }
        }
      }
      return@coroutineScope job
    }

    private fun LibraryPropertiesEntity.getPropertiesIfRepositoryLibrary(): RepositoryLibraryProperties? {
      val propertiesXmlTag = this.propertiesXmlTag ?: return null
      if (REPOSITORY_LIBRARY_KIND.equals(libraryKindRegistry.findKindById(this.libraryType))) {
        return deserialize<RepositoryLibraryProperties>(JDOMUtil.load(StringReader(propertiesXmlTag)))
      }
      return null
    }

    private fun LibraryEntity.isGlobal() = tableId is LibraryTableId.GlobalLibraryTableId
  }
}
