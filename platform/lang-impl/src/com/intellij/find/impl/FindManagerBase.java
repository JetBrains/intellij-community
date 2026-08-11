// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.impl;

import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.find.FindBundle;
import com.intellij.find.FindManager;
import com.intellij.find.FindModel;
import com.intellij.find.FindModel.SearchContext;
import com.intellij.find.FindResult;
import com.intellij.find.FindSettings;
import com.intellij.find.findUsages.FindUsagesManager;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.KeyWithDefaultValue;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.patterns.StringPattern;
import com.intellij.util.containers.IntObjectMap;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.text.StringSearcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.SoftReference;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public abstract class FindManagerBase extends FindManager {
  private static final Logger LOG = Logger.getInstance(FindManagerBase.class);
  private static final Key<ThreadLocal<SoftReference<FindExceptCommentsOrLiteralsData>>> ourExceptCommentsOrLiteralsDataKey =
    KeyWithDefaultValue.create("except.comments.literals.search.data", () -> new ThreadLocal<>());
  private static final IntObjectMap<Boolean> ourReportedPatterns = ConcurrentCollectionFactory.createConcurrentIntObjectMap();
  static final FindResultImpl NOT_FOUND_RESULT = new FindResultImpl();

  protected final FindUsagesManager myFindUsagesManager;
  protected final @NotNull FindModel myFindInProjectModel = new FindModel();
  protected final @NotNull FindModel myFindInFileModel = new FindModel();
  @NotNull protected final Project myProject;
  private final @NotNull CommentsAndLiteralsSearcher myCommentsAndLiteralsSearcher;

  public FindManagerBase(@NotNull Project project) {
    myProject = project;
    myFindUsagesManager = new FindUsagesManager(project);
    myCommentsAndLiteralsSearcher = new CommentsAndLiteralsSearcher(project);

    FindSettings findSettings = FindSettings.getInstance();
    findSettings.initModelBySetings(myFindInProjectModel);

    myFindInFileModel.setCaseSensitive(findSettings.isLocalCaseSensitive());
    myFindInFileModel.setWholeWordsOnly(findSettings.isLocalWholeWordsOnly());
    myFindInFileModel.setRegularExpressions(findSettings.isLocalRegularExpressions());

    myFindInProjectModel.setMultipleFiles(true);
  }

  static void clearPreviousFindData(FindModel model) {
    synchronized (model) {
      CommentsAndLiteralsSearcher.clearPreviousFindData(model);
      model.putUserData(ourExceptCommentsOrLiteralsDataKey, null);
    }
  }

  @Override
  public @NotNull FindModel getFindInFileModel() {
    return myFindInFileModel;
  }

  @Override
  public @NotNull FindModel getFindInProjectModel() {
    myFindInProjectModel.setFromCursor(false);
    myFindInProjectModel.setForward(true);
    myFindInProjectModel.setGlobal(true);
    myFindInProjectModel.setSearchInProjectFiles(false);
    return myFindInProjectModel;
  }

  @Override
  public @NotNull FindResult findString(@NotNull CharSequence text, int offset, @NotNull FindModel model) {
    return findString(text, offset, model, null);
  }

  @Override
  public @NotNull FindResult findString(@NotNull CharSequence text, int offset, @NotNull FindModel model, @Nullable VirtualFile file) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("offset=" + offset);
      LOG.debug("textlength=" + text.length());
      LOG.debug(model.toString());
    }

    return findStringLoop(text, offset, model, file, getFindContextPredicate(model, file, text));
  }

  FindResult findStringLoop(@NotNull CharSequence text,
                                    int offset,
                                    @NotNull FindModel model,
                                    @Nullable VirtualFile file,
                                    @Nullable Predicate<? super FindResult> filter) {
    final char[] textArray = CharArrayUtil.fromSequenceWithoutCopying(text);
    while (true) {
      ProgressManager.checkCanceled();

      FindResult result = doFindString(text, textArray, offset, model, file);
      if (filter == null || filter.test(result)) {
        if (!model.isWholeWordsOnly() || !result.isStringFound() || isWholeWord(text, result.getStartOffset(), result.getEndOffset())) {
          return result;
        }
      }

      offset = model.isForward() ? result.getStartOffset() + 1 : result.getEndOffset() - 1;
      if (offset > text.length() || offset < 0) return NOT_FOUND_RESULT;
    }
  }

  /**
   * Performs a single search attempt and returns the first occurrence it runs into, without checking whether that occurrence is
   * acceptable: neither {@link FindModel#isWholeWordsOnly() whole words only} nor the find context are taken into account here.
   * Filtering those out and resuming the search past a rejected occurrence is the job of {@link #findStringLoop}, which is why this
   * method is only meaningful as a step of that loop.
   * <p>
   * A forward search covers {@code [offset, text.length())} and a backward one {@code [0, offset - 1)}, so {@code offset} is where
   * the search starts rather than where it is anchored. Depending on the model the search runs over comments and string literals only,
   * as a regular expression, or as a plain {@link StringSearcher} scan.
   *
   * @param text      the text in which the search is performed.
   * @param textArray the backing array of {@code text} as returned by {@link CharArrayUtil#fromSequenceWithoutCopying}, or {@code null}
   *                  if it has none; purely an optimization that lets the scan avoid {@link CharSequence#charAt} calls.
   * @param offset    the start offset for the search.
   * @param model     the settings for the search, including the string to find.
   * @param file      the file {@code text} belongs to, needed to search comments and literals; may be {@code null} for other searches.
   * @return the first occurrence found, or a result with {@link FindResult#isStringFound()} set to {@code false} if there is none.
   */
  private @NotNull FindResult doFindString(@NotNull CharSequence text,
                                           char @Nullable [] textArray,
                                           int offset,
                                           @NotNull FindModel model,
                                           @Nullable VirtualFile file) {
    String toFind = model.getStringToFind();
    if (toFind.isEmpty()) {
      return NOT_FOUND_RESULT;
    }

    if (model.isInCommentsOnly() || model.isInStringLiteralsOnly()) {
      if (file == null) return NOT_FOUND_RESULT;
      return myCommentsAndLiteralsSearcher.findInCommentsAndLiterals(text, textArray, offset, model, file);
    }

    if (model.isRegularExpressions()) {
      return findStringByRegularExpression(text, offset, model, file);
    }

    final StringSearcher searcher = createStringSearcher(model);

    int index;
    if (model.isForward()) {
      final int res = searcher.scan(text, textArray, offset, text.length());
      index = res < 0 ? -1 : res;
    }
    else {
      index = offset == 0 ? -1 : searcher.scan(text, textArray, 0, offset - 1);
    }
    if (index < 0) {
      return NOT_FOUND_RESULT;
    }
    return new FindResultImpl(index, index + toFind.length());
  }

  private @NotNull FindResult findStringByRegularExpression(@NotNull CharSequence text,
                                                            int startOffset,
                                                            @NotNull FindModel model,
                                                            @Nullable VirtualFile file) {
    Matcher matcher = compileRegExp(model, text);
    if (matcher == null) {
      return NOT_FOUND_RESULT;
    }
    try {
      if (model.isForward()) {
        if (matcher.find(startOffset)) {
          if (matcher.end() <= text.length()) {
            return new FindResultImpl(matcher.start(), matcher.end());
          }
        }
        return NOT_FOUND_RESULT;
      }
      else {
        int start = -1;
        int end = -1;
        while (matcher.find() && matcher.end() < startOffset) {
          start = matcher.start();
          end = matcher.end();
        }
        if (start < 0) {
          return NOT_FOUND_RESULT;
        }
        return new FindResultImpl(start, end);
      }
    }
    catch (StackOverflowError soe) {
      String stringToFind = model.getStringToFind();

      if (!ApplicationManager.getApplication().isHeadlessEnvironment() &&
          ourReportedPatterns.put(stringToFind.hashCode(), Boolean.TRUE) == null) {
        String content = FindBundle.message("notification.content.regular.expression.soe", stringToFind, file != null ? file.getPresentableUrl() : "<no-file>");
        LOG.info(content);
        String message = FindBundle.message("notification.title.regular.expression.failed.to.match");
        NotificationGroupManager.getInstance().getNotificationGroup("Find Problems")
          .createNotification(message, content, NotificationType.ERROR).notify(myProject);
      }
      return NOT_FOUND_RESULT;
    }
  }

  private Predicate<FindResult> getFindContextPredicate(@NotNull FindModel model, @Nullable VirtualFile file, @NotNull CharSequence text) {
    if (file == null) return null;
    SearchContext context = model.getSearchContext();
    if (context == SearchContext.ANY || context == SearchContext.IN_COMMENTS || context == SearchContext.IN_STRING_LITERALS) {
      return null;
    }

    ThreadLocal<SoftReference<FindExceptCommentsOrLiteralsData>> data;
    synchronized (model) {
      data = model.getUserData(ourExceptCommentsOrLiteralsDataKey);
      assert data != null;
    }

    SoftReference<FindExceptCommentsOrLiteralsData> currentThreadDataRef = data.get();
    FindExceptCommentsOrLiteralsData currentThreadData = currentThreadDataRef == null ? null : currentThreadDataRef.get();
    if (currentThreadData == null || !currentThreadData.isAcceptableFor(model, file, text)) {
      currentThreadData = FindExceptCommentsOrLiteralsData.create(file, model, text, this);
      data.set(new SoftReference<>(currentThreadData));
    }
    return currentThreadData;
  }

  private static @NotNull StringSearcher createStringSearcher(@NotNull FindModel model) {
    return new StringSearcher(model.getStringToFind(), model.isCaseSensitive(), model.isForward());
  }



  private static boolean isWholeWord(@NotNull CharSequence text, int startOffset, int endOffset) {
    boolean isWordStart;

    if (startOffset != 0) {
      boolean previousCharacterIsIdentifier = Character.isJavaIdentifierPart(text.charAt(startOffset - 1)) &&
                                              (startOffset <= 1 || text.charAt(startOffset - 2) != '\\');
      boolean previousCharacterIsSameAsNext = text.charAt(startOffset - 1) == text.charAt(startOffset);

      boolean firstCharacterIsIdentifier = Character.isJavaIdentifierPart(text.charAt(startOffset));
      isWordStart = firstCharacterIsIdentifier ? !previousCharacterIsIdentifier : !previousCharacterIsSameAsNext;
    }
    else {
      isWordStart = true;
    }

    boolean isWordEnd;

    if (endOffset != text.length()) {
      boolean nextCharacterIsIdentifier = Character.isJavaIdentifierPart(text.charAt(endOffset));
      boolean nextCharacterIsSameAsPrevious = endOffset > 0 && text.charAt(endOffset) == text.charAt(endOffset - 1);
      boolean lastSearchedCharacterIsIdentifier = endOffset > 0 && Character.isJavaIdentifierPart(text.charAt(endOffset - 1));

      isWordEnd = lastSearchedCharacterIsIdentifier ? !nextCharacterIsIdentifier : !nextCharacterIsSameAsPrevious;
    }
    else {
      isWordEnd = true;
    }

    return isWordStart && isWordEnd;
  }

  static @Nullable Matcher compileRegExp(@NotNull FindModel model, @NotNull CharSequence text) {
    Pattern pattern = model.compileRegExp();
    return pattern == null ? null : pattern.matcher(StringPattern.newBombedCharSequence(text));
  }

  @Override
  public @NotNull String getStringToReplace(@NotNull String foundString,
                                            @NotNull FindModel model,
                                            int startOffset,
                                            @NotNull CharSequence documentText) throws MalformedReplacementStringException {
    String replacement = model.getStringToReplace();
    if (model.isRegularExpressions()) {
      replacement = getStringToReplaceByRegexp(model, documentText, startOffset);
    }
    if (model.isPreserveCase()) {
      replacement = Registry.is("ide.find.word.based.preserve.case")
                    ? PreserveCaseUtil.applyCase(foundString, replacement)
                    : PreserveCaseUtil.replaceWithCaseRespect(replacement, foundString);
    }
    return replacement;
  }

  private static String getStringToReplaceByRegexp(@NotNull FindModel model, @NotNull CharSequence text, int startOffset)
    throws MalformedReplacementStringException {
    return getStringToReplaceByRegexp(model, compileRegexAndFindFirst(model, text, startOffset));
  }

  private static @Nullable String getStringToReplaceByRegexp(@NotNull FindModel model, Matcher matcher)
    throws MalformedReplacementStringException {
    if (matcher == null) return null;
    try {
      return new RegExReplacementBuilder(matcher).createReplacement(model.getStringToReplace());
    }
    catch (Exception e) {
      throw createMalformedReplacementException(e);
    }
  }

  private static @Nullable Matcher compileRegexAndFindFirst(@NotNull FindModel model, @NotNull CharSequence text, int startOffset) {
    Matcher matcher = compileRegExp(model, text);
    assert matcher != null;

    if (model.isForward()) {
      if (!matcher.find(startOffset)) {
        return null;
      }
      if (matcher.end() > text.length()) {
        return null;
      }
    }
    else {
      int start = -1;
      while (matcher.find() && matcher.end() < startOffset) {
        start = matcher.start();
      }
      if (start < 0) {
        return null;
      }
    }
    return matcher;
  }


  private static @NotNull MalformedReplacementStringException createMalformedReplacementException(@NotNull Exception e) {
    String message = FindBundle.message("find.replace.invalid.replacement.string", e.getMessage());
    return new MalformedReplacementStringException(message, e);
  }


  public @NotNull FindUsagesManager getFindUsagesManager() {
    return myFindUsagesManager;
  }
}
