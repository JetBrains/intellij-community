/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.xml.ui;

import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import org.jetbrains.annotations.Nullable;

import java.beans.PropertyChangeListener;

/**
 * User: Sergey.Vasiliev
 */
abstract public class PerspectiveFileEditor extends UserDataHolderBase implements DocumentsEditor, Committable {
  private final Project myProject;
  private final VirtualFile myFile;
  private final FileEditorManagerAdapter myFileEditorManagerAdapter;
  private boolean myShowing;
  private final DocumentAdapter myDocumentAdapter = new DocumentAdapter() {
    public void documentChanged(DocumentEvent e) {
      if (myShowing) {
        reset();
      }
    }
  };

  protected PerspectiveFileEditor(final Project project, final VirtualFile file) {
    myProject = project;

    myFile = file;

    myFileEditorManagerAdapter = new FileEditorManagerAdapter() {
     public void selectionChanged(FileEditorManagerEvent event) {
       if (PerspectiveFileEditor.this.equals(event.getOldEditor())) {
         deselectNotify();
         if (event.getNewEditor() instanceof TextEditor) {
           setSelectionInTextEditor((TextEditor)event.getNewEditor(), getSelectedDomElement());
         }
       }
       else if (PerspectiveFileEditor.this.equals(event.getNewEditor())) {
         selectNotify();
         if (event.getOldEditor() instanceof TextEditor) {
           setSelectedDomElement(getSelectedDomElementFromTextEditor((TextEditor)event.getOldEditor()));
         } else if (event.getOldEditor() instanceof PerspectiveFileEditor) {
            setSelectedDomElement(((PerspectiveFileEditor)event.getOldEditor()).getSelectedDomElement());
         }
       }
     }
    };
    FileEditorManager.getInstance(myProject).addFileEditorManagerListener(myFileEditorManagerAdapter);
    startListeningDocuments();
  }

  protected final void startListeningDocuments() {
    for (final Document document : getDocuments()) {
      document.addDocumentListener(myDocumentAdapter);
    }
  }

  abstract protected DomElement getSelectedDomElement();
  abstract protected void setSelectedDomElement(DomElement domElement);

  protected DomElement getSelectedDomElementFromTextEditor(final TextEditor textEditor) {
    final PsiElement psiElement = getPsiFile().findElementAt(textEditor.getEditor().getCaretModel().getOffset());

    if(psiElement == null) return null;

    final XmlTag xmlTag = PsiTreeUtil.getParentOfType(psiElement, XmlTag.class);

    return DomManager.getDomManager(myProject).getDomElement(xmlTag);
  }

  public void setSelectionInTextEditor(final TextEditor textEditor, final DomElement element) {
    if (element != null && element.isValid()) {
      final XmlTag tag = element.getXmlTag();
      if (tag == null) return;

      final PsiFile file = tag.getContainingFile();
      if (file == null) return;

      final Document document = getDocumentManager().getDocument(file);
      if (document == null || !document.equals(textEditor.getEditor().getDocument())) return;

      textEditor.getEditor().getCaretModel().moveToOffset(tag.getTextOffset());
      textEditor.getEditor().getScrollingModel().scrollToCaret(ScrollType.CENTER);
    }
  }

  private PsiDocumentManager getDocumentManager() {
    return PsiDocumentManager.getInstance(myProject);
  }

  @Nullable
  public final PsiFile getPsiFile() {
    return PsiManager.getInstance(myProject).findFile(myFile);
  }

  public Document[] getDocuments() {
    final PsiFile psiFile = getPsiFile();
    return psiFile == null ? new Document[0] : new Document[]{getDocumentManager().getDocument(psiFile)};
  }

  public final Project getProject() {
    return myProject;
  }

  public final VirtualFile getVirtualFile() {
    return myFile;
  }

  public void dispose() {
    stopListeningDocuments();
    FileEditorManager.getInstance(myProject).removeFileEditorManagerListener(myFileEditorManagerAdapter);
 }

  protected final void stopListeningDocuments() {
    for (final Document document : getDocuments()) {
      document.removeDocumentListener(myDocumentAdapter);
    }
  }

  public final boolean isModified() {
    return FileDocumentManager.getInstance().isFileModified(getVirtualFile());
  }

  public final boolean isValid() {
    return getVirtualFile().isValid();
  }

  public void selectNotify() {
    commitAllDocuments();
    myShowing = true;
    reset();
  }

  public void deselectNotify() {
    commitAllDocuments();
    myShowing = false;
    commit();
  }

  private void commitAllDocuments() {
    final PsiDocumentManager manager = getDocumentManager();
    for (final Document document : getDocuments()) {
      manager.commitDocument(document);
    }
  }

  public void addPropertyChangeListener(PropertyChangeListener listener) {
  }

  public void removePropertyChangeListener(PropertyChangeListener listener) {
  }

  public BackgroundEditorHighlighter getBackgroundHighlighter() {
    return null;
  }

  public FileEditorLocation getCurrentLocation() {
    return new FileEditorLocation() {
      public FileEditor getEditor() {
        return PerspectiveFileEditor.this;
      }

      public int compareTo(final FileEditorLocation fileEditorLocation) {
        return 0;
      }
    };
  }

  public StructureViewBuilder getStructureViewBuilder() {
    return null;
  }



}
