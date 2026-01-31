// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl;

import com.intellij.codeInsight.multiverse.CodeInsightContext;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.PsiFileImpl;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@ApiStatus.Internal
public abstract class PsiDocumentManagerEx extends PsiDocumentManager implements Disposable {
  public abstract @NotNull Project getProject();

  public abstract void associatePsi(@NotNull Document document, @NotNull PsiFile file);

  public abstract @Nullable PsiFile getRawCachedFile(@NotNull VirtualFile virtualFile, @NotNull CodeInsightContext context);

  public abstract @NotNull @Unmodifiable List<FileViewProvider> getCachedViewProviders(@NotNull Document document);

  public abstract boolean cancelAndRunWhenAllCommitted(@NonNls @NotNull Object key, @NotNull Runnable action);

  public abstract void addRunOnCommit(@NotNull Document document, @NotNull Consumer<? super @NotNull Document> action);

  public abstract boolean isEventSystemEnabled(@NotNull Document document);

  public abstract boolean finishCommit(@NotNull Document document,
                                       @NotNull @Unmodifiable List<? extends BooleanRunnable> finishProcessors,
                                       @NotNull @Unmodifiable List<? extends BooleanRunnable> reparseInjectedProcessors,
                                       boolean synchronously,
                                       @NotNull Object reason);

  public abstract void forceReload(@Nullable VirtualFile virtualFile,
                                   @NotNull @Unmodifiable List<? extends FileViewProvider> viewProviders);

  // true if the PSI is being modified and events being sent
  public abstract boolean isCommitInProgress();

  public abstract @NotNull DocumentEx getLastCommittedDocument(@NotNull Document document);

  public abstract @NotNull @Unmodifiable List<DocumentEvent> getEventsSinceCommit(@NotNull Document document);

  public abstract @NotNull Map<Document, Throwable> getUncommitedDocumentsWithTraces();

  public abstract boolean isInUncommittedSet(@NotNull Document document);

  public abstract void handleCommitWithoutPsi(@NotNull Document document);

  @TestOnly
  public abstract void clearUncommittedDocuments();

  @TestOnly
  public abstract void disableBackgroundCommit(@NotNull Disposable parentDisposable);

  public abstract @NotNull PsiToDocumentSynchronizer getSynchronizer();

  public abstract void reparseFileFromText(@NotNull PsiFileImpl file);

  @NotNull
  public abstract @Unmodifiable List<BooleanRunnable> reparseChangedInjectedFragments(@NotNull Document hostDocument,
                                                                                      @NotNull PsiFile hostPsiFile,
                                                                                      @NotNull TextRange range,
                                                                                      @NotNull ProgressIndicator indicator,
                                                                                      @NotNull ASTNode oldRoot,
                                                                                      @NotNull ASTNode newRoot);

  @TestOnly
  public abstract boolean isDefaultProject();

  public abstract @NonNls String someDocumentDebugInfo(@NotNull Document document);

  public abstract void assertFileIsFromCorrectProject(@NotNull VirtualFile virtualFile);
}
