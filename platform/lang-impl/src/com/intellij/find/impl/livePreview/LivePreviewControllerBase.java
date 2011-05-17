package com.intellij.find.impl.livePreview;

import com.intellij.find.FindManager;
import com.intellij.find.FindModel;
import com.intellij.find.FindUtil;
import com.intellij.find.editorHeaderActions.Utils;
import com.intellij.find.impl.FindResultImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

public class LivePreviewControllerBase implements LivePreview.Delegate, FindUtil.ReplaceDelegate, SearchResults.SearchResultsListener {

  private static final Logger LOG = Logger.getInstance("#com.intellij.find.impl.livePreview.LivePreviewControllerBase");

  private static final String EMPTY_STRING_DISPLAY_TEXT = "<Empty string>";

  private static final int USER_ACTIVITY_TRIGGERING_DELAY = 30;

  private int myUserActivityDelay = USER_ACTIVITY_TRIGGERING_DELAY;

  private final Alarm myLivePreviewAlarm = new Alarm(Alarm.ThreadToUse.SHARED_THREAD);
  private SearchResults mySearchResults;
  private LivePreview myLivePreview;
  private boolean myReplaceDenied = false;

  private void updateSelection() {
    Editor editor = mySearchResults.getEditor();
    SelectionModel selection = editor.getSelectionModel();
    FindModel findModel = mySearchResults.getFindModel();
    if (findModel != null && findModel.isGlobal()) {
      LiveOccurrence cursor = mySearchResults.getCursor();
      if (cursor != null) {
        TextRange range = cursor.getPrimaryRange();
        FoldingModel foldingModel = editor.getFoldingModel();
        final FoldRegion startFolding = foldingModel.getCollapsedRegionAtOffset(range.getStartOffset());
        final FoldRegion endFolding = foldingModel.getCollapsedRegionAtOffset(range.getEndOffset());
        foldingModel.runBatchFoldingOperation(new Runnable() {
          @Override
          public void run() {
            if (startFolding != null) {
              startFolding.setExpanded(true);
            }
            if (endFolding != null) {
              endFolding.setExpanded(true);
            }
          }
        });
        selection.setSelection(range.getStartOffset(), range.getEndOffset());

        editor.getCaretModel().moveToOffset(range.getEndOffset());
        editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);

      }
    }
  }

  @Override
  public void searchResultsUpdated(SearchResults sr) {
    //setReplaceDenied(false);
  }

  @Override
  public void editorChanged(SearchResults sr, Editor oldEditor) {}

  @Override
  public void cursorMoved(boolean toChangeSelection) {
    if (toChangeSelection) {
      updateSelection();
    }
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

  public void setReplaceDenied(final boolean replaceDenied) {
    boolean changed = replaceDenied != myReplaceDenied;
    myReplaceDenied = replaceDenied;
    if (changed && myReplaceListener != null) {
      if (replaceDenied) {
        myReplaceListener.replaceDenied();
      }
      else {
        myReplaceListener.replaceAllowed();
      }
    }
  }

  public interface ReplaceListener {
    void replacePerformed(LiveOccurrence occurrence, final String replacement, final Editor editor);
    void replaceAllPerformed(Editor e);
    void replaceDenied();
    void replaceAllowed();
  }

  private ReplaceListener myReplaceListener;

  public ReplaceListener getReplaceListener() {
    return myReplaceListener;
  }

  public void setReplaceListener(ReplaceListener replaceListener) {
    myReplaceListener = replaceListener;
  }

  public LivePreviewControllerBase(SearchResults searchResults, LivePreview livePreview) {
    mySearchResults = searchResults;
    mySearchResults.addListener(this);
    myLivePreview = livePreview;
    myLivePreview.setDelegate(this);
  }

  public int getUserActivityDelay() {
    return myUserActivityDelay;
  }

  public void setUserActivityDelay(int userActivityDelay) {
    myUserActivityDelay = userActivityDelay;
  }

  public void updateInBackground(FindModel findModel, final boolean allowedToChangedEditorSelection) {
    myLivePreviewAlarm.cancelAllRequests();
    if (findModel == null) return;
    final boolean unitTestMode = ApplicationManager.getApplication().isUnitTestMode();
    final FindModel copy = new FindModel();
    copy.copyFrom(findModel);
    
    final ModalityState modalityState = ModalityState.current();
    final int stamp = mySearchResults.getStamp();
    Runnable request = new Runnable() {
      @Override
      public void run() {
        Runnable denyReplace = new Runnable() {
          @Override
          public void run() {
            //setReplaceDenied(true);
          }
        };
        if (unitTestMode) {
          denyReplace.run();
        } else {
          ApplicationManager.getApplication().invokeAndWait(denyReplace, modalityState);
        }
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
  public String getStringToReplace(Editor editor, LiveOccurrence liveOccurrence) {
    if (liveOccurrence == null) {
      return null;
    }
    String foundString = editor.getDocument().getText(liveOccurrence.getPrimaryRange());
    String documentText = editor.getDocument().getText();
    FindModel currentModel = mySearchResults.getFindModel();
    String stringToReplace = null;

    if (currentModel != null) {
      if (currentModel.isReplaceState()) {
        FindManager findManager = FindManager.getInstance(editor.getProject());
        try {
          stringToReplace = findManager.getStringToReplace(foundString, currentModel,
                                                           liveOccurrence.getPrimaryRange().getStartOffset(), documentText);
        }
        catch (FindManager.MalformedReplacementStringException e) {
          return null;
        }
        if (stringToReplace != null && stringToReplace.isEmpty()) {
          stringToReplace = EMPTY_STRING_DISPLAY_TEXT;
        }
      }
    }
    return stringToReplace;
  }

  @Nullable
  @Override
  public TextRange performReplace(final LiveOccurrence occurrence, final String replacement, final Editor editor) {
    if (myReplaceDenied || !Utils.ensureOkToWrite(editor)) return null;
    TextRange range = occurrence.getPrimaryRange();
    FindModel findModel = mySearchResults.getFindModel();
    TextRange result = null;
    try {
      result = FindUtil.doReplace(editor.getProject(), editor.getDocument(), findModel, new FindResultImpl(range.getStartOffset(), range.getEndOffset()),
                         FindManager.getInstance(editor.getProject()).getStringToReplace(editor.getDocument().getText(range), findModel),
                         true, new ArrayList<Pair<TextRange, String>>());
    }
    catch (FindManager.MalformedReplacementStringException e) {
      /**/
    }
    if (myReplaceListener != null) {
      myReplaceListener.replacePerformed(occurrence, replacement, editor);
    }
    //setReplaceDenied(true);
    myLivePreview.inSmartUpdate();
    mySearchResults.updateThreadSafe(findModel, true, result, mySearchResults.getStamp());
    //myLivePreview.supressUpdate();
    return result;
  }

  @Override
  public void performReplaceAll(Editor e) {
    if (!Utils.ensureOkToWrite(e)) return;
    if (mySearchResults.getFindModel() != null) {
      final FindModel copy = new FindModel();
      copy.copyFrom(mySearchResults.getFindModel());

      final SelectionModel selectionModel = mySearchResults.getEditor().getSelectionModel();

      int offset = 0;
      if (selectionModel.getSelectedText() != null) {
        if (!mySearchResults.getFindModel().isGlobal()) {
          offset = selectionModel.getSelectionStart();
        }
      } else {
        copy.setGlobal(true);
      }

      FindUtil.replace(e.getProject(), e, offset, copy, this);

      if (myReplaceListener != null) {
        myReplaceListener.replaceAllPerformed(e);
      }
    }
  }

  @Override
  public void getFocusBack() {}

  @Override
  public boolean shouldReplace(TextRange range, String replace) {
    for (LiveOccurrence o : mySearchResults.getExcluded()) {
      TextRange primaryRange = o.getPrimaryRange();
      if (primaryRange.equals(range)) {
        return false;
      }
    }
    return true;
  }
}
