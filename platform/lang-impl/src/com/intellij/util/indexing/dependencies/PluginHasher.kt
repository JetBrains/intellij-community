// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.dependencies

import com.google.common.hash.HashCode
import com.google.common.hash.Hashing
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.openapi.progress.ProgressManager
import com.intellij.util.lang.UrlClassLoader
import org.jetbrains.annotations.ApiStatus.Internal
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.walk

@Internal
class PluginHasher {
  private val hashedClassLoaders = ConcurrentHashMap<ClassLoader, String>()
  private val hasher = Hashing.sha256().newHasher()
  fun addPluginFingerprint(plugin: IdeaPluginDescriptor) {
    val pluginFingerprint = if (plugin.version.contains("SNAPSHOT", ignoreCase = true)) {
      when (val classLoader = plugin.classLoader) {
        is UrlClassLoader -> {
          fingerprintFromFilesContent(plugin, classLoader)
        }
        else -> {
          fingerprintWithCurrentTimestamp(plugin)
        }
      }
    }
    else {
      fingerprintFromStringAttributes(plugin)
    }

    hasher.putBytes(pluginFingerprint.toByteArray())
  }

  @OptIn(ExperimentalPathApi::class)
  private fun fingerprintFromFilesContent(plugin: IdeaPluginDescriptor, classLoader: UrlClassLoader): String {
    val hash = hashedClassLoaders.getOrPut(classLoader) {
      val hasher = Hashing.sha256().newHasher()
      val charset = StandardCharsets.UTF_8
      classLoader.files // TODO: parallel stream
        .sorted() // not sure if we really want to sort, because order may change class resolution.
        .forEach { path ->
          ProgressManager.checkCanceled()
          // if path is not a directory, only "this" file will be visited
          // if path is a directory, all the regular files will be visited
          // note, that symlinks are not followed
          path.walk().sorted().forEach { f ->
            // /tmp/byteBuddyAgent12978532094762450051.jar:1261:2023-09-21T12:46:17.196727952Z <= this file is always different :(
            // see also https://youtrack.jetbrains.com/issue/IJPL-166
            val absolutePathString = f.toAbsolutePath().toString()
            if (!absolutePathString.startsWith("/tmp/byteBuddyAgent")) {
              val attributes = Files.readAttributes(f, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
              hasher.putBytes("$absolutePathString:${attributes.size()}:${attributes.lastModifiedTime()}".toByteArray(charset))
            }
          }
        }
      hasher.hash().toString()
    }
    return plugin.pluginId.idString + ":" + plugin.version + "-sha256" + hash
  }

  private fun fingerprintWithCurrentTimestamp(plugin: IdeaPluginDescriptor): String {
    return plugin.pluginId.idString + ":" + plugin.version + "-time" + System.currentTimeMillis()
  }

  private fun fingerprintFromStringAttributes(plugin: IdeaPluginDescriptor) = plugin.pluginId.idString + ":" + plugin.version
  fun mixInInt(i: Int) {
    hasher.putInt(i)
  }

  fun getFingerprint(): HashCode {
    return hasher.hash()
  }
}