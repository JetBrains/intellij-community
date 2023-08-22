// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.diagnostic

import com.intellij.diagnostic.PluginException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.indexing.ID
import com.intellij.util.indexing.impl.MapReduceIndexMappingException
import org.jetbrains.annotations.VisibleForTesting

object BrokenIndexingDiagnostics {

  private val LOG = logger<BrokenIndexingDiagnostics>()

  @VisibleForTesting
  var exceptionListener: Listener = DefaultListener

  interface Listener {
    fun onFileIndexMappingFailed(
      fileId: Int,
      file: VirtualFile?,
      fileType: FileType?,
      indexId: ID<*, *>,
      exception: MapReduceIndexMappingException
    )
  }

  private object DefaultListener : Listener {
    override fun onFileIndexMappingFailed(
      fileId: Int,
      file: VirtualFile?,
      fileType: FileType?,
      indexId: ID<*, *>,
      exception: MapReduceIndexMappingException
    ) {
      val message = buildString {
        append("Failed to build index '${indexId.name}' for file ")
        append(file?.url ?: "")
        append(" (id = $fileId)")
        if (fileType != null) {
          append(" (file type = ${fileType.name})")
        }
      }
      val classToBlame = exception.classToBlame
      val pluginException = if (classToBlame != null) {
        PluginException.createByClass(message, exception, classToBlame)
      }
      else {
        PluginException(message, exception, indexId.pluginId)
      }
      LOG.error(pluginException)
    }
  }
}