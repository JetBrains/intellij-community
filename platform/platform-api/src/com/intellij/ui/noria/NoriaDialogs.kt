// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.noria

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import java.util.function.Consumer

data class DialogProps(val north: Element? = null,
                       val center: Element? = null,
                       val errorText: Cell<String?> = cell(null as String?),
                       val actions: List<NoriaAction>,
                       val leftSideActions: List<NoriaAction> = emptyList(),
                       val project: Project? = null,
                       val canBeParent: Boolean = true,
                       val helpId: String? = null,
                       val title: String)

enum class ActionRole {
  Default, Cancel, None
}

data class NoriaAction(val enabled: Cell<Boolean>,
                       val name: String,
                       val role: ActionRole = ActionRole.None,
                       val focused: Boolean = false,
                       val mnemonic: Char? = null,
                       val lambda: Consumer<NoriaDialogHandle>,
                       val isExclusive: Boolean = false)

interface NoriaDialogs {
  companion object {
    val instance: NoriaDialogs
      get() = ServiceManager.getService(NoriaDialogs::class.java)
  }

  fun show(dialogProps: DialogProps) : NoriaDialogHandle
}

interface NoriaDialogHandle {
  fun close(exitCode: Int)
  fun shake()
}











