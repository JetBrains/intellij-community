package com.intellij.codeInsight.daemon.impl;

import com.intellij.lang.ASTNode;
import com.intellij.lang.StdLanguages;
import com.intellij.lang.Language;
import com.intellij.lang.properties.parsing.PropertiesTokenTypes;
import com.intellij.lang.xml.XmlFileViewProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorMarkupModel;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocToken;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;

public class PsiChangeHandler extends PsiTreeChangeAdapter {
  private final Project myProject;
  private final DaemonCodeAnalyzerImpl myDaemonCodeAnalyzer;

  public PsiChangeHandler(Project project, DaemonCodeAnalyzerImpl daemonCodeAnalyzer) {
    myProject = project;
    myDaemonCodeAnalyzer = daemonCodeAnalyzer;
  }

  public void childAdded(PsiTreeChangeEvent event) {
    updateByChange(event.getParent(), true);
  }

  public void childRemoved(PsiTreeChangeEvent event) {
    updateByChange(event.getParent(), true);
  }

  public void childReplaced(PsiTreeChangeEvent event) {
    updateByChange(event.getNewChild(), typesEqual(event.getNewChild(), event.getOldChild()));
  }

  private static boolean typesEqual(final PsiElement newChild, final PsiElement oldChild) {
    return newChild != null && oldChild != null && newChild.getClass() == oldChild.getClass();
  }

  public void childrenChanged(PsiTreeChangeEvent event) {
    updateByChange(event.getParent(), true);
  }

  public void beforeChildMovement(PsiTreeChangeEvent event) {
    updateByChange(event.getOldParent(), true);
    updateByChange(event.getNewParent(), true);
  }

  public void beforeChildrenChange(PsiTreeChangeEvent event) {
    // this event sent always before every PSI change, even not significant one (like after quick typing/backspacing char)
    // mark file dirty just in case
    PsiFile psiFile = event.getFile();
    if (psiFile != null) {
      Document document = PsiDocumentManager.getInstance(myProject).getCachedDocument(psiFile);
      if (document != null) {
        myDaemonCodeAnalyzer.getFileStatusMap().markFileScopeDirtyDefensively(psiFile);
        myDaemonCodeAnalyzer.stopProcess(true);
      }
    }
  }

  public void propertyChanged(PsiTreeChangeEvent event) {
    String propertyName = event.getPropertyName();
    if (!propertyName.equals(PsiTreeChangeEvent.PROP_WRITABLE)) {
      myDaemonCodeAnalyzer.getFileStatusMap().markAllFilesDirty();
      myDaemonCodeAnalyzer.stopProcess(true);
    }
  }

  private void updateByChange(PsiElement child, final boolean whitespaceOptimizationAllowed) {
    final Editor editor = FileEditorManager.getInstance(myProject).getSelectedTextEditor();
    if (editor != null) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          EditorMarkupModel markupModel = (EditorMarkupModel)editor.getMarkupModel();
          markupModel.setErrorStripeRenderer(markupModel.getErrorStripeRenderer());
        }
      }, ModalityState.stateForComponent(editor.getComponent()));
    }

    PsiFile file = child.getContainingFile();
    if (file == null) {
      myDaemonCodeAnalyzer.getFileStatusMap().markAllFilesDirty();
      return;
    }
    Document document = PsiDocumentManager.getInstance(myProject).getCachedDocument(file);
    if (document == null) return;

    // optimization
    if (whitespaceOptimizationAllowed && UpdateHighlightersUtil.isWhitespaceOptimizationAllowed(document)) {
      final ASTNode node = child.getNode();
      if (child instanceof PsiWhiteSpace || child instanceof PsiComment || child instanceof PsiDocToken ||
          node != null && PropertiesTokenTypes.PROPERTIES_TYPES_TO_IGNORE.contains(node.getElementType())) {
        return;
      }
    }

    if (file instanceof XmlFile) {
      // TODO: Hackery. Need an API for language plugin developers to define dirty scope for their changes.
      final FileViewProvider provider = file.getViewProvider();
      if (provider instanceof XmlFileViewProvider && ((XmlFileViewProvider)provider).getLanguageExtensions().length > 0) {
        myDaemonCodeAnalyzer.getFileStatusMap().markAllFilesDirty();
        return;
      }
    }
    // change in e.g. sciptlet may lead to error in any other place
    Language language = file.getLanguage();
    if (language == StdLanguages.JSPX || language == StdLanguages.JSP) {
      myDaemonCodeAnalyzer.getFileStatusMap().markAllFilesDirty();
      return;
    }

    // optimization:
    PsiElement parent = child;
    while (true) {
      if (parent instanceof PsiFile || parent instanceof PsiDirectory) {
        myDaemonCodeAnalyzer.getFileStatusMap().markAllFilesDirty();
        return;
      }
      PsiElement pparent = parent.getParent();

      if (parent instanceof XmlTag) {
        PsiElement dirtyScope = pparent;

        if (pparent instanceof XmlTag && "head".equals(((XmlTag)pparent).getLocalName())) {
          final PsiFile containingFile = parent.getContainingFile();
          final FileType fileType = containingFile == null ? null : containingFile.getFileType();

          if (fileType == StdFileTypes.JSP || fileType == StdFileTypes.JSPX || fileType == StdFileTypes.HTML ||
              fileType == StdFileTypes.XHTML) {
            // change in head will result in changes for css/javascript code highlighting
            dirtyScope = containingFile;
          }
        }

        myDaemonCodeAnalyzer.getFileStatusMap().markFileScopeDirty(document, dirtyScope);
        return;
      }

      if (parent instanceof PsiCodeBlock && pparent instanceof PsiMethod && !((PsiMethod)pparent).isConstructor() &&
          pparent.getParent()instanceof PsiClass && !(pparent.getParent()instanceof PsiAnonymousClass)) {
        // do not use this optimization for constructors and class initializers - to update non-initialized fields
        myDaemonCodeAnalyzer.getFileStatusMap().markFileScopeDirty(document, pparent);
        return;
      }
      parent = pparent;
    }
  }
}