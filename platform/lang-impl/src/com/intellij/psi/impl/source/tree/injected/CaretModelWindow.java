// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.psi.impl.source.tree.injected;

import com.intellij.injected.editor.EditorWindow;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.markup.TextAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class CaretModelWindow implements CaretModel {
  private final CaretModel myDelegate;
  private final EditorEx myHostEditor;
  private final EditorWindow myEditorWindow;
  private final Map<Caret, InjectedCaret> myInjectedCaretMap = new HashMap<>(); // guarded by myInjectedCaretMap

  CaretModelWindow(@NotNull CaretModel delegate, @NotNull EditorWindow editorWindow) {
    myDelegate = delegate;
    myHostEditor = (EditorEx)editorWindow.getDelegate();
    myEditorWindow = editorWindow;
  }

  private final ListenerWrapperMap<CaretListener> myCaretListeners = new ListenerWrapperMap<>();
  @Override
  public void addCaretListener(final @NotNull CaretListener listener) {
    CaretListener wrapper = new CaretListener() {
      @Override
      public void caretPositionChanged(@NotNull CaretEvent e) {
        if (!myEditorWindow.getDocument().isValid()) return; // injected document can be destroyed by now
        Caret caret = e.getCaret();
        assert caret != null;
        CaretEvent event = new CaretEvent(createInjectedCaret(caret),
                                          myEditorWindow.hostToInjected(e.getOldPosition()),
                                          myEditorWindow.hostToInjected(e.getNewPosition()));
        listener.caretPositionChanged(event);
      }
    };
    myCaretListeners.registerWrapper(listener, wrapper);
    myDelegate.addCaretListener(wrapper);
  }

  @Override
  public void removeCaretListener(final @NotNull CaretListener listener) {
    CaretListener wrapper = myCaretListeners.removeWrapper(listener);
    if (wrapper != null) {
      myDelegate.removeCaretListener(wrapper);
    }
  }

  void disposeModel() {
    for (CaretListener wrapper : myCaretListeners.wrappers()) {
      myDelegate.removeCaretListener(wrapper);
    }
    myCaretListeners.clear();
  }

  @Override
  public TextAttributes getTextAttributes() {
    return myDelegate.getTextAttributes();
  }

  @Override
  public boolean supportsMultipleCarets() {
    return myDelegate.supportsMultipleCarets();
  }

  @Override
  public int getMaxCaretCount() {
    return myDelegate.getMaxCaretCount();
  }

  @Override
  public @NotNull Caret getCurrentCaret() {
    return createInjectedCaret(myDelegate.getCurrentCaret());
  }

  @Override
  public @NotNull Caret getPrimaryCaret() {
    return createInjectedCaret(myDelegate.getPrimaryCaret());
  }

  @Override
  public int getCaretCount() {
    return myDelegate.getCaretCount();
  }

  @Override
  public @NotNull List<Caret> getAllCarets() {
    List<Caret> hostCarets = myDelegate.getAllCarets();
    List<Caret> carets = new ArrayList<>(hostCarets.size());
    for (Caret hostCaret : hostCarets) {
      carets.add(createInjectedCaret(hostCaret));
    }
    return carets;
  }

  @Override
  public @Nullable Caret getCaretAt(@NotNull VisualPosition pos) {
    LogicalPosition hostPos = myEditorWindow.injectedToHost(myEditorWindow.visualToLogicalPosition(pos));
    Caret caret = myDelegate.getCaretAt(myHostEditor.logicalToVisualPosition(hostPos));
    return caret == null ? null : createInjectedCaret(caret);
  }

  @Override
  public @Nullable Caret addCaret(@NotNull VisualPosition pos, boolean makePrimary) {
    LogicalPosition hostPos = myEditorWindow.injectedToHost(myEditorWindow.visualToLogicalPosition(pos));
    Caret caret = myDelegate.addCaret(myHostEditor.logicalToVisualPosition(hostPos));
    return caret == null ? null : createInjectedCaret(caret);
  }

  @Override
  public @Nullable Caret addCaret(@NotNull LogicalPosition pos, boolean makePrimary) {
    LogicalPosition hostPos = myEditorWindow.injectedToHost(pos);
    Caret caret = myDelegate.addCaret(hostPos, makePrimary);
    return caret == null ? null : createInjectedCaret(caret);
  }

  @Override
  public boolean removeCaret(@NotNull Caret caret) {
    if (caret instanceof InjectedCaret) {
      caret = ((InjectedCaret)caret).myDelegate;
    }
    return myDelegate.removeCaret(caret);
  }

  @Override
  public void removeSecondaryCarets() {
    myDelegate.removeSecondaryCarets();
  }

  @Override
  public void setCaretsAndSelections(@NotNull List<? extends CaretState> caretStates) {
    List<CaretState> convertedStates = convertCaretStates(caretStates);
    myDelegate.setCaretsAndSelections(convertedStates);
  }

  @Override
  public void setCaretsAndSelections(@NotNull List<? extends CaretState> caretStates, boolean updateSystemSelection) {
    List<CaretState> convertedStates = convertCaretStates(caretStates);
    myDelegate.setCaretsAndSelections(convertedStates, updateSystemSelection);
  }

  private List<CaretState> convertCaretStates(List<? extends CaretState> caretStates) {
    List<CaretState> convertedStates = new ArrayList<>(caretStates.size());
    for (CaretState state : caretStates) {
      convertedStates.add(new CaretState(injectedToHost(state.getCaretPosition()),
                                         injectedToHost(state.getSelectionStart()),
                                         injectedToHost(state.getSelectionEnd())));
    }
    return convertedStates;
  }

  private LogicalPosition injectedToHost(@Nullable LogicalPosition position) {
    return position == null ? null : myEditorWindow.injectedToHost(position);
  }

  @Override
  public @NotNull List<CaretState> getCaretsAndSelections() {
    List<CaretState> caretsAndSelections = myDelegate.getCaretsAndSelections();
    List<CaretState> convertedStates = new ArrayList<>(caretsAndSelections.size());
    for (CaretState state : caretsAndSelections) {
      convertedStates.add(new CaretState(hostToInjected(state.getCaretPosition()),
                                         hostToInjected(state.getSelectionStart()),
                                         hostToInjected(state.getSelectionEnd())));
    }
    return convertedStates;
  }

  private LogicalPosition hostToInjected(@Nullable LogicalPosition position) {
    return position == null ? null : myEditorWindow.hostToInjected(position);
  }

  private @NotNull InjectedCaret createInjectedCaret(@NotNull Caret caret) {
    synchronized (myInjectedCaretMap) {
      return myInjectedCaretMap.computeIfAbsent(caret, c->new InjectedCaret(myEditorWindow, c));
    }
  }

  @Override
  public void runForEachCaret(final @NotNull CaretAction action) {
    myDelegate.runForEachCaret(caret -> action.perform(createInjectedCaret(caret)));
  }

  @Override
  public void runForEachCaret(final @NotNull CaretAction action, boolean reverseOrder) {
    myDelegate.runForEachCaret(caret -> action.perform(createInjectedCaret(caret)), reverseOrder);
  }

  @Override
  public void addCaretActionListener(@NotNull CaretActionListener listener, @NotNull Disposable disposable) {
    myDelegate.addCaretActionListener(listener, disposable);
  }

  @Override
  public void runBatchCaretOperation(@NotNull Runnable runnable) {
    myDelegate.runBatchCaretOperation(runnable);
  }
}
