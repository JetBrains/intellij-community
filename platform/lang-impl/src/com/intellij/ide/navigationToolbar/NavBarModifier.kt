// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.navigationToolbar

import com.intellij.ide.ui.customization.CustomisedActionGroup
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener

class NavBarModifier(onChange: () -> Unit, disposable: Disposable) {
  companion object {
    const val navBarKey = "ide.new.navbar"
    const val runDebugKey = "ide.new.navbar.run.debug"
    const val vcsKey = "ide.new.navbar.vcs.group"
  }

  private val registryListener = object : RegistryValueListener {
    override fun afterValueChanged(value: RegistryValue) {
      onChange()
    }
  }

  private fun isNewMainToolbar(): Boolean {
    return Registry.get(navBarKey).asBoolean()
  }

  private fun isNewRunDebug(): Boolean {
    return Registry.get(runDebugKey).asBoolean()
  }

  private fun isNewNavBarVcsGroup(): Boolean {
    return Registry.get(vcsKey).asBoolean()
  }

  init {
    Registry.get(navBarKey).addListener(registryListener, disposable)
    Registry.get(runDebugKey).addListener(registryListener, disposable)
    Registry.get(vcsKey).addListener(registryListener, disposable)
  }


  fun modify(group: AnAction?): AnAction? {
    group ?: return group

    if (group !is CustomisedActionGroup) return group
    val resultGroup = DefaultActionGroup()

    if (isNewMainToolbar()) {
      return resultGroup
    }

    if (!isNewRunDebug() && !isNewNavBarVcsGroup()) {
      return group
    }

    val runDebugGroup = ActionManager.getInstance().getAction("ToolbarRunGroup")
    val navBarVcsGroup = ActionManager.getInstance().getAction("NavBarVcsGroup")
    val codeWithMeGroup = ActionManager.getInstance().getAction("CodeWithMeAction")

    group.getChildren(null).forEach { child ->
      when (child) {
        runDebugGroup -> {
          if (isNewRunDebug()) {
            getNewRunDebug()?.let {
              codeWithMeGroup?.let {
                resultGroup.add(it)
              }
              it
            } ?: runDebugGroup
          }
          else runDebugGroup
        }

        navBarVcsGroup -> {
          if(isNewNavBarVcsGroup()) {
            getNewVcsGroup() ?: navBarVcsGroup
          } else navBarVcsGroup
        }

        else -> child
      }?.let {
        resultGroup.add(it)
      }
    }
    return resultGroup
  }

  fun getNewRunDebug(): AnAction? {
    return ActionManager.getInstance().getAction("RunDebugControlAction")
  }

  fun getNewVcsGroup(): AnAction? {
    return ActionManager.getInstance().getAction("VcsNavBarToolbarActionsLight");
  }

}