// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.psi.impl;

import com.intellij.injected.editor.DocumentWindow;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.core.impl.PomModelImpl;
import com.intellij.pom.tree.events.impl.ChangeInfoImpl;
import com.intellij.pom.tree.events.impl.TreeChangeEventImpl;
import com.intellij.pom.tree.events.impl.TreeChangeImpl;
import com.intellij.psi.IgnorePsiEventsMarker;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiTreeChangeEvent;
import com.intellij.psi.impl.source.DummyHolder;
import com.intellij.psi.impl.source.tree.ForeignLeafPsiElement;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.text.ImmutableCharSequence;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PsiToDocumentSynchronizer {
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
    Pair<DocumentChangeTransaction, Integer> pair = myTransactionsMap.get(document);
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

  private void checkPsiModificationAllowed(@NotNull PsiTreeChangeEvent event) {
    if (!toProcessPsiEvent()) return;
    PsiFile psiFile = event.getFile();
    if (!(psiFile instanceof PsiFileEx) || !((PsiFileEx)psiFile).isContentsLoaded()) return;

    Document document = myPsiDocumentManager.getCachedDocument(psiFile);
    if (document != null && myPsiDocumentManager.isUncommited(document)) {
      throw new IllegalStateException("Attempt to modify PSI for non-committed Document!");
    }
  }

  private DocumentEx getCachedDocument(PsiFile psiFile, boolean force) {
    DocumentEx document = (DocumentEx)FileDocumentManager.getInstance().getCachedDocument(psiFile.getViewProvider().getVirtualFile());
    if (document == null || document instanceof DocumentWindow || !force && getTransaction(document) == null) {
      return null;
    }
    return document;
  }

  private void doSync(@NotNull PsiTreeChangeEvent event, @NotNull DocSyncAction syncAction) {
    if (!toProcessPsiEvent()) return;
    PsiFile psiFile = event.getFile();
    if (!(psiFile instanceof PsiFileEx) || !((PsiFileEx)psiFile).isContentsLoaded()) return;

    DocumentEx document = getCachedDocument(psiFile, true);
    if (document == null) return;

    performAtomically(psiFile, () -> syncAction.syncDocument(document, (PsiTreeChangeEventImpl)event));

    boolean insideTransaction = myTransactionsMap.containsKey(document);
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
    DocumentChangeTransaction documentChangeTransaction = getTransaction(document);
    if(documentChangeTransaction != null) {
      documentChangeTransaction.replace(startOffset, endOffset - startOffset, s, null);
    }
  }

  @TestOnly
  public void insertString(@NotNull Document document, int offset, @NotNull String s) {
    DocumentChangeTransaction documentChangeTransaction = getTransaction(document);
    if(documentChangeTransaction != null){
      documentChangeTransaction.replace(offset, 0, s, null);
    }
  }

  public void startTransaction(@NotNull Project project, @NotNull Document doc, @NotNull PsiFile scope) {
    LOG.assertTrue(!project.isDisposed());
    Pair<DocumentChangeTransaction, Integer> pair = myTransactionsMap.get(doc);
    Pair<DocumentChangeTransaction, Integer> prev = pair;
    if (pair == null) {
      PsiFile psiFile = scope.getContainingFile();
      pair = new Pair<>(new DocumentChangeTransaction(doc, psiFile), 0);
      if (scope.isPhysical()) {
        myBus.syncPublisher(PsiDocumentTransactionListener.TOPIC).transactionStarted(doc, psiFile);
      }
    }
    else {
      pair = new Pair<>(pair.getFirst(), pair.getSecond().intValue() + 1);
    }
    LOG.assertTrue(myTransactionsMap.put(doc, pair) == prev);
  }

  public boolean commitTransaction(@NotNull Document document){
    DocumentChangeTransaction documentChangeTransaction = removeTransaction(document);
    if(documentChangeTransaction == null) return false;
    PsiFile changeScope = documentChangeTransaction.myChangeScope;
    try {
      mySyncDocument = document;

      PsiTreeChangeEventImpl fakeEvent = new PsiTreeChangeEventImpl(changeScope.getManager());
      fakeEvent.setParent(changeScope);
      fakeEvent.setFile(changeScope);
      checkPsiModificationAllowed(fakeEvent);
      doSync(fakeEvent, (document1, event) -> doCommitTransaction(document1, documentChangeTransaction));
      if (PomModelImpl.shouldFirePhysicalPsiEvents(changeScope)) {
        myBus.syncPublisher(PsiDocumentTransactionListener.TOPIC).transactionCompleted(document, changeScope);
      }
    }
    catch (Throwable e) {
      myPsiDocumentManager.forceReload(changeScope.getViewProvider().getVirtualFile(), Collections.singletonList(changeScope.getViewProvider()));
      //noinspection ConstantConditions
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

  @ApiStatus.Internal
  public void processEvents(@NotNull TreeChangeEventImpl changeSet, @NotNull PsiFile file) {
    if (file instanceof DummyHolder || !toProcessPsiEvent()) return;

    Document document = getCachedDocument(file, false);
    DocumentChangeTransaction transaction = document == null ? null : getTransaction(document);
    if (transaction == null) return;

    for (TreeChangeImpl change : changeSet.getSortedChanges()) {
      int parentStart = change.getChangedParent().getStartOffset();
      for (ASTNode child : change.getAffectedChildren()) {
        ChangeInfoImpl info = change.getChangeByChild(child);
        ASTNode newChild = info.getNewChild();
        PsiElement newPsi = newChild == null ? null : newChild.getPsi();
        if (!(newPsi instanceof ForeignLeafPsiElement)) {
          transaction.replace(info.getOffsetInParent() + parentStart, info.getOldLength(), newChild == null ? "" : newChild.getText(), newPsi);
        }
      }
    }
  }

  public static class DocumentChangeTransaction{
    private final TreeMap<TextRange, CharSequence> myAffectedFragments = new TreeMap<>(Comparator.comparingInt(TextRange::getStartOffset));
    private final @NotNull PsiFile myChangeScope;
    private @NotNull ImmutableCharSequence myPsiText;

    DocumentChangeTransaction(@NotNull Document doc, @NotNull PsiFile scope) {
      myChangeScope = scope;
      myPsiText = CharArrayUtil.createImmutableCharSequence(doc.getImmutableCharSequence());
    }

    @TestOnly
    public @NotNull Map<TextRange, CharSequence> getAffectedFragments() {
      return myAffectedFragments;
    }

    void replace(int psiStart, int length, @NotNull String replace, @Nullable PsiElement replacement) {
      // calculating fragment
      // minimize replace
      int start = 0;
      int end = start + length;

      CharSequence chars = myPsiText.subSequence(psiStart, psiStart + length);
      if (StringUtil.equals(chars, replace)) return;

      int newStartInReplace = 0;
      int replaceLength = replace.length();
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

      updateFragments(start, end, replace.substring(newStartInReplace, newEndInReplace));
    }

    private void updateFragments(int start, int end, @NotNull String replace) {
      int docStart = psiToDocumentOffset(start);
      int docEnd = psiToDocumentOffset(end);

      TextRange startRange = findFragment(docStart);
      TextRange endRange = findFragment(docEnd);

      myPsiText = myPsiText.replace(start, end, replace);

      TextRange newFragment = new TextRange(startRange != null ? startRange.getStartOffset() : docStart,
                                            endRange != null ? endRange.getEndOffset() : docEnd);
      CharSequence newReplacement = myPsiText.subSequence(documentToPsiOffset(newFragment.getStartOffset(), false),
                                                          documentToPsiOffset(newFragment.getEndOffset(), true) + replace.length() - (end - start));

      myAffectedFragments.keySet().removeIf(range -> range.intersects(newFragment));
      myAffectedFragments.put(newFragment, newReplacement);
    }

    private TextRange findFragment(int docOffset) {
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
