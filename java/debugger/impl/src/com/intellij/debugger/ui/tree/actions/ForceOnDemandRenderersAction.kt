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
package com.intellij.debugger.ui.tree.actions

import com.intellij.debugger.impl.DebuggerUtilsEx
import com.intellij.debugger.settings.NodeRendererSettings
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.Key
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.ui.XDebugSessionData

/**
 * @author egor
 */
class ForceOnDemandRenderersAction : ToggleAction(), DumbAware {

  override fun isSelected(e: AnActionEvent): Boolean {
    return RENDERERS_ONDEMAND_FORCED.get(getSessionData(e), false)
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    RENDERERS_ONDEMAND_FORCED.set(getSessionData(e), state)
    NodeRendererSettings.getInstance().fireRenderersChanged()
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = DebuggerUtilsEx.isInJavaSession(e)
  }

  companion object {
    private val RENDERERS_ONDEMAND_FORCED = Key.create<Boolean>("RENDERERS_ONDEMAND_FORCED")

    private fun getSessionData(e: AnActionEvent): XDebugSessionData? {
      var data = e.getData(XDebugSessionData.DATA_KEY)
      if (data == null) {
        val project = e.project
        if (project != null) {
          val session = XDebuggerManager.getInstance(project).currentSession
          if (session != null) {
            data = (session as XDebugSessionImpl).sessionData
          }
        }
      }
      return data
    }

    @JvmStatic
    fun isForcedOnDemand(session: XDebugSessionImpl): Boolean {
      return RENDERERS_ONDEMAND_FORCED.get(session.sessionData, false)
    }
  }
}
