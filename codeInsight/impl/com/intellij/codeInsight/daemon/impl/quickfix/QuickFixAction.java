package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * @author Alexey Kudravtsev
 */
public final class QuickFixAction {
  public static void registerQuickFixAction(HighlightInfo info, IntentionAction action, HighlightDisplayKey key) {
    registerQuickFixAction(info, null, action, key);
  }

  public static void registerQuickFixAction(HighlightInfo info, IntentionAction action) {
    registerQuickFixAction(info, null, action, null);
  }

  @Deprecated
  public static void registerQuickFixAction(HighlightInfo info, IntentionAction action, List<IntentionAction> options, String displayName) {
    if (info == null || action == null) return;
    final TextRange fixRange = new TextRange(info.startOffset, info.endOffset);
    if (info.quickFixActionRanges == null) {
      info.quickFixActionRanges = new ArrayList<Pair<HighlightInfo.IntentionActionDescriptor, TextRange>>();
    }
    info.quickFixActionRanges.add(Pair.create(new HighlightInfo.IntentionActionDescriptor(action, options, displayName), fixRange));
    info.fixStartOffset = Math.min (info.fixStartOffset, fixRange.getStartOffset());
    info.fixEndOffset = Math.max (info.fixEndOffset, fixRange.getEndOffset());
  }

  public static void registerQuickFixAction(HighlightInfo info, TextRange fixRange, IntentionAction action, final HighlightDisplayKey key) {
    if (info == null || action == null) return;
    if (fixRange == null) fixRange = new TextRange(info.startOffset, info.endOffset);
    if (info.quickFixActionRanges == null) {
      info.quickFixActionRanges = new ArrayList<Pair<HighlightInfo.IntentionActionDescriptor, TextRange>>();
    }
    info.quickFixActionRanges.add(Pair.create(new HighlightInfo.IntentionActionDescriptor(action, key), fixRange));
    info.fixStartOffset = Math.min (info.fixStartOffset, fixRange.getStartOffset());
    info.fixEndOffset = Math.max (info.fixEndOffset, fixRange.getEndOffset());
  }

  public static void removeAction(Editor editor, Project project, IntentionAction action) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    int offset = editor.getCaretModel().getOffset();

    DaemonCodeAnalyzer codeAnalyzer = DaemonCodeAnalyzer.getInstance(project);
    HighlightInfo info = ((DaemonCodeAnalyzerImpl)codeAnalyzer).findHighlightByOffset(editor.getDocument(), offset, true);
    if (info != null) {
      removeFrom(info.quickFixActionMarkers, action);
      removeFrom(info.quickFixActionRanges, action);
    }
  }

  private static <T> void removeFrom(List<Pair<HighlightInfo.IntentionActionDescriptor, T>> list, IntentionAction action) {
    Iterator<Pair<HighlightInfo.IntentionActionDescriptor, T>> iterator = list.iterator();
    while (iterator.hasNext()) {
      Pair<HighlightInfo.IntentionActionDescriptor, T> pair = iterator.next();
      if (pair.getFirst().getAction() == action) {
        iterator.remove();
        break;
      }
    }
  }

  /**
   * Is invoked inside atomic action.
   */
  public static List<HighlightInfo.IntentionActionDescriptor> getAvailableActions(@NotNull Editor editor, @NotNull PsiFile file, final int passId) {
    int offset = editor.getCaretModel().getOffset();
    final Project project = file.getProject();
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    DaemonCodeAnalyzer codeAnalyzer = DaemonCodeAnalyzer.getInstance(project);
    HighlightInfo info = ((DaemonCodeAnalyzerImpl)codeAnalyzer).findHighlightByOffset(editor.getDocument(), offset, true);
    List<HighlightInfo.IntentionActionDescriptor> list = new ArrayList<HighlightInfo.IntentionActionDescriptor>();
    addAvailableActionsForGroups(info, editor, file, list, passId == -1 ? null : new int[]{passId});
    return list;
  }

  private static void addAvailableActionsForGroups(HighlightInfo info, Editor editor, PsiFile file, List<HighlightInfo.IntentionActionDescriptor> outList, int[] groups) {
    int offset = editor.getCaretModel().getOffset();
    if (info == null || info.quickFixActionMarkers == null) return;
    if (groups != null && Arrays.binarySearch(groups, info.group) < 0) return;
    for (Pair<HighlightInfo.IntentionActionDescriptor, RangeMarker> pair : info.quickFixActionMarkers) {
      HighlightInfo.IntentionActionDescriptor actionInGroup = pair.first;
      RangeMarker range = pair.second;
      if (range.isValid()) {
        int start = range.getStartOffset();
        int end = range.getEndOffset();
        final Project project = file.getProject();
        if (start <= offset && offset <= end && actionInGroup.getAction().isAvailable(project, editor, file)) {
          outList.add(actionInGroup);
        }
      }
    }
  }

}