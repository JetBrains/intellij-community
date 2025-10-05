// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.TailTypes;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupValueWithPsiElement;
import com.intellij.diagnostic.ThreadDumper;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.RuntimeExceptionWithAttachments;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.patterns.CharPattern;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.*;
import com.intellij.psi.filters.TrueFilter;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.UnmodifiableIterator;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;

public final class CompletionUtil {

  private static final CompletionData ourGenericCompletionData = new CompletionData() {
    {
      CompletionVariant variant = new CompletionVariant(PsiElement.class, TrueFilter.INSTANCE);
      variant.addCompletionFilter(TrueFilter.INSTANCE, TailTypes.noneType());
      registerVariant(variant);
    }
  };
  public static final @NonNls String DUMMY_IDENTIFIER = CompletionInitializationContext.DUMMY_IDENTIFIER;
  public static final @NonNls String DUMMY_IDENTIFIER_TRIMMED = CompletionInitializationContext.DUMMY_IDENTIFIER_TRIMMED;

  @ApiStatus.Internal
  public static @Nullable CompletionData getCompletionDataByElement(@Nullable PsiElement position, @NotNull PsiFile originalFile) {
    if (position == null) return null;
    return ourGenericCompletionData;
  }

  public static boolean shouldShowFeature(@NotNull CompletionParameters parameters, @NonNls String id) {
    return shouldShowFeature(parameters.getPosition().getProject(), id);
  }

  public static boolean shouldShowFeature(Project project, @NonNls String id) {
    if (FeatureUsageTracker.getInstance().isToBeAdvertisedInLookup(id, project)) {
      FeatureUsageTracker.getInstance().triggerFeatureShown(id);
      return true;
    }
    return false;
  }

  /**
   * @return a prefix for completion matching, calculated from the given parameters.
   * The prefix is the longest substring from inside {@code parameters.getPosition()}'s text,
   * ending at {@code parameters.getOffset()}, being a valid Java identifier.
   */
  public static @NotNull String findJavaIdentifierPrefix(@NotNull CompletionParameters parameters) {
    return findJavaIdentifierPrefix(parameters.getPosition(), parameters.getOffset());
  }

  /**
   * @return a prefix for completion matching, calculated from the given parameters.
   * The prefix is the longest substring from inside {@code position}'s text,
   * ending at {@code offsetInFile}, being a valid Java identifier.
   */
  public static @NotNull String findJavaIdentifierPrefix(@Nullable PsiElement position, int offsetInFile) {
    return findIdentifierPrefix(position, offsetInFile, CharPattern.javaIdentifierPartCharacter(), CharPattern.javaIdentifierStartCharacter());
  }

  /**
   * @return the result of {@link #findReferencePrefix}, or {@link #findAlphanumericPrefix} if there's no reference.
   */
  public static @NotNull String findReferenceOrAlphanumericPrefix(@NotNull CompletionParameters parameters) {
    String prefix = findReferencePrefix(parameters);
    return prefix == null ? findAlphanumericPrefix(parameters) : prefix;
  }

  /**
   * @return an alphanumeric prefix for completion matching, calculated from the given parameters.
   * The prefix is the longest substring from inside {@code parameters.getPosition()}'s text,
   * ending at {@code parameters.getOffset()}, consisting of letters and digits.
   */
  public static @NotNull String findAlphanumericPrefix(@NotNull CompletionParameters parameters) {
    return findIdentifierPrefix(parameters.getPosition().getContainingFile(), parameters.getOffset(), CharPattern.letterOrDigitCharacter(), CharPattern.letterOrDigitCharacter());
  }

  /**
   * @return a prefix for completion matching, calculated from the given element's text and the offsets.
   * The prefix is the longest substring from inside {@code position}'s text,
   * ending at {@code offsetInFile}, beginning with a character
   * satisfying {@code idStart}, and with all other characters satisfying {@code idPart}.
   */
  public static @NotNull String findIdentifierPrefix(@Nullable PsiElement position,
                                                     int offsetInFile,
                                                     @NotNull ElementPattern<Character> idPart,
                                                     @NotNull ElementPattern<Character> idStart) {
    if (position == null) return "";
    int startOffset = position.getTextRange().getStartOffset();
    return findInText(offsetInFile, startOffset, idPart, idStart, position.getNode().getChars());
  }

  public static @NotNull String findIdentifierPrefix(@NotNull Document document,
                                                     int offset,
                                                     @NotNull ElementPattern<Character> idPart,
                                                     @NotNull ElementPattern<Character> idStart) {
    CharSequence text = document.getImmutableCharSequence();
    return findInText(offset, 0, idPart, idStart, text);
  }

  private static @NotNull String findInText(int offset,
                                            int startOffset,
                                            @NotNull ElementPattern<Character> idPart,
                                            @NotNull ElementPattern<Character> idStart,
                                            @NotNull CharSequence text) {
    int offsetInElement = offset - startOffset;
    int start = offsetInElement - 1;
    while (start >=0) {
      if (!idPart.accepts(text.charAt(start))) break;
      --start;
    }
    while (start + 1 < offsetInElement && !idStart.accepts(text.charAt(start + 1))) {
      start++;
    }

    return text.subSequence(start + 1, offsetInElement).toString().trim();
  }

  /**
   * @return a prefix from completion matching calculated by a reference found at parameters' offset
   * (the reference text from the beginning until that offset),
   * or {@code null} if there's no reference there.
   */
  public static @Nullable String findReferencePrefix(@NotNull CompletionParameters parameters) {
    return findReferencePrefix(parameters.getPosition(), parameters.getOffset());
  }

  /**
   * @return a prefix from completion matching calculated by a reference found at the given offset
   * (the reference text from the beginning until that offset),
   * or {@code null} if there's no reference there.
   */
  public static @Nullable String findReferencePrefix(@NotNull PsiElement position, int offsetInFile) {
    try {
      PsiUtilCore.ensureValid(position);
      PsiReference ref = position.getContainingFile().findReferenceAt(offsetInFile);
      if (ref != null) {
        PsiElement element = ref.getElement();
        int offsetInElement = offsetInFile - element.getTextRange().getStartOffset();
        for (TextRange refRange : ReferenceRange.getRanges(ref)) {
          if (refRange.contains(offsetInElement)) {
            int beginIndex = refRange.getStartOffset();
            String text = element.getText();
            if (beginIndex < 0 || beginIndex > offsetInElement || offsetInElement > text.length()) {
              throw new AssertionError("Inconsistent reference range:" +
                                       " ref=" + ref.getClass() +
                                       " element=" + element.getClass() +
                                       " ref.start=" + refRange.getStartOffset() +
                                       " offset=" + offsetInElement +
                                       " psi.length=" + text.length());
            }
            return text.substring(beginIndex, offsetInElement);
          }
        }
      }
    }
    catch (IndexNotReadyException ignored) {
    }
    return null;
  }

  public static @NotNull InsertionContext emulateInsertion(@NotNull InsertionContext oldContext, int newStart, @NotNull LookupElement item) {
    InsertionContext newContext = newContext(oldContext, item);
    emulateInsertion(item, newStart, newContext);
    return newContext;
  }

  private static @NotNull InsertionContext newContext(@NotNull InsertionContext oldContext, @NotNull LookupElement forElement) {
    Editor editor = oldContext.getEditor();
    return new InsertionContext(new OffsetMap(editor.getDocument()), Lookup.AUTO_INSERT_SELECT_CHAR, new LookupElement[]{forElement}, oldContext.getFile(), editor,
                                oldContext.shouldAddCompletionChar());
  }

  public static @NotNull InsertionContext newContext(@NotNull InsertionContext oldContext,
                                                     @NotNull LookupElement forElement,
                                                     int startOffset,
                                                     int tailOffset) {
    InsertionContext context = newContext(oldContext, forElement);
    setOffsets(context, startOffset, tailOffset);
    return context;
  }

  public static void emulateInsertion(@NotNull LookupElement item, int offset, @NotNull InsertionContext context) {
    setOffsets(context, offset, offset);

    Editor editor = context.getEditor();
    Document document = editor.getDocument();
    String lookupString = item.getLookupString();

    document.insertString(offset, lookupString);
    editor.getCaretModel().moveToOffset(context.getTailOffset());
    PsiDocumentManager.getInstance(context.getProject()).commitDocument(document);
    item.handleInsert(context);
    PsiDocumentManager.getInstance(context.getProject()).doPostponedOperationsAndUnblockDocument(document);
  }

  private static void setOffsets(@NotNull InsertionContext context, int offset, int tailOffset) {
    OffsetMap offsetMap = context.getOffsetMap();
    offsetMap.addOffset(CompletionInitializationContext.START_OFFSET, offset);
    offsetMap.addOffset(CompletionInitializationContext.IDENTIFIER_END_OFFSET, tailOffset);
    offsetMap.addOffset(CompletionInitializationContext.SELECTION_END_OFFSET, tailOffset);
    context.setTailOffset(tailOffset);
  }

  public static @Nullable PsiElement getTargetElement(@NotNull LookupElement lookupElement) {
    PsiElement psiElement = lookupElement.getPsiElement();
    if (psiElement != null && psiElement.isValid()) {
      return getOriginalElement(psiElement);
    }

    Object object = lookupElement.getObject();
    if (object instanceof LookupValueWithPsiElement value) {
      PsiElement element = value.getElement();
      if (element != null && element.isValid()) return getOriginalElement(element);
    }

    return null;
  }

  public static @Nullable <T extends PsiElement> T getOriginalElement(@NotNull T psi) {
    return CompletionUtilCoreImpl.getOriginalElement(psi);
  }

  public static @NotNull <T extends PsiElement> T getOriginalOrSelf(@NotNull T psi) {
    T element = getOriginalElement(psi);
    return element == null ? psi : element;
  }

  public static @NotNull Iterable<String> iterateLookupStrings(@NotNull LookupElement element) {
    return new Iterable<>() {
      @Override
      public @NotNull Iterator<String> iterator() {
        Iterator<String> original = element.getAllLookupStrings().iterator();
        return new UnmodifiableIterator<>(original) {
          @Override
          public boolean hasNext() {
            try {
              return super.hasNext();
            }
            catch (ConcurrentModificationException e) {
              throw handleCME(e);
            }
          }

          @Override
          public String next() {
            try {
              return super.next();
            }
            catch (ConcurrentModificationException e) {
              throw handleCME(e);
            }
          }

          private RuntimeException handleCME(ConcurrentModificationException cme) {
            RuntimeExceptionWithAttachments ewa = new RuntimeExceptionWithAttachments(
              "Error while traversing lookup strings of " + element + " of " + element.getClass(),
              new Attachment("threadDump.txt", ThreadDumper.dumpThreadsToString()));
            ewa.initCause(cme);
            return ewa;
          }
        };
      }
    };
  }

  @ApiStatus.Internal
  public static @NotNull CompletionAssertions.WatchingInsertionContext createInsertionContext(@Nullable List<LookupElement> lookupItems,
                                                                                              @NotNull LookupElement item,
                                                                                              char completionChar,
                                                                                              Editor editor,
                                                                                              PsiFile psiFile,
                                                                                              int caretOffset,
                                                                                              int idEndOffset,
                                                                                              OffsetMap offsetMap) {
    int initialStartOffset = Math.max(0, caretOffset - item.getLookupString().length());

    return createInsertionContext(lookupItems, completionChar, editor, psiFile, initialStartOffset, caretOffset, idEndOffset, offsetMap);
  }

  @ApiStatus.Internal
  public static @NotNull CompletionAssertions.WatchingInsertionContext createInsertionContext(@Nullable List<LookupElement> lookupItems,
                                                                                              char completionChar,
                                                                                              @NotNull Editor editor,
                                                                                              @NotNull PsiFile psiFile,
                                                                                              int startOffset,
                                                                                              int caretOffset,
                                                                                              int idEndOffset,
                                                                                              @NotNull OffsetMap offsetMap) {

    offsetMap.addOffset(CompletionInitializationContext.START_OFFSET, startOffset);
    offsetMap.addOffset(CompletionInitializationContext.SELECTION_END_OFFSET, caretOffset);
    offsetMap.addOffset(CompletionInitializationContext.IDENTIFIER_END_OFFSET, idEndOffset);

    List<LookupElement> items = lookupItems == null ? Collections.emptyList() : lookupItems;

    return new CompletionAssertions.WatchingInsertionContext(offsetMap, psiFile, completionChar, items, editor);
  }

  @ApiStatus.Internal
  public static int calcIdEndOffset(@NotNull OffsetMap offsetMap, @NotNull Editor editor, @NotNull Integer initOffset) {
    return offsetMap.containsOffset(CompletionInitializationContext.IDENTIFIER_END_OFFSET) ?
           offsetMap.getOffset(CompletionInitializationContext.IDENTIFIER_END_OFFSET) :
           CompletionInitializationContext.calcDefaultIdentifierEnd(editor, initOffset);
  }

  @ApiStatus.Internal
  public static int calcIdEndOffset(@NotNull CompletionProcessEx indicator) {
    return calcIdEndOffset(indicator.getOffsetMap(), indicator.getEditor(), indicator.getCaret().getOffset());
  }

}
