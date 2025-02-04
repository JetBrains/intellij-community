// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.editor.backend

import com.intellij.codeWithMe.clientId
import com.intellij.openapi.application.isRhizomeAdEnabled
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.ad.AdDocumentEntity
import com.intellij.openapi.editor.impl.editorId
import com.intellij.platform.editor.EditorEntity
import com.intellij.platform.kernel.KernelService
import com.intellij.platform.kernel.util.kernelCoroutineContext
import com.intellij.platform.util.coroutines.childScope
import fleet.kernel.change
import fleet.kernel.shared
import kotlinx.coroutines.*

internal class BackendEditorState(
  parentScope: CoroutineScope,
  private val editor: Editor,
) {

  init {
    check(isRhizomeAdEnabled)
  }

  private val cs: CoroutineScope = parentScope.childScope(
    editor.toString(),
    KernelService.instance.kernelCoroutineScope.getCompleted().coroutineContext.kernelCoroutineContext(), // IJPL-176847
  )

  private val entity: Deferred<EditorEntity> = cs.async {
    val clientId = requireNotNull(currentCoroutineContext().clientId())
    val editorEntity = change {
      shared {
        val document = AdDocumentEntity.fromString(editor.document.text, editor.document.modificationStamp)
        EditorEntity.new {
          it[EditorEntity.idAttr] = editor.editorId()
          it[EditorEntity.clientIdAttr] = clientId
          it[EditorEntity.documentAttr] = document
        }
      }
    }

    // note `cs` is used
    cs.launch(start = CoroutineStart.UNDISPATCHED) {
      try {
        awaitCancellation()
      }
      finally {
        withContext(NonCancellable) {
          change {
            shared {
              editorEntity.delete()
            }
          }
        }
      }
    }

    editorEntity
  }

  fun release() {
    cs.cancel()
  }
}
