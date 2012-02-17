/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.indexing.fileBasedIndex.impl;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.impl.EditorHighlighterCache;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLock;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.containers.HashSet;
import com.intellij.util.indexing.FileContentImpl;
import com.intellij.util.indexing.ID;
import com.intellij.util.indexing.IndexingDataKeys;
import com.intellij.util.indexing.StorageException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Set;

public class FileBasedIndexUnsavedDocumentsManagerImpl implements FileBasedIndexUnsavedDocumentsManager {
  private FileDocumentManager myFileDocumentManager;
  private FileBasedIndexIndicesManager myIndexIndicesManager;
  private FileBasedIndexTransactionMap myTransactionMap;
  private FileBasedIndexLimitsChecker myLimitsChecker;

  public FileBasedIndexUnsavedDocumentsManagerImpl(FileDocumentManager fileDocumentManager,
                                                   FileBasedIndexIndicesManager indexIndicesManager,
                                                   FileBasedIndexTransactionMap transactionMap,
                                                   FileBasedIndexLimitsChecker limitsChecker) {
    myFileDocumentManager = fileDocumentManager;
    myIndexIndicesManager = indexIndicesManager;
    myTransactionMap = transactionMap;
    myLimitsChecker = limitsChecker;
  }

  @Override
  public void indexUnsavedDocuments(ID<?, ?> indexId, @Nullable Project project, GlobalSearchScope filter,
                                    VirtualFile restrictedFile) throws StorageException {
    if (myIndexIndicesManager.isUpToDate(indexId)) {
      return; // no need to index unsaved docs
    }

    final Set<Document> documents = getUnsavedOrTransactedDocuments();
    if (!documents.isEmpty()) {
      // now index unsaved data
      final FileBasedIndexIndicesManager.StorageGuard.Holder guard = myIndexIndicesManager.setDataBufferingEnabled(true);
      try {
        final Semaphore semaphore = myIndexIndicesManager.getUnsavedDataIndexingSemaphore(indexId);

        assert semaphore != null : "Semaphore for unsaved data indexing was not initialized for index " + indexId;

        semaphore.down();
        boolean allDocsProcessed = true;
        try {
          for (Document document : documents) {
            allDocsProcessed &= indexUnsavedDocument(document, indexId, project, filter, restrictedFile);
          }
        }
        finally {
          semaphore.up();

          while (!semaphore.waitFor(500)) { // may need to wait until another thread is done with indexing
            if (Thread.holdsLock(PsiLock.LOCK)) {
              break; // hack. Most probably that other indexing threads is waiting for PsiLock, which we're are holding.
            }
          }
          if (allDocsProcessed && !hasActiveTransactions()) {
            myIndexIndicesManager.addUpToDate(indexId); // safe to set the flag here, because it will be cleared under the WriteAction
          }
        }
      }
      finally {
        guard.leave();
      }
    }
  }


  // returns false if doc was not indexed because the file does not fit in scope
  private boolean indexUnsavedDocument(@NotNull final Document document, @NotNull final ID<?, ?> requestedIndexId, final Project project,
                                       GlobalSearchScope filter, VirtualFile restrictedFile) throws StorageException {
    final VirtualFile vFile = myFileDocumentManager.getFile(document);
    if (!(vFile instanceof VirtualFileWithId) || !vFile.isValid()) {
      return true;
    }

    if (restrictedFile != null) {
      if(vFile != restrictedFile) {
        return false;
      }
    }
    else if (filter != null && !filter.accept(vFile)) {
      return false;
    }

    final PsiFile dominantContentFile = findDominantPsiForDocument(document, project);

    final DocumentContent content;
    if (dominantContentFile != null && dominantContentFile.getModificationStamp() != document.getModificationStamp()) {
      content = new PsiContent(document, dominantContentFile);
    }
    else {
      content = new AuthenticContent(document);
    }

    final long currentDocStamp = content.getModificationStamp();
    if (currentDocStamp != myIndexIndicesManager.getAndSetLastIndexedDocStamps(document, requestedIndexId, currentDocStamp)) {
      final Ref<StorageException> exRef = new Ref<StorageException>(null);
      ProgressManager.getInstance().executeNonCancelableSection(new Runnable() {
        @Override
        public void run() {
          try {
            final String contentText = content.getText();
            if (myLimitsChecker.isTooLarge(vFile, contentText.length())) {
              return;
            }

            final FileContentImpl newFc = new FileContentImpl(vFile, contentText, vFile.getCharset());

            if (dominantContentFile != null) {
              dominantContentFile.putUserData(PsiFileImpl.BUILDING_STUB, true);
              newFc.putUserData(IndexingDataKeys.PSI_FILE, dominantContentFile);
            }

            if (content instanceof AuthenticContent) {
              newFc.putUserData(EDITOR_HIGHLIGHTER, EditorHighlighterCache.getEditorHighlighterForCachesBuilding(document));
            }

            if (myIndexIndicesManager.getInputFilter(requestedIndexId).acceptInput(vFile)) {
              newFc.putUserData(IndexingDataKeys.PROJECT, project);
              final int inputId = Math.abs(getFileId(vFile));
              myIndexIndicesManager.getIndex(requestedIndexId).update(inputId, newFc);
            }

            if (dominantContentFile != null) {
              dominantContentFile.putUserData(PsiFileImpl.BUILDING_STUB, null);
            }
          }
          catch (StorageException e) {
            exRef.set(e);
          }
        }
      });
      final StorageException storageException = exRef.get();
      if (storageException != null) {
        throw storageException;
      }
    }
    return true;
  }

  @Override
  public void initComponent() {
  }

  @Override
  public void disposeComponent() {
  }

  @NotNull
  @Override
  public String getComponentName() {
    return "FileBasedIndexUnsavedDocumentsManager";
  }

  public static int getFileId(final VirtualFile file) {
    if (file instanceof VirtualFileWithId) {
      return ((VirtualFileWithId)file).getId();
    }

    throw new IllegalArgumentException("Virtual file doesn't support id: " + file + ", implementation class: " + file.getClass().getName());
  }

  public static final Key<EditorHighlighter> EDITOR_HIGHLIGHTER = new Key<EditorHighlighter>("Editor");


  @Nullable
  private static PsiFile findLatestKnownPsiForUncomittedDocument(@NotNull Document doc, @NotNull Project project) {
    return PsiDocumentManager.getInstance(project).getCachedPsiFile(doc);
  }


  private interface DocumentContent {
    String getText();
    long getModificationStamp();
  }

  private static class AuthenticContent implements DocumentContent {
    private final Document myDocument;

    private AuthenticContent(final Document document) {
      myDocument = document;
    }

    @Override
    public String getText() {
      return myDocument.getText();
    }

    @Override
    public long getModificationStamp() {
      return myDocument.getModificationStamp();
    }
  }

  private static class PsiContent implements DocumentContent {
    private final Document myDocument;
    private final PsiFile myFile;

    private PsiContent(final Document document, final PsiFile file) {
      myDocument = document;
      myFile = file;
    }

    @Override
    public String getText() {
      if (myFile.getModificationStamp() != myDocument.getModificationStamp()) {
        final ASTNode node = myFile.getNode();
        assert node != null;
        return node.getText();
      }
      return myDocument.getText();
    }

    @Override
    public long getModificationStamp() {
      return myFile.getModificationStamp();
    }
  }

  @Nullable
  private PsiFile findDominantPsiForDocument(@NotNull Document document, @Nullable Project project) {
    synchronized (myTransactionMap) {
      PsiFile psiFile = myTransactionMap.get(document);
      if (psiFile != null) return psiFile;
    }

    return project == null ? null : findLatestKnownPsiForUncomittedDocument(document, project);
  }

  private Set<Document> getUnsavedOrTransactedDocuments() {
    final Set<Document> docs = new HashSet<Document>(Arrays.asList(myFileDocumentManager.getUnsavedDocuments()));
    synchronized (myTransactionMap) {
      docs.addAll(myTransactionMap.keySet());
    }
    return docs;
  }

  private boolean hasActiveTransactions() {
    synchronized (myTransactionMap) {
      return !myTransactionMap.isEmpty();
    }
  }
}
