/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.xml.ui;

import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.command.CommandAdapter;
import com.intellij.openapi.command.CommandEvent;
import com.intellij.openapi.command.CommandProcessor;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.HashSet;
import java.util.Set;

/**
 * User: Sergey.Vasiliev
 */
abstract public class PerspectiveFileEditor extends UserDataHolderBase implements DocumentsEditor, Committable {
  private final PropertyChangeSupport myPropertyChangeSupport = new PropertyChangeSupport(this);
  private final Project myProject;
  private final VirtualFile myFile;

  private boolean myInvalidated;
  private boolean myShowing;
  private boolean myCommitting;
  private final Set<Document> myCurrentDocuments = new HashSet<Document>();
  private boolean myDirty;
  private final DocumentAdapter myDocumentAdapter = new DocumentAdapter() {
    public void documentChanged(DocumentEvent e) {
      if (myShowing && !myCommitting) {
        myDirty = true;
      }
    }
  };
  private static final FileEditorState FILE_EDITOR_STATE = new FileEditorState() {
    public boolean canBeMergedWith(FileEditorState otherState, FileEditorStateLevel level) {
      return true;
    }
  };

  protected PerspectiveFileEditor(final Project project, final VirtualFile file) {
    myProject = project;

    myFile = file;

    FileEditorManager.getInstance(myProject).addFileEditorManagerListener(new FileEditorManagerAdapter() {
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
            final DomElement element = getSelectedDomElementFromTextEditor((TextEditor)event.getOldEditor());
            if (element != null) {
              setSelectedDomElement(element);
            }
          } else if (event.getOldEditor() instanceof PerspectiveFileEditor) {
            setSelectedDomElement(((PerspectiveFileEditor)event.getOldEditor()).getSelectedDomElement());
          }
        }
      }
    }, this);
    startListeningDocuments();

    final PsiFile psiFile = getPsiFile();
    if (psiFile != null) {
      final Document document = PsiDocumentManager.getInstance(getProject()).getDocument(psiFile);
      if (document != null) {
        addWatchedDocument(document);
      }
    }

    CommandProcessor.getInstance().addCommandListener(new CommandAdapter() {
      public void commandStarted(CommandEvent event) {
        undoTransparentActionStarted();
      }

      public void undoTransparentActionStarted() {
        myDirty = false;
      }

      public void undoTransparentActionFinished() {
        if (myDirty) {
          SwingUtilities.invokeLater(new Runnable() {
            public void run() {
              PsiDocumentManager.getInstance(project).commitAllDocuments();
              reset();
            }
          });
        }
      }

      public void commandFinished(CommandEvent event) {
        undoTransparentActionFinished();
      }
    }, this);
  }

  protected final void startListeningDocuments() {
    for (final Document document : myCurrentDocuments) {
      document.addDocumentListener(myDocumentAdapter);
    }
  }

  abstract protected DomElement getSelectedDomElement();
  abstract protected void setSelectedDomElement(DomElement domElement);

  public final void addWatchedElement(final DomElement domElement) {
    addWatchedDocument(getDocumentManager().getDocument(domElement.getRoot().getFile()));
  }

  public final void removeWatchedElement(final DomElement domElement) {
    removeWatchedDocument(getDocumentManager().getDocument(domElement.getRoot().getFile()));
  }

  public final void addWatchedDocument(final Document document) {
    stopListeningDocuments();
    myCurrentDocuments.add(document);
    startListeningDocuments();
  }

  public final void removeWatchedDocument(final Document document) {
    stopListeningDocuments();
    myCurrentDocuments.remove(document);
    startListeningDocuments();
  }

  protected final void doCommit() {
    myCommitting = true;
    try {
      commit();
    }
    finally {
      myCommitting = false;
    }
  }

  protected DomElement getSelectedDomElementFromTextEditor(final TextEditor textEditor) {
    final PsiFile psiFile = getPsiFile();
    if (psiFile == null) return null;
    final PsiElement psiElement = psiFile.findElementAt(textEditor.getEditor().getCaretModel().getOffset());

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

  protected final PsiDocumentManager getDocumentManager() {
    return PsiDocumentManager.getInstance(myProject);
  }

  @Nullable
  public final PsiFile getPsiFile() {
    return PsiManager.getInstance(myProject).findFile(myFile);
  }

  public final Document[] getDocuments() {
    return myCurrentDocuments.toArray(new Document[myCurrentDocuments.size()]);
  }

  public final Project getProject() {
    return myProject;
  }

  public final VirtualFile getVirtualFile() {
    return myFile;
  }

  public void dispose() {
    stopListeningDocuments();
  }

  protected final void stopListeningDocuments() {
    for (final Document document : myCurrentDocuments) {
      document.removeDocumentListener(myDocumentAdapter);
    }
  }

  public final boolean isModified() {
    return FileDocumentManager.getInstance().isFileModified(getVirtualFile());
  }

  public boolean isValid() {
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
    for (final Document document : myCurrentDocuments) {
      manager.commitDocument(document);
    }
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

  @NotNull
  public FileEditorState getState(FileEditorStateLevel level) {
    return FILE_EDITOR_STATE;
  }

  public void setState(FileEditorState state) {
  }

  public void addPropertyChangeListener(PropertyChangeListener listener) {
    myPropertyChangeSupport.addPropertyChangeListener(listener);
  }

  public void removePropertyChangeListener(PropertyChangeListener listener) {
    myPropertyChangeSupport.removePropertyChangeListener(listener);
  }

  protected final boolean checkIsValid() {
    if (!myInvalidated && !isValid()) {
      myInvalidated = true;
      myPropertyChangeSupport.firePropertyChange(FileEditor.PROP_VALID, Boolean.TRUE, Boolean.FALSE);
    }
    return !myInvalidated;
  }
}
