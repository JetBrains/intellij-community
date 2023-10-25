// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("TestOnlyProblems") // KTIJ-19938

package com.intellij.lang.documentation.ide.impl

import com.intellij.lang.documentation.ide.ui.ExpandableDefinition
import com.intellij.lang.documentation.ide.ui.UISnapshot
import com.intellij.lang.documentation.ide.ui.UIState
import com.intellij.lang.documentation.ide.ui.createExpandableDefinition
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.platform.backend.documentation.ContentUpdater
import com.intellij.platform.backend.documentation.DocumentationContentData
import com.intellij.platform.backend.documentation.LinkData
import com.intellij.platform.backend.documentation.impl.DocumentationRequest
import com.intellij.platform.backend.documentation.impl.computeDocumentation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*

internal class DocumentationPage(val requests: List<DocumentationRequest>) {

  private val myContentFlow = MutableStateFlow<DocumentationPageContent?>(null)
  val request = requests.first()
  val contentFlow: SharedFlow<DocumentationPageContent?> = myContentFlow.asSharedFlow()
  val currentContent: DocumentationPageContent.Content? get() = myContentFlow.value as? DocumentationPageContent.Content
  var expandableDefinition: ExpandableDefinition? = null

  /**
   * @return `true` if some content was loaded, `false` if content is empty
   */
  suspend fun waitForContent(): Boolean {
    return myContentFlow.filterNotNull().first() is DocumentationPageContent.Content
  }

  suspend fun loadPage() {
    val data = try {
      computeDocumentation(request.targetPointer)
    }
    catch (e: IndexNotReadyException) {
      null // normal situation, nothing to do
    }
    if (data == null) {
      myContentFlow.value = DocumentationPageContent.Empty
      return
    }
    val uiState = data.anchor?.let(UIState::ScrollToAnchor) ?: UIState.Reset
    expandableDefinition = createExpandableDefinition(data.content)
    myContentFlow.value = prepareContent(data.content.copy(html = expandableDefinition?.getDecorated() ?: data.content.html), data.links, uiState)
    update(data.updates, data.links)
  }

  suspend fun updatePage(contentUpdater: ContentUpdater) {
    val pageContent = checkNotNull(currentContent)
    val (html, imageResolver) = pageContent.content
    val updates = contentUpdater.prepareContentUpdates(html).map {
      DocumentationContentData(it, imageResolver)
    }
    update(updates, pageContent.links)
  }

  private suspend fun update(updates: Flow<DocumentationContentData>, links: LinkData) {
    updates.flowOn(Dispatchers.Default).collectLatest {
      myContentFlow.value = prepareContent(it, links, uiState = null)
    }
  }

  private fun prepareContent(content: DocumentationContentData, links: LinkData, uiState: UIState?): DocumentationPageContent.Content {
    return DocumentationPageContent.Content(content, links, uiState)
  }

  /**
   * @return whether the page was restored
   */
  fun restorePage(snapshot: UISnapshot): Boolean {
    when (val pageContent = myContentFlow.value) {
      null -> {
        // This can happen in the following scenario:
        // 1. Show doc.
        // 2. Click a link, a new page will open and start loading.
        // 3. Invoke the Back action during "Fetching..." message.
        //    At this point the request from link is cancelled, but stored in the forward stack of the history.
        // 4. Invoke the Forward action.
        //    The page is taken from forward stack, but it was never loaded.
        //    Here we reload that cancelled request again
        return false
      }
      is DocumentationPageContent.Empty -> {
        // nothing to do here
        return true
      }
      is DocumentationPageContent.Content -> {
        // Consider the following steps:
        // 1. Open doc with anchor.
        // 2. Select some link with Tab.
        // 3. Activate the link, a new page will open.
        // 4. Go back, the previous page is restored, its last computed UI state is UIState.ScrollToAnchor.
        // Expected: scroll state and selected link from 2 are restored.
        // Without the following line: the doc scrolls to the anchor, computed in 1.
        // Regardless of last computed UIState, we always want to restore the snapshot.
        myContentFlow.value = pageContent.copy(uiState = UIState.RestoreFromSnapshot(snapshot))
        return true
      }
    }
  }
}

internal sealed class DocumentationPageContent {

  object Empty : DocumentationPageContent()

  data class Content(
    val content: DocumentationContentData,
    val links: LinkData,
    val uiState: UIState?,
  ) : DocumentationPageContent()
}
