// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints.parameter;

import com.intellij.codeInsight.hints.HintWidthAdjustment;
import com.intellij.codeInsight.hints.InlayModelWrapper;
import com.intellij.codeInsight.hints.presentation.InlayPresentation;
import com.intellij.codeInsight.hints.presentation.PresentationRenderer;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.util.DocumentUtil;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Stream;

public class NewParameterHintsUpdater {
  private static final Key<Boolean> HINT_REMOVAL_DELAYED = Key.create("hint.removal.delayed");
  private static final Key<Boolean> REPEATED_PASS = Key.create("RepeatedParameterHintsPass");

  //private final ParameterHintsPresentationManager myHintsManager = ParameterHintsPresentationManager.getInstance();
  private final TIntObjectHashMap<Caret> myCaretMap;

  private final TIntObjectHashMap<List<ParameterHintInfo>> myNewHints;
  private final TIntObjectHashMap<InlayPresentation> myHintsToPreserve;
  private final boolean myForceImmediateUpdate;
  @NotNull private final InlayModelWrapper myModel;

  private final Editor myEditor;
  private final List<Inlay> myEditorInlays;
  private List<InlayUpdateInfo> myUpdateList;

  public NewParameterHintsUpdater(@NotNull Editor editor,
                                  @NotNull List<Inlay> editorInlays,
                                  @NotNull TIntObjectHashMap<List<ParameterHintInfo>> newHints,
                                  @NotNull TIntObjectHashMap<InlayPresentation> hintsToPreserve,
                                  boolean forceImmediateUpdate,
                                  @NotNull InlayModelWrapper model) {
    myEditor = editor;
    myNewHints = newHints;
    myHintsToPreserve = hintsToPreserve;
    myForceImmediateUpdate = forceImmediateUpdate;
    myModel = model;

    myCaretMap = new TIntObjectHashMap<>();
    List<Caret> allCarets = myEditor.getCaretModel().getAllCarets();
    allCarets.forEach((caret) -> myCaretMap.put(caret.getOffset(), caret));

    myEditorInlays = editorInlays;
  }


  private List<InlayUpdateInfo> getInlayUpdates(List<Inlay> editorHints) {
    myEditor.putUserData(HINT_REMOVAL_DELAYED, Boolean.FALSE);

    List<InlayUpdateInfo> updates = ContainerUtil.newArrayList();

    editorHints.forEach(editorHint -> {
      int offset = editorHint.getOffset();
      ParameterHintInfo newHint = findAndRemoveMatchingHint(offset, editorHint.isRelatedToPrecedingText(), myNewHints);
      if (!myForceImmediateUpdate && delayRemoval(editorHint)) {
        myEditor.putUserData(HINT_REMOVAL_DELAYED, Boolean.TRUE);
        return;
      }
      InlayPresentation newText = newHint == null ? null : newHint.getPresentation();
      if (isPreserveHint(editorHint, newText)) return;
      updates.add(new InlayUpdateInfo(offset, editorHint, newHint));
    });

    Arrays.stream(myNewHints.keys()).forEach((offset) -> {
      for (ParameterHintInfo hint : myNewHints.get(offset)) {
        updates.add(new InlayUpdateInfo(offset, null, hint));
      }
    });

    updates.sort(Comparator.comparing((update) -> update.offset));
    return updates;
  }

  public static boolean hintRemovalDelayed(@NotNull Editor editor) {
    return editor.getUserData(HINT_REMOVAL_DELAYED) == Boolean.TRUE;
  }

  @Nullable
  private static ParameterHintInfo findAndRemoveMatchingHint(int offset, boolean relatesToPrecedingText,
                                                             TIntObjectHashMap<List<ParameterHintInfo>> data) {
    List<ParameterHintInfo> newHintList = data.get(offset);
    ParameterHintInfo newHint = null;
    if (newHintList != null) {
      for (Iterator<ParameterHintInfo> iterator = newHintList.iterator(); iterator.hasNext(); ) {
        ParameterHintInfo hint = iterator.next();
        if (hint.getRelatesToPrecedingText() == relatesToPrecedingText) {
          newHint = hint;
          iterator.remove();
          break;
        }
      }
      if (newHintList.isEmpty()) data.remove(offset);
    }
    return newHint;
  }

  private boolean isPreserveHint(@NotNull Inlay inlay, @Nullable InlayPresentation newPresentation) {
    if (newPresentation == null) {
      newPresentation = myHintsToPreserve.get(inlay.getOffset());
    }
    //TODO
    InlayPresentation oldPresentation = getPresentation(inlay);
    return oldPresentation == newPresentation;
  }


  public void update() {
    myUpdateList = getInlayUpdates(myEditorInlays);
    boolean firstTime = myEditor.getUserData(REPEATED_PASS) == null;
    boolean isUpdateInBulkMode = myUpdateList.size() > 1000;
    DocumentUtil.executeInBulk(myEditor.getDocument(), isUpdateInBulkMode, () -> performHintsUpdate(firstTime, isUpdateInBulkMode));
    myEditor.putUserData(REPEATED_PASS, Boolean.TRUE);
  }

  private void performHintsUpdate(boolean firstTime, boolean isInBulkMode) {
    for (int infoIndex = 0; infoIndex < myUpdateList.size(); infoIndex++) {
      InlayUpdateInfo info = myUpdateList.get(infoIndex);
      InlayPresentation oldPresentation = info.oldText;
      InlayPresentation newPresentation = info.newText;

      InlayUpdateInfo.Action action = info.action();
      System.out.println(action);
      if (action == InlayUpdateInfo.Action.ADD) {
        boolean useAnimation = !myForceImmediateUpdate && !firstTime && !isSameHintRemovedNear(newPresentation, infoIndex) && !isInBulkMode;
        //TODO
        //Inlay inlay = myHintsManager.addHint(myEditor, info.offset, info.relatesToPrecedingText, newPresentation, info.widthAdjustment, useAnimation);
        Inlay inlay = myModel.addInlineElement(info.offset, new PresentationRenderer(newPresentation));
        if (inlay != null && !((DocumentEx)myEditor.getDocument()).isInBulkUpdate()) {
          VisualPosition inlayPosition = inlay.getVisualPosition();
          VisualPosition visualPosition = new VisualPosition(inlayPosition.line,
                                                             inlayPosition.column + (info.relatesToPrecedingText ? 1 : 0));
          Caret caret = myEditor.getCaretModel().getCaretAt(visualPosition);
          if (caret != null) caret.moveToVisualPosition(new VisualPosition(inlayPosition.line,
                                                                           inlayPosition.column + (info.relatesToPrecedingText ? 0 : 1)));
        }
      }
      else if (action == InlayUpdateInfo.Action.DELETE) {
        boolean useAnimation = !myForceImmediateUpdate && oldPresentation != null && !isSameHintAddedNear(oldPresentation, infoIndex) && !isInBulkMode;
        //myHintsManager.deleteHint(myEditor, info.inlay, useAnimation);
        // TODO
        System.out.println("Deleting inlay");
        Disposer.dispose(info.inlay);
      }
      else if (action == InlayUpdateInfo.Action.REPLACE) {
        // TODO
        //myHintsManager.replaceHint(myEditor, info.inlay, newPresentation, info.widthAdjustment, !myForceImmediateUpdate);
        Disposer.dispose(info.inlay);
        myModel.addInlineElement(info.offset, new PresentationRenderer(newPresentation));
      }
    }
  }

  private boolean isSameHintRemovedNear(@NotNull InlayPresentation text, int index) {
    return getInfosNear(index).anyMatch((info) -> text.equals(info.oldText));
  }


  private boolean isSameHintAddedNear(@NotNull InlayPresentation text, int index) {
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
    public final InlayPresentation newText;
    public final InlayPresentation oldText;
    public final boolean relatesToPrecedingText;
    public final HintWidthAdjustment widthAdjustment;

    InlayUpdateInfo(int offset, @Nullable Inlay current, @Nullable ParameterHintInfo newHintData) {
      this.offset = offset;
      inlay = current;
      oldText = inlay == null ? null : getPresentation(inlay);
      if (newHintData == null) {
        newText = null;
        relatesToPrecedingText = false;
        widthAdjustment = null;
      }
      else {
        newText = newHintData.getPresentation();
        relatesToPrecedingText = newHintData.getRelatesToPrecedingText();
        widthAdjustment = newHintData.getWidthAdjustment();
      }
    }

    public Action action() {
      if (inlay == null) {
        return newText != null ? Action.ADD : Action.SKIP;
      }
      else {
        return newText != null ? Action.REPLACE : Action.DELETE;
      }
    }
  }

  @Nullable
  static InlayPresentation getPresentation(Inlay<?> inlay) {
    EditorCustomElementRenderer renderer = inlay.getRenderer();
    if (renderer instanceof PresentationRenderer) {
      return ((PresentationRenderer)renderer).getPresentation();
    }
    return null;
  }
}