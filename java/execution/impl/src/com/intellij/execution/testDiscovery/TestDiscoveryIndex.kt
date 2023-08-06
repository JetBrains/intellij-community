// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testDiscovery

import com.intellij.execution.testDiscovery.indices.DiscoveredTestDataHolder
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.Couple
import com.intellij.serviceContainer.NonInjectable
import com.intellij.util.containers.MultiMap
import com.intellij.util.io.delete
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path

private val LOG: Logger
  get() = logger<TestDiscoveryIndex>()

@Service(Service.Level.PROJECT)
class TestDiscoveryIndex @NonInjectable constructor(private val basePath: Path) : Disposable {
  @Volatile
  private var myHolder: DiscoveredTestDataHolder? = null
  private val lock = Any()

  @Suppress("unused")
  internal constructor(project: Project) : this(basePath = TestDiscoveryExtension.baseTestDiscoveryPathForProject(project))

  companion object {
    @JvmStatic
    fun getInstance(project: Project): TestDiscoveryIndex {
      return project.getService(TestDiscoveryIndex::class.java)
    }
  }

  internal class MyPostStartUpActivity : ProjectActivity {
    init {
      if (ApplicationManager.getApplication().isHeadlessEnvironment()) {
        throw ExtensionNotApplicableException.create()
      }
    }

    override suspend fun execute(project: Project) {
      val service = getInstance(project)
      if (!withContext(Dispatchers.IO) { Files.exists(service.basePath) }) {
        return
      }

      // proactively init with maybe io costly compact
      service.holder
    }
  }

  fun hasTestTrace(testClassName: String, testMethodName: String, frameworkId: Byte): Boolean {
    return executeUnderLock { holder -> holder.hasTestTrace(testClassName, testMethodName, frameworkId) }!!
  }

  fun getTestsByFile(relativePath: String?, frameworkId: Byte): MultiMap<String, String> {
    return executeUnderLock { holder -> holder.getTestsByFile(relativePath!!, frameworkId) } ?: MultiMap.empty()
  }

  fun getTestsByClassName(classFQName: String, frameworkId: Byte): MultiMap<String, String> {
    return executeUnderLock { holder -> holder.getTestsByClassName(classFQName, frameworkId) } ?: MultiMap.empty()
  }

  fun getTestsByMethodName(classFQName: String, methodName: String, frameworkId: Byte): MultiMap<String, String> {
    return executeUnderLock { holder -> holder.getTestsByMethodName(classFQName, methodName, frameworkId) } ?: MultiMap.empty()
  }

  fun getTestModulesByMethodName(classFQName: String, methodName: String, frameworkId: Byte): Collection<String> {
    return executeUnderLock { holder -> holder.getTestModulesByMethodName(classFQName, methodName, frameworkId) } ?: emptySet()
  }

  fun getAffectedFiles(testQName: Couple<String?>?, frameworkId: Byte): Collection<String> {
    return executeUnderLock { holder -> holder.getAffectedFiles(testQName!!, frameworkId) } ?: emptySet()
  }

  override fun dispose() {
    synchronized(lock) {
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
    executeUnderLock { holder ->
      holder.updateTestData(testClassName, testMethodName, usedMethods, usedFiles, moduleName, frameworkId)
    }
  }

  private val holder: DiscoveredTestDataHolder?
    get() {
      var holder = myHolder
      if (holder == null) {
        synchronized(lock) {
          holder = myHolder
          if (holder == null) {
            holder = DiscoveredTestDataHolder(basePath)
            myHolder = holder
          }
        }
      }
      return holder
    }

  private fun <R : Any?> executeUnderLock(action: (DiscoveredTestDataHolder) -> R): R? {
    synchronized(lock) {
      val holder = holder
      if (holder == null || holder.isDisposed) {
        return null
      }

      try {
        return action(holder)
      }
      catch (e: Throwable) {
        LOG.error("Unexpected problem", e)
        holder.dispose()
        basePath.delete()
        myHolder = null
      }
      return null
    }
  }
}
