package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.UpdateHighlightersUtil;
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
  protected void addAvailableActions(HighlightInfo info, Editor editor, PsiFile file, ArrayList<HighlightInfo.IntentionActionDescriptor> list) {
    addAvailableActionsForGroups(info, editor, file, list, UpdateHighlightersUtil.NORMAL_HIGHLIGHT_GROUPS );
  }

  public static void registerQuickFixAction(HighlightInfo info, IntentionAction action, List<IntentionAction> options, String displayName) {
    registerQuickFixAction(info, null, action, options, displayName);
  }

  public static void registerQuickFixAction(HighlightInfo info, IntentionAction action) {
    registerQuickFixAction(info, null, action, null, null);
  }


  public static void registerQuickFixAction(HighlightInfo info, TextRange fixRange, IntentionAction action, final List<IntentionAction> options, final String displayName) {
    if (info == null || action == null) return;
    if (fixRange == null) fixRange = new TextRange(info.startOffset, info.endOffset);
    if (info.quickFixActionRanges == null) {
      info.quickFixActionRanges = new ArrayList<Pair<HighlightInfo.IntentionActionDescriptor, TextRange>>();
    }
    info.quickFixActionRanges.add(Pair.create(new HighlightInfo.IntentionActionDescriptor(action, options, displayName), fixRange));
    info.fixStartOffset = Math.min (info.fixStartOffset, fixRange.getStartOffset());
    info.fixEndOffset = Math.max (info.fixEndOffset, fixRange.getEndOffset());
  }

}