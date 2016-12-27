package com.intellij.configurationStore

import com.intellij.openapi.components.RoamingType

import java.io.InputStream

class StreamProviderWrapper : StreamProvider {
  var streamProvider: StreamProvider? = null

  override val enabled: Boolean
    get() = streamProvider.let { it != null && it.enabled }

  override fun isApplicable(fileSpec: String, roamingType: RoamingType) = streamProvider?.isApplicable(fileSpec, roamingType) ?: false

  override fun read(fileSpec: String, roamingType: RoamingType, consumer: (InputStream?) -> Unit) = streamProvider?.read(fileSpec, roamingType, consumer) ?: false

  override fun processChildren(path: String,
                               roamingType: RoamingType,
                               filter: Function1<String, Boolean>,
                               processor: Function3<String, InputStream, Boolean, Boolean>): Boolean {
    return streamProvider?.let {
      it.processChildren(path, roamingType, filter, processor)
      true
    } ?: false
  }

  override fun write(fileSpec: String, content: ByteArray, size: Int, roamingType: RoamingType) {
    streamProvider!!.write(fileSpec, content, size, roamingType)
  }

  override fun delete(fileSpec: String, roamingType: RoamingType) = streamProvider?.delete(fileSpec, roamingType) ?: false
}

fun StreamProvider?.getOriginalProvider() = if (this is StreamProviderWrapper) streamProvider else null