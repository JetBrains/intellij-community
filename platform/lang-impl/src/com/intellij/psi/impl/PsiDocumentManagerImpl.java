/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

package com.intellij.psi.impl;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.AppTopics;
import com.intellij.injected.editor.DocumentWindow;
import com.intellij.lang.ASTNode;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.components.SettingsSavingComponent;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.DocumentBulkUpdateListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileDocumentManagerAdapter;
import com.intellij.openapi.fileEditor.impl.FileDocumentManagerImpl;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectLocator;
import com.intellij.openapi.project.impl.ProjectImpl;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.core.impl.PomModelImpl;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageManagerImpl;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.FileContentUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;

//todo listen & notifyListeners readonly events?
public class PsiDocumentManagerImpl extends PsiDocumentManagerBase implements SettingsSavingComponent {
  private final DocumentCommitProcessor myDocumentCommitThread;
  private final boolean myUnitTestMode = ApplicationManager.getApplication().isUnitTestMode();

  public PsiDocumentManagerImpl(@NotNull final Project project,
                                @NotNull PsiManager psiManager,
                                @NotNull EditorFactory editorFactory,
                                @NotNull MessageBus bus,
                                @NonNls @NotNull final DocumentCommitProcessor documentCommitThread) {
    super(project, psiManager, bus, documentCommitThread);
    myDocumentCommitThread = documentCommitThread;
    editorFactory.getEventMulticaster().addDocumentListener(this, project);
    MessageBusConnection connection = bus.connect();
    connection.subscribe(AppTopics.FILE_DOCUMENT_SYNC, new FileDocumentManagerAdapter() {
      @Override
      public void fileContentLoaded(@NotNull final VirtualFile virtualFile, @NotNull Document document) {
        PsiFile psiFile = ReadAction.compute(() -> myProject.isDisposed() || !virtualFile.isValid() ? null : getCachedPsiFile(virtualFile));
        fireDocumentCreated(document, psiFile);
      }
    });
    connection.subscribe(DocumentBulkUpdateListener.TOPIC, new DocumentBulkUpdateListener.Adapter() {
      @Override
      public void updateFinished(@NotNull Document doc) {
        documentCommitThread.commitAsynchronously(project, doc, "Bulk update finished", TransactionGuard.getInstance().getContextTransaction());
      }
    });
    Disposer.register(project, () -> ((DocumentCommitThread)myDocumentCommitThread).cancelTasksOnProjectDispose(project));
  }

  @Nullable
  @Override
  public PsiFile getPsiFile(@NotNull Document document) {
    final PsiFile psiFile = super.getPsiFile(document);
    if (myUnitTestMode) {
      final VirtualFile virtualFile = FileDocumentManager.getInstance().getFile(document);
      if (virtualFile != null && virtualFile.isValid()) {
        Collection<Project> projects = ProjectLocator.getInstance().getProjectsForFile(virtualFile);
        if (!projects.isEmpty() && !projects.contains(myProject)) {
          LOG.error("Trying to get PSI for an alien project. VirtualFile=" + virtualFile +
                    ";\n myProject=" + myProject +
                    ";\n projects returned: " + projects);
        }
      }
    }
    return psiFile;
  }

  @Override
  public void documentChanged(DocumentEvent event) {
    super.documentChanged(event);
    // optimisation: avoid documents piling up during batch processing
    if (isUncommited(event.getDocument()) && FileDocumentManagerImpl.areTooManyDocumentsInTheQueue(myUncommittedDocuments)) {
      if (myUnitTestMode) {
        myStopTrackingDocuments = true;
        try {
          LOG.error("Too many uncommitted documents for " + myProject + "(" +myUncommittedDocuments.size()+")"+
                    ":\n" + StringUtil.join(myUncommittedDocuments, "\n") +
                    "\n\n Project creation trace: " + myProject.getUserData(ProjectImpl.CREATION_TRACE));
        }
        finally {
          //noinspection TestOnlyProblems
          clearUncommittedDocuments();
        }
      }
      // must not commit during document save
      if (PomModelImpl.isAllowPsiModification()
          // it can happen that document(forUseInNonAWTThread=true) outside write action caused this
          && ApplicationManager.getApplication().isWriteAccessAllowed()) {
        // commit one document to avoid OOME
        for (Document document : myUncommittedDocuments) {
          if (document != event.getDocument()) {
            doCommitWithoutReparse(document);
            break;
          }
        }
      }
    }
  }

  @VisibleForTesting
  public void doCommitWithoutReparse(@NotNull Document document) {
    finishCommitInWriteAction(document, Collections.emptyList(), Collections.emptyList(), true, true);
  }

  @Override
  protected void beforeDocumentChangeOnUnlockedDocument(@NotNull final FileViewProvider viewProvider) {
    PostprocessReformattingAspect.getInstance(myProject).assertDocumentChangeIsAllowed(viewProvider);
    super.beforeDocumentChangeOnUnlockedDocument(viewProvider);
  }


  @Override
  protected boolean finishCommitInWriteAction(@NotNull Document document,
                                              @NotNull List<BooleanRunnable> finishProcessors,
                                              List<BooleanRunnable> reparseInjectedProcessors,
                                              boolean synchronously,
                                              boolean forceNoPsiCommit) {
    if (ApplicationManager.getApplication().isWriteAccessAllowed()) { // can be false for non-physical PSI
      InjectedLanguageManagerImpl.disposeInvalidEditors();
    }
    return super.finishCommitInWriteAction(document, finishProcessors, reparseInjectedProcessors, synchronously, forceNoPsiCommit);
  }

  @Override
  public boolean isDocumentBlockedByPsi(@NotNull Document doc) {
    final FileViewProvider viewProvider = getCachedViewProvider(doc);
    return viewProvider != null && PostprocessReformattingAspect.getInstance(myProject).isViewProviderLocked(viewProvider);
  }

  @Override
  public void doPostponedOperationsAndUnblockDocument(@NotNull Document doc) {
    if (doc instanceof DocumentWindow) doc = ((DocumentWindow)doc).getDelegate();
    final PostprocessReformattingAspect component = myProject.getComponent(PostprocessReformattingAspect.class);
    final FileViewProvider viewProvider = getCachedViewProvider(doc);
    if (viewProvider != null && component != null) component.doPostponedFormatting(viewProvider);
  }

  @Override
  public void save() {
    // Ensure all documents are committed on save so file content dependent indices, that use PSI to build have consistent content.
    try {
      commitAllDocuments();
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }

  @Override
  @TestOnly
  public void clearUncommittedDocuments() {
    super.clearUncommittedDocuments();
    ((DocumentCommitThread)myDocumentCommitThread).clearQueue();
  }

  @NotNull
  @Override
  List<BooleanRunnable> reparseChangedInjectedFragments(@NotNull Document hostDocument,
                                                        @NotNull PsiFile hostPsiFile,
                                                        @NotNull TextRange hostChangedRange,
                                                        @NotNull ProgressIndicator indicator,
                                                        @NotNull ASTNode oldRoot,
                                                        @NotNull ASTNode newRoot) {
    List<DocumentWindow> changedInjected = InjectedLanguageManager.getInstance(myProject).getCachedInjectedDocumentsInRange(hostPsiFile, hostChangedRange);
    if (changedInjected.isEmpty()) return Collections.emptyList();
    FileViewProvider hostViewProvider = hostPsiFile.getViewProvider();
    List<DocumentWindow> fromLast = new ArrayList<>(changedInjected);
    // make sure modifications do not ruin all document offsets after
    fromLast.sort(Collections.reverseOrder(Comparator.comparingInt(doc -> ArrayUtil.getLastElement(doc.getHostRanges()).getEndOffset())));
    List<BooleanRunnable> result = new ArrayList<>(changedInjected.size());
    for (DocumentWindow document : fromLast) {
      Segment[] ranges = document.getHostRanges();
      if (ranges.length != 0) {
        // host document change has left something valid in this document window place. Try to reparse.
        PsiFile injectedPsiFile = getCachedPsiFile(document);
        if (injectedPsiFile  == null || !injectedPsiFile.isValid()) continue;

        BooleanRunnable runnable = InjectedLanguageUtil.reparse(injectedPsiFile, document, hostPsiFile, hostViewProvider, indicator, oldRoot, newRoot);
        ContainerUtil.addIfNotNull(result, runnable);
      }
    }

    return result;
  }

  @NonNls
  @Override
  public String toString() {
    return super.toString() + " for the project " + myProject + ".";
  }

  @Override
  public void reparseFiles(@NotNull Collection<VirtualFile> files, boolean includeOpenFiles) {
    FileContentUtil.reparseFiles(myProject, files, includeOpenFiles);
  }

  @NotNull
  @Override
  protected DocumentWindow freezeWindow(@NotNull DocumentWindow document) {
    return InjectedLanguageManager.getInstance(myProject).freezeWindow(document);
  }
}
