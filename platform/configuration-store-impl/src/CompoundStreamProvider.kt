package com.intellij.configurationStore

import com.intellij.openapi.components.RoamingType
import com.intellij.util.containers.ConcurrentList
import com.intellij.util.containers.ContainerUtil
import java.io.InputStream

class CompoundStreamProvider : StreamProvider {
  val providers: ConcurrentList<StreamProvider> = ContainerUtil.createConcurrentList<StreamProvider>()

  override val enabled: Boolean
    get() = providers.any { it.enabled }

  override fun isApplicable(fileSpec: String, roamingType: RoamingType): Boolean = providers.any { it.isApplicable(fileSpec, roamingType) }

  override fun read(fileSpec: String, roamingType: RoamingType, consumer: (InputStream?) -> Unit): Boolean = providers.any { it.read(fileSpec, roamingType, consumer) }

  override fun processChildren(path: String,
                               roamingType: RoamingType,
                               filter: Function1<String, Boolean>,
                               processor: Function3<String, InputStream, Boolean, Boolean>): Boolean {
    return providers.any { it.processChildren(path, roamingType, filter, processor) }
  }

  override fun write(fileSpec: String, content: ByteArray, size: Int, roamingType: RoamingType) {
    providers.forEach {
      if (it.isApplicable(fileSpec, roamingType)) {
        it.write(fileSpec, content, size, roamingType)
      }
    }
  }

  override fun delete(fileSpec: String, roamingType: RoamingType): Boolean = providers.any { it.delete(fileSpec, roamingType) }
}