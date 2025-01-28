package com.intellij.database.datagrid

import com.intellij.database.datagrid.DocumentDataHookUp.UpdateSession

class UpdateSessionCloser(val session: UpdateSession, val finish: (UpdateSession, Boolean) -> Unit) : AutoCloseable {
  var success: Boolean = false
  var closed: Boolean = false

  override fun close() {
    if (closed) {
      return
    }
    finish(session, success)
    closed = true
  }
}