package com.intellij.find.impl;

import com.intellij.find.FindModel;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.UserActivityListener;
import com.intellij.ui.UserActivityWatcher;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;
import java.util.List;

public class LivePreviewController implements LivePreview.Delegate, UserActivityListener {


  private final FindDialog myFindDialog;
  private final LivePreview myLivePreview;
  private final UserActivityWatcher myWatcher = new MomentoUserActivityWatcher();

  LivePreviewControllerBase myDelegate = new LivePreviewControllerBase();

  private void updateDelegatesFindModel() {
    FindModel currentModel = myFindDialog.getCurrentModel();
    myDelegate.setFindModel(currentModel);
  }

  public LivePreviewController(FindDialog findDialog, LivePreview livePreview, Component toWatch) {
    myFindDialog = findDialog;
    myLivePreview = livePreview;
    myLivePreview.setDelegate(this);
    myWatcher.register(toWatch);
    myWatcher.addUserActivityListener(this);
    myFindDialog.getWindow().addWindowFocusListener(new WindowFocusListener() {
      @Override
      public void windowGainedFocus(WindowEvent windowEvent) {
        myLivePreview.update();
      }

      @Override
      public void windowLostFocus(WindowEvent windowEvent) {
      }
    });

    JComponent contentPane = (JComponent)findDialog.getContentPane();
    new AnAction() {

      @Override
      public void actionPerformed(AnActionEvent e) {
        myLivePreview.nextOccurrence();
      }
    }.registerCustomShortcutSet(KeyEvent.VK_F3, 0, contentPane);

    new AnAction() {

      @Override
      public void actionPerformed(AnActionEvent e) {
        myLivePreview.prevOccurrence();
      }
    }.registerCustomShortcutSet(KeyEvent.VK_F3, KeyEvent.SHIFT_MASK, contentPane);
  }

  public void cleanUp() {
    myWatcher.removeUserActivityListener(this);
    myLivePreview.cleanUp();
  }

  @NotNull
  @Override
  public List<LiveOccurrence> performSearchInBackgroundInReadAction(Editor editor) {
    updateDelegatesFindModel();
    return myDelegate.performSearchInBackgroundInReadAction(editor);
  }

  @Override
  public void performReplaceAll(Editor e) {
    updateDelegatesFindModel();
    myDelegate.performReplaceAll(e);
  }

  @Override
  public void getFocusBack() {
    myFindDialog.getContentPane().requestFocus();
  }

  @Override
  public Editor getEditor(Ref<Boolean> needToUpdate) {
    Editor result = null;
    FileEditorManagerEx instanceEx = FileEditorManagerEx.getInstanceEx(myLivePreview.getProject());
    if (instanceEx != null) {
      VirtualFile currentFile = instanceEx.getCurrentFile();
      if (currentFile != null) {
        FileEditor[] editors = instanceEx.getEditors(currentFile);
        if (editors.length > 0) {
          FileEditor fileEditor = editors[0];
          boolean focusedFound = false;
          for (FileEditor e : editors) {
            if (e instanceof TextEditor && ((TextEditor)e).getEditor().getContentComponent().hasFocus()) {
              fileEditor = e;
              focusedFound = true;
            }
          }
          needToUpdate.set(true);
          if (!focusedFound) {
            for (FileEditor e : editors) {
              if (e instanceof TextEditor && ((TextEditor)e).getEditor() == myLivePreview.getEditor()) {
                needToUpdate.set(false);
              }
            }
          }
          if(needToUpdate.get() && fileEditor instanceof TextEditor) {
            result = ((TextEditor)fileEditor).getEditor();
          }
        }
      }
    }
    return result;
  }

  @Override
  public TextRange performReplace(final LiveOccurrence occurrence, final String replacement, final Editor editor) {
    updateDelegatesFindModel();
    return myDelegate.performReplace(occurrence, replacement, editor);
  }

  @Override
  public String getReplacementPreviewText(Editor editor, LiveOccurrence liveOccurrence) {
    updateDelegatesFindModel();
    return myDelegate.getReplacementPreviewText(editor, liveOccurrence);
  }


  @Override
  public void stateChanged() {
    myLivePreview.update();
  }
}
