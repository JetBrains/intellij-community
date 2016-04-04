/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.diff.tools.util;

import com.intellij.diff.DiffContext;
import com.intellij.diff.impl.DiffSettingsHolder;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.tools.holders.BinaryEditorHolder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.FileEditorStateLevel;
import com.intellij.openapi.fileEditor.TransferableFileEditorState;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.ui.ToggleActionButton;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TransferableFileEditorStateSupport {
  @NotNull private static final Key<Map<String, Map<String, String>>> TRANSFERABLE_FILE_EDITOR_STATE =
    Key.create("Diff.TransferableFileEditorState");

  private static final Condition<BinaryEditorHolder> IS_SUPPORTED = new Condition<BinaryEditorHolder>() {
    @Override
    public boolean value(BinaryEditorHolder holder) {
      return getEditorState(holder.getEditor()) != null;
    }
  };

  @NotNull private final DiffSettingsHolder.DiffSettings mySettings;
  @NotNull private final List<BinaryEditorHolder> myHolders;
  private final boolean mySupported;

  public TransferableFileEditorStateSupport(@NotNull DiffSettingsHolder.DiffSettings settings,
                                            @NotNull List<BinaryEditorHolder> holders,
                                            @NotNull Disposable disposable) {
    mySettings = settings;
    myHolders = holders;
    mySupported = ContainerUtil.or(myHolders, IS_SUPPORTED);
    new MySynchronizer(ContainerUtil.filter(myHolders, IS_SUPPORTED)).install(disposable);
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

  public void processContextHints(@NotNull DiffRequest request, @NotNull DiffContext context) {
    if (!isEnabled()) return;

    for (BinaryEditorHolder holder : myHolders) {
      FileEditor editor = holder.getEditor();
      TransferableFileEditorState state = getEditorState(holder.getEditor());
      if (state != null) {
        readContextData(context, editor, state);
      }
    }
  }

  public void updateContextHints(@NotNull DiffRequest request, @NotNull DiffContext context) {
    if (!isEnabled()) return;

    Set<String> updated = ContainerUtil.newHashSet();

    for (BinaryEditorHolder holder : myHolders) {
      TransferableFileEditorState state = getEditorState(holder.getEditor());
      if (state != null) {
        boolean processed = !updated.add(state.getEditorId());
        if (!processed) writeContextData(context, state);
      }
    }
  }

  @NotNull
  public AnAction createToggleAction() {
    return new ToggleSynchronousEditorStatesAction(this);
  }

  private static void readContextData(@NotNull DiffContext context,
                                      @NotNull FileEditor editor,
                                      @NotNull TransferableFileEditorState state) {
    Map<String, Map<String, String>> map = context.getUserData(TRANSFERABLE_FILE_EDITOR_STATE);
    Map<String, String> options = map != null ? map.get(state.getEditorId()) : null;
    if (options == null) return;

    state.setTransferableOptions(options);
    editor.setState(state);
  }

  private static void writeContextData(@NotNull DiffContext context, @NotNull TransferableFileEditorState state) {
    Map<String, Map<String, String>> map = context.getUserData(TRANSFERABLE_FILE_EDITOR_STATE);
    if (map == null) {
      map = ContainerUtil.newHashMap();
      context.putUserData(TRANSFERABLE_FILE_EDITOR_STATE, map);
    }

    map.put(state.getEditorId(), state.getTransferableOptions());
  }

  @Nullable
  private static TransferableFileEditorState getEditorState(@NotNull FileEditor editor) {
    FileEditorState state = editor.getState(FileEditorStateLevel.FULL);
    return state instanceof TransferableFileEditorState ? (TransferableFileEditorState)state : null;
  }

  private class MySynchronizer implements PropertyChangeListener {
    @NotNull private final List<? extends FileEditor> myEditors;

    private boolean myDuringUpdate = false;

    public MySynchronizer(@NotNull List<BinaryEditorHolder> editors) {
      myEditors = ContainerUtil.map(editors, new Function<BinaryEditorHolder, FileEditor>() {
        @Override
        public FileEditor fun(BinaryEditorHolder holder) {
          return holder.getEditor();
        }
      });
    }

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
      if (!(evt.getSource() instanceof FileEditor)) return;

      TransferableFileEditorState sourceState = getEditorState(((FileEditor)evt.getSource()));
      if (sourceState == null) return;

      Map<String, String> options = sourceState.getTransferableOptions();
      String id = sourceState.getEditorId();

      for (FileEditor editor : myEditors) {
        if (evt.getSource() != editor) {
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
          editor.setState(state);
        }
      }
      finally {
        myDuringUpdate = false;
      }
    }
  }

  private class ToggleSynchronousEditorStatesAction extends ToggleActionButton implements DumbAware {
    @NotNull private final TransferableFileEditorStateSupport mySupport;

    public ToggleSynchronousEditorStatesAction(@NotNull TransferableFileEditorStateSupport support) {
      super("Synchronize Editors Settings", AllIcons.Actions.SyncPanels);
      mySupport = support;
      setEnabledInModalContext(true);
    }

    @Override
    public boolean isVisible() {
      return mySupport.isSupported();
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return mySupport.isEnabled();
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      mySupport.setEnabled(state);
    }
  }
}
