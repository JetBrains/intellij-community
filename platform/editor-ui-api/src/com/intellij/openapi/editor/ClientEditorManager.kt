// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor

import com.intellij.codeWithMe.ClientId
import com.intellij.codeWithMe.ClientId.Companion.current
import com.intellij.codeWithMe.ClientId.Companion.isLocal
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.client.ClientKind
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.WeakList
import org.jetbrains.annotations.ApiStatus
import java.util.stream.Stream

/**
 * Manages editors for particular clients. Take a look a [com.intellij.openapi.client.ClientSession]
 */
@ApiStatus.Experimental
@ApiStatus.Internal
class ClientEditorManager {
  private val clientId = current

  val editors: MutableList<Editor> = ContainerUtil.createLockFreeCopyOnWriteList()

  companion object {
    fun getCurrentInstance(): ClientEditorManager = ApplicationManager.getApplication().getService(ClientEditorManager::class.java)

    fun getCurrentInstanceIfCreated(): ClientEditorManager? = ApplicationManager.getApplication().getServiceIfCreated(ClientEditorManager::class.java)

    fun getAllInstances(): List<ClientEditorManager> = ApplicationManager.getApplication().getServices(ClientEditorManager::class.java, ClientKind.ALL)

    /**
     * @return clientId of a user that the editor corresponds to.
     */
    @JvmStatic
    fun getClientId(editor: Editor): ClientId? = CLIENT_ID.get(editor)

    @JvmStatic
    fun getClientEditor(editor: Editor, clientId: ClientId?): Editor {
      val editors = COPIED_EDITORS.get(editor)
      if (clientId == null || editors == null) {
        return editor
      }
      return editors.firstOrNull { clientId == getClientId(it) } ?: editor
    }

    @ApiStatus.Internal
    fun assignClientId(editor: Editor, clientId: ClientId?) {
      CLIENT_ID.set(editor, clientId)
    }

    @ApiStatus.Internal
    fun addCopiedEditor(from: Editor, to: Editor) {
      var list = COPIED_EDITORS.get(from)
      if (list == null) {
        list = WeakList()
        COPIED_EDITORS.set(from, list)
      }
      list.add(to)
    }

    private val CLIENT_ID = Key.create<ClientId>("CLIENT_ID")
    private val COPIED_EDITORS = Key.create<WeakList<Editor>>("COPIED_EDITORS")
  }

  fun editorsSequence(): Sequence<Editor> = editors.asSequence()
  fun editors(): Stream<Editor> = editors.stream()

  fun editors(document: Document, project: Project?): Sequence<Editor> {
    return editors.asSequence().filter { editor -> editor.document == document && (project == null || project == editor.project) }
  }

  fun editorCreated(editor: Editor) {
    if (!clientId.isLocal) {
      CLIENT_ID.set(editor, clientId)
    }
    editors.add(editor)
  }

  fun editorReleased(editor: Editor): Boolean {
    if (!clientId.isLocal) {
      CLIENT_ID.set(editor, null)
    }
    return editors.remove(editor)
  }
}
