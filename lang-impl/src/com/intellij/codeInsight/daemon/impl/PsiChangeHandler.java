package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.ChangeLocalityDetector;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorMarkupModel;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiDocumentTransactionListener;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class PsiChangeHandler extends PsiTreeChangeAdapter {
  private static final ExtensionPointName<ChangeLocalityDetector> EP_NAME = ExtensionPointName.create("com.intellij.daemon.changeLocalityDetector");
  private final Project myProject;
  private final DaemonCodeAnalyzerImpl myDaemonCodeAnalyzer;
  private final List<Pair<PsiElement, Boolean>> changedElements = new ArrayList<Pair<PsiElement, Boolean>>();

  public PsiChangeHandler(Project project, DaemonCodeAnalyzerImpl daemonCodeAnalyzer, MessageBusConnection connection) {
    myProject = project;
    myDaemonCodeAnalyzer = daemonCodeAnalyzer;

    connection.subscribe(PsiDocumentTransactionListener.TOPIC, new PsiDocumentTransactionListener() {
      public void transactionStarted(final Document doc, final PsiFile file) {
      }

      public void transactionCompleted(final Document doc, final PsiFile file) {
        for (Pair<PsiElement, Boolean> changedElement : changedElements) {
          updateByChange(changedElement.getFirst(), changedElement.getSecond());
        }
        changedElements.clear();
      }
    });
  }

  public void childAdded(PsiTreeChangeEvent event) {
    queueElement(event.getParent(), true);
  }

  public void childRemoved(PsiTreeChangeEvent event) {
    queueElement(event.getParent(), true);
  }

  public void childReplaced(PsiTreeChangeEvent event) {
    queueElement(event.getNewChild(), typesEqual(event.getNewChild(), event.getOldChild()));
  }

  private static boolean typesEqual(final PsiElement newChild, final PsiElement oldChild) {
    return newChild != null && oldChild != null && newChild.getClass() == oldChild.getClass();
  }

  public void childrenChanged(PsiTreeChangeEvent event) {
    queueElement(event.getParent(), true);
  }

  public void beforeChildMovement(PsiTreeChangeEvent event) {
    queueElement(event.getOldParent(), true);
    queueElement(event.getNewParent(), true);
  }

  public void beforeChildrenChange(PsiTreeChangeEvent event) {
    // this event sent always before every PSI change, even not significant one (like after quick typing/backspacing char)
    // mark file dirty just in case
    PsiFile psiFile = event.getFile();
    if (psiFile != null) {
      myDaemonCodeAnalyzer.getFileStatusMap().markFileScopeDirtyDefensively(psiFile);
    }
  }

  public void propertyChanged(PsiTreeChangeEvent event) {
    String propertyName = event.getPropertyName();
    if (!propertyName.equals(PsiTreeChangeEvent.PROP_WRITABLE)) {
      myDaemonCodeAnalyzer.getFileStatusMap().markAllFilesDirty();
      myDaemonCodeAnalyzer.stopProcess(true);
    }
  }

  private void queueElement(PsiElement child, final boolean whitespaceOptimizationAllowed) {
    changedElements.add(Pair.create(child, whitespaceOptimizationAllowed));
  }

  private void updateByChange(PsiElement child, final boolean whitespaceOptimizationAllowed) {
    final Editor editor = FileEditorManager.getInstance(myProject).getSelectedTextEditor();
    if (editor != null) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          if (myProject.isDisposed()) return;
          EditorMarkupModel markupModel = (EditorMarkupModel)editor.getMarkupModel();
          markupModel.setErrorStripeRenderer(markupModel.getErrorStripeRenderer());
        }
      }, ModalityState.stateForComponent(editor.getComponent()));
    }

    final FileStatusMap fileStatusMap = myDaemonCodeAnalyzer.getFileStatusMap();

    PsiFile file = child.getContainingFile();
    if (file == null || file instanceof PsiCompiledElement) {
      fileStatusMap.markAllFilesDirty();
      return;
    }

    Document document = PsiDocumentManager.getInstance(myProject).getCachedDocument(file);
    if (document == null) return;

    int fileLength = file.getTextLength();
    if (!file.getViewProvider().isPhysical()) {
      fileStatusMap.markFileScopeDirty(document, new TextRange(0, fileLength), fileLength);
      return;
    }

    // optimization
    if (whitespaceOptimizationAllowed && UpdateHighlightersUtil.isWhitespaceOptimizationAllowed(document)) {
      if (child instanceof PsiWhiteSpace || child instanceof PsiComment) {
        fileStatusMap.markFileScopeDirty(document, child.getTextRange(), fileLength);
        return;
      }
    }

    PsiElement element = child;
    while (true) {
      if (element instanceof PsiFile || element instanceof PsiDirectory) {
        fileStatusMap.markAllFilesDirty();
        return;
      }

      final PsiElement scope = getChangeHighlightingScope(element);
      if (scope != null) {
        fileStatusMap.markFileScopeDirty(document, scope.getTextRange(), fileLength);
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