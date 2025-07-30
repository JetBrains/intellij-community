// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.jpsCache

import io.netty.util.AsciiString
import io.opentelemetry.api.trace.Span
import org.jetbrains.jps.incremental.storage.ProjectStamps
import java.net.URI
import java.nio.file.Path
import java.util.*

/**
 * URL for read-only operations
 */
private const val URL_PROPERTY = "intellij.jps.remote.cache.url"

/**
 * IntelliJ repository git remote url
 */
private const val GIT_REPOSITORY_URL_PROPERTY = "intellij.remote.url"

/**
 * Commit hash for which JPS Cache is to be built/downloaded
 */
private const val COMMIT_HASH_PROPERTY = "build.vcs.number"

private val IS_JPS_CACHE_URL_CONFIGURED = !System.getProperty(URL_PROPERTY).isNullOrBlank()

internal val isPortableCompilationCacheEnabled: Boolean
  get() = ProjectStamps.PORTABLE_CACHES && IS_JPS_CACHE_URL_CONFIGURED

/**
 * Folder to store JPS Cache for later upload to AWS S3 bucket.
 * Upload performed in a separate process on CI.
 */
internal val jpsCacheS3Dir: Path?
  get() = System.getProperty("jps.caches.aws.sync.folder")?.let<String, Path> { Path.of(it) }

/**
 * Whether to download JPS Cache even if there are caches available locally.
 */
internal val isForceDownloadJpsCache: Boolean
  get() = System.getProperty("intellij.jps.cache.download.force").toBoolean()

internal fun getJpsCacheUrl(default: String? = null): URI {
  return URI(getRequiredSystemProperty(systemProperty = URL_PROPERTY, description = "Remote Cache url", default = default).trimEnd('/'))
}

internal val jpsCacheUploadUrl: URI
  get() = URI(getRequiredSystemProperty("intellij.jps.remote.cache.upload.url", "Remote Cache upload url").trimEnd('/'))

internal val jpsCacheCommit: String
  get() = getRequiredSystemProperty(COMMIT_HASH_PROPERTY, "Repository commit")

internal val jpsCacheRemoteGitUrl: String
  get() {
    val remoteGitUrl = getRequiredSystemProperty(GIT_REPOSITORY_URL_PROPERTY, "Repository url")
    Span.current().addEvent("Git remote url $remoteGitUrl")
    return remoteGitUrl
  }

internal val jpsCacheAuthHeader: CharSequence?
  get() {
    val username = System.getProperty("jps.auth.spaceUsername")
    val password = System.getProperty("jps.auth.spacePassword")
    return when {
      password == null -> null
      username == null -> AsciiString.of("Bearer $password")
      else -> AsciiString.of("Basic " + Base64.getEncoder().encodeToString("$username:$password".toByteArray()))
    }
  }

private fun getRequiredSystemProperty(systemProperty: String, description: String, default: String? = null): String {
  val value = System.getProperty(systemProperty) ?: default
  require(!value.isNullOrBlank()) {
    "$description is not defined. Please set '$systemProperty' system property."
  }
  return value
}