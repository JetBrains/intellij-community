package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.HintAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
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
  private QuickFixAction() {
  }

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
    if (action instanceof HintAction) {
      info.setHint(true);
    }
  }

  public static void unregisterQuickFixAction(HighlightInfo info, Condition<IntentionAction> condition) {
    for (Iterator<Pair<HighlightInfo.IntentionActionDescriptor, TextRange>> it = info.quickFixActionRanges.iterator(); it.hasNext();) {
      Pair<HighlightInfo.IntentionActionDescriptor, TextRange> pair = it.next();
      if (condition.value(pair.first.getAction())) {
        it.remove();
      }
    }
  }

  /**
   * Is invoked inside atomic action.
   */
  @NotNull
  public static List<HighlightInfo.IntentionActionDescriptor> getAvailableActions(@NotNull Editor editor, @NotNull PsiFile file, final int passId) {
    int offset = editor.getCaretModel().getOffset();
    final Project project = file.getProject();
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    List<HighlightInfo.IntentionActionDescriptor> result = new ArrayList<HighlightInfo.IntentionActionDescriptor>();
    HighlightInfo[] infos = DaemonCodeAnalyzerImpl.getHighlightsAround(editor.getDocument(), project, offset);
    int[] groups = passId == -1 ? null : new int[]{passId};
    for (HighlightInfo info : infos) {
      addAvailableActionsForGroups(info, editor, file, result, groups, offset);
    }
    return result;
  }

  private static void addAvailableActionsForGroups(HighlightInfo info, Editor editor, PsiFile file, List<HighlightInfo.IntentionActionDescriptor> outList,
                                                   int[] groups,
                                                   int offset) {
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
