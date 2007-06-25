package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.IntentionActionComposite;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Alexey Kudravtsev
 */
public final class QuickFixAction extends IntentionActionComposite {
  protected void addAvailableActions(HighlightInfo info, Editor editor, PsiFile file, List<HighlightInfo.IntentionActionDescriptor> list,
                                     final int passId) {
    addAvailableActionsForGroups(info, editor, file, list, passId == -1 ? null : new int[]{passId});
  }

  public static void registerQuickFixAction(HighlightInfo info, IntentionAction action, HighlightDisplayKey key, String displayName) {
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
}