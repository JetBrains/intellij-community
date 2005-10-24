package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.UpdateHighlightersUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.IntentionActionComposite;
import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.DocumentReferenceByDocument;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.command.undo.UndoableAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Alexey Kudravtsev
 */
public final class QuickFixAction extends IntentionActionComposite {
  protected void addAvailableActions(HighlightInfo info, Editor editor, PsiFile file, List<Pair<IntentionAction, List<IntentionAction>>> list) {
    addAvailableActionsForGroups(info, editor, file, list, UpdateHighlightersUtil.NORMAL_HIGHLIGHT_GROUPS );
  }

  public static void registerQuickFixAction(HighlightInfo info, IntentionAction action, final List<IntentionAction> options) {
    registerQuickFixAction(info, null, action, options);
  }

  public static void registerQuickFixAction(HighlightInfo info, TextRange fixRange, IntentionAction action, final List<IntentionAction> options) {
    if (info == null || action == null) return;
    if (fixRange == null) fixRange = new TextRange(info.startOffset, info.endOffset);
    if (info.quickFixActionRanges == null) {
      info.quickFixActionRanges = new ArrayList<Pair<Pair<IntentionAction, List<IntentionAction>>, TextRange>>();
    }
    info.quickFixActionRanges.add(Pair.create(Pair.create(action, options), fixRange));
    info.fixStartOffset = Math.min (info.fixStartOffset, fixRange.getStartOffset());
    info.fixEndOffset = Math.max (info.fixEndOffset, fixRange.getEndOffset());
  }

  // make undoable action in current document in order to Undo action work from current file
  public static void markDocumentForUndo(PsiFile file) {
    Project project = file.getProject();
    final Document document = PsiDocumentManager.getInstance(project).getDocument(file);
    final DocumentReference ref = DocumentReferenceByDocument.createDocumentReference(document);
    UndoManager.getInstance(project).undoableActionPerformed(new UndoableAction() {
      public void undo() {}

      public void redo() {}

      public DocumentReference[] getAffectedDocuments() {
        return new DocumentReference[] {ref};
      }

      public boolean isComplex() { return false; }
    });
  }
}