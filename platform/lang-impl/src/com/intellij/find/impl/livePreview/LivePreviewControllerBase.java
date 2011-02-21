package com.intellij.find.impl.livePreview;

import com.intellij.find.FindManager;
import com.intellij.find.FindModel;
import com.intellij.find.FindUtil;
import com.intellij.find.impl.FindResultImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

public class LivePreviewControllerBase implements LivePreview.Delegate, FindUtil.ReplaceDelegate {

  private static final String EMPTY_STRING_DISPLAY_TEXT = "<Empty string>";

  private static final int USER_ACTIVITY_TRIGGERING_DELAY = 300;

  private int myUserActivityDelay = USER_ACTIVITY_TRIGGERING_DELAY;

  private final Alarm myLivePreviewAlarm = new Alarm(Alarm.ThreadToUse.SHARED_THREAD);

  private SearchResults mySearchResults;
  private LivePreview myLivePreview;

  public LivePreviewControllerBase(SearchResults searchResults, LivePreview livePreview) {
    mySearchResults = searchResults;
    myLivePreview = livePreview;
    myLivePreview.setDelegate(this);
  }

  public int getUserActivityDelay() {
    return myUserActivityDelay;
  }

  public void setUserActivityDelay(int userActivityDelay) {
    myUserActivityDelay = userActivityDelay;
  }

  public void updateInBackground(final FindModel findModel) {
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
  }

  @Override
  public String getReplacementPreviewText(Editor editor, LiveOccurrence liveOccurrence) {
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
    TextRange range = occurrence.getPrimaryRange();
    FindModel findModel = mySearchResults.getFindModel();
    try {
      return FindUtil.doReplace(editor.getProject(), editor.getDocument(), findModel, new FindResultImpl(range.getStartOffset(), range.getEndOffset()),
                         FindManager.getInstance(editor.getProject()).getStringToReplace(editor.getDocument().getText(range), findModel),
                         true, new ArrayList<Pair<TextRange, String>>());
    }
    catch (FindManager.MalformedReplacementStringException e) {
      /**/
    }
    mySearchResults.updateThreadSafe(findModel);
    return null;
  }

  @Override
  public void performReplaceAll(Editor e) {
    FindUtil.replace(e.getProject(), e, 0, mySearchResults.getFindModel(), this);
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
