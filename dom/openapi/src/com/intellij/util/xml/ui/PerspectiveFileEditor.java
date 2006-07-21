/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.xml.ui;

import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
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
import java.awt.event.FocusEvent;
import java.awt.event.AWTEventListener;
import java.awt.*;

/**
 * User: Sergey.Vasiliev
 */
abstract public class PerspectiveFileEditor extends UserDataHolderBase implements DocumentsEditor, Committable, NavigatableFileEditor {
  private final PropertyChangeSupport myPropertyChangeSupport = new PropertyChangeSupport(this);
  private final Project myProject;
  private final VirtualFile myFile;
  private final UndoHelper myUndoHelper;
  private boolean myInvalidated;

  private static final FileEditorState FILE_EDITOR_STATE = new FileEditorState() {
    public boolean canBeMergedWith(FileEditorState otherState, FileEditorStateLevel level) {
      return true;
    }
  };

  protected PerspectiveFileEditor(final Project project, final VirtualFile file) {
    myProject = project;
    myUndoHelper = new UndoHelper(project, this);
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
    myUndoHelper.startListeningDocuments();

    final PsiFile psiFile = getPsiFile();
    if (psiFile != null) {
      final Document document = PsiDocumentManager.getInstance(getProject()).getDocument(psiFile);
      if (document != null) {
        addWatchedDocument(document);
      }
    }
  }

  abstract protected DomElement getSelectedDomElement();
  abstract protected void setSelectedDomElement(DomElement domElement);

  public boolean canNavigateTo(@NotNull final Navigatable navigatable) {
    if (navigatable instanceof OpenFileDescriptor) {
      final VirtualFile file = ((OpenFileDescriptor)navigatable).getFile();
      return file != null && isMyFile(file);
    }
    return true;
  }

  protected boolean isMyFile(VirtualFile file) {
    return file.equals(myFile);
  }

  public void navigateTo(@NotNull final Navigatable navigatable) {
    final JComponent focusedComponent = getPreferredFocusedComponent();
    if (focusedComponent != null) {
      if (!focusedComponent.requestFocusInWindow()) {
        focusedComponent.requestFocus();
      }
    }
  }

  public final void addWatchedElement(final DomElement domElement) {
    addWatchedDocument(getDocumentManager().getDocument(domElement.getRoot().getFile()));
  }

  public final void removeWatchedElement(final DomElement domElement) {
    removeWatchedDocument(getDocumentManager().getDocument(domElement.getRoot().getFile()));
  }

  public final void addWatchedDocument(final Document document) {
    myUndoHelper.addWatchedDocument(document);
  }

  public final void removeWatchedDocument(final Document document) {
    myUndoHelper.removeWatchedDocument(document);
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
    return myUndoHelper.getDocuments();
  }

  public final Project getProject() {
    return myProject;
  }

  public final VirtualFile getVirtualFile() {
    return myFile;
  }

  public void dispose() {
    if (myInvalidated) return;
    myInvalidated = true;
    myUndoHelper.stopListeningDocuments();
  }

  public final boolean isModified() {
    return FileDocumentManager.getInstance().isFileModified(getVirtualFile());
  }

  public boolean isValid() {
    return getVirtualFile().isValid();
  }

  public void selectNotify() {
    myUndoHelper.setShowing(true);
    reset();
  }

  public void deselectNotify() {
    if (myInvalidated) return;
    myUndoHelper.setShowing(false);
    commit();
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

  private static Thread focusCatcher = new FocusDrawer();

  static {
    focusCatcher.start();
  }

  private static class FocusDrawer extends Thread implements AWTEventListener {
    private Component myCurrent;
    private Component myPrevious;
    private boolean myTemporary;

    public FocusDrawer() {
      Toolkit.getDefaultToolkit().addAWTEventListener(this, AWTEvent.FOCUS_EVENT_MASK);
    }

    public void run() {
      try {
        while (true) {
          paintFocusBorders(false);

          sleep(100);
        }
      }
      catch (InterruptedException e) {
        e.printStackTrace();
      }
    }

    private void paintFocusBorders(boolean clean) {
      if (myCurrent != null) {
        Graphics currentFocusGraphics = myCurrent.getGraphics();
        if (currentFocusGraphics != null) {
          if (clean) {
            currentFocusGraphics.setXORMode(Color.RED);
          }
          currentFocusGraphics.setColor(Color.RED);
          drawDottedRectangle(currentFocusGraphics, 1, 1, myCurrent.getSize().width - 2, myCurrent.getSize().height - 2);
        }
      }

      if (myPrevious != null) {
        Graphics previousFocusGraphics = myPrevious.getGraphics();
        if (previousFocusGraphics != null) {
          if (clean) {
            previousFocusGraphics.setXORMode(Color.BLUE);
          }
          previousFocusGraphics.setColor(Color.BLUE);
          drawDottedRectangle(previousFocusGraphics, 1, 1, myPrevious.getSize().width - 2, myPrevious.getSize().height - 2);
        }
      }
    }

    public static void drawDottedRectangle(Graphics g, int x, int y, int x1, int y1) {
      int i1;
      for(i1 = x; i1 <= x1; i1 += 2){
        g.drawLine(i1, y, i1, y);
      }

      for(i1 = i1 != x1 + 1 ? y + 2 : y + 1; i1 <= y1; i1 += 2){
        g.drawLine(x1, i1, x1, i1);
      }

      for(i1 = i1 != y1 + 1 ? x1 - 2 : x1 - 1; i1 >= x; i1 -= 2){
        g.drawLine(i1, y1, i1, y1);
      }

      for(i1 = i1 != x - 1 ? y1 - 2 : y1 - 1; i1 >= y; i1 -= 2){
        g.drawLine(x, i1, x, i1);
      }
    }

    public void eventDispatched(AWTEvent event) {
      if (event instanceof FocusEvent) {
        FocusEvent focusEvent = (FocusEvent) event;
        Component fromComponent = focusEvent.getComponent();
        Component oppositeComponent = focusEvent.getOppositeComponent();

        paintFocusBorders(true);

        switch (event.getID()) {
          case FocusEvent.FOCUS_GAINED:
            myCurrent = fromComponent;
            myPrevious = oppositeComponent;
            break;
          case FocusEvent.FOCUS_LOST:
            myTemporary = focusEvent.isTemporary();
          default:
            break;
        }
      }
    }
  }
}
