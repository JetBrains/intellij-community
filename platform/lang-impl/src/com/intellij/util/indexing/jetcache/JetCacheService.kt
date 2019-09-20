// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.jetcache

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.ByteArraySequence
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.Semaphore
import com.intellij.util.concurrency.SequentialTaskExecutor
import com.intellij.util.indexing.*
import com.jetbrains.rd.util.error
import com.jetbrains.rd.util.getLogger
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.reactive.IScheduler
import com.jetbrains.rd.util.spinUntil
import com.jetbrains.rd.util.threading.SingleThreadScheduler
import org.jetbrains.jetcache.Client
import org.jetbrains.jetcache.DiscoveryClient
import org.jetbrains.jetcache.JetCache
import org.jetbrains.jetcache.NetworkJetCache
import java.io.File
import java.net.InetAddress

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.LongAdder
import kotlin.concurrent.thread

class JetCacheService: Disposable {
  val projectCurrentVersion = ConcurrentHashMap<Project, ByteArray>()
  @Volatile
  var jetCache: JetCache? = null

  init {

  }

  override fun dispose() {
  }


  init {
    if (IS_ENABLED) {

      val semaphore = Semaphore()
      semaphore.down()

      DiscoveryClient(Lifetime.Eternal, 8888, predefinedServers = arrayOf(InetAddress.getByName("172.30.162.233"))) {
        if (jetCache != null) return@DiscoveryClient
        for (address in it.addresses) {
          val host = address.host
          val port = address.port
          LOG.info("connecting to JetCache server ${host}:${port}")
          val scheduler =  ThreadScheduler()/*SingleThreadScheduler(Lifetime.Eternal, "JetCache thread")*/
          val client = Client(host, port, scheduler)
          jetCache = NetworkJetCache(Lifetime.Eternal, client.model, scheduler)
          semaphore.up()
          return@DiscoveryClient
        }
      }
      if (!semaphore.waitFor(10000)) {
        LOG.error("we don't have JetCache")
      }
    } else {
      jetCache = null
    }
  }

  fun getProjectHash(project: Project): ByteArray {
    return projectCurrentVersion.get(project)!!
  }

  fun tryGetIndexes(project: Project) {
    if (jetCache == null) {
      return
    }
    if (!IS_ENABLED) {
      LOG.error("JetCache service is disabled")
    }
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
    Thread.sleep(1000 * 10)
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

  class JetCacheScheduler() : IScheduler {

    val l = LongAdder()
    init {

      ApplicationManager.getApplication().executeOnPooledThread({

        var i = 0
                                                                  while (true) {
                                                                    i++
                                                                    Thread.sleep(1000)
                                                                    queue {
                                                                      println("alive " + i)
                                                                    }
                                                                  }

                                                                })


    }
    override val isActive: Boolean
      get() = true

    private val executor = SequentialTaskExecutor.createSequentialApplicationPoolExecutor("fuck")

    override fun flush() {
      throw AssertionError()
    }

    override fun queue(action: () -> Unit) {
      l.increment()
      println("queue count " + l.sum())
      executor.submit(action)
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
