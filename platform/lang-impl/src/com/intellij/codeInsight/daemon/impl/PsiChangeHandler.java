/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.ChangeLocalityDetector;
import com.intellij.codeInspection.SuppressionUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.EditorMarkupModel;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiDocumentManagerImpl;
import com.intellij.psi.impl.PsiDocumentTransactionListener;
import com.intellij.util.SmartList;
import com.intellij.util.messages.MessageBusConnection;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public class PsiChangeHandler extends PsiTreeChangeAdapter implements Disposable {
  private static final ExtensionPointName<ChangeLocalityDetector> EP_NAME = ExtensionPointName.create("com.intellij.daemon.changeLocalityDetector");
  private /*NOT STATIC!!!*/ final Key<Boolean> UPDATE_ON_COMMIT_ENGAGED = Key.create("UPDATE_ON_COMMIT_ENGAGED");

  private final Project myProject;
  private final DaemonCodeAnalyzerImpl myDaemonCodeAnalyzer;
  private final Map<Document, List<Pair<PsiElement, Boolean>>> changedElements = new THashMap<Document, List<Pair<PsiElement, Boolean>>>();
  private final FileStatusMap myFileStatusMap;

  public PsiChangeHandler(Project project, DaemonCodeAnalyzerImpl daemonCodeAnalyzer, final PsiDocumentManagerImpl documentManager, EditorFactory editorFactory, MessageBusConnection connection) {
    myProject = project;
    myDaemonCodeAnalyzer = daemonCodeAnalyzer;
    myFileStatusMap = daemonCodeAnalyzer.getFileStatusMap();
    editorFactory.getEventMulticaster().addDocumentListener(new DocumentAdapter() {
      @Override
      public void beforeDocumentChange(DocumentEvent e) {
        final Document document = e.getDocument();
        if (documentManager.getSynchronizer().isInSynchronization(document)) return;
        if (documentManager.getCachedPsiFile(document) == null) return;
        if (document.getUserData(UPDATE_ON_COMMIT_ENGAGED) == null) {
          document.putUserData(UPDATE_ON_COMMIT_ENGAGED, Boolean.TRUE);
          documentManager.addRunOnCommit(document, new Runnable() {
            public void run() {
              updateChangesForDocument(document);
              document.putUserData(UPDATE_ON_COMMIT_ENGAGED, null);
            }
          });
        }
      }
    }, this);

    connection.subscribe(PsiDocumentTransactionListener.TOPIC, new PsiDocumentTransactionListener() {
      public void transactionStarted(final Document doc, final PsiFile file) {
      }

      public void transactionCompleted(final Document doc, final PsiFile file) {
        updateChangesForDocument(doc);
      }
    });
  }

  public void dispose() {
  }

  private void updateChangesForDocument(@NotNull Document document) {
    if (DaemonListeners.isUnderIgnoredAction(null)) return;
    List<Pair<PsiElement, Boolean>> toUpdate = changedElements.get(document);
    if (toUpdate != null) {
      for (Pair<PsiElement, Boolean> changedElement : toUpdate) {
        PsiElement element = changedElement.getFirst();
        Boolean whiteSpaceOptimizationAllowed = changedElement.getSecond();
        updateByChange(element, whiteSpaceOptimizationAllowed);
      }
      changedElements.remove(document);
    }
  }

  public void childAdded(PsiTreeChangeEvent event) {
    queueElement(event.getParent(), true, event);
  }

  public void childRemoved(PsiTreeChangeEvent event) {
    queueElement(event.getParent(), true, event);
  }

  public void childReplaced(PsiTreeChangeEvent event) {
    queueElement(event.getNewChild(), typesEqual(event.getNewChild(), event.getOldChild()), event);
  }

  private static boolean typesEqual(final PsiElement newChild, final PsiElement oldChild) {
    return newChild != null && oldChild != null && newChild.getClass() == oldChild.getClass();
  }

  public void childrenChanged(PsiTreeChangeEvent event) {
    queueElement(event.getParent(), true, event);
  }

  public void beforeChildMovement(PsiTreeChangeEvent event) {
    queueElement(event.getOldParent(), true, event);
    queueElement(event.getNewParent(), true, event);
  }

  public void beforeChildrenChange(PsiTreeChangeEvent event) {
    // this event sent always before every PSI change, even not significant one (like after quick typing/backspacing char)
    // mark file dirty just in case
    PsiFile psiFile = event.getFile();
    if (psiFile != null) {
      myFileStatusMap.markFileScopeDirtyDefensively(psiFile);
    }
  }

  public void propertyChanged(PsiTreeChangeEvent event) {
    String propertyName = event.getPropertyName();
    if (!propertyName.equals(PsiTreeChangeEvent.PROP_WRITABLE)) {
      myFileStatusMap.markAllFilesDirty();
      myDaemonCodeAnalyzer.stopProcess(true);
    }
  }

  private void queueElement(PsiElement child, final boolean whitespaceOptimizationAllowed, PsiTreeChangeEvent event) {
    PsiFile file = event.getFile();
    if (file == null) file = child.getContainingFile();
    if (file == null) {
      myFileStatusMap.markAllFilesDirty();
      return;
    }

    if (!child.isValid()) return;
    Document document = PsiDocumentManager.getInstance(myProject).getCachedDocument(file);
    if (document != null) {
      List<Pair<PsiElement, Boolean>> toUpdate = changedElements.get(document);
      if (toUpdate == null) {
        toUpdate = new SmartList<Pair<PsiElement, Boolean>>();
        changedElements.put(document, toUpdate);
      }
      toUpdate.add(Pair.create(child, whitespaceOptimizationAllowed));
    }
  }

  private void updateByChange(PsiElement child, final boolean whitespaceOptimizationAllowed) {
    final Editor editor = FileEditorManager.getInstance(myProject).getSelectedTextEditor();
    Application application = ApplicationManager.getApplication();
    if (editor != null && !application.isUnitTestMode()) {
      application.invokeLater(new Runnable() {
        public void run() {
          if (myProject.isDisposed()) return;
          EditorMarkupModel markupModel = (EditorMarkupModel)editor.getMarkupModel();
          markupModel.setErrorStripeRenderer(markupModel.getErrorStripeRenderer());
        }
      }, ModalityState.stateForComponent(editor.getComponent()));
    }

    PsiFile file = child.getContainingFile();
    if (file == null || file instanceof PsiCompiledElement) {
      myFileStatusMap.markAllFilesDirty();
      return;
    }

    Document document = PsiDocumentManager.getInstance(myProject).getCachedDocument(file);
    if (document == null) return;

    int fileLength = file.getTextLength();
    if (!file.getViewProvider().isPhysical()) {
      myFileStatusMap.markFileScopeDirty(document, new TextRange(0, fileLength), fileLength);
      return;
    }

    // optimization
    if (whitespaceOptimizationAllowed && UpdateHighlightersUtil.isWhitespaceOptimizationAllowed(document)) {
      if (child instanceof PsiWhiteSpace ||
          child instanceof PsiComment && !child.getText().contains(SuppressionUtil.SUPPRESS_INSPECTIONS_TAG_NAME)) {
        myFileStatusMap.markFileScopeDirty(document, child.getTextRange(), fileLength);
        return;
      }
    }

    PsiElement element = child;
    while (true) {
      if (element instanceof PsiFile || element instanceof PsiDirectory) {
        myFileStatusMap.markAllFilesDirty();
        return;
      }

      final PsiElement scope = getChangeHighlightingScope(element);
      if (scope != null) {
        myFileStatusMap.markFileScopeDirty(document, scope.getTextRange(), fileLength);
        return;
      }

      element = element.getParent();
    }
  }

  @Nullable
  private static PsiElement getChangeHighlightingScope(PsiElement element) {
    for (ChangeLocalityDetector detector : Extensions.getExtensions(EP_NAME)) {
      final PsiElement scope = detector.getChangeHighlightingDirtyScopeFor(element);
      if (scope != null) return scope;
    }
    return null;
  }
}
