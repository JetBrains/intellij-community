// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.UndoableEditEvent;
import javax.swing.event.UndoableEditListener;
import javax.swing.text.JTextComponent;
import javax.swing.undo.UndoManager;

/**
 * @deprecated use {@link com.intellij.util.ui.UIUtil#addUndoRedoActions(javax.swing.text.JTextComponent)}
 * @author yole
 */
@Deprecated
public class TextComponentUndoProvider implements Disposable {
  protected final JTextComponent myTextComponent;
  protected final UndoManager myUndoManager = new UndoManager();
  private UndoableEditListener myUndoableEditListener;

  public TextComponentUndoProvider(final JTextComponent textComponent) {
    myTextComponent = textComponent;

    myUndoableEditListener = new UndoableEditListener() {
      @Override
      public void undoableEditHappened(UndoableEditEvent e) {
        myUndoManager.addEdit(e.getEdit());
      }
    };
    myTextComponent.getDocument().addUndoableEditListener(myUndoableEditListener);
    Keymap activeKeymap = KeymapManager.getInstance().getActiveKeymap();
    Shortcut[] undoShortcuts = activeKeymap.getShortcuts(IdeActions.ACTION_UNDO);
    Shortcut[] redoShortcuts = activeKeymap.getShortcuts(IdeActions.ACTION_REDO);

    AnAction undoAction = new AnAction() {
      @Override
      public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabled(canUndo());
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        undo();
      }
    };

    AnAction redoAction = new AnAction() {
      @Override
      public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabled(canRedo());
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        redo();
      }
    };

    undoAction.registerCustomShortcutSet(new CustomShortcutSet(undoShortcuts), myTextComponent);
    redoAction.registerCustomShortcutSet(new CustomShortcutSet(redoShortcuts), myTextComponent);
  }

  protected boolean canUndo() {
    return myUndoManager.canUndo();
  }

  protected boolean canRedo() {
    return myUndoManager.canRedo();
  }

  protected void redo() {
    if (myUndoManager.canRedo()) {
      myUndoManager.redo();
    }
  }

  protected void undo() {
    if (myUndoManager.canUndo()) {
      myUndoManager.undo();
    }
  }

  @Override
  public void dispose() {
    if (myUndoableEditListener != null) {
      myTextComponent.getDocument().removeUndoableEditListener(myUndoableEditListener);
      myUndoableEditListener = null;
    }
  }
}
