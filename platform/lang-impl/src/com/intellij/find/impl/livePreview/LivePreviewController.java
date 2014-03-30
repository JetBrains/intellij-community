package com.intellij.find.impl.livePreview;

import com.intellij.find.*;
import com.intellij.find.impl.FindResultImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.SelectionEvent;
import com.intellij.openapi.editor.event.SelectionListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

public class LivePreviewController implements LivePreview.Delegate, FindUtil.ReplaceDelegate {

  private static final Logger LOG = Logger.getInstance("#com.intellij.find.impl.livePreview.LivePreviewController");

  public static final int USER_ACTIVITY_TRIGGERING_DELAY = 30;
  public static final int MATCHES_LIMIT = 10000;
  protected EditorSearchComponent myComponent;

  private int myUserActivityDelay = USER_ACTIVITY_TRIGGERING_DELAY;

  private final Alarm myLivePreviewAlarm = new Alarm(Alarm.ThreadToUse.SHARED_THREAD);
  protected SearchResults mySearchResults;
  private LivePreview myLivePreview;
  private final boolean myReplaceDenied = false;
  private boolean mySuppressUpdate = false;

  private boolean myTrackingDocument;
  private boolean myChanged;

  private boolean myListeningSelection = false;

  private final SelectionListener mySelectionListener = new SelectionListener() {
    @Override
    public void selectionChanged(SelectionEvent e) {
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


  private final DocumentAdapter myDocumentListener = new DocumentAdapter() {
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
    myLivePreview.inSmartUpdate();
    updateInBackground(mySearchResults.getFindModel(), false);
  }

  public void moveCursor(SearchResults.Direction direction) {
    if (direction == SearchResults.Direction.UP) {
      mySearchResults.prevOccurrence();
    } else {
      mySearchResults.nextOccurrence();
    }
  }

  public boolean isReplaceDenied() {
    return myReplaceDenied;
  }

  public LivePreviewController(SearchResults searchResults, @Nullable EditorSearchComponent component) {
    mySearchResults = searchResults;
    myComponent = component;
    getEditor().getDocument().addDocumentListener(myDocumentListener);
  }

  public int getUserActivityDelay() {
    return myUserActivityDelay;
  }

  public void setUserActivityDelay(int userActivityDelay) {
    myUserActivityDelay = userActivityDelay;
  }

  public void updateInBackground(FindModel findModel, final boolean allowedToChangedEditorSelection) {
    final int stamp = mySearchResults.getStamp();
    myLivePreviewAlarm.cancelAllRequests();
    if (findModel == null) return;
    final boolean unitTestMode = ApplicationManager.getApplication().isUnitTestMode();
    final FindModel copy = new FindModel();
    copy.copyFrom(findModel);

    Runnable request = new Runnable() {
      @Override
      public void run() {
        if (myDisposed) return;
        Project project = mySearchResults.getProject();
        if (project != null && project.isDisposed()) return;
        mySearchResults.updateThreadSafe(copy, allowedToChangedEditorSelection, null, stamp);
      }
    };
    if (unitTestMode) {
      request.run();
    } else {
      myLivePreviewAlarm.addRequest(request, myUserActivityDelay);
    }
  }

  @Override
  public String getStringToReplace(Editor editor, FindResult findResult) {
    if (findResult == null) {
      return null;
    }
    String foundString = editor.getDocument().getText(findResult);
    String documentText = editor.getDocument().getText();
    FindModel currentModel = mySearchResults.getFindModel();
    String stringToReplace = null;

    if (currentModel != null) {
      if (currentModel.isReplaceState()) {
        FindManager findManager = FindManager.getInstance(editor.getProject());
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
    if (myReplaceDenied || !ReadonlyStatusHandler.ensureDocumentWritable(editor.getProject(), editor.getDocument())) return null;
    FindModel findModel = mySearchResults.getFindModel();
    TextRange result = FindUtil.doReplace(editor.getProject(),
                                          editor.getDocument(),
                                          findModel,
                                          new FindResultImpl(occurrence.getStartOffset(), occurrence.getEndOffset()),
                                          replacement,
                                          true,
                                          new ArrayList<Pair<TextRange, String>>());
    myLivePreview.inSmartUpdate();
    mySearchResults.updateThreadSafe(findModel, true, result, mySearchResults.getStamp());
    return result;
  }

  public void performReplaceAll(Editor e) {
    if (!ReadonlyStatusHandler.ensureDocumentWritable(e.getProject(), e.getDocument())) return;
    if (mySearchResults.getFindModel() != null) {
      final FindModel copy = new FindModel();
      copy.copyFrom(mySearchResults.getFindModel());

      final SelectionModel selectionModel = mySearchResults.getEditor().getSelectionModel();

      final int offset;
      if ((!selectionModel.hasSelection() && !selectionModel.hasBlockSelection()) || copy.isGlobal()) {
        copy.setGlobal(true);
        offset = 0;
      } else {
        offset = selectionModel.getBlockSelectionStarts()[0];
      }
      FindUtil.replace(e.getProject(), e, offset, copy, this);
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
    if (mySearchResults != null && mySearchResults.getCursor() != null &&
        !isReplaceDenied() && (mySearchResults.getFindModel().isGlobal() ||
                                                       !mySearchResults.getEditor().getSelectionModel()
                                                         .hasBlockSelection()) ) {

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
      myComponent.addTextToRecent(myComponent.getReplaceField());
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

    myLivePreview.cleanUp();
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
