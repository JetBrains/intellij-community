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

import com.intellij.AppTopics;
import com.intellij.injected.editor.DocumentWindow;
import com.intellij.injected.editor.DocumentWindowImpl;
import com.intellij.injected.editor.EditorWindowImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.SettingsSavingComponent;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.DocumentBulkUpdateListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileDocumentManagerAdapter;
import com.intellij.openapi.fileEditor.impl.FileDocumentManagerImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectLocator;
import com.intellij.openapi.project.impl.ProjectImpl;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.core.impl.PomModelImpl;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.impl.source.tree.injected.MultiHostRegistrarImpl;
import com.intellij.util.FileContentUtil;
import com.intellij.util.Processor;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.Collection;
import java.util.List;

//todo listen & notifyListeners readonly events?
public class PsiDocumentManagerImpl extends PsiDocumentManagerBase implements SettingsSavingComponent {
  private final DocumentCommitThread myDocumentCommitThread;
  private final boolean myUnitTestMode = ApplicationManager.getApplication().isUnitTestMode();

  public PsiDocumentManagerImpl(@NotNull final Project project,
                                @NotNull PsiManager psiManager,
                                @NotNull EditorFactory editorFactory,
                                @NotNull MessageBus bus,
                                @NonNls @NotNull final DocumentCommitThread documentCommitThread) {
    super(project, psiManager, bus, documentCommitThread);
    myDocumentCommitThread = documentCommitThread;
    editorFactory.getEventMulticaster().addDocumentListener(this, project);
    MessageBusConnection busConnection = bus.connect();
    busConnection.subscribe(AppTopics.FILE_DOCUMENT_SYNC, new FileDocumentManagerAdapter() {
      @Override
      public void fileContentLoaded(@NotNull final VirtualFile virtualFile, @NotNull Document document) {
        PsiFile psiFile = ApplicationManager.getApplication().runReadAction(new Computable<PsiFile>() {
          @Override
          public PsiFile compute() {
            return myProject.isDisposed() || !virtualFile.isValid() ? null : getCachedPsiFile(virtualFile);
          }
        });
        fireDocumentCreated(document, psiFile);
      }
    });
    busConnection.subscribe(DocumentBulkUpdateListener.TOPIC, new DocumentBulkUpdateListener.Adapter() {
      @Override
      public void updateFinished(@NotNull Document doc) {
        documentCommitThread.queueCommit(project, doc, "Bulk update finished", ApplicationManager.getApplication().getDefaultModalityState());
      }
    });
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
    if (FileDocumentManagerImpl.areTooManyDocumentsInTheQueue(myUncommittedDocuments)) {
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
      if (PomModelImpl.isAllowPsiModification()) {
        commitAllDocuments();
      }
    }
  }

  @Override
  protected void beforeDocumentChangeOnUnlockedDocument(@NotNull final FileViewProvider viewProvider) {
    PostprocessReformattingAspect.getInstance(myProject).beforeDocumentChanged(viewProvider);
    super.beforeDocumentChangeOnUnlockedDocument(viewProvider);
  }

  @Override
  protected boolean finishCommitInWriteAction(@NotNull Document document,
                                              @NotNull List<Processor<Document>> finishProcessors,
                                              boolean synchronously) {
    EditorWindowImpl.disposeInvalidEditors();  // in write action
    return super.finishCommitInWriteAction(document, finishProcessors, synchronously);
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
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        try {
          commitAllDocuments();
        }
        catch (Exception e) {
          LOG.error(e);
        }
      }
    });
  }

  @Override
  @TestOnly
  public void clearUncommittedDocuments() {
    super.clearUncommittedDocuments();
    myDocumentCommitThread.clearQueue();
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
    return MultiHostRegistrarImpl.freezeWindow((DocumentWindowImpl)document);
  }
}
