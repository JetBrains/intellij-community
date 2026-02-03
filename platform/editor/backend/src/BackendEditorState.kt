// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.editor.backend

import com.intellij.codeWithMe.clientId
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.ad.isRhizomeAdRebornEnabled
import com.intellij.openapi.editor.impl.editorId
import com.intellij.platform.editor.EditorEntity
import com.intellij.platform.kernel.KernelService
import com.intellij.platform.kernel.util.kernelCoroutineContext
import com.intellij.platform.pasta.common.DocumentEntity
import com.intellij.platform.util.coroutines.childScope
import fleet.kernel.change
import fleet.kernel.rebase.shared
import fleet.util.UID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class BackendEditorState(
  parentScope: CoroutineScope,
  private val editor: Editor,
) {

  init {
    check(isRhizomeAdRebornEnabled)
  }

  private val cs: CoroutineScope = parentScope.childScope(
    editor.toString(),
    KernelService.instance.kernelCoroutineScope.getCompleted().coroutineContext.kernelCoroutineContext(), // IJPL-176847
  )

  private val entity: Deferred<EditorEntity> = cs.async {
    val clientId = requireNotNull(currentCoroutineContext().clientId())
    val editorEntity = change {
      shared {
        val document = DocumentEntity.fromText(UID.random(), editor.document.text)
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
