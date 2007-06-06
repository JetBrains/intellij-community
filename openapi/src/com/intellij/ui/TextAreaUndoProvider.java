package com.intellij.ui;

import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.keymap.KeymapManager;

import javax.swing.*;
import javax.swing.text.Keymap;
import javax.swing.event.UndoableEditListener;
import javax.swing.event.UndoableEditEvent;
import javax.swing.undo.UndoManager;
import java.awt.event.ActionEvent;

/**
 * @author yole
 */
public class TextAreaUndoProvider {
  private JTextArea myTextArea;
  private UndoManager myUndoManager = new UndoManager();

  public TextAreaUndoProvider(final JTextArea textArea) {
    myTextArea = textArea;

    myTextArea.getDocument().addUndoableEditListener(new UndoableEditListener() {
      public void undoableEditHappened(UndoableEditEvent e) {
        myUndoManager.addEdit(e.getEdit());
      }
    });
    Keymap keymap = myTextArea.getKeymap();
    com.intellij.openapi.keymap.Keymap activeKeymap = KeymapManager.getInstance().getActiveKeymap();
    Shortcut[] undoShortcuts = activeKeymap.getShortcuts("$Undo");
    Shortcut[] redoShortcuts = activeKeymap.getShortcuts("$Redo");

    //noinspection HardCodedStringLiteral
    Action undoAction = new AbstractAction ("Undo") {
      public void actionPerformed(ActionEvent e) {
        if (myUndoManager.canUndo())
          myUndoManager.undo();
      }
    };

    //noinspection HardCodedStringLiteral
    Action redoAction = new AbstractAction ("Redo") {
      public void actionPerformed(ActionEvent e) {
        if (myUndoManager.canRedo())
          myUndoManager.redo();
      }
    };

    for (Shortcut undoShortcut : undoShortcuts) {
      if (undoShortcut instanceof KeyboardShortcut) {
        keymap.addActionForKeyStroke(((KeyboardShortcut)undoShortcut).getFirstKeyStroke(), undoAction);
      }
    }

    for (Shortcut redoShortcut : redoShortcuts) {
      if (redoShortcut instanceof KeyboardShortcut) {
        keymap.addActionForKeyStroke(((KeyboardShortcut)redoShortcut).getFirstKeyStroke(), redoAction);
      }
    }

    myTextArea.setKeymap(keymap);
  }
}