/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

/**
 * User: lex
 * Date: Oct 24, 2003
 * Time: 8:23:06 PM
 */
public abstract class SourcePosition implements Navigatable{
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.SourcePosition");

  public abstract PsiFile getFile();

  /**
   * @return a zero-based line number
   */
  public abstract int getLine();

  public abstract int getOffset();

  public abstract Editor openEditor(boolean requestFocus);

  private abstract static class SourcePositionCache extends SourcePosition {
    private final PsiFile myFile;
    private long myModificationStamp;

    protected int myLine;
    protected int myOffset;

    public SourcePositionCache(PsiFile file) {
      LOG.assertTrue(file != null);
      myFile = file;
      myModificationStamp = myFile.getModificationStamp();
    }

    public PsiFile getFile() {
      return myFile;
    }

    public boolean canNavigate() {
      return getFile().isValid();
    }

    public boolean canNavigateToSource() {
      return canNavigate();
    }

    public void navigate(final boolean requestFocus) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          if (!canNavigate()) {
            return;
          }
          openEditor(requestFocus);
        }
      });
    }

    public Editor openEditor(final boolean requestFocus) {
      final PsiFile psiFile = getFile();
      final Project project = psiFile.getProject();
      if (project.isDisposed()) {
        return null;
      }
      final VirtualFile virtualFile = psiFile.getVirtualFile();
      if (virtualFile == null || !virtualFile.isValid()) {
        return null;
      }
      return FileEditorManager.getInstance(project).openTextEditor(new OpenFileDescriptor(project, virtualFile, getOffset()), requestFocus);
    }

    private boolean checkRecalculate() {
      if(myModificationStamp != myFile.getModificationStamp()) {
        myModificationStamp = myFile.getModificationStamp();
        return false;
      } else {
        return true;
      }
    }

    public int getLine() {
      if(checkRecalculate()) {
        myLine = calcLine();
      }
      return myLine;
    }

    public int getOffset() {
      if(checkRecalculate()) {
        myOffset = calcOffset();
      }
      return myOffset;
    }

    protected abstract int calcLine();
    protected abstract int calcOffset();
  }

  public static SourcePosition createFromLine(final PsiFile file, final int line) {
    return new SourcePositionCache(file) {
      protected int calcLine() {
        return line;
      }

      protected int calcOffset() {
        final Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
        return document.getLineStartOffset(line);
      }
    };
  }

  public static SourcePosition createFromOffset(final PsiFile file, final int offset) {
    return new SourcePositionCache(file) {
      protected int calcLine() {
        final Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
        return document.getLineNumber(offset);
      }

      protected int calcOffset() {
        return offset;
      }
    };
  }

  public static SourcePosition createFromElement(PsiElement element) {
    PsiElement navigationElement = element.getNavigationElement();
    return createFromOffset(navigationElement.getContainingFile(), navigationElement.getTextOffset());
  }

  public boolean equals(Object o) {
    if(o instanceof SourcePosition) {
      SourcePosition sourcePosition = ((SourcePosition)o);
      return Comparing.equal(sourcePosition.getFile(), getFile()) &&
             sourcePosition.getOffset() == getOffset();
    }

    return false;
  }
}
