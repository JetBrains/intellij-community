// Please, don't add new functions to this file.
package org.jetbrains.plugins.notebooks.editor

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.impl.EditorImpl
import org.jetbrains.annotations.ApiStatus.ScheduledForRemoval

@Deprecated("Just inline this simple function")
@ScheduledForRemoval
fun Editor.addEditorDocumentListener(listener: DocumentListener) {
  require(this is EditorImpl)
  if (!isDisposed) {
    document.addDocumentListener(listener, disposable)
  }
}