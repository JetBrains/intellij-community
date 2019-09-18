// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.jetcache

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.ByteArraySequence
import com.intellij.util.indexing.ContentHashesSupport
import com.intellij.util.indexing.FileBasedIndexExtension
import com.intellij.util.indexing.ID
import org.jetbrains.jetcache.JcKey
import org.jetbrains.jetcache.JcValue
import org.jetbrains.jetcache.JetCache

import java.util.concurrent.ConcurrentHashMap
import java.util.function.BiConsumer

class JetCacheService {

  private val myStorages = ConcurrentHashMap<ID<*, *>, JetCacheLocalStorage<*, *>>()

  init {
    if (IS_ENABLED) {
      for (extension in FileBasedIndexExtension.EXTENSION_POINT_NAME.extensionList) {
        if (extension.dependsOnFileContent()) {
          myStorages[extension.name] = JetCacheLocalStorage(extension)
        }
      }
    }
  }

  fun tryGetIndexes(project: Project) {
    if (!IS_ENABLED) {
      LOG.error("JetCache service is disabled")
    }
    val hash = ProjectStateHashGenerator.generateHashFor(project)

    //TODO start from here
    val jetCache: JetCache? = null
    jetCache!!.getMuliple(JcKey(hash)).whenComplete(object : BiConsumer<Array<JcValue>?, Throwable?> {
      override fun accept(t: Array<JcValue>?, u: Throwable?) {
        if (u != null) {
          LOG.error(u)
        }
        val keys = t!!
        jetCache.get(keys = null!!, onReceived = { jcKey, bytes ->
          val hashAndId = ContentHashesSupport.splitHashAndId(jcKey.serialized)
          if (hashAndId != null) {
            val id = hashAndId.second
            val contentHash = hashAndId.first
            getStorage(id).put(contentHash, ByteArraySequence(bytes))
          }
        }, onFinished = { success ->
          if (!success) {
            LOG.error("Can't load keys")
          }
        })
      }
    })
  }

  internal fun <K, V> getStorage(indexId: ID<K, V>): JetCacheLocalStorage<K, V> {
    return myStorages[indexId] as JetCacheLocalStorage<K, V>
  }

  companion object {
    private val LOG = Logger.getInstance(JetCacheService::class.java)
    var IS_ENABLED = System.getProperty("jet.cache.is.enabled") != null

    val instance: JetCacheService
      get() = ServiceManager.getService(JetCacheService::class.java)
  }
}
