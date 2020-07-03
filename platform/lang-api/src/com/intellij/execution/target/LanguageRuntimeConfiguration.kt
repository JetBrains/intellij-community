// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target

import com.intellij.execution.target.ContributedConfigurationBase.Companion.getTypeImpl
import com.intellij.execution.target.LanguageRuntimeType.Companion.EXTENSION_NAME
import com.intellij.execution.target.LanguageRuntimeType.VolumeDescriptor
import com.intellij.execution.target.LanguageRuntimeType.VolumeType
import com.intellij.execution.target.TargetEnvironment.TargetPath
import com.intellij.execution.target.TargetEnvironment.UploadRoot
import com.intellij.execution.target.TargetEnvironmentType.TargetSpecificVolumeData
import com.intellij.openapi.components.BaseState
import com.intellij.util.xmlb.annotations.Transient
import java.nio.file.Path

/**
 * Base class for configuration instances contributed by the ["com.intellij.executionTargetLanguageRuntimeType"][EXTENSION_NAME] extension point.
 *
 * Since different language configurations do not share any common bits, this is effectively a marker.
 */
abstract class LanguageRuntimeConfiguration(typeId: String) : ContributedConfigurationBase(typeId, EXTENSION_NAME) {
  private val volumeTargetSpecificBits = mutableMapOf<VolumeType, TargetSpecificVolumeData>()
  private val volumePaths = mutableMapOf<VolumeType, String>()

  fun createUploadRoot(descriptor: VolumeDescriptor, localRootPath: Path): UploadRoot {
    return UploadRoot(localRootPath, getTargetPath(descriptor))
  }

  fun getTargetSpecificData(volumeDescriptor: VolumeDescriptor): TargetSpecificVolumeData? {
    return volumeTargetSpecificBits[volumeDescriptor.type]
  }

  fun setTargetSpecificData(volumeDescriptor: VolumeDescriptor, data: TargetSpecificVolumeData?) {
    volumeTargetSpecificBits.putOrClear(volumeDescriptor.type, data)
  }

  protected fun getTargetPathValue(volumeDescriptor: VolumeDescriptor): String? = volumePaths[volumeDescriptor.type]

  fun getTargetPath(volumeDescriptor: VolumeDescriptor): TargetPath {
    val path = getTargetPathValue(volumeDescriptor)
    return if (path.isNullOrEmpty()) TargetPath.Temporary(hint = volumeDescriptor.wizardLabel) else TargetPath.Persistent(path)
  }

  fun setTargetPath(volumeDescriptor: VolumeDescriptor, targetPath: String?) {
    volumePaths.putOrClear(volumeDescriptor.type, targetPath)
  }

  companion object {
    private fun <K, V> MutableMap<K, V>.putOrClear(key: K, value: V?) {
      if (value == null) {
        this.remove(key)
      }
      else {
        this[key] = value
      }
    }
  }

  class VolumeState : BaseState() {
    var remotePath by string()
    var targetSpecificBits by map<String, String>()

    var targetSpecificData: TargetSpecificVolumeData?
      @Transient
      get() = object : TargetSpecificVolumeData {
        override fun toStorableMap(): Map<String, String> = targetSpecificBits.toMap()
      }
      set(data) {
        val dataAsMap = data?.toStorableMap() ?: emptyMap()
        targetSpecificBits = dataAsMap.toMutableMap()
      }
  }
}

fun <C : LanguageRuntimeConfiguration, T : LanguageRuntimeType<C>> C.getRuntimeType(): T = this.getTypeImpl()