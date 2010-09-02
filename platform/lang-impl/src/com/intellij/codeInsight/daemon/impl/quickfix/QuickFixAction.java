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

package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.HintAction;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
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
    doregister(info, action, options, displayName, null, null);
  }

  private static void doregister(HighlightInfo info,
                                 IntentionAction action,
                                 List<IntentionAction> options,
                                 String displayName,
                                 TextRange fixRange,
                                 HighlightDisplayKey key) {
    if (info == null || action == null) return;
    if (fixRange == null) fixRange = new TextRange(info.startOffset, info.endOffset);
    if (info.quickFixActionRanges == null) {
      info.quickFixActionRanges = ContainerUtil.createEmptyCOWList();
    }
    HighlightInfo.IntentionActionDescriptor desc = new HighlightInfo.IntentionActionDescriptor(action, options, displayName, null, key);
    info.quickFixActionRanges.add(Pair.create(desc, fixRange));
    info.fixStartOffset = Math.min (info.fixStartOffset, fixRange.getStartOffset());
    info.fixEndOffset = Math.max (info.fixEndOffset, fixRange.getEndOffset());
    if (action instanceof HintAction) {
      info.setHint(true);
    }
  }

  public static void registerQuickFixAction(HighlightInfo info, TextRange fixRange, IntentionAction action, final HighlightDisplayKey key) {
    doregister(info, action, null, HighlightDisplayKey.getDisplayNameByKey(key), fixRange, key);
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
  public static List<HighlightInfo.IntentionActionDescriptor> getAvailableActions(@NotNull final Editor editor, @NotNull final PsiFile file, final int passId) {
    final int offset = editor.getCaretModel().getOffset();
    final Project project = file.getProject();

    final List<HighlightInfo.IntentionActionDescriptor> result = new ArrayList<HighlightInfo.IntentionActionDescriptor>();
    DaemonCodeAnalyzerImpl.processHighlightsNearOffset(editor.getDocument(), project, HighlightSeverity.INFORMATION, offset, true, new Processor<HighlightInfo>() {
      public boolean process(HighlightInfo info) {
        addAvailableActionsForGroups(info, editor, file, result, passId, offset);
        return true;
      }
    });
    return result;
  }

  private static void addAvailableActionsForGroups(HighlightInfo info,
                                                   Editor editor,
                                                   PsiFile file,
                                                   List<HighlightInfo.IntentionActionDescriptor> outList,
                                                   int group,
                                                   int offset) {
    if (info == null || info.quickFixActionMarkers == null) return;
    if (group != -1 && group != info.group) return;
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
