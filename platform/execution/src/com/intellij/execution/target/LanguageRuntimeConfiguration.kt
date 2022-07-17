// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target

import com.intellij.execution.configurations.RuntimeConfigurationException
import com.intellij.execution.target.ContributedConfigurationBase.Companion.getTypeImpl
import com.intellij.execution.target.LanguageRuntimeType.Companion.EXTENSION_NAME
import com.intellij.execution.target.LanguageRuntimeType.VolumeDescriptor
import com.intellij.execution.target.LanguageRuntimeType.VolumeType
import com.intellij.execution.target.TargetEnvironment.TargetPath
import com.intellij.execution.target.TargetEnvironment.UploadRoot
import com.intellij.execution.target.TargetEnvironmentType.TargetSpecificVolumeData
import com.intellij.openapi.components.BaseState
import com.intellij.util.text.nullize
import com.intellij.util.xmlb.annotations.Transient
import java.nio.file.Path

/**
 * Base class for configuration instances contributed by the ["com.intellij.executionTargetLanguageRuntimeType"][EXTENSION_NAME] extension point.
 * <p/>
 * Language configurations may define separate volume types to group together several related file transfers, and allow user to configure the
 * target-specific remote paths for each group, along with the target-specific options for every transfer.
 */
abstract class LanguageRuntimeConfiguration(typeId: String) : ContributedConfigurationBase(typeId, EXTENSION_NAME) {
  private val volumeTargetSpecificBits = mutableMapOf<VolumeType, TargetSpecificVolumeData>()
  private val volumePaths = mutableMapOf<VolumeType, String>()

  fun createUploadRoot(descriptor: VolumeDescriptor, localRootPath: Path): UploadRoot {
    return UploadRoot(localRootPath, getTargetPath(descriptor)).also {
      it.volumeData = getTargetSpecificData(descriptor)
    }
  }

  fun getTargetSpecificData(volumeDescriptor: VolumeDescriptor): TargetSpecificVolumeData? {
    return volumeTargetSpecificBits[volumeDescriptor.type]
  }

  fun setTargetSpecificData(volumeDescriptor: VolumeDescriptor, data: TargetSpecificVolumeData?) {
    volumeTargetSpecificBits.putOrClear(volumeDescriptor.type, data)
  }

  fun getTargetPathValue(volumeDescriptor: VolumeDescriptor): String? = volumePaths[volumeDescriptor.type]

  fun getTargetPath(volumeDescriptor: VolumeDescriptor): TargetPath {
    return TargetPath.Temporary(hint = volumeDescriptor.type.id,
                                parentDirectory = getTargetPathValue(volumeDescriptor)?.nullize()
                                                  ?: volumeDescriptor.defaultPath.nullize(),
                                prefix = volumeDescriptor.directoryPrefix)
  }

  fun setTargetPath(volumeDescriptor: VolumeDescriptor, targetPath: String?) {
    volumePaths.putOrClear(volumeDescriptor.type, targetPath)
  }

  @Throws(RuntimeConfigurationException::class)
  open fun validateConfiguration() = Unit

  protected fun saveInState(volumeDescriptor: VolumeDescriptor, doSave: (VolumeState?) -> Unit) {
    val volumeState = VolumeState().also {
      it.remotePath = getTargetPathValue(volumeDescriptor)
      it.targetSpecificData = getTargetSpecificData(volumeDescriptor)
    }
    doSave(volumeState)
  }

  protected fun loadVolumeState(volumeDescriptor: VolumeDescriptor, volumeState: VolumeState?) {
    volumeState?.let {
      setTargetPath(volumeDescriptor, it.remotePath)
      setTargetSpecificData(volumeDescriptor, it.targetSpecificData)
    }
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

  /**
   * Proposed serialization format for the volume data, including both the target path and target specific data configured by the user
   */
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