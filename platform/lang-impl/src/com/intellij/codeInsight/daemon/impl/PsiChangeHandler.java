// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.ChangeLocalityDetector;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ProjectDisposeAwareDocumentListener;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.EditorMarkupModel;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiDocumentManagerImpl;
import com.intellij.psi.impl.PsiDocumentTransactionListener;
import com.intellij.psi.impl.PsiTreeChangeEventImpl;
import com.intellij.util.SmartList;
import com.intellij.util.messages.SimpleMessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

final class PsiChangeHandler extends PsiTreeChangeAdapter {
  private static final ExtensionPointName<ChangeLocalityDetector> EP_NAME = new ExtensionPointName<>("com.intellij.daemon.changeLocalityDetector");
  private /*NOT STATIC!!!*/ final Key<Boolean> UPDATE_ON_COMMIT_ENGAGED = Key.create("UPDATE_ON_COMMIT_ENGAGED");

  private final Project myProject;
  private final Map<Document, List<Pair<PsiElement, Boolean>>> changedElements = new WeakHashMap<>();
  private final FileStatusMap myFileStatusMap;

  PsiChangeHandler(@NotNull Project project, @NotNull SimpleMessageBusConnection connection, @NotNull Disposable parentDisposable) {
    myProject = project;
    myFileStatusMap = DaemonCodeAnalyzerEx.getInstanceEx(myProject).getFileStatusMap();
    EditorFactory.getInstance().getEventMulticaster().addDocumentListener(ProjectDisposeAwareDocumentListener.create(project, new DocumentListener() {
      @Override
      public void beforeDocumentChange(@NotNull DocumentEvent event) {
        Document document = event.getDocument();
        PsiDocumentManagerImpl documentManager = (PsiDocumentManagerImpl)PsiDocumentManager.getInstance(myProject);
        if (documentManager.getSynchronizer().isInSynchronization(document)) {
          return;
        }

        PsiFile psi = documentManager.getCachedPsiFile(document);
        if (psi == null || !psi.getViewProvider().isEventSystemEnabled()) {
          return;
        }

        if (document.getUserData(UPDATE_ON_COMMIT_ENGAGED) == null) {
          document.putUserData(UPDATE_ON_COMMIT_ENGAGED, Boolean.TRUE);
          documentManager.addRunOnCommit(document, () -> {
            if (document.getUserData(UPDATE_ON_COMMIT_ENGAGED) != null) {
              updateChangesForDocument(document);
              document.putUserData(UPDATE_ON_COMMIT_ENGAGED, null);
            }
          });
        }
      }
    }), parentDisposable);

    connection.subscribe(PsiDocumentTransactionListener.TOPIC, new PsiDocumentTransactionListener() {
      @Override
      public void transactionStarted(@NotNull Document doc, @NotNull PsiFile file) {
      }

      @Override
      public void transactionCompleted(@NotNull Document document, @NotNull PsiFile file) {
        updateChangesForDocument(document);
        document.putUserData(UPDATE_ON_COMMIT_ENGAGED, null); // ensure we don't call updateChangesForDocument() twice which can lead to whole file re-highlight
      }
    });
  }

  private void updateChangesForDocument(@NotNull Document document) {
    ApplicationManager.getApplication().assertIsWriteThread();
    if (myProject.isDisposed()) return;
    List<Pair<PsiElement, Boolean>> toUpdate = changedElements.get(document);
    if (toUpdate == null) {
      // The document has been changed, but psi hasn't
      // We may still need to rehighlight the file if there were changes inside highlighted ranges.
      if (UpdateHighlightersUtil.isWhitespaceOptimizationAllowed(document)) return;

      // don't create PSI for files in other projects
      PsiElement file = PsiDocumentManager.getInstance(myProject).getCachedPsiFile(document);
      if (file == null) return;

      toUpdate = Collections.singletonList(Pair.create(file, true));
    }
    Application application = ApplicationManager.getApplication();
    Editor editor = FileEditorManager.getInstance(myProject).getSelectedTextEditor();
    if (editor != null && !application.isUnitTestMode()) {
      application.invokeLater(() -> {
        if (!editor.isDisposed()) {
          EditorMarkupModel markupModel = (EditorMarkupModel)editor.getMarkupModel();
          PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(editor.getDocument());
          ErrorStripeUpdateManager.getInstance(myProject).setOrRefreshErrorStripeRenderer(markupModel, file);
        }
      }, ModalityState.stateForComponent(editor.getComponent()), myProject.getDisposed());
    }

    for (Pair<PsiElement, Boolean> changedElement : toUpdate) {
      PsiElement element = changedElement.getFirst();
      Boolean whiteSpaceOptimizationAllowed = changedElement.getSecond();
      updateByChange(element, document, whiteSpaceOptimizationAllowed);
    }
    changedElements.remove(document);
  }

  @Override
  public void childAdded(@NotNull PsiTreeChangeEvent event) {
    queueElement(event.getParent(), true, event);
  }

  @Override
  public void childRemoved(@NotNull PsiTreeChangeEvent event) {
    queueElement(event.getParent(), true, event);
  }

  @Override
  public void childReplaced(@NotNull PsiTreeChangeEvent event) {
    queueElement(event.getNewChild(), typesEqual(event.getNewChild(), event.getOldChild()), event);
  }

  private static boolean typesEqual(PsiElement newChild, PsiElement oldChild) {
    return newChild != null && oldChild != null && newChild.getClass() == oldChild.getClass();
  }

  @Override
  public void childrenChanged(@NotNull PsiTreeChangeEvent event) {
    if (((PsiTreeChangeEventImpl)event).isGenericChange()) {
      return;
    }
    queueElement(event.getParent(), true, event);
  }

  @Override
  public void beforeChildMovement(@NotNull PsiTreeChangeEvent event) {
    queueElement(event.getOldParent(), true, event);
    queueElement(event.getNewParent(), true, event);
  }

  @Override
  public void beforeChildrenChange(@NotNull PsiTreeChangeEvent event) {
    // this event sent always before every PSI change, even not significant one (like after quick typing/backspacing char)
    // mark file dirty just in case
    PsiFile psiFile = event.getFile();
    if (psiFile != null) {
      myFileStatusMap.markFileScopeDirtyDefensively(psiFile, event);
    }
  }

  @Override
  public void propertyChanged(@NotNull PsiTreeChangeEvent event) {
    String propertyName = event.getPropertyName();
    if (!propertyName.equals(PsiTreeChangeEvent.PROP_WRITABLE)) {
      Object oldValue = event.getOldValue();
      if (oldValue instanceof VirtualFile && shouldBeIgnored((VirtualFile)oldValue)) {
        // ignore workspace.xml
        return;
      }
      myFileStatusMap.markAllFilesDirty(event);
    }
  }

  private void queueElement(@NotNull PsiElement child, boolean whitespaceOptimizationAllowed, @NotNull PsiTreeChangeEvent event) {
    ApplicationManager.getApplication().assertIsWriteThread();
    PsiFile file = event.getFile();
    if (file == null) file = child.getContainingFile();
    if (file == null) {
      myFileStatusMap.markAllFilesDirty(child);
      return;
    }

    if (!child.isValid()) return;

    PsiDocumentManagerImpl pdm = (PsiDocumentManagerImpl)PsiDocumentManager.getInstance(myProject);
    Document document = pdm.getCachedDocument(file);
    if (document != null) {
      if (pdm.getSynchronizer().getTransaction(document) == null) {
        // content reload, language level change or some other big change
        myFileStatusMap.markAllFilesDirty(child);
        return;
      }

      List<Pair<PsiElement, Boolean>> toUpdate = changedElements.get(document);
      if (toUpdate == null) {
        toUpdate = new SmartList<>();
        changedElements.put(document, toUpdate);
      }
      toUpdate.add(Pair.create(child, whitespaceOptimizationAllowed));
    }
  }

  private void updateByChange(@NotNull PsiElement child, @NotNull Document document, boolean whitespaceOptimizationAllowed) {
    ApplicationManager.getApplication().assertIsWriteThread();
    PsiFile file;
    try {
      file = child.getContainingFile();
    }
    catch (PsiInvalidElementAccessException e) {
      myFileStatusMap.markAllFilesDirty(e);
      return;
    }
    if (file == null || file instanceof PsiCompiledElement) {
      myFileStatusMap.markAllFilesDirty(child);
      return;
    }
    VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile != null && shouldBeIgnored(virtualFile)) {
      // ignore workspace.xml
      return;
    }

    int fileLength = file.getTextLength();
    if (!file.getViewProvider().isPhysical()) {
      myFileStatusMap.markFileScopeDirty(document, new TextRange(0, fileLength), fileLength, "Non-physical file update: "+file);
      return;
    }

    TextRange existingDirtyScope = myFileStatusMap.getFileDirtyScopeForAllPassesCombined(document);
    PsiElement element = whitespaceOptimizationAllowed && UpdateHighlightersUtil.isWhitespaceOptimizationAllowed(document) ? child : child.getParent();
    while (true) {
      if (element == null || element instanceof PsiFile || element instanceof PsiDirectory) {
        myFileStatusMap.markAllFilesDirty("Top element: "+element);
        return;
      }

      PsiElement scope = getChangeHighlightingScope(element);
      if (scope != null) {
        TextRange scopeRange = scope.getTextRange();
        // if some unrelated scope already marked dirty, we shouldn't just add another scope and return,
        // because between these two dirty whitespaces might easily be some other non-whitespace PSI,
        // and this PSI element is not expected to be highlighted alone, which could lead to unexpected highlighter disappearances
        // see DaemonRespondToChangesTest.testPutArgumentsOnSeparateLinesIntentionMustNotRemoveErrorHighlighting
        if (existingDirtyScope == null || scopeRange.contains(existingDirtyScope)) {
          myFileStatusMap.markFileScopeDirty(document, scopeRange, fileLength, "Scope: " + scope);
          return;
        }
        existingDirtyScope = existingDirtyScope.union(scopeRange);
      }

      element = element.getParent();
    }
  }

  private boolean shouldBeIgnored(@NotNull VirtualFile virtualFile) {
    return ProjectUtil.isProjectOrWorkspaceFile(virtualFile) ||
           ProjectRootManager.getInstance(myProject).getFileIndex().isExcluded(virtualFile);
  }

  private static @Nullable PsiElement getChangeHighlightingScope(@NotNull PsiElement element) {
    DefaultChangeLocalityDetector defaultDetector = null;
    for (ChangeLocalityDetector detector : EP_NAME.getExtensionList()) {
      if (detector instanceof DefaultChangeLocalityDetector) {
        // run default detector last
        assert defaultDetector == null : defaultDetector;
        defaultDetector = (DefaultChangeLocalityDetector)detector;
        continue;
      }
      PsiElement scope = detector.getChangeHighlightingDirtyScopeFor(element);
      if (scope != null) return scope;
    }
    assert defaultDetector != null : "com.intellij.codeInsight.daemon.impl.DefaultChangeLocalityDetector is unregistered";
    return defaultDetector.getChangeHighlightingDirtyScopeFor(element);
  }
}
