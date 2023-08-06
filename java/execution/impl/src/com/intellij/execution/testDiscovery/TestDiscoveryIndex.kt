// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testDiscovery

import com.intellij.execution.testDiscovery.TestDiscoveryIndex
import com.intellij.execution.testDiscovery.indices.DiscoveredTestDataHolder
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.util.Couple
import com.intellij.serviceContainer.NonInjectable
import com.intellij.util.ThrowableConvertor
import com.intellij.util.concurrency.NonUrgentExecutor
import com.intellij.util.containers.MultiMap
import com.intellij.util.io.delete
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

@Service(Service.Level.PROJECT)
class TestDiscoveryIndex @NonInjectable constructor(basePath: Path) : Disposable {
  @Volatile
  private var myHolder: DiscoveredTestDataHolder? = null
  private val myLock = Any()
  private val basePath: Path?

  @Suppress("unused")
  internal constructor(project: Project) : this(TestDiscoveryExtension.baseTestDiscoveryPathForProject(project))

  init {
    this.basePath = basePath
  }

  internal class MyPostStartUpActivity : StartupActivity.DumbAware {
    override fun runActivity(project: Project) {
      if (ApplicationManager.getApplication().isHeadlessEnvironment()) {
        return
      }
      NonUrgentExecutor.getInstance().execute(Runnable {
        val service = getInstance(project)
        if (!Files.exists(service.basePath)) {
          return@execute
        }

        // proactively init with maybe io costly compact
        service.holder
      })
    }
  }

  fun hasTestTrace(testClassName: String, testMethodName: String, frameworkId: Byte): Boolean {
    val result = executeUnderLock(
      ThrowableConvertor { holder: DiscoveredTestDataHolder -> holder.hasTestTrace(testClassName, testMethodName, frameworkId) })!!
    return result === java.lang.Boolean.TRUE
  }

  fun removeTestTrace(testClassName: String, testMethodName: String, frameworkId: Byte) {
    executeUnderLock(ThrowableConvertor<DiscoveredTestDataHolder, Any?, IOException> { holder: DiscoveredTestDataHolder ->
      holder.removeTestTrace(testClassName, testMethodName, frameworkId)
      null
    })
  }

  fun getTestsByFile(relativePath: String?, frameworkId: Byte): MultiMap<String, String> {
    val map = executeUnderLock(
      ThrowableConvertor { holder: DiscoveredTestDataHolder ->
        holder.getTestsByFile(
          relativePath!!, frameworkId)
      })
    return map ?: MultiMap.empty()
  }

  fun getTestsByClassName(classFQName: String, frameworkId: Byte): MultiMap<String, String> {
    val map = executeUnderLock(
      ThrowableConvertor { holder: DiscoveredTestDataHolder -> holder.getTestsByClassName(classFQName, frameworkId) })
    return map ?: MultiMap.empty()
  }

  fun getTestsByMethodName(classFQName: String, methodName: String, frameworkId: Byte): MultiMap<String, String> {
    val map = executeUnderLock(
      ThrowableConvertor { holder: DiscoveredTestDataHolder -> holder.getTestsByMethodName(classFQName, methodName, frameworkId) })
    return map ?: MultiMap.empty()
  }

  fun getTestModulesByMethodName(classFQName: String, methodName: String, frameworkId: Byte): Collection<String> {
    val modules = executeUnderLock(
      ThrowableConvertor { holder: DiscoveredTestDataHolder -> holder.getTestModulesByMethodName(classFQName, methodName, frameworkId) })
    return modules ?: emptySet()
  }

  fun getAffectedFiles(testQName: Couple<String?>?, frameworkId: Byte): Collection<String> {
    val files = executeUnderLock(
      ThrowableConvertor { holder: DiscoveredTestDataHolder ->
        holder.getAffectedFiles(
          testQName!!, frameworkId)
      })
    return files ?: emptySet()
  }

  override fun dispose() {
    synchronized(myLock) {
      val holder = myHolder
      if (holder != null) {
        holder.dispose()
        myHolder = null
      }
    }
  }

  fun updateTestData(testClassName: String,
                     testMethodName: String,
                     usedMethods: MultiMap<String?, String?>,
                     usedFiles: List<String?>,
                     moduleName: String?,
                     frameworkId: Byte) {
    executeUnderLock(ThrowableConvertor<DiscoveredTestDataHolder, Any?, IOException> { holder: DiscoveredTestDataHolder ->
      holder.updateTestData(testClassName, testMethodName, usedMethods, usedFiles, moduleName, frameworkId)
      null
    })
  }

  private val holder: DiscoveredTestDataHolder?
    get() {
      var holder = myHolder
      if (holder == null) {
        synchronized(myLock) {
          holder = myHolder
          if (holder == null && basePath != null) {
            holder = DiscoveredTestDataHolder(basePath)
            myHolder = holder
          }
        }
      }
      return holder
    }

  private fun <R> executeUnderLock(action: ThrowableConvertor<DiscoveredTestDataHolder, R, IOException>): R? {
    synchronized(myLock) {
      val holder = holder
      if (holder == null || holder.isDisposed) return null
      try {
        return action.convert(holder)
      }
      catch (throwable: Throwable) {
        LOG.error("Unexpected problem", throwable)
        holder.dispose()
        basePath!!.delete()
        myHolder = null
      }
      return null
    }
  }

  companion object {
    val LOG: Logger = Logger.getInstance(TestDiscoveryIndex::class.java)
    fun getInstance(project: Project): TestDiscoveryIndex {
      return project.getService(TestDiscoveryIndex::class.java)
    }
  }
}
