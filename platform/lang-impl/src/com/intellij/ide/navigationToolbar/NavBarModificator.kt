// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.navigationToolbar

import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.ide.ui.customization.CustomisedActionGroup
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener

class NavBarModificator(onChange: () -> Unit, disposable: Disposable) {
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

    val runDebugGroup = CustomActionsSchema.getInstance().getCorrectedAction("ToolbarRunGroup")
    val navBarVcsGroup = CustomActionsSchema.getInstance().getCorrectedAction("NavBarVcsGroup")

    group.getChildren(null).forEach { child ->
      when (child) {
        runDebugGroup -> {
          if(isNewRunDebug()) {
            getNewRunDebug() ?: runDebugGroup
          } else runDebugGroup
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
    return null
  }

  fun getNewVcsGroup(): AnAction? {
    return null
  }

}