// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.transformer

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileTypes.FileTypeExtension
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.KeyedLazyInstance
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Service(Service.Level.APP)
class TextPresentationTransformers : FileTypeExtension<TextPresentationTransformer>(EP), Disposable.Default {
  init {
    val extensionPoint = ApplicationManager.getApplication().extensionArea.getExtensionPointIfRegistered<TextPresentationTransformer>(EP.name)
    extensionPoint?.addChangeListener({ notifyTextPresentationSetChange() }, null)
  }

  private fun notifyTextPresentationSetChange() {
    val fileTypes = EP.extensionsIfPointIsRegistered.map {
      val key = it.key
      FileTypeRegistry.getInstance().getFileTypeByExtension(key)
    }.toSet()
    ApplicationManager.getApplication().invokeLater(
      { FileDocumentManager.getInstance().reloadFileTypes(fileTypes) }, ModalityState.nonModal())
  }

  companion object {
    val EP: ExtensionPointName<KeyedLazyInstance<TextPresentationTransformer>> = ExtensionPointName("com.intellij.fileEditor.textPresentationTransformer")

    @JvmStatic
    fun fromPersistent(text: CharSequence, virtualFile: VirtualFile): CharSequence {
      val transformer = service<TextPresentationTransformers>().forFileType(virtualFile.fileType)
      if (transformer == null) {
        return text
      }

      return transformer.fromPersistent(text, virtualFile)
    }

    @JvmStatic
    fun toPersistent(text: CharSequence, virtualFile: VirtualFile): CharSequence {
      val transformer = service<TextPresentationTransformers>().forFileType(virtualFile.fileType)
      if (transformer == null) {
        return text
      }

      return transformer.toPersistent(text, virtualFile)
    }
  }
}