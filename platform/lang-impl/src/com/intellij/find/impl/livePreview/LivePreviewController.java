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
package com.intellij.find.impl.livePreview;

import com.intellij.find.*;
import com.intellij.find.impl.FindResultImpl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.event.SelectionListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

public class LivePreviewController implements LivePreview.Delegate, FindUtil.ReplaceDelegate {
  public static final int USER_ACTIVITY_TRIGGERING_DELAY = 30;
  public static final int MATCHES_LIMIT = 10000;
  protected EditorSearchSession myComponent;

  private int myUserActivityDelay = USER_ACTIVITY_TRIGGERING_DELAY;

  private final Alarm myLivePreviewAlarm;
  protected SearchResults mySearchResults;
  private LivePreview myLivePreview;
  private final boolean myReplaceDenied = false;
  private boolean mySuppressUpdate;

  private boolean myTrackingDocument;
  private boolean myChanged;

  private boolean myListeningSelection;

  private final SelectionListener mySelectionListener = e -> smartUpdate();
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


  private final DocumentListener myDocumentListener = new DocumentListener() {
    @Override
    public void documentChanged(final DocumentEvent e) {
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
    myLivePreview.inSmartUpdate();
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

  public boolean isReplaceDenied() {
    return myReplaceDenied;
  }

  public LivePreviewController(SearchResults searchResults, @Nullable EditorSearchSession component, @NotNull Disposable parentDisposable) {
    mySearchResults = searchResults;
    myComponent = component;
    getEditor().getDocument().addDocumentListener(myDocumentListener);
    myLivePreviewAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, parentDisposable);
  }

  public int getUserActivityDelay() {
    return myUserActivityDelay;
  }

  public void setUserActivityDelay(int userActivityDelay) {
    myUserActivityDelay = userActivityDelay;
  }

  public void updateInBackground(@NotNull FindModel findModel, final boolean allowedToChangedEditorSelection) {
    final int stamp = mySearchResults.getStamp();
    myLivePreviewAlarm.cancelAllRequests();
    final FindModel copy = new FindModel();
    copy.copyFrom(findModel);

    Runnable request = () -> {
      if (myDisposed) return;
      Project project = mySearchResults.getProject();
      if (project != null && project.isDisposed()) return;
      mySearchResults.updateThreadSafe(copy, allowedToChangedEditorSelection, null, stamp);
    };
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      request.run();
    }
    else {
      myLivePreviewAlarm.addRequest(request, myUserActivityDelay);
    }
  }

  @Override
  public String getStringToReplace(@NotNull Editor editor, @Nullable FindResult findResult) {
    if (findResult == null) {
      return null;
    }
    String foundString = editor.getDocument().getText(findResult);
    CharSequence documentText = editor.getDocument().getImmutableCharSequence();
    FindModel currentModel = mySearchResults.getFindModel();
    String stringToReplace = null;

    if (currentModel != null) {
      if (currentModel.isReplaceState()) {
        FindManager findManager = FindManager.getInstance(mySearchResults.getProject());
        try {
          stringToReplace = findManager.getStringToReplace(foundString, currentModel,
                                                           findResult.getStartOffset(), documentText);
        }
        catch (FindManager.MalformedReplacementStringException e) {
          return null;
        }
      }
    }
    return stringToReplace;
  }

  @Nullable
  public TextRange performReplace(final FindResult occurrence, final String replacement, final Editor editor) {
    Project project = mySearchResults.getProject();
    if (myReplaceDenied || !ReadonlyStatusHandler.ensureDocumentWritable(project, editor.getDocument())) return null;
    FindModel findModel = mySearchResults.getFindModel();
    CommandProcessor.getInstance().runUndoTransparentAction(() -> getEditor().getCaretModel().moveToOffset(occurrence.getEndOffset()));
    TextRange result = FindUtil.doReplace(project,
                                          editor.getDocument(),
                                          findModel,
                                          new FindResultImpl(occurrence.getStartOffset(), occurrence.getEndOffset()),
                                          replacement,
                                          true,
                                          new ArrayList<>());
    myLivePreview.inSmartUpdate();
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
      FindUtil.replace(project, e, offset, copy, this);
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

  public boolean canReplace() {
    if (mySearchResults != null && mySearchResults.getCursor() != null && !isReplaceDenied()) {

      final String replacement = getStringToReplace(getEditor(), mySearchResults.getCursor());
      return replacement != null;
    }
    return false;
  }

  private Editor getEditor() {
    return mySearchResults.getEditor();
  }

  public void performReplace() {
    mySuppressUpdate = true;
    String replacement = getStringToReplace(getEditor(), mySearchResults.getCursor());
    if (replacement == null) {
      return;
    }
    final TextRange textRange = performReplace(mySearchResults.getCursor(), replacement, getEditor());
    if (textRange == null) {
      mySuppressUpdate = false;
    }
    if (myComponent != null) {
      myComponent.addTextToRecent(myComponent.getComponent().getReplaceTextComponent());
      myComponent.clearUndoInTextFields();
    }
  }

  public void exclude() {
    mySearchResults.exclude(mySearchResults.getCursor());
  }

  public void performReplaceAll() {
    performReplaceAll(getEditor());
  }

  public void setTrackingDocument(boolean trackingDocument) {
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

    setLivePreview(new LivePreview(mySearchResults));
  }

  public void off() {
    if (myDisposed) return;

    setTrackingDocument(false);
    setLivePreview(null);
    setTrackingSelection(false);
  }
}
