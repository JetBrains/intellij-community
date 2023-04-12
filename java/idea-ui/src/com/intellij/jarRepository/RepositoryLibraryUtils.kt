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
  fun guessAndBindRemoteRepositoriesBackground(): Deferred<Unit> {
    return runBackground(JavaUiBundle.message("repository.library.utils.progress.title.binding.remote.repos")) {
      BuildExtendedLibraryPropertiesJob(buildMissingChecksums = false, bindRepositories = true, disableInfoNotifications = false).run()
    }
  }

  /**
   * Unbinds remote repositories from all the [RepositoryLibraryType] libraries of project. Global libraries are excluded.
   *
   * See [JpsMavenRepositoryLibraryDescriptor.getJarRepositoryId], [RemoteRepositoriesConfiguration].
   */
  fun unbindRemoteRepositoriesBackground(): Deferred<Unit> {
    return runBackground(JavaUiBundle.message("repository.library.utils.progress.title.unbinding.remote.repos")) {
      ClearExtendedLibraryPropertiesJob(unbindRepositories = true, removeExistingChecksums = false, disableInfoNotifications = false).run()
    }
  }

  /**
   * Enable and build SHA256 checksums for all [RepositoryLibraryType] libraries for which checksums were not enabled.
   *
   * Global libraries are excluded.
   *
   * See [JpsMavenRepositoryLibraryDescriptor.ArtifactVerification], [JpsMavenRepositoryLibraryDescriptor.isVerifySha256Checksum].
   */
  fun buildMissingSha256ChecksumsBackground(): Deferred<Unit> {
    return runBackground(JavaUiBundle.message("repository.library.utils.progress.title.building.sha256sum")) {
      BuildExtendedLibraryPropertiesJob(buildMissingChecksums = true, bindRepositories = false, disableInfoNotifications = false).run()
    }
  }

  /**
   * Disable SHA256 checksum for all [RepositoryLibraryType] libraries. Global libraries are excluded.
   *
   * See [JpsMavenRepositoryLibraryDescriptor.ArtifactVerification], [JpsMavenRepositoryLibraryDescriptor.isVerifySha256Checksum].
   */
  fun removeSha256ChecksumsBackground(): Deferred<Unit> {
    return runBackground(JavaUiBundle.message("repository.library.utils.progress.title.removing.sha256sum")) {
      ClearExtendedLibraryPropertiesJob(removeExistingChecksums = true, unbindRepositories = false, disableInfoNotifications = false).run()
    }
  }

  fun resolveAllBackground(): Deferred<Boolean> {
    return runBackground(JavaUiBundle.message("repository.library.utils.progress.title.resolving.all.libraries")) {
      val snapshot = WorkspaceModel.getInstance(project).currentSnapshot
      val libraries = snapshot.entities(LibraryEntity::class.java).toList()
      val failedToResolve = resolve(libraries.asSequence(), reportProgress = true)
      if (failedToResolve.isEmpty()) {
        showNotification(JavaUiBundle.message("repository.library.utils.notification.content.libraries.resolve.success", libraries.size),
                         NotificationType.INFORMATION)
      }
      else {
        logger.info("resolveAllBackground complete, failed to resolve ${failedToResolve.size} libraries")
        showFailedToResolveNotification(failedToResolve, snapshot) { libsList, leftNotDisplayedSize ->
          JavaUiBundle.message("repository.library.utils.notification.content.libraries.resolve.fail", libsList, leftNotDisplayedSize)
        }
      }

      failedToResolve.isEmpty()
    }
  }

  /**
   * Build SHA256 checksums if [buildSha256Checksum]` == true`. Checksums are always rebuilt, event if ones exist.
   * Guess and bind remote repository if [guessAndBindRemoteRepository]` == true`. If bind repository already exists, it won't be changed.
   *
   * Expected to be used when a new library is added/existing library is modified (ex. version) and its properties should be filled.
   *
   * Does nothing if [library] is not [RepositoryLibraryType], if [library] is global.
   */
  fun computeExtendedPropertiesFor(libraries: Set<LibraryEntity>,
                                   buildSha256Checksum: Boolean,
                                   guessAndBindRemoteRepository: Boolean): Job? {
    if (!buildSha256Checksum && !guessAndBindRemoteRepository) {
      return null
    }

    return runBackground(JavaUiBundle.message("repository.library.utils.progress.title.libraries.changed")) {
      BuildExtendedLibraryPropertiesJob(buildMissingChecksums = buildSha256Checksum,
                                        bindRepositories = guessAndBindRemoteRepository,
                                        disableInfoNotifications = true,
                                        preFilter = { it in libraries }).run()
    }
  }


  private fun <T> runBackground(title: @NlsContexts.ProgressTitle String, action: suspend () -> T): Deferred<T> {
    val deferred = myCoroutineScope.async {
      withBackgroundProgress(project, title) {
        withRawProgressReporter { action() }
      }
    }

    deferred.invokeOnCompletion { error ->
      when (error) {
        null, is CancellationException -> return@invokeOnCompletion
        else -> logger.error("Operation '${title}' failed", error)
      }
    }
    return deferred
  }


  /**
   * @return A list of library entities failed to resolve or an empty list if resolution is successful.
   */
  private suspend fun resolve(libraries: Sequence<LibraryEntity>, reportProgress: Boolean): List<LibraryEntity> = coroutineScope {
    val librariesAsList = libraries.toList()
    if (librariesAsList.isEmpty()) return@coroutineScope emptyList<LibraryEntity>()

    val total = librariesAsList.size
    val completeCounter = AtomicInteger(0)
    if (reportProgress) updateProgressDetailsFraction(completeCounter, total)

    val failedList: MutableList<LibraryEntity> = CopyOnWriteArrayList()
    librariesAsList.map { lib ->
      val entity = lib.libraryProperties ?: return@map resolvedPromise()
      val properties = entity.toRepositoryLibraryProperties() ?: return@map resolvedPromise()

      val downloadSources = lib.roots.any { it.type == LibraryRootTypeId.SOURCES }
      val downloadJavadoc = lib.roots.any { it.type.name == JavadocOrderRootType.getInstance().name() }

      val promise =
        JarRepositoryManager.loadDependenciesAsync(project, properties, downloadSources, downloadJavadoc, null, null).thenAsync {
          if (it == null || it.isEmpty()) rejectedPromise() else resolvedPromise(it)
        }
      promise.onError { failedList.add(lib) }
      if (reportProgress) promise.onProcessed { updateProgressDetailsFraction(completeCounter, total) }
      promise
    }.collectResults(ignoreErrors = true).await() // We're collecting errors manually, fail silently

    if (reportProgress) resetProgressDetailsFraction()
    return@coroutineScope failedList
  }

  /**
   * [notificationContentProvider] consumes a lines list of unresolved libraries names (max 10 lines) and number of libraries not included
   * in the list (if more than 10 libraries are unresolved)
   */
  private fun showFailedToResolveNotification(
    libraries: List<LibraryEntity>,
    snapshot: EntityStorage,
    notificationContentProvider: (String, Int) -> @NotificationContent String,
  ) {
    val libNames = libraries.map {
      // for module libraries append module name in braces to simplify search
      val moduleId = (it.tableId as? LibraryTableId.ModuleLibraryTableId)?.moduleId
      val module = if (moduleId != null) snapshot.resolve(moduleId) else null
      if (module == null) it.name else "${it.name} (${module.name})"
    }

    val displayed = ContainerUtil.getFirstItems(libNames, 10)

    val showStructureSettingsAction = ShowStructureSettingsAction().apply {
      templatePresentation.text = JavaUiBundle.message("repository.library.utils.notification.action.open.project.structure")
    }

    val humanReadableFailedList = displayed.joinToString { "<br/> - $it" }
    showNotification(notificationContentProvider(humanReadableFailedList, libNames.size - displayed.size), NotificationType.ERROR,
                     action = showStructureSettingsAction)
  }


  private fun updateEntityIfPropertiesChanged(builder: MutableEntityStorage,
                                              newProperties: RepositoryLibraryProperties,
                                              entity: LibraryPropertiesEntity): Boolean {
    val element = checkNotNull(serialize(newProperties))
    element.name = JpsLibraryTableSerializer.PROPERTIES_TAG
    val newXmlTag = JDOMUtil.writeElement(element)

    if (entity.propertiesXmlTag == newXmlTag) return false

    builder.modifyEntity(entity) {
      propertiesXmlTag = newXmlTag
    }
    return true
  }

  private fun showNotification(content: @NotificationContent String,
                               type: NotificationType,
                               isInfoNotificationDisabled: Boolean = false,
                               action: AnAction? = null) {
    if (isInfoNotificationDisabled && type == NotificationType.INFORMATION) return

    /* Reuse [JarRepositoryManager]'s notification group */
    val notification = JarRepositoryManager.GROUP.createNotification(
      JavaUiBundle.message("repository.library.utils.library.update.title"),
      content,
      type
    )
    if (action != null) notification.addAction(action)
    Notifications.Bus.notify(notification, project)
  }

  private suspend fun commitBuilderIfModified(workspaceModel: WorkspaceModel, builder: MutableEntityStorage) {
    if (!builder.hasChanges()) return

    withContext(Dispatchers.EDT) {
      WriteAction.run<Throwable> {
        workspaceModel.updateProjectModel("RepositoryLibraryUtils update") {
          it.addDiff(builder)
        }
      }
    }
  }

  private fun CoroutineScope.updateProgressDetailsFraction(counter: AtomicInteger, total: Int) {
    val current = counter.get()
    rawProgressReporterOrError.details(JavaUiBundle.message("repository.library.utils.progress.details.complete.for", current, total))
    rawProgressReporterOrError.fraction(current.toDouble() / total)
    counter.incrementAndGet()
  }

  private fun CoroutineScope.resetProgressDetailsFraction() {
    rawProgressReporterOrError.details(null)
    rawProgressReporterOrError.fraction(null)
  }

  private inner class BuildExtendedLibraryPropertiesJob(
    private val buildMissingChecksums: Boolean,
    private val bindRepositories: Boolean,
    private val disableInfoNotifications: Boolean,
    private val preFilter: (LibraryEntity) -> Boolean = { true }
  ) {

    suspend fun run() = coroutineScope {
      if (!buildMissingChecksums && !bindRepositories) return@coroutineScope
      logger.info("Building libraries properties started: buildMissingChecksums=$buildMissingChecksums, bindRepositories=$bindRepositories")

      val repositoriesConfiguration = RemoteRepositoriesConfiguration.getInstance(project)
      val repositories = repositoriesConfiguration.repositories
      val workspaceModel = WorkspaceModel.getInstance(project)
      val snapshot = workspaceModel.currentSnapshot

      val entities = snapshot.entities(LibraryPropertiesEntity::class.java).filter {
        preFilter(it.library) && it.library.tableId !is LibraryTableId.GlobalLibraryTableId && it.isRepositoryLibraryProperties()
      }.toList()

      if (entities.isEmpty()) {
        logger.info("Building libraries properties complete: nothing to update")
        showNotification(JavaUiBundle.message("repository.library.utils.notification.content.library.properties.built", 0),
                         NotificationType.INFORMATION, disableInfoNotifications)
        return@coroutineScope
      }

      // Try to resolve libraries with missing roots silently
      rawProgressReporterOrError.text(JavaUiBundle.message("repository.library.utils.progress.text.resolving.before.update"))
      logger.info("Building libraries properties progressed: library list collected, resolving before update")
      if (!tryResolveLibraries(entities.asSequence(), snapshot, afterPropertiesUpdate = false)) {
        logger.info("Building libraries properties progressed: resolving before update failed, cancelling operation")
        return@coroutineScope
      }

      logger.info("Building libraries properties progressed: resolving before update complete, computing properties")
      rawProgressReporterOrError.text(JavaUiBundle.message("repository.library.utils.progress.text.computing.properties"))
      val builder = snapshot.toBuilder()

      val updatedCounter = AtomicInteger(0)
      val progressCounter = AtomicInteger(0)
      val progressTotal = entities.size
      updateProgressDetailsFraction(progressCounter, progressTotal)

      entities.map { entity ->
        async(Dispatchers.IO) {
          val properties = entity.toRepositoryLibraryProperties()!!
          val checksumsUpdated = buildMissingChecksums && buildSha256Checksum(entity, properties)
          val bindRepoUpdated = bindRepositories && tryGuessAndBindRemoteRepository(repositories, properties)
          if (checksumsUpdated || bindRepoUpdated) updatedCounter.incrementAndGet()
          updateProgressDetailsFraction(progressCounter, progressTotal)
          entity to properties
        }
      }.awaitAll().forEach { (entity, properties) ->
        updateEntityIfPropertiesChanged(builder, properties, entity)
      }

      resetProgressDetailsFraction()
      rawProgressReporterOrError.text(JavaUiBundle.message("repository.library.utils.progress.text.saving.changes"))
      commitBuilderIfModified(workspaceModel, builder)
      logger.info("Building libraries properties progressed: computing properties complete, ${updatedCounter.get()} libraries changed")

      if (updatedCounter.get() == 0) {
        logger.info("Building libraries properties complete: no updates")
        showNotification(
          JavaUiBundle.message("repository.library.utils.notification.content.library.properties.built", updatedCounter.get()),
          NotificationType.INFORMATION, disableInfoNotifications)
        return@coroutineScope
      }

      if (!bindRepositories) {
        logger.info("Building libraries properties complete: ${updatedCounter.get()} libraries updated successfully")
        showNotification(
          JavaUiBundle.message("repository.library.utils.notification.content.library.properties.built", updatedCounter.get()),
          NotificationType.INFORMATION, disableInfoNotifications)
        return@coroutineScope
      }

      rawProgressReporterOrError.text(JavaUiBundle.message("repository.library.utils.progress.text.verifying.resolution.after.update"))
      logger.info("Building libraries properties progressed: started checking resolution after update")
      // Check libraries' resolution with updated properties -> refresh required
      val snapshotAfterUpdate = workspaceModel.currentSnapshot
      val entitiesAfterUpdate = entities.mapNotNull { snapshotAfterUpdate.resolve(it.library.symbolicId)?.libraryProperties }

      if (tryResolveLibraries(entitiesAfterUpdate.asSequence(), snapshotAfterUpdate, afterPropertiesUpdate = true)) {
        logger.info("Building libraries properties complete: ${updatedCounter.get()} libraries updated successfully")
        showNotification(
          JavaUiBundle.message("repository.library.utils.notification.content.library.properties.built", updatedCounter.get()),
          NotificationType.INFORMATION, disableInfoNotifications)
      }
      else {
        logger.info("Building libraries properties progressed: verifying resolution after update complete, resolution failed")
      }
    }

    private fun LibraryEntity.getHashableRoots() =
      roots.asSequence().filter { it.type == LibraryRootTypeId.COMPILED }.map { JpsPathUtil.urlToFile(it.url.url) }

    private fun LibraryEntity.isCompiledArtifactsResolved() = getHashableRoots().all { it.exists() }

    private suspend fun tryResolveLibraries(entities: Sequence<LibraryPropertiesEntity>,
                                            snapshot: EntityStorage,
                                            afterPropertiesUpdate: Boolean): Boolean {
      // re-resolve libraries before update to ensure compile artifacts present and after update to ensure the library can be
      // resolved from bind repository
      val failedList =
        if (afterPropertiesUpdate) resolve(entities.map { it.library }, reportProgress = true)
        else resolve(entities.map { it.library }.filter { !it.isCompiledArtifactsResolved() }, reportProgress = true)

      if (failedList.isNotEmpty()) showFailedToResolveNotification(failedList, snapshot) { libsList, leftNotDisplayedSize ->
        if (afterPropertiesUpdate) JavaUiBundle.message(
          "repository.library.utils.notification.content.libraries.resolve.fail.after.update", libsList, leftNotDisplayedSize
        )
        else JavaUiBundle.message(
          "repository.library.utils.notification.content.libraries.resolve.fail.before.update", libsList, leftNotDisplayedSize
        )
      }
      return failedList.isEmpty()
    }

    private fun buildSha256Checksum(entity: LibraryPropertiesEntity, properties: RepositoryLibraryProperties): Boolean {
      if (properties.isEnableSha256Checksum ||
          properties.version == RepositoryLibraryDescription.LatestVersionId ||
          properties.version == RepositoryLibraryDescription.ReleaseVersionId ||
          properties.version.endsWith(RepositoryLibraryDescription.SnapshotVersionSuffix)) return false

      val verifiableJars = entity.library.getHashableRoots().toList()

      properties.artifactsVerification = verifiableJars.map {
        require(it.exists()) { "Verifiable JAR not exists: $it" }
        val artifactFileUrl = VfsUtilCore.fileToUrl(it)
        val sha256sum = try {
          JpsChecksumUtil.getSha256Checksum(it.toPath())
        }
        catch (e: IOException) {
          logger.error("Failed to build SHA256 checksum for $artifactFileUrl", e)
          throw RuntimeException(e)
        }
        ArtifactVerification(artifactFileUrl, sha256sum)
      }.toList()

      return true
    }

    private suspend fun tryGuessAndBindRemoteRepository(repositories: List<RemoteRepositoryDescription>,
                                                        properties: RepositoryLibraryProperties): Boolean {
      if (properties.jarRepositoryId != null) return false

      val description = RepositoryLibraryDescription.findDescription(properties)
      properties.jarRepositoryId = repositories.firstOrNull {
        val versions = JarRepositoryManager.getAvailableVersions(project, description, listOf(it)).await()
        versions.isNotEmpty() && versions.contains(properties.version)
      }?.id

      return properties.jarRepositoryId != null
    }
  }


  private inner class ClearExtendedLibraryPropertiesJob(
    private val removeExistingChecksums: Boolean,
    private val unbindRepositories: Boolean,
    private val disableInfoNotifications: Boolean
  ) {

    suspend fun run() = coroutineScope {
      if (!removeExistingChecksums && !unbindRepositories) return@coroutineScope
      logger.info("Clear libraries properties started: " +
                  "removeExistingChecksums=$removeExistingChecksums, unbindRepositories=$unbindRepositories")

      val workspaceModel = WorkspaceModel.getInstance(project)
      val snapshot = workspaceModel.currentSnapshot
      val entitiesAndProperties = snapshot.entities(LibraryPropertiesEntity::class.java)
        .filter { it.library.tableId !is LibraryTableId.GlobalLibraryTableId && it.isRepositoryLibraryProperties() }
        .map { it to checkNotNull(it.toRepositoryLibraryProperties()) }
        .toList()

      if (entitiesAndProperties.isEmpty()) {
        logger.info("Clear libraries properties progressed: nothing to update")
        showNotification(JavaUiBundle.message("repository.library.utils.notification.content.library.properties.built", 0),
                         NotificationType.INFORMATION, disableInfoNotifications)
        return@coroutineScope
      }

      logger.info("Clear libraries properties progressed: computing properties started")
      rawProgressReporterOrError.text(JavaUiBundle.message("repository.library.utils.progress.text.computing.properties"))
      val builder = snapshot.toBuilder()
      val updatedCounter = AtomicInteger(0)
      entitiesAndProperties.forEach { (entity, properties) ->
        val checksumUpdated = removeExistingChecksums && removeChecksums(properties)
        val bindRepoUpdated = unbindRepositories && unbindRepo(properties)
        if (checksumUpdated || bindRepoUpdated) updatedCounter.incrementAndGet()
        updateEntityIfPropertiesChanged(builder, properties, entity)
      }

      logger.info("Clear libraries properties progressed: computing properties complete")
      rawProgressReporterOrError.text(JavaUiBundle.message("repository.library.utils.progress.text.saving.changes"))
      commitBuilderIfModified(workspaceModel, builder)

      logger.info("Clear libraries properties complete: ${updatedCounter.get()} libraries updated")
      showNotification(
        JavaUiBundle.message("repository.library.utils.notification.content.library.properties.cleared", updatedCounter.get()),
        NotificationType.INFORMATION, disableInfoNotifications)
    }

    private fun unbindRepo(properties: RepositoryLibraryProperties): Boolean {
      if (properties.jarRepositoryId == null) return false
      properties.jarRepositoryId = null
      return true
    }

    private fun removeChecksums(properties: RepositoryLibraryProperties): Boolean {
      if (!properties.isEnableSha256Checksum) return false
      properties.artifactsVerification = emptyList()
      return true
    }
  }
}

private val CoroutineScope.rawProgressReporterOrError: RawProgressReporter get() = requireNotNull(rawProgressReporter)

private fun LibraryPropertiesEntity.isRepositoryLibraryProperties() = propertiesXmlTag != null && REPOSITORY_LIBRARY_KIND.kindId == libraryType

private fun LibraryPropertiesEntity.toRepositoryLibraryProperties(): RepositoryLibraryProperties? {
  return if (isRepositoryLibraryProperties()) deserialize(JDOMUtil.load(checkNotNull(propertiesXmlTag))) else null
}
