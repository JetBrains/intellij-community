/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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











