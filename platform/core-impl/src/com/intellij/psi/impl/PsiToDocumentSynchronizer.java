// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.psi.impl;

import com.intellij.injected.editor.DocumentWindow;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.ForeignLeafPsiElement;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.text.ImmutableCharSequence;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

public class PsiToDocumentSynchronizer extends PsiTreeChangeAdapter {
  private static final Logger LOG = Logger.getInstance(PsiToDocumentSynchronizer.class);
  private static final Key<Boolean> PSI_DOCUMENT_ATOMIC_ACTION = Key.create("PSI_DOCUMENT_ATOMIC_ACTION");

  private final PsiDocumentManagerBase myPsiDocumentManager;
  private final MessageBus myBus;
  private final Map<Document, Pair<DocumentChangeTransaction, Integer>> myTransactionsMap = new ConcurrentHashMap<>();

  private volatile Document mySyncDocument;

  PsiToDocumentSynchronizer(@NotNull PsiDocumentManagerBase psiDocumentManager, @NotNull MessageBus bus) {
    myPsiDocumentManager = psiDocumentManager;
    myBus = bus;
  }

  public @Nullable DocumentChangeTransaction getTransaction(@NotNull Document document) {
    final Pair<DocumentChangeTransaction, Integer> pair = myTransactionsMap.get(document);
    return Pair.getFirst(pair);
  }

  public boolean isInSynchronization(@NotNull Document document) {
    return mySyncDocument == document;
  }

  @TestOnly
  void cleanupForNextTest() {
    myTransactionsMap.clear();
    mySyncDocument = null;
  }

  @FunctionalInterface
  private interface DocSyncAction {
    void syncDocument(@NotNull Document document, @NotNull PsiTreeChangeEventImpl event);
  }

  private void checkPsiModificationAllowed(final @NotNull PsiTreeChangeEvent event) {
    if (!toProcessPsiEvent()) return;
    final PsiFile psiFile = event.getFile();
    if (!(psiFile instanceof PsiFileEx) || !((PsiFileEx)psiFile).isContentsLoaded()) return;

    final Document document = myPsiDocumentManager.getCachedDocument(psiFile);
    if (document != null && myPsiDocumentManager.isUncommited(document)) {
      throw new IllegalStateException("Attempt to modify PSI for non-committed Document!");
    }
  }

  private DocumentEx getCachedDocument(PsiFile psiFile, boolean force) {
    final DocumentEx document = (DocumentEx)myPsiDocumentManager.getCachedDocument(psiFile);
    if (document == null || document instanceof DocumentWindow || !force && getTransaction(document) == null) {
      return null;
    }
    return document;
  }

  private void doSync(final @NotNull PsiTreeChangeEvent event, boolean force, final @NotNull DocSyncAction syncAction) {
    if (!toProcessPsiEvent()) return;
    final PsiFile psiFile = event.getFile();
    if (!(psiFile instanceof PsiFileEx) || !((PsiFileEx)psiFile).isContentsLoaded()) return;

    final DocumentEx document = getCachedDocument(psiFile, force);
    if (document == null) return;

    performAtomically(psiFile, () -> syncAction.syncDocument(document, (PsiTreeChangeEventImpl)event));

    final boolean insideTransaction = myTransactionsMap.containsKey(document);
    if (!insideTransaction) {
      document.setModificationStamp(psiFile.getViewProvider().getModificationStamp());
    }
  }

  static boolean isInsideAtomicChange(@NotNull PsiFile file) {
    return file.getUserData(PSI_DOCUMENT_ATOMIC_ACTION) == Boolean.TRUE;
  }

  public static void performAtomically(@NotNull PsiFile file, @NotNull Runnable runnable) {
    PsiUtilCore.ensureValid(file);
    assert !isInsideAtomicChange(file);
    file.putUserData(PSI_DOCUMENT_ATOMIC_ACTION, Boolean.TRUE);

    try {
      runnable.run();
    }
    finally {
      file.putUserData(PSI_DOCUMENT_ATOMIC_ACTION, null);
    }
  }

  @Override
  public void beforeChildAddition(@NotNull PsiTreeChangeEvent event) {
    checkPsiModificationAllowed(event);
  }

  @Override
  public void beforeChildRemoval(@NotNull PsiTreeChangeEvent event) {
    checkPsiModificationAllowed(event);
  }

  @Override
  public void beforeChildReplacement(@NotNull PsiTreeChangeEvent event) {
    checkPsiModificationAllowed(event);
  }

  @Override
  public void beforeChildrenChange(@NotNull PsiTreeChangeEvent event) {
    checkPsiModificationAllowed(event);
  }

  @Override
  public void childAdded(final @NotNull PsiTreeChangeEvent event) {
    if (!(event.getChild() instanceof ForeignLeafPsiElement)) {
      doSync(event, false, (document, event1) -> insertString(document, event1.getOffset(), event1.getChild().getText()));
    }
  }

  @Override
  public void childRemoved(final @NotNull PsiTreeChangeEvent event) {
    if (!(event.getChild() instanceof ForeignLeafPsiElement)) {
      doSync(event, false, (document, event1) -> deleteString(document, event1.getOffset(), event1.getOffset() + event1.getOldLength()));
    }
  }

  @Override
  public void childReplaced(final @NotNull PsiTreeChangeEvent event) {
    doSync(event, false, (document, event1) -> {
      int oldLength = event1.getOldChild() instanceof ForeignLeafPsiElement ? 0 : event1.getOldLength();
      String newText = event1.getNewChild() instanceof ForeignLeafPsiElement ? "" : event1.getNewChild().getText();
      replaceString(document, event1.getOffset(), event1.getOffset() + oldLength, newText, event1.getNewChild());
    });
  }

  @Override
  public void childrenChanged(final @NotNull PsiTreeChangeEvent event) {
    doSync(event, false, (document, event1) ->
      replaceString(document, event1.getOffset(), event1.getOffset() + event1.getOldLength(), event1.getParent().getText(), event1
             .getParent()));
  }

  private boolean myIgnorePsiEvents;
  public void setIgnorePsiEvents(boolean ignorePsiEvents) {
    myIgnorePsiEvents = ignorePsiEvents;
  }

  public boolean isIgnorePsiEvents() {
    return myIgnorePsiEvents;
  }

  public boolean toProcessPsiEvent() {
    return !myIgnorePsiEvents && !myPsiDocumentManager.isCommitInProgress() && !ApplicationManager.getApplication().hasWriteAction(IgnorePsiEventsMarker.class);
  }

  @TestOnly
  public void replaceString(@NotNull Document document, int startOffset, int endOffset, @NotNull String s) {
    replaceString(document, startOffset, endOffset, s, null);
  }

  private void replaceString(@NotNull Document document, int startOffset, int endOffset, @NotNull String s, @Nullable PsiElement replacement) {
    final DocumentChangeTransaction documentChangeTransaction = getTransaction(document);
    if(documentChangeTransaction != null) {
      documentChangeTransaction.replace(startOffset, endOffset - startOffset, s, replacement);
    }
  }

  public void insertString(@NotNull Document document, int offset, @NotNull String s) {
    final DocumentChangeTransaction documentChangeTransaction = getTransaction(document);
    if(documentChangeTransaction != null){
      documentChangeTransaction.replace(offset, 0, s, null);
    }
  }

  private void deleteString(@NotNull Document document, int startOffset, int endOffset){
    final DocumentChangeTransaction documentChangeTransaction = getTransaction(document);
    if(documentChangeTransaction != null){
      documentChangeTransaction.replace(startOffset, endOffset - startOffset, "", null);
    }
  }

  public void startTransaction(@NotNull Project project, @NotNull Document doc, @NotNull PsiElement scope) {
    LOG.assertTrue(!project.isDisposed());
    Pair<DocumentChangeTransaction, Integer> pair = myTransactionsMap.get(doc);
    Pair<DocumentChangeTransaction, Integer> prev = pair;
    if (pair == null) {
      final PsiFile psiFile = scope.getContainingFile();
      pair = new Pair<>(new DocumentChangeTransaction(doc, psiFile), 0);
      myBus.syncPublisher(PsiDocumentTransactionListener.TOPIC).transactionStarted(doc, psiFile);
    }
    else {
      pair = new Pair<>(pair.getFirst(), pair.getSecond().intValue() + 1);
    }
    LOG.assertTrue(myTransactionsMap.put(doc, pair) == prev);
  }

  public boolean commitTransaction(@NotNull Document document){
    ApplicationManager.getApplication().assertIsWriteThread();
    final DocumentChangeTransaction documentChangeTransaction = removeTransaction(document);
    if(documentChangeTransaction == null) return false;
    final PsiFile changeScope = documentChangeTransaction.myChangeScope;
    try {
      mySyncDocument = document;

      final PsiTreeChangeEventImpl fakeEvent = new PsiTreeChangeEventImpl(changeScope.getManager());
      fakeEvent.setParent(changeScope);
      fakeEvent.setFile(changeScope);
      checkPsiModificationAllowed(fakeEvent);
      doSync(fakeEvent, true, (document1, event) -> doCommitTransaction(document1, documentChangeTransaction));
      myBus.syncPublisher(PsiDocumentTransactionListener.TOPIC).transactionCompleted(document, changeScope);
    }
    catch (Throwable e) {
      myPsiDocumentManager.forceReload(changeScope.getViewProvider().getVirtualFile(), changeScope.getViewProvider());
      ExceptionUtil.rethrowAllAsUnchecked(e);
    }
    finally {
      mySyncDocument = null;
    }
    return true;
  }

  private static void doCommitTransaction(@NotNull Document document, @NotNull DocumentChangeTransaction documentChangeTransaction) {
    DocumentEx ex = (DocumentEx) document;
    ex.suppressGuardedExceptions();
    try {
      boolean isReadOnly = !document.isWritable();
      ex.setReadOnly(false);

      for (Map.Entry<TextRange, CharSequence> entry : documentChangeTransaction.myAffectedFragments.descendingMap().entrySet()) {
        ex.replaceString(entry.getKey().getStartOffset(), entry.getKey().getEndOffset(), entry.getValue());
      }

      ex.setReadOnly(isReadOnly);
    }
    finally {
      ex.unSuppressGuardedExceptions();
    }
  }

  private @Nullable DocumentChangeTransaction removeTransaction(@NotNull Document doc) {
    Pair<DocumentChangeTransaction, Integer> pair = myTransactionsMap.get(doc);
    if(pair == null) return null;
    int nestedCount = pair.getSecond().intValue();
    if(nestedCount > 0){
      pair = Pair.create(pair.getFirst(), nestedCount - 1);
      myTransactionsMap.put(doc, pair);
      return null;
    }
    myTransactionsMap.remove(doc);
    return pair.getFirst();
  }

  public boolean isDocumentAffectedByTransactions(@NotNull Document document) {
    return myTransactionsMap.containsKey(document);
  }

  public static class DocumentChangeTransaction{
    private final TreeMap<TextRange, CharSequence> myAffectedFragments = new TreeMap<>(Comparator.comparingInt(TextRange::getStartOffset));
    private final PsiFile myChangeScope;
    private ImmutableCharSequence myPsiText;

    DocumentChangeTransaction(@NotNull Document doc, @NotNull PsiFile scope) {
      myChangeScope = scope;
      myPsiText = CharArrayUtil.createImmutableCharSequence(doc.getImmutableCharSequence());
    }

    public @NotNull Map<TextRange, CharSequence> getAffectedFragments() {
      return myAffectedFragments;
    }

    public void replace(int psiStart, int length, @NotNull String replace, @Nullable PsiElement replacement) {
      // calculating fragment
      // minimize replace
      int start = 0;
      int end = start + length;

      final CharSequence chars = myPsiText.subSequence(psiStart, psiStart + length);
      if (StringUtil.equals(chars, replace)) return;

      int newStartInReplace = 0;
      final int replaceLength = replace.length();
      while (newStartInReplace < replaceLength && start < end && replace.charAt(newStartInReplace) == chars.charAt(start)) {
        start++;
        newStartInReplace++;
      }

      int newEndInReplace = replaceLength;
      while (start < end && newStartInReplace < newEndInReplace && replace.charAt(newEndInReplace - 1) == chars.charAt(end - 1)) {
        newEndInReplace--;
        end--;
      }

      // increase the changed range to start and end on PSI token boundaries
      // this will help to survive smart pointers with the same boundaries
      if (replacement != null && (newStartInReplace > 0 || newEndInReplace < replaceLength)) {
        PsiElement startLeaf = replacement.findElementAt(newStartInReplace);
        PsiElement endLeaf = replacement.findElementAt(newEndInReplace - 1);
        if (startLeaf != null && endLeaf != null) {
          int leafStart = startLeaf.getTextRange().getStartOffset() - replacement.getTextRange().getStartOffset();
          int leafEnd = endLeaf.getTextRange().getEndOffset() - replacement.getTextRange().getStartOffset();
          start += leafStart - newStartInReplace;
          end += leafEnd - newEndInReplace;
          newStartInReplace = leafStart;
          newEndInReplace = leafEnd;
        }
      }

      // optimization: when delete fragment from the middle of the text, prefer split at the line boundaries
      if (newStartInReplace == newEndInReplace && start > 0 && start < end && StringUtil.indexOf(chars, '\n', start, end) != -1) {
        // try to align to the line boundaries
        while (start > 0 &&
               newStartInReplace > 0 &&
               chars.charAt(start - 1) == chars.charAt(end - 1) &&
               chars.charAt(end - 1) != '\n'
          ) {
          start--;
          end--;
          newStartInReplace--;
          newEndInReplace--;
        }
      }

      start += psiStart;
      end += psiStart;

      //[mike] dirty hack for xml:
      //make sure that deletion of <t> in: <tag><t/><tag> doesn't remove t/><
      //which is perfectly valid but invalidates range markers
      final CharSequence charsSequence = myPsiText;
      while (start < charsSequence.length() && end < charsSequence.length() && start > 0 &&
             charsSequence.subSequence(start, end).toString().endsWith("><") && charsSequence.charAt(start - 1) == '<') {
        start--;
        newStartInReplace--;
        end--;
        newEndInReplace--;
      }

      updateFragments(start, end, replace.substring(newStartInReplace, newEndInReplace));
    }

    private void updateFragments(int start, int end, @NotNull String replace) {
      int docStart = psiToDocumentOffset(start);
      int docEnd = psiToDocumentOffset(end);

      TextRange startRange = findFragment(docStart);
      TextRange endRange = findFragment(docEnd);

      myPsiText = myPsiText.delete(start, end).insert(start, replace);

      TextRange newFragment = new TextRange(startRange != null ? startRange.getStartOffset() : docStart,
                                            endRange != null ? endRange.getEndOffset() : docEnd);
      CharSequence newReplacement = myPsiText.subSequence(documentToPsiOffset(newFragment.getStartOffset(), false),
                                                          documentToPsiOffset(newFragment.getEndOffset(), true) + replace.length() - (end - start));

      myAffectedFragments.keySet().removeIf(range -> range.intersects(newFragment));
      myAffectedFragments.put(newFragment, newReplacement);
    }

    private TextRange findFragment(final int docOffset) {
      return ContainerUtil.find(myAffectedFragments.keySet(), range -> range.containsOffset(docOffset));
    }

    private int psiToDocumentOffset(int offset) {
      for (Map.Entry<TextRange, CharSequence> entry : myAffectedFragments.entrySet()) {
        int lengthAfter = entry.getValue().length();
        TextRange range = entry.getKey();
        if (range.getStartOffset() + lengthAfter < offset) {
          offset += range.getLength() - lengthAfter;
          continue;
        }

        // for offsets inside replaced ranges, return the starts of the original affected fragments in document
        return Math.min(range.getStartOffset(), offset);
      }
      return offset;
    }

    private int documentToPsiOffset(int offset, boolean greedyRight) {
      int delta = 0;
      for (Map.Entry<TextRange, CharSequence> entry : myAffectedFragments.entrySet()) {
        int lengthAfter = entry.getValue().length();
        TextRange range = entry.getKey();
        // for offsets inside affected fragments, return either start or end of the updated range
        if (range.containsOffset(offset)) {
          return range.getStartOffset() + delta + (greedyRight ? lengthAfter : 0);
        }
        if (range.getStartOffset() > offset) {
          break;
        }
        delta += lengthAfter - range.getLength();
      }
      return offset + delta;
    }
  }
}
