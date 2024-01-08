// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.images.editor.impl

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import org.intellij.images.vfs.IfsUtil

@Service(Service.Level.PROJECT)
internal class ImageFileService(
  private val coroutineScope: CoroutineScope,
) {

  fun createImageFileLoader(target: ImageEditorImpl): ImageFileLoader =
    ImageFileLoaderImpl(
      target,
      coroutineScope
    )

  class ImageFileLoaderImpl(private val target: ImageEditorImpl, childScope: CoroutineScope) : ImageFileLoader {

    private val flow = MutableSharedFlow<VirtualFile>(
      replay = 1,
      onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private val job = childScope.launch(CoroutineName("ImageFileLoader for $target")) {
      flow.collectLatest { file ->
        try {
          val imageProvider = withContext(Dispatchers.IO) { IfsUtil.getImageProvider(file) }
          val format = withContext(Dispatchers.IO) { IfsUtil.getFormat(file) }
          withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
            target.setImageProvider(imageProvider, format)
          }
        }
        catch (e: CancellationException) {
          throw e // We don't care why it's cancelled: the editor is disposed or the next request has arrived.
        }
        catch (e: Exception) { // Likely an I/O error.
          LOG.warn("Exception when loading the image from $file", e)
          target.setImageProvider(null, null)
        }
      }
    }

    override fun loadFile(file: VirtualFile?) {
      if (file == null) {
        target.setImageProvider(null, null)
        return
      }
      flow.tryEmit(file)
    }

    override fun dispose() {
      job.cancel()
    }
  }

}

private val LOG = logger<ImageFileService>()
