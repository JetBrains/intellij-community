// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.impl.livePreview;

import com.intellij.find.*;
import com.intellij.find.impl.FindResultImpl;
import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.event.BulkAwareDocumentListener;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.event.SelectionEvent;
import com.intellij.openapi.editor.event.SelectionListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.util.Alarm;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class LivePreviewController implements LivePreview.Delegate, FindUtil.ReplaceDelegate {
  public static final int USER_ACTIVITY_TRIGGERING_DELAY = 30;
  public static final int MATCHES_LIMIT = 10000;
  private final SearchSession myComponent;

  private int myUserActivityDelay = USER_ACTIVITY_TRIGGERING_DELAY;

  private final Alarm myLivePreviewAlarm;
  private final SearchResults mySearchResults;
  private LivePreview myLivePreview;
  private boolean mySuppressUpdate;

  private boolean myTrackingDocument;
  private boolean myChanged;

  private boolean myListeningSelection;

  private final SelectionListener mySelectionListener = new SelectionListener() {
    @Override
    public void selectionChanged(@NotNull SelectionEvent e) {
      smartUpdate();
    }
  };
  private boolean myDisposed;

  public void setTrackingSelection(boolean b) {
    if (b) {
      if (!myListeningSelection) {
        getEditor().getSelectionModel().addSelectionListener(mySelectionListener);
      }
    } else {
      if (myListeningSelection) {
        getEditor().getSelectionModel().removeSelectionListener(mySelectionListener);
      }
    }
    myListeningSelection = b;
  }


  private final DocumentListener myDocumentListener = new BulkAwareDocumentListener.Simple() {
    @Override
    public void afterDocumentChange(final @NotNull Document document) {
      if (!myTrackingDocument) {
        myChanged = true;
        return;
      }
      if (!mySuppressUpdate) {
        smartUpdate();
      } else {
        mySuppressUpdate = false;
      }
    }
  };

  private void smartUpdate() {
    if (myLivePreview == null) return;
    FindModel findModel = mySearchResults.getFindModel();
    if (findModel != null) {
      updateInBackground(findModel, false);
    }
  }

  public void moveCursor(SearchResults.Direction direction) {
    if (direction == SearchResults.Direction.UP) {
      mySearchResults.prevOccurrence(false);
    }
    else {
      mySearchResults.nextOccurrence(false);
    }
  }

  public boolean isLast(SearchResults.Direction direction) {
    List<FindResult> occurrences = mySearchResults.getOccurrences();
    FindResult cursor = mySearchResults.getCursor();
    FindResult last = direction == SearchResults.Direction.UP
                      ? ContainerUtil.getFirstItem(occurrences)
                      : ContainerUtil.getLastItem(occurrences);
    return cursor == last;
  }

  public LivePreviewController(SearchResults searchResults, @Nullable SearchSession component, @NotNull Disposable parentDisposable) {
    mySearchResults = searchResults;
    myComponent = component;
    getEditor().getDocument().addDocumentListener(myDocumentListener);
    myLivePreviewAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, parentDisposable);
  }

  public void setUserActivityDelay(int userActivityDelay) {
    myUserActivityDelay = userActivityDelay;
  }

  public void updateInBackground(@NotNull FindModel findModel, final boolean allowedToChangedEditorSelection) {
    final int stamp = mySearchResults.getStamp();
    myLivePreviewAlarm.cancelAllRequests();
    final FindModel copy = new FindModel();
    copy.copyFrom(findModel);
    mySearchResults.setUpdating(true);
    if (myComponent != null) {
      myComponent.getComponent().updateActions();
    }
    Runnable request = () -> mySearchResults.updateThreadSafe(copy, allowedToChangedEditorSelection, null, stamp)
      .doWhenRejected(() -> updateInBackground(findModel, allowedToChangedEditorSelection));
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      request.run();
    }
    else {
      myLivePreviewAlarm.addRequest(request, myUserActivityDelay);
    }
  }

  @Override
  public String getStringToReplace(@NotNull Editor editor, @Nullable FindResult findResult) throws FindManager.MalformedReplacementStringException {
    if (findResult == null) {
      return null;
    }
    String foundString = editor.getDocument().getText(findResult);
    CharSequence documentText = editor.getDocument().getImmutableCharSequence();
    FindModel currentModel = mySearchResults.getFindModel();

    if (currentModel != null && currentModel.isReplaceState()) {
      FindManager findManager = FindManager.getInstance(mySearchResults.getProject());
      return findManager.getStringToReplace(foundString, currentModel, findResult.getStartOffset(), documentText);
    }
    return null;
  }

  public @Nullable TextRange performReplace(final FindResult occurrence, final String replacement, final Editor editor) {
    Project project = mySearchResults.getProject();
    if (!ReadonlyStatusHandler.ensureDocumentWritable(project, editor.getDocument())) return null;
    FindModel findModel = mySearchResults.getFindModel();
    CommandProcessor.getInstance().runUndoTransparentAction(() -> getEditor().getCaretModel().moveToOffset(occurrence.getEndOffset()));
    TextRange result = FindUtil.doReplace(project,
                                          editor.getDocument(),
                                          findModel,
                                          new FindResultImpl(occurrence.getStartOffset(), occurrence.getEndOffset()),
                                          replacement,
                                          true,
                                          new ArrayList<>());
    mySearchResults.updateThreadSafe(findModel, true, result, mySearchResults.getStamp());
    return result;
  }

  private void performReplaceAll(Editor e) {
    Project project = mySearchResults.getProject();
    if (!ReadonlyStatusHandler.ensureDocumentWritable(project, e.getDocument())) {
      return;
    }
    if (mySearchResults.getFindModel() != null) {
      final FindModel copy = new FindModel();
      copy.copyFrom(mySearchResults.getFindModel());

      final SelectionModel selectionModel = mySearchResults.getEditor().getSelectionModel();

      final int offset;
      if (!selectionModel.hasSelection() || copy.isGlobal()) {
        copy.setGlobal(true);
        offset = 0;
      }
      else {
        offset = selectionModel.getBlockSelectionStarts()[0];
      }
      LocalHistoryAction action = LocalHistory.getInstance().startAction(
        FindBundle.message("find.replace.all.local.history.action", copy.getStringToFind(), copy.getStringToReplace()));
      try {
        FindUtil.replace(project, e, offset, copy, this);
      } finally {
        action.finish();
      }
    }
  }

  @Override
  public boolean shouldReplace(TextRange range, String replace) {
    for (RangeMarker r : mySearchResults.getExcluded()) {
      if (TextRange.areSegmentsEqual(r, range)) {
        return false;
      }
    }
    return true;
  }

  private Editor getEditor() {
    return mySearchResults.getEditor();
  }

  public void performReplace() throws FindManager.MalformedReplacementStringException {
    mySuppressUpdate = true;
    String replacement = getStringToReplace(getEditor(), mySearchResults.getCursor());
    if (replacement == null) {
      return;
    }
    final TextRange textRange = performReplace(mySearchResults.getCursor(), replacement, getEditor());
    if (textRange == null) {
      mySuppressUpdate = false;
    }
  }

  public void exclude() {
    mySearchResults.exclude(mySearchResults.getCursor());
  }

  public void performReplaceAll() {
    performReplaceAll(getEditor());
  }

  private void setTrackingDocument(boolean trackingDocument) {
    myTrackingDocument = trackingDocument;
  }

  public void setLivePreview(LivePreview livePreview) {
    if (myLivePreview != null) {
      myLivePreview.dispose();
      myLivePreview.setDelegate(null);
    }
    myLivePreview = livePreview;
    if (myLivePreview != null) {
      myLivePreview.setDelegate(this);
    }
  }

  public void dispose() {
    if (myDisposed) return;

    off();

    mySearchResults.dispose();
    getEditor().getDocument().removeDocumentListener(myDocumentListener);
    myDisposed = true;
  }

  public void on() {
    if (myDisposed) return;

    mySearchResults.setMatchesLimit(MATCHES_LIMIT);
    setTrackingDocument(true);

    if (myChanged) {
      mySearchResults.clear();
      myChanged = false;
    }

    var presentation = new EditorLivePreviewPresentation(getEditor().getColorsScheme());
    setLivePreview(new LivePreview(mySearchResults, presentation));
  }

  public void off() {
    if (myDisposed) return;

    mySearchResults.clear();
    setTrackingDocument(false);
    setLivePreview(null);
    setTrackingSelection(false);
  }
}
