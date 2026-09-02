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
import com.intellij.openapi.vfs.newvfs.CacheAvoidingVirtualFile
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
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.util.PsiUtilCore
import com.intellij.util.IntPair
import com.intellij.util.concurrency.annotations.RequiresReadLock
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
    val navigationOptions = NavigationOptions.defaultOptions()
      .openInRightSplit((modifiers and InputEvent.SHIFT_DOWN_MASK) != 0)
      .preserveCaret(true).forceFocus(true)
    // the client id must reach the navigation
    project.service<SearchEverywhereContributorCoroutineScopeHolder>().coroutineScope.launch(ClientId.coroutineContext()) {
      val navigationService = project.serviceAsync<NavigationService>()
      semaphore.withPermit {
        navigationService.navigateRequests(navigationOptions) {
          makeNavigationRequests(selected, searchText, offset)
        }
      }
    }
  }

  private suspend fun makeNavigationRequests(selected: PsiElement, searchText: String, offset: Int): Collection<NavigationRequest> {
    val target = readAction {
      if (!selected.isValid) {
        LOG.warn("Cannot navigate to invalid PsiElement")
        return@readAction null
      }

      val psiElement = preparePsi(selected, searchText)
      val file =
        if (selected is PsiFile) selected.virtualFile
        else PsiUtilCore.getVirtualFile(psiElement)
      psiElement to file
    } ?: return emptyList()

    val (rawPsiElement, rawFile) = target
    if (rawFile == null) {
      // Navigation items from rd protocol often lack .containingFile or other PSI extensions, and are only expected to be
      // navigated through the Navigatable API.
      // This fallback is for items like that.
      val navigatable = rawPsiElement as? Navigatable
      if (navigatable == null) {
        LOG.warn("Cannot navigate to invalid PsiElement (psiElement=$rawPsiElement)")
        return emptyList()
      }
      return listOf(RawNavigationRequest(navigatable, true))
    }

    // An item may be backed by a cache-avoiding file (e.g. a file outside the project found by its absolute path).
    // Now that the user is really opening it, the file has to become a regular one: an editor, its document and VFS events
    // all rely on a single VirtualFile instance per path, which cache-avoiding files do not provide.
    // Deliberately done outside the read action above: it does IO and puts new entries into the VFS cache.
    val file = rawFile.asCacheableOrSelf()
    if (file == null) {
      LOG.warn("Cannot navigate: $rawFile cannot be added to VFS")
      return emptyList()
    }

    val extendedNavigatable = lineAndColumnNavigatable(file, searchText)
    if (extendedNavigatable != null) {
      // navigates by the file alone, so there is no need to build PSI for the element
      triggerLineOrColumnFeatureUsed(extendedNavigatable)
      return listOfNotNull(rawNavigationRequest(extendedNavigatable))
    }

    @Suppress("UseVirtualFileEquals")
    val psiElement = if (file === rawFile) rawPsiElement else readAction { reResolveOnCacheableFile(rawPsiElement, file) }
    return listOfNotNull(
      createSourceNavigationRequest(project = project, element = psiElement, file = file, searchText = searchText, offset = offset)
    )
  }

  /**
   * @return a regular VFS file for [this], adding it to the VFS cache if needed, or null if it no longer exists;
   * [this] itself if it is a regular file already
   */
  private fun VirtualFile.asCacheableOrSelf(): VirtualFile? =
    if (this is CacheAvoidingVirtualFile) asCacheable() else this

  /**
   * Re-resolves an element that was found on a cache-avoiding [file] against its regular counterpart,
   * so that navigation doesn't use the cache-avoiding file behind our back.
   *
   * Only file system items can be re-resolved this way; for anything else navigation may still go through
   * the cache-avoiding file, hence the warning.
   */
  @RequiresReadLock
  private fun reResolveOnCacheableFile(element: PsiElement, file: VirtualFile): PsiElement {
    val reResolved = if (element is PsiFileSystemItem) PsiUtilCore.findFileSystemItem(project, file) else null
    if (reResolved == null) {
      LOG.warn("Cannot re-resolve $element on $file, navigating through a cache-avoiding file")
      return element
    }
    return reResolved
  }

  private fun lineAndColumnNavigatable(file: VirtualFile, searchText: String): OpenFileDescriptor? {
    val position = getLineAndColumn(searchText)
    if (position.first < 0 && position.second < 0) {
      return null
    }
    //todo create a navigation request by line&column, not by offset only
    return OpenFileDescriptor(project, file, position.first, position.second)
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

  /**
   * The same request [NavigationService] would build for [navigatable] on its own, see [Navigatable.navigationRequest].
   */
  private fun rawNavigationRequest(navigatable: Navigatable): NavigationRequest? {
    return if (navigatable.canNavigate()) RawNavigationRequest(navigatable, navigatable.canNavigateToSource()) else null
  }

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