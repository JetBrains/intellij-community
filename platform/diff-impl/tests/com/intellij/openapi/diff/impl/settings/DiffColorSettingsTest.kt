/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.diff.impl.settings

import com.intellij.diff.HeavyDiffTestCase
import com.intellij.diff.tools.simple.SimpleThreesideDiffViewer
import com.intellij.diff.util.TextDiffType
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.RangeHighlighter

class DiffColorSettingsTest : HeavyDiffTestCase() {
  fun testChanges() {
    var panel: DiffPreviewPanel? = null
    try {
      panel = DiffPreviewPanel()
      val viewer = panel.testGetViewer()

      assertEquals(viewer.changes.size, 6)
      assertContainsRange(viewer, TextDiffType.MODIFIED)
      assertContainsRange(viewer, TextDiffType.INSERTED)
      assertContainsRange(viewer, TextDiffType.DELETED)
      assertContainsRange(viewer, TextDiffType.CONFLICT)

      assertContainsBackgroundColor(viewer, TextDiffType.MODIFIED, true)
      assertContainsBackgroundColor(viewer, TextDiffType.INSERTED, true)
      assertContainsBackgroundColor(viewer, TextDiffType.DELETED, true)
      assertContainsBackgroundColor(viewer, TextDiffType.CONFLICT, true)

      assertContainsBackgroundColor(viewer, TextDiffType.MODIFIED, false)
      assertContainsBackgroundColor(viewer, TextDiffType.INSERTED, false)
      assertContainsBackgroundColor(viewer, TextDiffType.DELETED, false)
      assertContainsBackgroundColor(viewer, TextDiffType.CONFLICT, false)

      assertContainsMarkerColor(viewer, TextDiffType.MODIFIED)
      assertContainsMarkerColor(viewer, TextDiffType.INSERTED)
      assertContainsMarkerColor(viewer, TextDiffType.DELETED)
      assertContainsMarkerColor(viewer, TextDiffType.CONFLICT)

      assertContainsFoldedFragment(viewer)
    }
    finally {
      panel?.disposeUIResources()
    }
  }

  private fun assertContainsRange(viewer: SimpleThreesideDiffViewer, type: TextDiffType) {
    assertNotNull(viewer.changes.find { change ->
      change.diffType == type
    })
  }

  private fun assertContainsBackgroundColor(viewer: SimpleThreesideDiffViewer, type: TextDiffType, isIgnored: Boolean) {
    assertContainsBackgroundColor(viewer) { editor, highlighter ->
      val actual = highlighter.textAttributes?.backgroundColor
      val expected = if (isIgnored) type.getIgnoredColor(editor) else type.getColor(editor)
      actual == expected
    }
  }

  private fun assertContainsMarkerColor(viewer: SimpleThreesideDiffViewer, type: TextDiffType) {
    assertContainsBackgroundColor(viewer) { editor, highlighter ->
      highlighter.textAttributes?.errorStripeColor == type.getMarkerColor(editor)
    }
  }

  private fun assertContainsBackgroundColor(viewer: SimpleThreesideDiffViewer, condition: (EditorEx, RangeHighlighter) -> Boolean) {
    val ranges = viewer.editors.flatMap { editor ->
      listOfNotNull(editor.markupModel.allHighlighters.map { highlighter ->
        return@map if (condition(editor, highlighter)) highlighter else null
      })
    }
    assertTrue(ranges.isNotEmpty())
  }

  private fun assertContainsFoldedFragment(viewer: SimpleThreesideDiffViewer) {
    assertTrue(viewer.editors.all {
      it.foldingModel.allFoldRegions.any { !it.isExpanded }
    })
  }
}
