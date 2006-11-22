/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactory;
import com.intellij.codeHighlighting.Pass;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import com.intellij.util.ArrayUtil;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * User: anna
 * Date: 19-Apr-2006
 */
public class TextEditorHighlightingPassRegistrarImpl extends TextEditorHighlightingPassRegistrarEx {
  private boolean myNeedAdditionalIntentionsPass = false;
  private final List<PassInfo> myRegisteredPasses = new ArrayList<PassInfo>();
  private int[] myPostHighlightingPassGroups = UpdateHighlightersUtil.POST_HIGHLIGHT_GROUPS;
  private final TIntObjectHashMap<int[]> predecessors = new TIntObjectHashMap<int[]>();


  public TextEditorHighlightingPassRegistrarImpl() {
    predecessors.put(Pass.POST_UPDATE_ALL, new int[]{Pass.UPDATE_ALL, Pass.UPDATE_VISIBLE});
    predecessors.put(Pass.POPUP_HINTS2, new int[]{Pass.POPUP_HINTS, Pass.UPDATE_ALL, Pass.UPDATE_VISIBLE, Pass.LOCAL_INSPECTIONS});
  }

  private static class PassInfo {
    TextEditorHighlightingPassFactory passFactory;
    Anchor anchor;
    int anchorPassId;

    public PassInfo(final TextEditorHighlightingPassFactory passFactory, final Anchor anchor, final int anchorPassId) {
      this.passFactory = passFactory;
      this.anchor = anchor;
      this.anchorPassId = anchorPassId;
    }
  }

  public void registerTextEditorHighlightingPass(TextEditorHighlightingPassFactory factory, int anchor, int anchorPass) {
    Anchor anc = Anchor.FIRST;
    switch (anchor) {
      case FIRST : anc = Anchor.FIRST;
        break;
      case LAST : anc = Anchor.LAST;
        break;
      case BEFORE : anc = Anchor.BEFORE;
        break;
      case AFTER : anc = Anchor.AFTER;
        break;
    }
    registerTextEditorHighlightingPass(factory, anc, anchorPass, true, true);
  }

  public int registerTextEditorHighlightingPass(TextEditorHighlightingPassFactory factory, Anchor anchor, int anchorPassId, boolean needAdditionalPass, boolean inPostHighlightingPass) {
    if (inPostHighlightingPass) {
      myPostHighlightingPassGroups = ArrayUtil.mergeArrays(myPostHighlightingPassGroups,
                                                           new int[] {myPostHighlightingPassGroups[myPostHighlightingPassGroups.length - 1] + 1});
    }
    myRegisteredPasses.add(new PassInfo(factory, anchor, anchorPassId));
    if (needAdditionalPass) {
      myNeedAdditionalIntentionsPass = true;
    }
    return myPostHighlightingPassGroups[myPostHighlightingPassGroups.length - 1];
  }

  public void modifyHighlightingPasses(final List<TextEditorHighlightingPass> passes,
                                                               final PsiFile psiFile,
                                                               final Editor editor) {
    if (myRegisteredPasses == null || psiFile == null){ //do nothing with non-project files
      return;
    }
    for (PassInfo passInfo : myRegisteredPasses) {
      TextEditorHighlightingPassFactory factory = passInfo.passFactory;
      final TextEditorHighlightingPass editorHighlightingPass = factory.createHighlightingPass(psiFile, editor);
      if (editorHighlightingPass == null) continue;
      final Anchor anchor = passInfo.anchor;
      if (anchor == Anchor.FIRST) {
        passes.add(0, editorHighlightingPass);
      }
      else if (anchor == Anchor.LAST) {
        passes.add(editorHighlightingPass);
      }
      else {
        final int passId = passInfo.anchorPassId;
        int anchorPassIdx = -1;
        for (int idx = 0; idx < passes.size(); idx++) {
          final TextEditorHighlightingPass highlightingPass = passes.get(idx);
          if (highlightingPass.getPassId() == passId) {
            anchorPassIdx = idx;
            break;
          }
        }
        if (anchorPassIdx == -1) {
          passes.add(editorHighlightingPass);
        }
        else {
          if (anchor == Anchor.BEFORE) {
            passes.add(Math.max(0, anchorPassIdx - 1), editorHighlightingPass);
          }
          else {
            passes.add(anchorPassIdx + 1, editorHighlightingPass);
          }
        }
      }
    }
  }

  public boolean needAdditionalIntentionsPass() {
    return myNeedAdditionalIntentionsPass;
  }

  @Nullable
  public int[] getPostHighlightingPasses() {
    return myPostHighlightingPassGroups;
  }

  public int[] getPredecessors(int passId) {
    return predecessors.get(passId);
  }

  @NotNull
  @NonNls
  public String getComponentName() {
    return "TextEditorHighlightingPassRegistrarImpl";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public void projectOpened() {

  }

  public void projectClosed() {
  }
}
