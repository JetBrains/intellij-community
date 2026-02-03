// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.navigation.impl

import com.intellij.ide.DataManager
import com.intellij.ide.IdeBundle
import com.intellij.navigation.ItemPresentation
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.platform.backend.navigation.NavigationRequest
import com.intellij.platform.backend.navigation.NavigationTarget
import com.intellij.platform.ide.navigation.NavigationOptions
import com.intellij.platform.ide.navigation.NavigationService
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import javax.swing.Icon

internal class NavigationTargetNavigatable(
  private val project: Project,
  navigationTarget: NavigationTarget,
) : NavigationItem, ItemPresentation {

  private val targetPresentation = navigationTarget.computePresentation()
  private val navigationTargetPointer = navigationTarget.createPointer()
  private val navigationTarget: NavigationTarget?
    get() = navigationTargetPointer.dereference()

  override fun navigationRequest(): NavigationRequest? = navigationTarget?.navigationRequest()

  override fun navigate(requestFocus: Boolean) {
    val component = IdeFocusManager.getInstance(project).getFocusOwner()
    val dataContext = component?.let { DataManager.getInstance().getDataContext(it) }

    runWithModalProgressBlocking(project, IdeBundle.message("progress.title.preparing.navigation")) {
      val request = readAction { navigationTarget?.navigationRequest() }
      request?.let {
        project.serviceAsync<NavigationService>().navigate(
          it, NavigationOptions.defaultOptions().requestFocus(requestFocus), dataContext
        )
      }
    }
  }

  override fun canNavigate(): Boolean = true

  override fun canNavigateToSource(): Boolean = false

  override fun getName(): String = targetPresentation.presentableText

  override fun getPresentation(): ItemPresentation = this

  override fun getPresentableText(): String = targetPresentation.presentableText

  override fun getIcon(unused: Boolean): Icon? = targetPresentation.icon

  override fun getLocationString(): String? {
    val container = targetPresentation.containerText
    val location = targetPresentation.locationText
    return if (container != null || location != null) {
      sequenceOf(container, location).joinToString(", ", "(", ")")
    }
    else null
  }

  override fun toString(): String =
    "NavigationTargetNavigatable[$navigationTarget]"

}