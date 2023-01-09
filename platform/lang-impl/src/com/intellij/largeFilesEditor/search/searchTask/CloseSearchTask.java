// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.largeFilesEditor.search.searchTask;

import com.intellij.largeFilesEditor.search.SearchResult;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class CloseSearchTask extends SearchTaskBase {
  private static final Logger logger = Logger.getInstance(CloseSearchTask.class);

  private final Callback myCallback;

  public CloseSearchTask(SearchTaskOptions options,
                         Project project,
                         FileDataProviderForSearch fileDataProviderForSearch,
                         Callback callback) {
    super(options, project, fileDataProviderForSearch);
    myCallback = callback;
  }

  @Override
  public void doRun() {
    FrameSearcher searcher;
    String prevPageText;
    String curPageText;
    String nextPageText;
    String tailText;
    char prefixSymbol;
    char postfixSymbol;
    long pagesAmount;
    int tailLength;
    long curPageNumber;
    ArrayList<SearchResult> allMatchesAtFrame;
    int index;

    searcher = createFrameSearcher(options, project);
    tailLength = getTailLength(options);

    try {
      /* preparing init data... */
      pagesAmount = fileDataProviderForSearch.getPagesAmount();
      curPageNumber = getPageNumberForBeginning(pagesAmount, options);

      /* checking if it is the end... */
      if (isTheEndOfSearchingCycle(curPageNumber, pagesAmount, options)) {
        myCallback.tellSearchIsFinished(this, curPageNumber);
        return;
      }

      prevPageText = curPageNumber > 0 ?
                     fileDataProviderForSearch.getPage_wait(curPageNumber - 1).getText() : "";
      curPageText = fileDataProviderForSearch.getPage_wait(curPageNumber).getText();
      nextPageText = curPageNumber < pagesAmount - 1 ?
                     fileDataProviderForSearch.getPage_wait(curPageNumber + 1).getText() : "";
      tailText = getTailFromPage(nextPageText, tailLength);
      prefixSymbol = getPrefixSymbol(prevPageText);
      postfixSymbol = getPostfixSymbol(nextPageText, tailLength);

      myCallback.tellSearchProgress(this, curPageNumber, pagesAmount);

      /* searching in start page... */
      searcher.setFrame(curPageNumber, prefixSymbol, curPageText, tailText, postfixSymbol);
      allMatchesAtFrame = searcher.findAllMatchesAtFrame();

      index = tryGetClosestResult(allMatchesAtFrame, options);
      if (index != -1) {
        myCallback.tellClosestResultFound(this, allMatchesAtFrame, index);
        return;
      }

      /* searching in next pages... */
      while (true) {

        /* preparing data for searching in next page if it's possible*/
        pagesAmount = fileDataProviderForSearch.getPagesAmount();
        if (options.searchForwardDirection) {
          curPageNumber++;
          prevPageText = curPageText;
          curPageText = nextPageText;
          nextPageText = curPageNumber < pagesAmount - 1 ?
                         fileDataProviderForSearch.getPage_wait(curPageNumber + 1).getText() : "";
        }
        else {
          curPageNumber--;
          nextPageText = curPageText;
          curPageText = prevPageText;
          prevPageText = curPageNumber > 0 ?
                         fileDataProviderForSearch.getPage_wait(curPageNumber - 1).getText() : "";
        }

        /* checking if it is the end... */
        if (isTheEndOfSearchingCycle(curPageNumber, pagesAmount, options)) {
          myCallback.tellSearchIsFinished(this, getPreviousPageNumber(curPageNumber, options));
          return;
        }
        if (isShouldStop()) {
          myCallback.tellSearchWasStopped(this, curPageNumber);
          return;
        }

        /* preparing addictive data */
        tailText = getTailFromPage(nextPageText, tailLength);
        prefixSymbol = getPrefixSymbol(prevPageText);
        postfixSymbol = getPostfixSymbol(nextPageText, tailLength);

        myCallback.tellSearchProgress(this, curPageNumber, pagesAmount);

        /* searching for result in current page */
        searcher.setFrame(curPageNumber, prefixSymbol, curPageText, tailText, postfixSymbol);
        allMatchesAtFrame = searcher.findAllMatchesAtFrame();
        if (allMatchesAtFrame.size() > 0) {
          myCallback.tellClosestResultFound(this, allMatchesAtFrame,
                                          options.searchForwardDirection ? 0 : allMatchesAtFrame.size() - 1);
          return;
        }
      }
    }
    catch (IOException e) {
      logger.warn(e);
      myCallback.tellSearchWasCatchedException(this, e);
    }
  }


  /**
   * @return the closest search result index in 'allMatchesAtFrame' for search direction
   * if such result exists; '-1' if doesn't.
   */
  private static int tryGetClosestResult(List<? extends SearchResult> allMatchesAtFrame, SearchTaskOptions options) {
    if (!allMatchesAtFrame.isEmpty()) {
      SearchResult searchResult;
      if (options.searchForwardDirection) {

        /* FORWARD */
        if (options.leftBoundPageNumber == SearchTaskOptions.NO_LIMIT) {
          return 0;
        }

        for (int i = 0; i < allMatchesAtFrame.size(); i++) {
          searchResult = allMatchesAtFrame.get(i);
          if (searchResult.startPosition.pageNumber > options.leftBoundPageNumber
              || searchResult.startPosition.pageNumber == options.leftBoundPageNumber
                 && searchResult.startPosition.symbolOffsetInPage >= options.leftBoundCaretPageOffset) {
            return i;
          }
        }
      }
      else {

        /* BACKWARD */
        if (options.rightBoundPageNumber == SearchTaskOptions.NO_LIMIT) {
          return allMatchesAtFrame.size() - 1;
        }

        for (int i = allMatchesAtFrame.size() - 1; i >= 0; i--) {
          searchResult = allMatchesAtFrame.get(i);
          if (searchResult.endPostion.pageNumber < options.rightBoundPageNumber
              || searchResult.endPostion.pageNumber == options.rightBoundPageNumber
                 && searchResult.endPostion.symbolOffsetInPage < options.rightBoundCaretPageOffset) {
            return i;
          }
        }
      }
    }
    return -1;
  }

  public interface Callback {

    void tellSearchIsFinished(CloseSearchTask caller, long curPageNumber);

    void tellSearchProgress(CloseSearchTask caller, long curPageNumber, long pagesAmount);

    void tellSearchWasStopped(CloseSearchTask caller, long curPageNumber);

    void tellSearchWasCatchedException(CloseSearchTask caller, IOException e);

    void tellClosestResultFound(CloseSearchTask caller, List<? extends SearchResult> allMatchesAtFrame, int index);
  }
}
