// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target

import com.intellij.execution.target.ContributedConfigurationBase.Companion.getTypeImpl
import com.intellij.execution.target.LanguageRuntimeType.Companion.EXTENSION_NAME
import com.intellij.execution.target.LanguageRuntimeType.VolumeDescriptor
import com.intellij.execution.target.TargetEnvironment.TargetPath
import com.intellij.execution.target.TargetEnvironment.UploadRoot
import com.intellij.openapi.components.BaseState
import java.nio.file.Path

/**
 * Base class for configuration instances contributed by the ["com.intellij.executionTargetLanguageRuntimeType"][EXTENSION_NAME] extension point.
 *
 * Since different language configurations do not share any common bits, this is effectively a marker.
 */
abstract class LanguageRuntimeConfiguration(typeId: String) : ContributedConfigurationBase(typeId, EXTENSION_NAME) {
  private val volumeTargetSpecificBits = mutableMapOf<VolumeDescriptor, BaseState?>()
  private val volumePaths = mutableMapOf<VolumeDescriptor, String>()

  fun createUploadRoot(descriptor: VolumeDescriptor, localRootPath: Path): UploadRoot {
    return UploadRoot(localRootPath, getTargetPath(descriptor))
  }

  fun getTargetSpecificData(volumeDescriptor: VolumeDescriptor): BaseState? {
    return volumeTargetSpecificBits[volumeDescriptor]
  }

  fun setTargetSpecificData(volumeDescriptor: VolumeDescriptor, data: BaseState?) {
    volumeTargetSpecificBits[volumeDescriptor] = data
  }

  protected fun getTargetPathValue(volumeDescriptor: VolumeDescriptor): String? = volumePaths[volumeDescriptor]

  fun getTargetPath(volumeDescriptor: VolumeDescriptor): TargetPath {
    val path = getTargetPathValue(volumeDescriptor)
    return if (path.isNullOrEmpty()) TargetPath.Temporary(hint = volumeDescriptor.wizardLabel) else TargetPath.Persistent(path)
  }

  fun setTargetPath(volumeDescriptor: VolumeDescriptor, targetPath: String?) {
    if (targetPath == null) {
      volumePaths.remove(volumeDescriptor)
    }
    else {
      volumePaths[volumeDescriptor] = targetPath
    }
  }
}

fun <C : LanguageRuntimeConfiguration, T : LanguageRuntimeType<C>> C.getRuntimeType(): T = this.getTypeImpl()