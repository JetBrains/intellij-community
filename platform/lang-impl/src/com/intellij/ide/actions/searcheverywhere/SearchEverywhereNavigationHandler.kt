// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.codeWithMe.ClientId
import com.intellij.ide.actions.searcheverywhere.AbstractGotoSEContributor.Companion.getElement
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.navigation.NavigationRequest
import com.intellij.platform.backend.navigation.NavigationRequests
import com.intellij.platform.backend.navigation.impl.RawNavigationRequest
import com.intellij.platform.ide.navigation.NavigationOptions
import com.intellij.platform.ide.navigation.NavigationService
import com.intellij.platform.util.coroutines.childScope
import com.intellij.platform.util.coroutines.sync.OverflowSemaphore
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiUtilCore
import com.intellij.util.IntPair
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.awt.event.InputEvent
import java.util.regex.Matcher
import java.util.regex.Pattern

private val LOG = logger<SearchEverywhereNavigationHandler>()

// NavigationService is designed to process one navigation request at a time.
// However, the current implementation of AbstractGotoSEContributor can potentially generate multiple concurrent navigation requests.
// The semaphore ensures these requests are processed sequentially, maintaining the NavigationService's single-request-at-a-time contract.
// See IJPL-188436
private val semaphore: OverflowSemaphore = OverflowSemaphore(permits = 1, overflow = BufferOverflow.SUSPEND)

@ApiStatus.Internal
open class SearchEverywhereNavigationHandler(val project: Project) {

  companion object {
    @JvmStatic
    private fun getLineAndColumn(text: String): IntPair {
      var line = getLineAndColumnRegexpGroup(text, 2)
      val column = getLineAndColumnRegexpGroup(text, 3)

      if (line == -1 && column != -1) {
        line = 0
      }

      return IntPair(line, column)
    }
  }

  fun gotoSelectedItem(selected: PsiElement, modifiers: Int, searchText: String, offset: Int = -1) {
    project.service<SearchEverywhereContributorCoroutineScopeHolder>().coroutineScope.launch(ClientId.coroutineContext()) {
      val navigatingAction = readAction { tryMakeNavigatingFunction(selected, modifiers, searchText, offset) }
      if (navigatingAction != null) {
        navigatingAction()
      }
      else {
        LOG.warn("Selected $selected produced an invalid navigation action! Doing nothing!")
      }
    }
  }

  private fun tryMakeNavigatingFunction(selected: PsiElement, modifiers: Int, searchText: String, offset: Int): (suspend () -> Unit)? {
    if (!selected.isValid) {
      LOG.warn("Cannot navigate to invalid PsiElement")
      return null
    }

    val psiElement = preparePsi(selected, searchText)
    val file =
      if (selected is PsiFile) selected.virtualFile
      else PsiUtilCore.getVirtualFile(psiElement)

    val extendedNavigatable = if (file == null) {
      null
    }
    else {
      val position = getLineAndColumn(searchText)
      if (position.first >= 0 || position.second >= 0) {
        //todo create a navigation request by line&column, not by offset only
        OpenFileDescriptor(project, file, position.first, position.second)
      }
      else {
        null
      }
    }

    return suspend {
      val navigationOptions = NavigationOptions.defaultOptions()
        .openInRightSplit((modifiers and InputEvent.SHIFT_DOWN_MASK) != 0)
        .preserveCaret(true).forceFocus(true)
      if (extendedNavigatable == null) {
        if (file == null) {
          val navigatable = psiElement as? Navigatable
          if (navigatable != null) {
            // Navigation items from rd protocol often lack .containingFile or other PSI extensions, and are only expected to be
            // navigated through the Navigatable API.
            // This fallback is for items like that.
            val navRequest = RawNavigationRequest(navigatable, true)
            semaphore.withPermit {
              project.serviceAsync<NavigationService>().navigate(navRequest, navigationOptions, null)
            }
          }
          else {
            LOG.warn("Cannot navigate to invalid PsiElement (psiElement=$psiElement)")
          }
        }
        else {
          createSourceNavigationRequest(project = project, element = psiElement, file = file, searchText = searchText, offset = offset)?.let {
            semaphore.withPermit {
              project.serviceAsync<NavigationService>().navigate(it, navigationOptions, null)
            }
          }
        }
      }
      else {
        semaphore.withPermit {
          project.serviceAsync<NavigationService>().navigate(extendedNavigatable, navigationOptions)
          triggerLineOrColumnFeatureUsed(extendedNavigatable)
        }
      }
    }
  }

  open suspend fun createSourceNavigationRequest(
    project: Project,
    element: PsiElement,
    file: VirtualFile,
    searchText: String,
    offset: Int,
  ): NavigationRequest? {
    if (element is Navigatable) {
      return readAction {
        element.navigationRequest()
      }
    }
    else {
      val navigationRequests = serviceAsync<NavigationRequests>()
      return readAction {
        navigationRequests.sourceNavigationRequest(project = project, file = file, offset = element.textOffset, elementRange = null)
      }
    }
  }

  protected open suspend fun triggerLineOrColumnFeatureUsed(extendedNavigatable: Navigatable) {}

  private fun preparePsi(originalPsiElement: PsiElement, searchText: String): PsiElement {
    var psiElement = originalPsiElement
    pathToAnonymousClass(searchText)?.let {
      psiElement = getElement(psiElement, it)
    }
    return psiElement.navigationElement
  }
}

@Service(Service.Level.PROJECT)
private class SearchEverywhereContributorCoroutineScopeHolder(coroutineScope: CoroutineScope) {
  @JvmField
  val coroutineScope: CoroutineScope = coroutineScope.childScope("SearchEverywhereContributorCoroutineScopeHolder")
}

private val ourPatternToDetectLinesAndColumns: Pattern = Pattern.compile(
  "(.+?)" +  // name, non-greedy matching
  "(?::|@|,| |#|#L|\\?l=| on line | at line |:line |:?\\(|:?\\[)" +  // separator
  "(\\d+)?(?:\\W(\\d+)?)?" +  // line + column
  "[)\\]]?" // possible closing paren/brace
)

private fun getLineAndColumnRegexpGroup(text: String, groupNumber: Int): Int {
  val matcher = ourPatternToDetectLinesAndColumns.matcher(text)
  if (matcher.matches()) {
    try {
      if (groupNumber <= matcher.groupCount()) {
        val group = matcher.group(groupNumber)
        if (group != null) return group.toInt() - 1
      }
    }
    catch (ignored: NumberFormatException) {
    }
  }

  return -1
}

private fun pathToAnonymousClass(searchedText: String): String? {
  return pathToAnonymousClass(patternToDetectAnonymousClasses.matcher(searchedText))
}

internal fun pathToAnonymousClass(matcher: Matcher): String? {
  if (matcher.matches()) {
    var path = matcher.group(2)?.trim() ?: return null
    if (path.endsWith('$') && path.length >= 2) {
      path = path.substring(0, path.length - 2)
    }
    if (!path.isEmpty()) {
      return path
    }
  }

  return null
}