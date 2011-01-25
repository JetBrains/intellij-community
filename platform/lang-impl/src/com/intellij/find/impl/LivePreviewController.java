package com.intellij.find.impl;

import com.intellij.find.FindManager;
import com.intellij.find.FindModel;
import com.intellij.find.FindResult;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.UserActivityListener;
import com.intellij.ui.UserActivityWatcher;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class LivePreviewController implements LivePreview.Delegate, UserActivityListener {

  private static final String EMPTY_STRING_DISPLAY_TEXT = "<Empty string>";
  private final FindDialog myFindDialog;
  private final LivePreview myLivePreview;
  private final UserActivityWatcher myWatcher = new MomentoUserActivityWatcher();
  private int myMatchesLimit = 100;

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

  public void setMatchesLimit(int matchesLimit) {
    myMatchesLimit = matchesLimit;
  }

  public int getMatchesLimit() {
    return myMatchesLimit;
  }

  @NotNull
  @Override
  public List<LiveOccurrence> performSearchInBackgroundInReadAction(Editor editor) {
    ArrayList<LiveOccurrence> occurrences = new ArrayList<LiveOccurrence>();
    FindModel currentModel = myFindDialog.getCurrentModel();
    if (currentModel != null) {
      int offset = 0;
      VirtualFile virtualFile = FileDocumentManager.getInstance().getFile(editor.getDocument());
      ArrayList<FindResult> results = new ArrayList<FindResult>();

      while (true) {
        FindManager findManager = FindManager.getInstance(editor.getProject());
        FindResult result = findManager.findString(editor.getDocument().getCharsSequence(), offset, currentModel, virtualFile);
        if (!result.isStringFound()) break;
        int newOffset = result.getEndOffset();
        if (offset == newOffset) break;
        offset = newOffset;
        results.add(result);

        if (results.size() > myMatchesLimit) break;
      }
      if (results.size() < myMatchesLimit) {
        findResultsToOccurrences(results, occurrences);
      }
    }
    return occurrences;
  }

  @Override
  public String getReplacementPreviewText(Editor editor, LiveOccurrence liveOccurrence) {
    String foundString = editor.getDocument().getText(liveOccurrence.getPrimaryRange());
    String documentText = editor.getDocument().getText();
    FindModel currentModel = myFindDialog.getCurrentModel();
    String stringToReplace = null;

    if (currentModel != null) {
      if (currentModel.isReplaceState()) {
        FindManager findManager = FindManager.getInstance(editor.getProject());
        stringToReplace = findManager.getStringToReplace(foundString, currentModel,
                                                         liveOccurrence.getPrimaryRange().getStartOffset(), documentText);
        if (stringToReplace != null && stringToReplace.isEmpty()) {
          stringToReplace = EMPTY_STRING_DISPLAY_TEXT;
        }
      }
    }
    return stringToReplace;
  }

  private static void findResultsToOccurrences(ArrayList<FindResult> results, Collection<LiveOccurrence> occurrences) {
    for (FindResult r : results) {
      LiveOccurrence occurrence = new LiveOccurrence();
      occurrence.setPrimaryRange(r);
      occurrences.add(occurrence);
    }
  }

  @Override
  public void stateChanged() {
    myLivePreview.update();
  }
}
