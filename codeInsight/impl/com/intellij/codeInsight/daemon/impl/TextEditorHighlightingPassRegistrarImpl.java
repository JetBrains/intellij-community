/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactory;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: anna
 * Date: 19-Apr-2006
 */
public class TextEditorHighlightingPassRegistrarImpl extends TextEditorHighlitingPassRegistrarEx {
  private boolean myNeedAdditionalIntentionsPass = false;
  private Map<TextEditorHighlightingPassFactory, TextEditorPassBean> myRegisteredPasses = null;
  private int[] myPostHighlighterPasses = UpdateHighlightersUtil.POST_HIGHLIGHT_GROUPS;

  public void registerTextEditorHighlightingPass(TextEditorHighlightingPassFactory factory, Anchor anchor, int anchorPass, boolean needAdditionalPass) {
    registerTextEditorHighlightingPass(factory, anchor, anchorPass, needAdditionalPass, -1, false);
  }


  public void registerTextEditorHighlightingPass(TextEditorHighlightingPassFactory factory,
                                                 Anchor anchor,
                                                 int anchorPass,
                                                 boolean needAdditionalPass,
                                                 int selfPassNumber,
                                                 boolean inPostHighlighterGroup) {
    if (myRegisteredPasses == null){
      myRegisteredPasses = new HashMap<TextEditorHighlightingPassFactory, TextEditorPassBean>();
    }
    if (selfPassNumber != -1 && inPostHighlighterGroup) {
      myPostHighlighterPasses = ArrayUtil.mergeArrays(myPostHighlighterPasses, new int[] {selfPassNumber} );      
    }
    myRegisteredPasses.put(factory, new TextEditorPassBean(anchor, anchorPass, selfPassNumber, inPostHighlighterGroup));
    if (needAdditionalPass) {
      myNeedAdditionalIntentionsPass = true;
    }
  }

  public TextEditorHighlightingPass[] modifyHighlightingPasses(final List<TextEditorHighlightingPass> passes,
                                                               final PsiFile psiFile,
                                                               final Editor editor) {
    if (myRegisteredPasses == null || psiFile == null){ //do nothing with non-project files
      return passes.toArray(new TextEditorHighlightingPass[passes.size()]);
    }
    List<TextEditorHighlightingPass> result = new ArrayList<TextEditorHighlightingPass>(passes);
    for (TextEditorHighlightingPassFactory factory : myRegisteredPasses.keySet()) {
      final TextEditorHighlightingPass editorHighlightingPass = factory.createHighlightingPass(psiFile, editor);
      if (editorHighlightingPass == null) continue;
      final TextEditorPassBean location = myRegisteredPasses.get(factory);
      final Anchor anchor = location.getAnchor();
      if (anchor == Anchor.FIRST){
        result.add(0, editorHighlightingPass);
      } else if (anchor == Anchor.LAST){
        result.add(editorHighlightingPass);
      } else {
        final int passId = location.getNeighborPass();
        int anchorPassIdx = -1;
        for (int idx = 0; idx < result.size(); idx++) {
          final TextEditorHighlightingPass highlightingPass = result.get(idx);
          if (highlightingPass.getPassId() == passId){
            anchorPassIdx = idx;
            break;
          }
        }
        if (anchorPassIdx != -1){
          if (anchor == Anchor.BEFORE){
            result.add(Math.max(0, anchorPassIdx - 1), editorHighlightingPass);
          } else {
            result.add(anchorPassIdx +1, editorHighlightingPass);
          }
        } else {
          result.add(editorHighlightingPass);
        }
      }
    }
    return result.toArray(new TextEditorHighlightingPass[result.size()]);
  }

  public boolean needAdditionalIntentionsPass() {
    return myNeedAdditionalIntentionsPass;
  }

  public int[] getPostHighlightingPasses() {
    return myPostHighlighterPasses;
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

  private static class TextEditorPassBean {
    private Anchor myAnchor;
    private int myNeighborPass;
    private int myGroupPassNumber;
    private boolean myInPostHighlightingGroup;

    public TextEditorPassBean(final Anchor anchor, final int neibourPass, final int selfPassNumber) {
      myAnchor = anchor;
      myNeighborPass = neibourPass;
      myGroupPassNumber = selfPassNumber;
    }


    public TextEditorPassBean(final Anchor anchor,
                              final int neighborPass,
                              final int selfPassNumber,
                              final boolean inPostHighlightingGroup) {
      this(anchor, neighborPass, selfPassNumber);
      myInPostHighlightingGroup = inPostHighlightingGroup;
    }

    public Anchor getAnchor() {
      return myAnchor;
    }

    public int getNeighborPass() {
      return myNeighborPass;
    }

    public int getGroupPassNumber() {
      return myGroupPassNumber;
    }

    public boolean isInPostHighlightingGroup() {
      return myInPostHighlightingGroup;
    }
  }
}
