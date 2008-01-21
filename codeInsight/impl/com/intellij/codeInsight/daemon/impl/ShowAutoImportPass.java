package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.quickfix.ImportClassFix;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.HintAction;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class ShowAutoImportPass extends TextEditorHighlightingPass {
  protected final Editor myEditor;

  protected final PsiFile myFile;

  private final int myStartOffset;
  private final int myEndOffset;

  public ShowAutoImportPass(@NotNull Project project, @NotNull Editor editor) {
    super(project, editor.getDocument());
    ApplicationManager.getApplication().assertIsDispatchThread();

    myEditor = editor;

    TextRange range = getVisibleRange(myEditor);
    myStartOffset = range.getStartOffset();
    myEndOffset = range.getEndOffset();

    myFile = PsiDocumentManager.getInstance(myProject).getPsiFile(myEditor.getDocument());
    assert myFile != null : FileDocumentManager.getInstance().getFile(myEditor.getDocument());
  }

  private static TextRange getVisibleRange(Editor editor) {
    Rectangle visibleRect = editor.getScrollingModel().getVisibleArea();

    LogicalPosition startPosition = editor.xyToLogicalPosition(new Point(visibleRect.x, visibleRect.y));
    int myStartOffset = editor.logicalPositionToOffset(startPosition);

    LogicalPosition endPosition = editor.xyToLogicalPosition(new Point(visibleRect.x + visibleRect.width, visibleRect.y + visibleRect.height));
    int myEndOffset = editor.logicalPositionToOffset(new LogicalPosition(endPosition.line + 1, 0));
    return new TextRange(myStartOffset, myEndOffset);
  }

  public void doCollectInformation(ProgressIndicator progress) {
  }

  public void doApplyInformationToEditor() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (!myEditor.getContentComponent().hasFocus()) return;
    doMyJob();
  }

  protected boolean doMyJob() {
    HighlightInfo[] visibleHighlights = getVisibleHighlights(myStartOffset, myEndOffset, myProject, myEditor);

    PsiElement[] elements = new PsiElement[visibleHighlights.length];
    for (int i = 0; i < visibleHighlights.length; i++) {
      ProgressManager.getInstance().checkCanceled();

      HighlightInfo highlight = visibleHighlights[i];
      final PsiElement elementAt = myFile.findElementAt(highlight.startOffset);
      elements[i] = elementAt;
    }

    int caretOffset = myEditor.getCaretModel().getOffset();
    for (int i = visibleHighlights.length - 1; i >= 0; i--) {
      HighlightInfo info = visibleHighlights[i];
      if (elements[i] != null && info.startOffset <= caretOffset && showAddImportHint(info, elements[i])) return true;
    }

    for (int i = 0; i < visibleHighlights.length; i++) {
      HighlightInfo info = visibleHighlights[i];
      if (elements[i] != null && info.startOffset > caretOffset && showAddImportHint(info, elements[i])) return true;
    }
    return false;
  }

  @NotNull
  private static HighlightInfo[] getVisibleHighlights(int startOffset, int endOffset, Project project, Editor editor) {
    HighlightInfo[] highlights = DaemonCodeAnalyzerImpl.getHighlights(editor.getDocument(), project);
    if (highlights == null) return HighlightInfo.EMPTY_ARRAY;

    List<HighlightInfo> array = new ArrayList<HighlightInfo>();
    for (HighlightInfo info : highlights) {
      if (info.hasHint()
          && startOffset <= info.startOffset && info.endOffset <= endOffset
          && !editor.getFoldingModel().isOffsetCollapsed(info.startOffset)) {
        array.add(info);
      }
    }
    return array.toArray(new HighlightInfo[array.size()]);
  }

  protected boolean showAddImportHint(HighlightInfo info, PsiElement element) {
    if (!DaemonCodeAnalyzerSettings.getInstance().isImportHintEnabled()) return false;
    if (!DaemonCodeAnalyzer.getInstance(myProject).isImportHintsEnabled(myFile)) return false;
    if (!element.isValid()) return false;

    final List<Pair<HighlightInfo.IntentionActionDescriptor, TextRange>> list = info.quickFixActionRanges;
    for (Pair<HighlightInfo.IntentionActionDescriptor, TextRange> pair : list) {
      final IntentionAction action = pair.getFirst().getAction();
      if (action instanceof HintAction) {
        return ((HintAction)action).showHint(myEditor);
      }
    }
    return false;
  }

  public static void autoImportReferenceAtCursor(@NotNull Editor editor, @NotNull PsiFile file) {
    int caretOffset = editor.getCaretModel().getOffset();
    Document document = editor.getDocument();
    int lineNumber = document.getLineNumber(caretOffset);
    int startOffset = document.getLineStartOffset(lineNumber);
    int endOffset = document.getLineEndOffset(lineNumber);

    List<PsiElement> elements = CodeInsightUtil.getElementsInRange(file, startOffset, endOffset);
    for (PsiElement element : elements) {
      if (element instanceof PsiJavaCodeReferenceElement) {
        PsiJavaCodeReferenceElement ref = (PsiJavaCodeReferenceElement)element;
        if (ref.resolve() == null) {
          new ImportClassFix(ref).doFix(editor, false);
        }
      }
    }
  }

  public static String getMessage(final boolean multiple, final String name) {
    final @NonNls String messageKey = multiple ? "import.popup.multiple" : "import.popup.text";
    String hintText = QuickFixBundle.message(messageKey, name);
    hintText += " " + KeymapUtil.getFirstKeyboardShortcutText(ActionManager.getInstance().getAction(IdeActions.ACTION_SHOW_INTENTION_ACTIONS));
    return hintText;
  }
}