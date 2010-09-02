/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeInsight.daemon.DaemonBundle;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.HintAction;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class ShowAutoImportPass extends TextEditorHighlightingPass {
  private final Editor myEditor;

  private final PsiFile myFile;

  private final int myStartOffset;
  private final int myEndOffset;

  public ShowAutoImportPass(@NotNull Project project, @NotNull final PsiFile file, @NotNull Editor editor) {
    super(project, editor.getDocument(), false);
    ApplicationManager.getApplication().assertIsDispatchThread();

    myEditor = editor;

    TextRange range = VisibleHighlightingPassFactory.calculateVisibleRange(myEditor);
    myStartOffset = range.getStartOffset();
    myEndOffset = range.getEndOffset();

    myFile = file;
  }

  public void doCollectInformation(ProgressIndicator progress) {
  }

  public void doApplyInformationToEditor() {
    Application application = ApplicationManager.getApplication();
    application.assertIsDispatchThread();
    if (!application.isUnitTestMode() && !myEditor.getContentComponent().hasFocus()) return;
    List<HighlightInfo> visibleHighlights = getVisibleHighlights(myStartOffset, myEndOffset, myProject, myEditor);

    int caretOffset = myEditor.getCaretModel().getOffset();
    for (int i = visibleHighlights.size() - 1; i >= 0; i--) {
      HighlightInfo info = visibleHighlights.get(i);
      if (info.startOffset <= caretOffset && showAddImportHint(info)) return;
    }

    for (HighlightInfo visibleHighlight : visibleHighlights) {
      if (visibleHighlight.startOffset > caretOffset && showAddImportHint(visibleHighlight)) return;
    }
  }

  @NotNull
  private static List<HighlightInfo> getVisibleHighlights(final int startOffset, final int endOffset, Project project, final Editor editor) {
    final List<HighlightInfo> highlights = new ArrayList<HighlightInfo>();
    DaemonCodeAnalyzerImpl.processHighlights(editor.getDocument(), project, null, startOffset, endOffset, new Processor<HighlightInfo>() {
      public boolean process(HighlightInfo info) {
        if (info.hasHint() && !editor.getFoldingModel().isOffsetCollapsed(info.startOffset)) {
          highlights.add(info);
        }
        return true;
      }
    });
    return highlights;
  }

  private boolean showAddImportHint(HighlightInfo info) {
    if (!DaemonCodeAnalyzerSettings.getInstance().isImportHintEnabled()) return false;
    if (!DaemonCodeAnalyzer.getInstance(myProject).isImportHintsEnabled(myFile)) return false;
    PsiElement element = myFile.findElementAt(info.startOffset);
    if (element == null || !element.isValid()) return false;

    final List<Pair<HighlightInfo.IntentionActionDescriptor, TextRange>> list = info.quickFixActionRanges;
    for (Pair<HighlightInfo.IntentionActionDescriptor, TextRange> pair : list) {
      final IntentionAction action = pair.getFirst().getAction();
      if (action instanceof HintAction && action.isAvailable(myProject, myEditor, myFile)) {
        return ((HintAction)action).showHint(myEditor);
      }
    }
    return false;
  }

  public static String getMessage(final boolean multiple, final String name) {
    final String messageKey = multiple ? "import.popup.multiple" : "import.popup.text";
    String hintText = DaemonBundle.message(messageKey, name);
    hintText += " " + KeymapUtil.getFirstKeyboardShortcutText(ActionManager.getInstance().getAction(IdeActions.ACTION_SHOW_INTENTION_ACTIONS));
    return hintText;
  }
}
