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
import com.intellij.util.DocumentUtil;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public class ParameterHintsUpdater {

  private static final Key<Boolean> REPEATED_PASS = Key.create("RepeatedParameterHintsPass");

  private final ParameterHintsPresentationManager myHintsManager = ParameterHintsPresentationManager.getInstance();
  private final TIntObjectHashMap<Caret> myCaretMap;
  
  private final TIntObjectHashMap<String> myNewHints;
  private final TIntObjectHashMap<String> myHintsToPreserve;

  private final Editor myEditor;
  private final List<InlayUpdateInfo> myUpdateList;

  public ParameterHintsUpdater(@NotNull Editor editor,
                               @NotNull List<Inlay> inlays,
                               @NotNull TIntObjectHashMap<String> newHints,
                               @NotNull TIntObjectHashMap<String> hintsToPreserve) {
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
      if (delayRemoval(editorHint) || myHintsManager.isPinned(editorHint) || isPreserveHint(editorHint, newText)) return;
      updates.add(new InlayUpdateInfo(offset, editorHint, newText));
    });

    Arrays.stream(myNewHints.keys()).forEach((offset) -> updates.add(new InlayUpdateInfo(offset, null, myNewHints.get(offset))));

    updates.sort(Comparator.comparing((update) -> update.offset));
    return updates;
  }

  
  private boolean isPreserveHint(@NotNull Inlay inlay, @Nullable String newText) {
    if (newText == null) {
      newText = myHintsToPreserve.get(inlay.getOffset());
    }
    String oldText = myHintsManager.getHintText(inlay);
    return Objects.equals(newText, oldText);
  }
  

  public void update() {
    boolean firstTime = myEditor.getUserData(REPEATED_PASS) == null;
    boolean isUpdateInBulkMode = myUpdateList.size() > 1000;
    DocumentUtil.executeInBulk(myEditor.getDocument(), isUpdateInBulkMode, () -> performHintsUpdate(firstTime, isUpdateInBulkMode));
    myEditor.putUserData(REPEATED_PASS, Boolean.TRUE);
  }

  private void performHintsUpdate(boolean firstTime, boolean isInBulkMode) {
    for (int infoIndex = 0; infoIndex < myUpdateList.size(); infoIndex++) {
      InlayUpdateInfo info = myUpdateList.get(infoIndex);
      String oldText = info.oldText;
      String newText = info.newText;

      InlayUpdateInfo.Action action = info.action();
      if (action == InlayUpdateInfo.Action.ADD) {
        boolean useAnimation = !firstTime && !isSameHintRemovedNear(newText, infoIndex) && !isInBulkMode;
        myHintsManager.addHint(myEditor, info.offset, newText, useAnimation, false);
      }
      else if (action == InlayUpdateInfo.Action.DELETE) {
        boolean useAnimation = oldText != null && !isSameHintAddedNear(oldText, infoIndex) && !isInBulkMode;
        myHintsManager.deleteHint(myEditor, info.inlay, useAnimation);
      }
      else if (action == InlayUpdateInfo.Action.REPLACE) {
        myHintsManager.replaceHint(myEditor, info.inlay, newText);
      }
    }
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
    public enum Action {
      ADD, DELETE, REPLACE, SKIP
    }

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

    public Action action() {
      if (inlay == null) {
        return newText != null ? Action.ADD : Action.SKIP;
      }
      else {
        return newText != null ? Action.REPLACE : Action.DELETE;
      }
    }

    @Nullable
    private String getHintText() {
      return inlay != null ? ParameterHintsPresentationManager.getInstance().getHintText(inlay) : null;
    }
  }
}