// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.jarRepository

import com.intellij.jarRepository.JarRepositoryAuthenticationDataProvider.AuthenticationData
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.idea.maven.aether.ArtifactRepositoryManager
import org.jetbrains.jps.model.serialization.JpsMavenSettings

/**
 * Extension point that provides credentials for Maven repositories that require authorization
 */
@ApiStatus.Experimental
interface JarRepositoryAuthenticationDataProvider {
  /**
   * @param remote Maven repository description
   * @return credentials that allow downloading libraries from the specified Maven repository.
   *         [null] if authorization is not needed.
   */
  fun provideAuthenticationData(remote: RemoteRepositoryDescription): AuthenticationData?

  data class AuthenticationData(val userName: String, val password: String) {
    override fun toString(): String {
      return "AuthenticationData(" +
             "userName='$userName', " +
             "password='${if (password.isEmpty()) "" else "[REDACTED]"}')"
    }
  }

  companion object {
    @JvmField
    val KEY: ExtensionPointName<JarRepositoryAuthenticationDataProvider> =
      ExtensionPointName("com.intellij.jarRepositoryAuthenticationDataProvider")
  }
}

@RequiresBackgroundThread
internal fun obtainAuthenticationData(description: RemoteRepositoryDescription): ArtifactRepositoryManager.ArtifactAuthenticationData? {
  for (extension in JarRepositoryAuthenticationDataProvider.KEY.extensionList) {
    val authData = extension.provideAuthenticationData(description)
    if (authData != null) {
      return ArtifactRepositoryManager.ArtifactAuthenticationData(authData.userName, authData.password)
    }
  }

  return null
}

private class MavenSettingsXmlRepositoryAuthenticationDataProvider: JarRepositoryAuthenticationDataProvider {
  override fun provideAuthenticationData(remote: RemoteRepositoryDescription): AuthenticationData? {
    return service<MavenSettingsXmlRepositoryAuthenticationDataService>().provideAuthenticationData(remote)
  }
}

@Service(Service.Level.APP)
private class MavenSettingsXmlRepositoryAuthenticationDataService(private val coroutineScope: CoroutineScope) {
  private val watchedRoots: List<LocalFileSystem.WatchRequest>

  private val globalMavenSettingsXml = JpsMavenSettings.getGlobalMavenSettingsXml()
  private val userMavenSettingsXml = JpsMavenSettings.getUserMavenSettingsXml()

  @Volatile
  private var cachedAuthentication: Map<String, AuthenticationData> = emptyMap()

  init {
    val localFileSystem = LocalFileSystem.getInstance()
    val absolutePaths = buildList {
      add(FileUtilRt.toSystemIndependentName(userMavenSettingsXml.absolutePath))
      globalMavenSettingsXml?.let { add(FileUtilRt.toSystemIndependentName(it.absolutePath)) }
    }
    watchedRoots = absolutePaths.mapNotNull {
      localFileSystem.refreshAndFindFileByPath(it)
      localFileSystem.addRootToWatch(it, false)
    }
    coroutineScope.coroutineContext.job.invokeOnCompletion {
      LocalFileSystem.getInstance().removeWatchedRoots(watchedRoots)
    }

    VirtualFileManager.getInstance().addAsyncFileListener(coroutineScope) { events ->
      if (events.any { it.path in absolutePaths }) {
        object : AsyncFileListener.ChangeApplier {
          override fun afterVfsChange() {
            coroutineScope.launch(Dispatchers.IO) { reload() }
          }
        }
      }
      else {
        null
      }
    }

    reload()
  }

  fun provideAuthenticationData(description: RemoteRepositoryDescription): AuthenticationData? {
    return cachedAuthentication[description.id]
  }

  private fun reload() {
    cachedAuthentication = JpsMavenSettings.loadAuthenticationSettings(globalMavenSettingsXml, userMavenSettingsXml)
      .asSequence()
      .map { (id, authentication) ->
        id to AuthenticationData(authentication.username, authentication.password)
      }.toMap()
  }
}
