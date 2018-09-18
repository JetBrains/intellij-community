// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeInsight.daemon.DaemonBundle;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.codeInsight.daemon.ReferenceImporter;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.HintAction;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
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

  @Override
  public void doCollectInformation(@NotNull ProgressIndicator progress) {
  }

  @Override
  public void doApplyInformationToEditor() {
    TransactionGuard.submitTransaction(myProject, this::addImports);
  }

  public void addImports() {
    Application application = ApplicationManager.getApplication();
    application.assertIsDispatchThread();
    if (!application.isHeadlessEnvironment() && !myEditor.getContentComponent().hasFocus()) return;
    if (DumbService.isDumb(myProject) || !myFile.isValid()) return;
    if (myEditor.isDisposed() || myEditor instanceof EditorWindow && !((EditorWindow)myEditor).isValid()) return;

    int caretOffset = myEditor.getCaretModel().getOffset();
    importUnambiguousImports(caretOffset);
    List<HighlightInfo> visibleHighlights = getVisibleHighlights(myStartOffset, myEndOffset, myProject, myEditor);

    for (int i = visibleHighlights.size() - 1; i >= 0; i--) {
      HighlightInfo info = visibleHighlights.get(i);
      if (info.startOffset <= caretOffset && showAddImportHint(info)) return;
    }

    for (HighlightInfo visibleHighlight : visibleHighlights) {
      if (visibleHighlight.startOffset > caretOffset && showAddImportHint(visibleHighlight)) return;
    }
  }

  private void importUnambiguousImports(final int caretOffset) {
    if (!DaemonCodeAnalyzerSettings.getInstance().isImportHintEnabled()) return;
    if (!DaemonCodeAnalyzer.getInstance(myProject).isImportHintsEnabled(myFile)) return;

    Document document = getDocument();
    final List<HighlightInfo> infos = new ArrayList<>();
    DaemonCodeAnalyzerEx.processHighlights(document, myProject, null, 0, document.getTextLength(), info -> {
      if (!info.hasHint() || info.getSeverity() != HighlightSeverity.ERROR) {
        return true;
      }
      PsiReference reference = myFile.findReferenceAt(info.getActualStartOffset());
      if (reference != null && reference.getElement().getTextRange().containsOffset(caretOffset)) return true;
      infos.add(info);
      return true;
    });

    ReferenceImporter[] importers = Extensions.getExtensions(ReferenceImporter.EP_NAME);
    for (HighlightInfo info : infos) {
      for(ReferenceImporter importer: importers) {
        if (importer.autoImportReferenceAt(myEditor, myFile, info.getActualStartOffset())) break;
      }
    }
  }

  @NotNull
  private static List<HighlightInfo> getVisibleHighlights(final int startOffset, final int endOffset, Project project, final Editor editor) {
    final List<HighlightInfo> highlights = new ArrayList<>();
    DaemonCodeAnalyzerEx.processHighlights(editor.getDocument(), project, null, startOffset, endOffset, info -> {
      if (info.hasHint() && !editor.getFoldingModel().isOffsetCollapsed(info.startOffset)) {
        highlights.add(info);
      }
      return true;
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
