/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInsight.hints;

import com.intellij.codeInsight.daemon.impl.ParameterHintsPresentationManager;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.util.Key;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Stream;

public class ParameterHintsUpdater {

  private static final Key<Boolean> REPEATED_PASS = Key.create("RepeatedParameterHintsPass");

  private final ParameterHintsPresentationManager myHintsManager = ParameterHintsPresentationManager.getInstance();
  private final TIntObjectHashMap<Caret> myCaretMap;
  
  private final Map<Integer, String> myNewHints;
  private final Map<Integer, String> myHintsToPreserve;

  private final Editor myEditor;
  private final List<InlayUpdateInfo> myUpdateList;

  public ParameterHintsUpdater(@NotNull Editor editor,
                               @NotNull List<Inlay> inlays,
                               @NotNull Map<Integer, String> newHints,
                               @NotNull Map<Integer, String> hintsToPreserve) {
    myEditor = editor;
    myNewHints = newHints;
    myHintsToPreserve = hintsToPreserve;

    myCaretMap = new TIntObjectHashMap<>();
    List<Caret> allCarets = myEditor.getCaretModel().getAllCarets();
    allCarets.forEach((caret) -> myCaretMap.put(caret.getOffset(), caret));

    myUpdateList = getInlayUpdates(inlays);
  }
  
  
  private List<InlayUpdateInfo> getInlayUpdates(List<Inlay> editorHints) {
    List<InlayUpdateInfo> updates = ContainerUtil.newArrayList();
    
    editorHints.forEach(editorHint -> {
      int offset = editorHint.getOffset();
      String newText = myNewHints.remove(offset);
      if (delayRemoval(editorHint) || myHintsManager.isPinned(editorHint) || isPreserveHint(editorHint)) return;
      updates.add(new InlayUpdateInfo(offset, editorHint, newText));
    });
    
    myNewHints.keySet().forEach((offset) -> updates.add(new InlayUpdateInfo(offset, null, myNewHints.get(offset))));
    
    updates.sort(Comparator.comparing((update) -> update.offset));
    return updates;
  }

  
  private boolean isPreserveHint(Inlay inlay) {
    int offset = inlay.getOffset();
    String newText = myNewHints.get(offset);
    if (newText == null) {
      newText = myHintsToPreserve.get(offset);
    }
    
    String oldText = myHintsManager.getHintText(inlay);
    return Objects.equals(newText, oldText);
  }
  

  public void update() {
    boolean firstTime = myEditor.getUserData(REPEATED_PASS) == null;

    for (int infoIndex = 0; infoIndex < myUpdateList.size(); infoIndex++) {
      InlayUpdateInfo info = myUpdateList.get(infoIndex);
      String oldText = info.oldText;
      String newText = info.newText;

      if (oldText == null) {
        boolean useAnimation = !firstTime && !isSameHintRemovedNear(newText, infoIndex);
        myHintsManager.addHint(myEditor, info.offset, newText, useAnimation, false);
      }
      else if (newText == null) {
        boolean useAnimation = !isSameHintAddedNear(oldText, infoIndex);
        myHintsManager.deleteHint(myEditor, info.inlay, useAnimation);
      }
      else {
        myHintsManager.replaceHint(myEditor, info.inlay, newText);
      }
    }

    myEditor.putUserData(REPEATED_PASS, Boolean.TRUE);
  }


  private boolean isSameHintRemovedNear(@NotNull String text, int index) {
    return getInfosNear(index).anyMatch((info) -> text.equals(info.oldText));
  }


  private boolean isSameHintAddedNear(@NotNull String text, int index) {
    return getInfosNear(index).anyMatch((info) -> text.equals(info.newText));
  }
  
  
  private Stream<InlayUpdateInfo> getInfosNear(int index) {
    List<InlayUpdateInfo> result = ContainerUtil.newArrayList();
    if (index > 0) {
      result.add(myUpdateList.get(index - 1));
    }
    if (index + 1 < myUpdateList.size()) {
      result.add(myUpdateList.get(index + 1));
    }
    return result.stream();
  }


  private boolean delayRemoval(Inlay inlay) {
    int offset = inlay.getOffset();
    Caret caret = myCaretMap.get(offset);
    if (caret == null) return false;
    CharSequence text = myEditor.getDocument().getImmutableCharSequence();
    if (offset >= text.length()) return false;
    char afterCaret = text.charAt(offset);
    if (afterCaret != ',' && afterCaret != ')') return false;
    VisualPosition afterInlayPosition = myEditor.offsetToVisualPosition(offset, true, false);
    // check whether caret is to the right of inlay
    if (!caret.getVisualPosition().equals(afterInlayPosition)) return false;
    return true;
  }

  
  private static class InlayUpdateInfo {
    public final int offset;
    public final Inlay inlay;
    public final String newText;
    public final String oldText;

    public InlayUpdateInfo(int offset, @Nullable Inlay current, @Nullable String newText) {
      this.offset = offset;
      this.inlay = current;
      this.newText = newText;
      this.oldText = getHintText();
    }

    @Nullable
    private String getHintText() {
      return inlay != null ? ParameterHintsPresentationManager.getInstance().getHintText(inlay) : null;
    }
  }
}