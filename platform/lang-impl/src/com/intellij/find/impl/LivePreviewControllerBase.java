package com.intellij.find.impl;

import com.intellij.find.FindManager;
import com.intellij.find.FindModel;
import com.intellij.find.FindResult;
import com.intellij.find.FindUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class LivePreviewControllerBase implements LivePreview.Delegate {

  private static final String EMPTY_STRING_DISPLAY_TEXT = "<Empty string>";

  private int myMatchesLimit = 100;

  public int getMatchesLimit() {
    return myMatchesLimit;
  }

  public void setMatchesLimit(int matchesLimit) {
    myMatchesLimit = matchesLimit;
  }

  private FindModel myFindModel;

  public FindModel getFindModel() {
    return myFindModel;
  }

  public void setFindModel(FindModel findModel) {
    myFindModel = findModel;
  }

  private static void findResultsToOccurrences(ArrayList<FindResult> results, Collection<LiveOccurrence> occurrences) {
    for (FindResult r : results) {
      LiveOccurrence occurrence = new LiveOccurrence();
      occurrence.setPrimaryRange(r);
      occurrences.add(occurrence);
    }
  }

  @NotNull
  @Override
  public List<LiveOccurrence> performSearchInBackgroundInReadAction(Editor editor) {
    ArrayList<LiveOccurrence> occurrences = new ArrayList<LiveOccurrence>();
    if (myFindModel != null) {

      TextRange r = myFindModel.isGlobal() ? new TextRange(0, Integer.MAX_VALUE) :
                    new TextRange(editor.getSelectionModel().getSelectionStart(),
                                  editor.getSelectionModel().getSelectionEnd());
      int offset = r.getStartOffset();
      VirtualFile virtualFile = FileDocumentManager.getInstance().getFile(editor.getDocument());
      ArrayList<FindResult> results = new ArrayList<FindResult>();

      while (true) {
        FindManager findManager = FindManager.getInstance(editor.getProject());
        FindResult result = findManager.findString(editor.getDocument().getCharsSequence(), offset, myFindModel, virtualFile);
        if (!result.isStringFound()) break;
        int newOffset = result.getEndOffset();
        if (offset == newOffset || result.getEndOffset() > r.getEndOffset()) break;
        offset = newOffset;
        results.add(result);

        if (results.size() > myMatchesLimit) break;
      }
      if (results.size() < myMatchesLimit) {
        if (results.isEmpty()) {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              notFound();
            }
          });
        }
        findResultsToOccurrences(results, occurrences);
      } else {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            tooManyMatches();
          }
        });
      }
    }
    return occurrences;
  }

  protected void tooManyMatches() {  }

  @Override
  public String getReplacementPreviewText(Editor editor, LiveOccurrence liveOccurrence) {
    String foundString = editor.getDocument().getText(liveOccurrence.getPrimaryRange());
    String documentText = editor.getDocument().getText();
    FindModel currentModel = myFindModel;
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

  @Override
  public TextRange performReplace(final LiveOccurrence occurrence, final String replacement, final Editor editor) {
    TextRange range = occurrence.getPrimaryRange();
    try {
      return FindUtil.doReplace(editor.getProject(), editor.getDocument(), myFindModel, new FindResultImpl(range.getStartOffset(), range.getEndOffset()),
                         FindManager.getInstance(editor.getProject()).getStringToReplace(editor.getDocument().getText(range), myFindModel), true, new ArrayList<Pair<TextRange, String>>());
    }
    catch (FindManager.MalformedReplacementStringException e) {
      /**/
    }
    return null;
  }

  @Override
  public void performReplaceAll(Editor e) {
    FindUtil.replace(e.getProject(), e, 0, myFindModel);
  }

  @Override
  public void getFocusBack() {}

  public void notFound() {}
}
