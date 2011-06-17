/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.editor.ex;

import com.intellij.injected.editor.DocumentWindow;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.impl.EditorRepaintStrategy;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * This strategy handles the situation when typing is performed inside parsed injected text. Usually editor's highlighter
 * doesn't know anything about injected context internals and sees it just as a single big token (e.g. MXML files may contain
 * Flash code inside CDATA comment and the highlighter (based on XML lexer) considers the whole Flash code block to be just
 * a CDATA token).
 * <p/>
 * So, every time user types at injected context, highlighter receives document change event and asks editor to repaint
 * affected token. That is rather heavy operation if performed frequently for the large text range. That's why current
 * strategy handles such requests to the whole injected context repaint and skips them.
 * <p/>
 * It's assumed that corresponding repainting is performed during editor's markup model updates triggered by injected
 * context processing.
 * 
 * @author Denis Zhdanov
 * @since 6/17/11 11:16 AM
 */
public class InjectedAwareEditorRepaintStrategy implements EditorRepaintStrategy {

  @Override
  public TextRange adjustHighlighterRegion(@NotNull EditorEx editor, int startOffset, int endOffset) {
    return isInjectedContext(editor, startOffset, endOffset) ? null : new TextRange(startOffset, endOffset);
  }

  private static boolean isInjectedContext(@NotNull EditorEx editor, int startOffset, int endOffset) {
    final Project project = editor.getProject();
    if (project == null) {
      return false;
    }
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
    final PsiFile psiFile = documentManager.getCachedPsiFile(editor.getDocument());
    if (psiFile == null) {
      return false;
    }

    final List<DocumentWindow> injectedDocuments = InjectedLanguageUtil.getCachedInjectedDocuments(psiFile);
    for (DocumentWindow injectedDocument : injectedDocuments) {
      for (RangeMarker rangeMarker : injectedDocument.getHostRanges()) {
        if (Math.max(rangeMarker.getStartOffset(), startOffset) <= Math.min(rangeMarker.getEndOffset(), endOffset)) {
          return true;
        }
      }
    }
    return false;
  }
}
