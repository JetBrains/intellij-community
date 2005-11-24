package com.intellij.codeInsight.daemon.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorMarkupModel;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocToken;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.lang.properties.parsing.PropertiesTokenTypes;
import gnu.trove.THashSet;

import java.util.Set;

public class PsiChangeHandler extends PsiTreeChangeAdapter {
  private final Project myProject;
  private final DaemonCodeAnalyzerImpl myDaemonCodeAnalyzer;

  public PsiChangeHandler(Project project, DaemonCodeAnalyzerImpl daemonCodeAnalyzer) {
    myProject = project;
    myDaemonCodeAnalyzer = daemonCodeAnalyzer;
  }

  public void childAdded(PsiTreeChangeEvent event) {
    updateByChange(event.getParent());
  }

  public void childRemoved(PsiTreeChangeEvent event) {
    updateByChange(event.getParent());
  }

  public void childReplaced(PsiTreeChangeEvent event) {
    updateByChange(event.getNewChild());
  }

  public void childrenChanged(PsiTreeChangeEvent event) {
    updateByChange(event.getParent());
  }

  public void beforeChildMovement(PsiTreeChangeEvent event) {
    updateByChange(event.getOldParent());
    updateByChange(event.getNewParent());
  }

  public void propertyChanged(PsiTreeChangeEvent event) {
    String propertyName = event.getPropertyName();
    if (!propertyName.equals(PsiTreeChangeEvent.PROP_WRITABLE)){
      myDaemonCodeAnalyzer.getFileStatusMap().markAllFilesDirty();
      myDaemonCodeAnalyzer.stopProcess(true);
    }
  }

  private void updateByChange(PsiElement child) {
    printDiff(child.getContainingFile());
    final Editor editor = FileEditorManager.getInstance(myProject).getSelectedTextEditor();
    if (editor != null) {
      ApplicationManager.getApplication().invokeLater(new Runnable(){
        public void run() {
          EditorMarkupModel markupModel = (EditorMarkupModel) editor.getMarkupModel();
          markupModel.setErrorStripeRenderer(markupModel.getErrorStripeRenderer());
        }
      },ModalityState.stateForComponent(editor.getComponent()));
    }

    PsiFile file = child.getContainingFile();
    if (file == null) {
      myDaemonCodeAnalyzer.getFileStatusMap().markAllFilesDirty();
      return;
    }
    Document document = PsiDocumentManager.getInstance(myProject).getCachedDocument(file);
    if (document == null) return;
    // optimization
    if (child instanceof PsiWhiteSpace 
        || child instanceof PsiComment
        || child instanceof PsiDocToken
        || PropertiesTokenTypes.PROPERTIES_TYPES_TO_IGNORE.contains(child.getNode().getElementType())) {
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

      if(parent instanceof XmlTag){
        PsiElement dirtyScope = pparent;

        if (pparent instanceof XmlTag &&
            "head".equals(((XmlTag)pparent).getLocalName())) {
          final PsiFile containingFile = parent.getContainingFile();
          final FileType fileType = containingFile == null ? null : containingFile.getFileType();

          if (fileType == StdFileTypes.JSP ||
              fileType == StdFileTypes.JSPX ||
              fileType == StdFileTypes.HTML ||
              fileType == StdFileTypes.XHTML
             ) {
            // change in head will result in changes for css/javascript code highlighting
            dirtyScope = containingFile;
          }
        }

        myDaemonCodeAnalyzer.getFileStatusMap().markFileScopeDirty(document, dirtyScope);
        return;
      }

      if (parent instanceof PsiCodeBlock
          && pparent instanceof PsiMethod
          && !((PsiMethod) pparent).isConstructor()
          && pparent.getParent() instanceof PsiClass
          && !(pparent.getParent() instanceof PsiAnonymousClass)) {
        // do not use this optimization for constructors and class initializers - to update non-initialized fields
        myDaemonCodeAnalyzer.getFileStatusMap().markFileScopeDirty(document, pparent);
        return;
      }
      parent = pparent;
    }
  }

  private PsiElement copy;
  Set<PsiElement> oldElements = new THashSet<PsiElement>();
  private void printDiff(final PsiFile containingFile) {
    if (containingFile != null && "x.xml".equals(containingFile.getName()))
    try {
      if (copy == null) {
        return;
      }
      //printDiff(copy, containingFile);
      containingFile.accept(new PsiRecursiveElementVisitor() {
        private boolean ignore;
        public void visitElement(PsiElement element) {
          if (!ignore && !oldElements.remove(element)) {
            ignore = true;
            System.out.println("'"+first(element)+"' added");
            super.visitElement(element);
            ignore = false;
          }
          else {
            super.visitElement(element);
          }
        }
      });
      for (PsiElement element : oldElements) {
        System.out.println("'"+first(element)+"' removed");
      }
    }
    finally {
      copy = containingFile.copy();
      oldElements.clear();
      containingFile.accept(new PsiRecursiveElementVisitor() {
        public void visitElement(PsiElement element) {
          oldElements.add(element);
          super.visitElement(element);
        }
      });
    }
  }

  private static String first(String s, int len) {
    if (s == null) {
      return null;
    }
    if (s.length() > len) {
      s = s.substring(0, len) + " ...";
    }
    return s;
  }
  private static String first(PsiElement s) { return s == null ? null : first(s.getClass() + ": "+s.getText(), 80); }
  private static void printDiff(final PsiElement element1, final PsiElement element2) {
    if (Comparing.strEqual(element1.getText(), element2.getText())) return;
    if (element1 instanceof LeafPsiElement || element2 instanceof LeafPsiElement) {
      System.out.println("'"+first(element1)+" -> '"+first(element2)+"'");
      return;
    }
    final PsiElement[] children1 = element1.getChildren();
    final PsiElement[] children2 = element2.getChildren();
    for (int i = 0; i < Math.max(children1.length, children2.length); i++) {
      if (i >= children1.length) {
        System.out.println("'"+first(element2)+"' -> '"+"<add>'");
        continue;
      }
      PsiElement child1 = children1[i];
      if (i >= children2.length) {
        System.out.println("'"+first(element1)+"' -> '"+"<delete>'");
        continue;
      }
      PsiElement child2 = children2[i];
      printDiff(child1, child2);
    }
  }
}