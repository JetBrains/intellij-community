// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.jetcache

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.ByteArraySequence
import com.intellij.util.indexing.*
import com.intellij.util.indexing.jetcache.local.IntellijLocalJetCache
import com.jetbrains.rd.util.spinUntil
import org.jetbrains.jetcache.Client
import org.jetbrains.jetcache.JetCache
import org.jetbrains.jetcache.NetworkJetCache
import java.io.File
import java.net.InetAddress

import java.util.concurrent.ConcurrentHashMap

class JetCacheService: Disposable {
  val projectCurrentVersion = ConcurrentHashMap<Project, ByteArray>()
  val jetCache: JetCache?

  init {

  }

  override fun dispose() {
    groupIdMap?.close()
    myStorages.values.forEach { it.close() }
  }

  private val myStorages = ConcurrentHashMap<ID<*, *>, JetCacheLocalStorage<*, *>>()
  val groupIdMap: GroupIdMap?

  init {
    if (IS_ENABLED) {
      for (extension in FileBasedIndexExtension.EXTENSION_POINT_NAME.extensionList) {
        if (extension.dependsOnFileContent()) {
          myStorages[extension.name] = JetCacheLocalStorage(extension)
        }
      }
      groupIdMap = GroupIdMap(File(File(JetCacheLocalStorage.getRoot(), ".LocalJetCache"), "group_id_map"))

      if (false) {
        jetCache = IntellijLocalJetCache()
      } else {
        val client = Client(InetAddress.getByName("172.30.163.59"), 8888)
        spinUntil { client.connected.value }
        val networkJetCache = NetworkJetCache(client.lifetime, client.model, client.scheduler)
        jetCache = networkJetCache
      }
    } else {
      jetCache = null
      groupIdMap = null
    }
    //Disposer.register(FileBasedIndex.getInstance() as FileBasedIndexImpl, this)
  }

  fun getProjectHash(project: Project): ByteArray {
    return projectCurrentVersion.get(project)!!
  }

  fun tryGetIndexes(project: Project) {
    if (!IS_ENABLED) {
      LOG.error("JetCache service is disabled")
    }
    val hash = ProjectStateHashGenerator.generateHashFor(project)
    projectCurrentVersion.put(project, hash)

    //TODO use remote implementation
    jetCache?.getMultiple(hash)?.whenComplete { keys, u ->
      if (u == null) {
        jetCache.get(keys) { key, value ->
          val hashAndId = ContentHashesSupport.splitHashAndId(key)
          if (hashAndId != null) {
            val id = hashAndId.second
            val contentHash = hashAndId.first
            getStorage(id)?.put(contentHash, ByteArraySequence(value))
          }
        }
      } else {
        LOG.error(u)
      }
    }
  }

  fun <K, V> getStorage(indexId: ID<K, V>): JetCacheLocalStorage<K, V>? {
    return myStorages[indexId] as JetCacheLocalStorage<K, V>?
  }

  companion object {
    private val LOG = Logger.getInstance(JetCacheService::class.java)

    @JvmStatic
    val IS_ENABLED = System.getProperty("jet.cache.is.enabled") != null

    @JvmStatic
    val instance: JetCacheService
      get() = ServiceManager.getService(JetCacheService::class.java)
  }
}
