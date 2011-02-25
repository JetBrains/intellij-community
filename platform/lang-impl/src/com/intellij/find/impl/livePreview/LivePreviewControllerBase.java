package com.intellij.find.impl.livePreview;

import com.intellij.find.FindManager;
import com.intellij.find.FindModel;
import com.intellij.find.FindUtil;
import com.intellij.find.impl.FindResultImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

public class LivePreviewControllerBase implements LivePreview.Delegate, FindUtil.ReplaceDelegate, SearchResults.SearchResultsListener {

  private static final String EMPTY_STRING_DISPLAY_TEXT = "<Empty string>";

  private static final int USER_ACTIVITY_TRIGGERING_DELAY = 300;

  private int myUserActivityDelay = USER_ACTIVITY_TRIGGERING_DELAY;

  private final Alarm myLivePreviewAlarm = new Alarm(Alarm.ThreadToUse.SHARED_THREAD);

  private SearchResults mySearchResults;
  private LivePreview myLivePreview;

  private boolean myToChangeSelection = true;

  private void updateSelection() {
    Editor editor = mySearchResults.getEditor();
    SelectionModel selection = editor.getSelectionModel();
    FindModel findModel = mySearchResults.getFindModel();
    if (myToChangeSelection && findModel.isGlobal()) {
      LiveOccurrence cursor = mySearchResults.getCursor();
      if (cursor != null) {
        TextRange range = cursor.getPrimaryRange();
        selection.setSelection(range.getStartOffset(), range.getEndOffset());

        editor.getCaretModel().moveToOffset(range.getEndOffset());
        editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);

      }
      myToChangeSelection = false;
    }
  }

  @Override
  public void searchResultsUpdated(SearchResults sr) {
    updateSelection();
  }

  @Override
  public void editorChanged(SearchResults sr, Editor oldEditor) {}

  @Override
  public void cursorMoved() {
    updateSelection();
  }

  public void moveCursor(SearchResults.Direction direction, boolean toChangeSelection) {
    myToChangeSelection = toChangeSelection;
    if (direction == SearchResults.Direction.UP) {
      mySearchResults.prevOccurrence();
    } else {
      mySearchResults.nextOccurrence();
    }
  }

  public interface ReplaceListener {
    void replacePerformed(LiveOccurrence occurrence, final String replacement, final Editor editor);
    void replaceAllPerformed(Editor e);
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

  public void updateInBackground(final FindModel findModel, boolean allowedToChangedEditorSelection) {
    myLivePreviewAlarm.cancelAllRequests();
    if (findModel == null) return;
    Runnable request = new Runnable() {
      @Override
      public void run() {
        mySearchResults.updateThreadSafe(findModel);
      }
    };
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      request.run();
    } else {
      myLivePreviewAlarm.addRequest(request, myUserActivityDelay);
    }
    if (allowedToChangedEditorSelection) {
      myToChangeSelection = true;
    }
  }

  @Override
  public String getStringToReplace(Editor editor, LiveOccurrence liveOccurrence) {
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
    myToChangeSelection = true;
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
    mySearchResults.updateThreadSafe(findModel);
    return result;
  }

  @Override
  public void performReplaceAll(Editor e) {
    FindUtil.replace(e.getProject(), e,
                     mySearchResults.getFindModel().isGlobal() ? 0 : mySearchResults.getEditor().getSelectionModel().getSelectionStart(),
                     mySearchResults.getFindModel(), this);
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
