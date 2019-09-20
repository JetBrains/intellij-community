// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.jetcache

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.ByteArraySequence
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.SystemProperties
import com.intellij.util.concurrency.Semaphore
import com.intellij.util.indexing.*
import com.jetbrains.rd.util.error
import com.jetbrains.rd.util.getLogger
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.reactive.IScheduler
import org.jetbrains.jetcache.Client
import org.jetbrains.jetcache.DiscoveryClient
import org.jetbrains.jetcache.JetCache
import org.jetbrains.jetcache.NetworkJetCache
import java.net.InetAddress

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.LongAdder
import kotlin.concurrent.thread

const val PREDEFINED_SERVERS_PROP = "jet.cache.predefined.servers"

class JetCacheService {
  val projectCurrentVersion = ConcurrentHashMap<Project, ByteArray>()
  @Volatile
  var jetCache: JetCache? = null

  fun getProjectHash(project: Project): ByteArray? {
    return projectCurrentVersion.get(project)
  }

  init {
    if (jetCache == null) {
      if (IS_ENABLED) {
        val semaphore = Semaphore()
        semaphore.down()

        val predefinedServers = System.getProperty(PREDEFINED_SERVERS_PROP)
        val predefinedAddresses = predefinedServers?.split(',')?.map { InetAddress.getByName(it) }?.toTypedArray()

        DiscoveryClient(Lifetime.Eternal, 8888, predefinedServers = predefinedAddresses) {
          if (jetCache != null) return@DiscoveryClient
          for (address in it.addresses) {
            val host = address.host
            val port = address.port
            LOG.info("connecting to JetCache server ${host}:${port}")
            val scheduler =  ThreadScheduler()
            val client = Client(host, port, scheduler)
            jetCache = NetworkJetCache(Lifetime.Eternal, client.model, scheduler)
            semaphore.up()
            return@DiscoveryClient
          }
        }
        if (!semaphore.waitFor(20000)) {
          LOG.error("we don't have JetCache")
        }
      } else {
      }
    }
  }
  fun tryGetIndexes(project: Project) {

    if (!IS_ENABLED) {
      LOG.error("JetCache service is disabled")
    }
    LOG.info("Query JetCache")
    val phash = ProjectStateHashGenerator.generateHashFor(project)
    projectCurrentVersion.put(project, phash)


    val l = LongAdder()
    LOG.info("query available keys for " + project.name)
    jetCache?.getMultiple(phash)?.whenComplete { keys, u ->
      LOG.info("available ${keys.size} keys received for " + project.name)
      if (u == null) {
        println("query size = " + keys.size + "")
        jetCache!!.get(keys) { key, value: ByteArray ->
          l.increment()
          if (l.sum().rem(1000) == 0L) {
            println("received " + l.sum())
          }
          try {
            val hashAndId = ContentHashesSupport.splitHashAndId(key)

            if (hashAndId != null) {
              val contentHash = hashAndId.first
              val fileBasedIndex = FileBasedIndex.getInstance() as FileBasedIndexImpl
              val snapshotInputMappings = (fileBasedIndex.getIndex(hashAndId.second) as VfsAwareMapReduceIndex).snapshotInputMappings
              (snapshotInputMappings as UpdatableSnapshotInputMappingIndex).putData(contentHash, ByteArraySequence(value), phash)
            }
            else {
              LOG.error(StringUtil.toHexString(key) + " is not found")
            }
          }
          catch (e: Throwable) {
            LOG.error(e)
          }
        }
      }
      else {
        LOG.error(u)
      }
    }
  }

  class ThreadScheduler : IScheduler {
    val q = LinkedBlockingQueue<() -> Unit>()
    init {
      thread(start = true, isDaemon = true) {
        while (true) {
          val a = q.take()
          try {
            a()
          } catch (e: Throwable) {
            getLogger<ThreadScheduler>().error(e)
          }

        }
      }
    }

    override val isActive: Boolean
      get() = true

    override fun flush() {
      throw NotImplementedError()
    }

    override fun queue(action: () -> Unit) {
      q.offer(action)
    }

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
