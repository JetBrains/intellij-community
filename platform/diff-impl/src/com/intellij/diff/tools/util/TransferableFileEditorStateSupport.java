// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.util;

import com.intellij.diff.DiffContext;
import com.intellij.diff.impl.DiffSettingsHolder.DiffSettings;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.tools.holders.BinaryEditorHolder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.FileEditorStateLevel;
import com.intellij.openapi.fileEditor.TransferableFileEditorState;
import com.intellij.openapi.project.DumbAwareToggleAction;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApiStatus.Internal
public class TransferableFileEditorStateSupport {
  private static final @NotNull Key<MyState> TRANSFERABLE_FILE_EDITOR_STATE =
    Key.create("Diff.TransferableFileEditorState");

  private static final Condition<BinaryEditorHolder> IS_SUPPORTED = holder -> getEditorState(holder.getEditor()) != null;

  private final @NotNull DiffSettings mySettings;
  private final @NotNull List<? extends BinaryEditorHolder> myHolders;
  private final @NotNull List<? extends FileEditor> myEditors;

  private final boolean mySupported;

  private int myMasterIndex = 0;
  private boolean myDuringUpdate = true;

  public TransferableFileEditorStateSupport(@NotNull DiffSettings settings,
                                            @NotNull List<? extends BinaryEditorHolder> holders,
                                            @NotNull Disposable disposable) {
    mySettings = settings;
    myHolders = holders;
    myEditors = ContainerUtil.map(ContainerUtil.filter(holders, IS_SUPPORTED), holder -> holder.getEditor());
    mySupported = !myEditors.isEmpty();

    new MySynchronizer().install(disposable);
  }

  public boolean isSupported() {
    return mySupported;
  }

  public boolean isEnabled() {
    return mySettings.isSyncBinaryEditorSettings();
  }

  public void setEnabled(boolean enabled) {
    mySettings.setSyncBinaryEditorSettings(enabled);
  }

  public void syncStatesNow() {
    if (myEditors.size() < 2) return;

    FileEditor masterEditor = myHolders.get(myMasterIndex).getEditor();
    syncStateFrom(masterEditor);
  }

  public @NotNull AnAction createToggleAction() {
    return new ToggleSynchronousEditorStatesAction(this);
  }


  public void processContextHints(@NotNull DiffRequest request, @NotNull DiffContext context) {
    myDuringUpdate = false;
    if (!isEnabled()) return;

    MyState state = context.getUserData(TRANSFERABLE_FILE_EDITOR_STATE);
    if (state == null) return;

    int newMasterIndex = state.getMasterIndex();
    if (newMasterIndex < myHolders.size()) myMasterIndex = newMasterIndex;

    try {
      myDuringUpdate = true;
      for (BinaryEditorHolder holder : myHolders) {
        state.restoreContextData(holder.getEditor());
      }

      syncStatesNow();
    }
    finally {
      myDuringUpdate = false;
    }
  }

  public void updateContextHints(@NotNull DiffRequest request, @NotNull DiffContext context) {
    if (!isEnabled()) return;

    MyState state = new MyState(myMasterIndex);
    context.putUserData(TRANSFERABLE_FILE_EDITOR_STATE, state);

    // master editor has priority
    state.storeContextData(myHolders.get(myMasterIndex).getEditor());

    for (BinaryEditorHolder holder : myHolders) {
      state.storeContextData(holder.getEditor());
    }
  }


  private class MySynchronizer implements PropertyChangeListener {
    public void install(@NotNull Disposable disposable) {
      if (myEditors.size() < 2) return;

      for (FileEditor editor : myEditors) {
        editor.addPropertyChangeListener(this);
      }

      Disposer.register(disposable, new Disposable() {
        @Override
        public void dispose() {
          for (FileEditor editor : myEditors) {
            editor.removePropertyChangeListener(MySynchronizer.this);
          }
        }
      });
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
      if (myDuringUpdate || !isEnabled()) return;
      if (!(evt.getSource() instanceof FileEditor editor)) return;

      if (!editor.getComponent().isShowing()) return;
      Dimension size = editor.getComponent().getSize();
      if (size.width <= 0 || size.height <= 0) return;

      int holderIndex = ContainerUtil.indexOf(myHolders, (Condition<BinaryEditorHolder>)holder -> editor.equals(holder.getEditor()));
      if (holderIndex != -1) myMasterIndex = holderIndex;

      syncStateFrom(editor);
    }
  }

  private void syncStateFrom(@NotNull FileEditor sourceEditor) {
    TransferableFileEditorState sourceState = getEditorState(sourceEditor);
    if (sourceState == null) return;

    Map<String, String> options = sourceState.getTransferableOptions();
    String id = sourceState.getEditorId();

    for (FileEditor editor : myEditors) {
      if (sourceEditor != editor) {
        updateEditor(editor, id, options);
      }
    }
  }

  private void updateEditor(@NotNull FileEditor editor, @NotNull String id, @NotNull Map<String, String> options) {
    try {
      myDuringUpdate = true;
      TransferableFileEditorState state = getEditorState(editor);
      if (state != null && state.getEditorId().equals(id)) {
        state.setTransferableOptions(options);
        state.setCopiedFromMasterEditor();
        editor.setState(state);
      }
    }
    finally {
      myDuringUpdate = false;
    }
  }

  private static @Nullable TransferableFileEditorState getEditorState(@NotNull FileEditor editor) {
    FileEditorState state = editor.getState(FileEditorStateLevel.FULL);
    return state instanceof TransferableFileEditorState ? (TransferableFileEditorState)state : null;
  }


  private static class ToggleSynchronousEditorStatesAction extends DumbAwareToggleAction {
    private final @NotNull TransferableFileEditorStateSupport mySupport;

    ToggleSynchronousEditorStatesAction(@NotNull TransferableFileEditorStateSupport support) {
      super(DiffBundle.message("synchronize.editors.settings"), null, AllIcons.Actions.SyncPanels);
      mySupport = support;
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      super.update(e);
      e.getPresentation().setVisible(mySupport.isSupported());
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return mySupport.isEnabled();
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      mySupport.setEnabled(state);
      if (state) {
        mySupport.syncStatesNow();
      }
    }
  }

  private static class MyState {
    private final Map<String, Map<String, String>> myMap = new HashMap<>();
    private final int myMasterIndex;

    MyState(int masterIndex) {
      myMasterIndex = masterIndex;
    }

    public int getMasterIndex() {
      return myMasterIndex;
    }

    public void restoreContextData(@NotNull FileEditor editor) {
      TransferableFileEditorState editorState = getEditorState(editor);
      if (editorState == null) return;

      Map<String, String> options = myMap.get(editorState.getEditorId());
      if (options == null) return;

      editorState.setTransferableOptions(options);
      editor.setState(editorState);
    }

    public void storeContextData(@NotNull FileEditor editor) {
      TransferableFileEditorState editorState = getEditorState(editor);
      if (editorState == null) return;

      if (!myMap.containsKey(editorState.getEditorId())) {
        myMap.put(editorState.getEditorId(), editorState.getTransferableOptions());
      }
    }
  }
}
