/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
package com.intellij.debugger;

import com.intellij.lang.StdLanguages;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;

/**
 * User: lex
 * Date: Oct 24, 2003
 * Time: 8:23:06 PM
 */
public abstract class SourcePosition implements Navigatable{
  @NotNull
  public abstract PsiFile getFile();

  public abstract PsiElement getElementAt();

  /**
   * @return a zero-based line number
   */
  public abstract int getLine();

  public abstract int getOffset();

  public abstract Editor openEditor(boolean requestFocus);

  private abstract static class SourcePositionCache extends SourcePosition {
    private final @NotNull PsiFile myFile;
    private long myModificationStamp = -1L;

    private PsiElement myPsiElement;
    private Integer myLine;
    private Integer myOffset;

    public SourcePositionCache(@NotNull PsiFile file) {
      myFile = file;
      updateData();
    }

    @NotNull
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
      final int offset = getOffset();
      if (offset < 0) {
        return null;
      }
      return FileEditorManager.getInstance(project).openTextEditor(new OpenFileDescriptor(project, virtualFile, offset), requestFocus);
    }

    private void updateData() {
      if(myModificationStamp != myFile.getModificationStamp()) {
        myModificationStamp = myFile.getModificationStamp();
        myLine = null;
        myOffset = null;
        myPsiElement = null;
      }
    }

    public int getLine() {
      updateData();
      if (myLine == null) {
        myLine = calcLine();
      }
      return myLine.intValue();
    }

    public int getOffset() {
      updateData();
      if (myOffset == null) {
        myOffset = calcOffset();
      }
      return myOffset.intValue();
    }

    public PsiElement getElementAt() {
      updateData();
      if (myPsiElement == null) {
        myPsiElement = calcPsiElement();
      }
      return myPsiElement;
    }

    protected int calcLine() {
      final PsiFile file = getFile();
      final Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
      if (document != null) {
        try {
          return document.getLineNumber(calcOffset());
        }
        catch (IndexOutOfBoundsException e) {
          // may happen if document has been changed since the this SourcePosition was created
        }
      }
      return -1;
    }

    protected int calcOffset() {
      final PsiFile file = getFile();
      final Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
      if (document != null) {
        try {
          return document.getLineStartOffset(calcLine());
        }
        catch (IndexOutOfBoundsException e) {
          // may happen if document has been changed since the this SourcePosition was created
        }
      }
      return -1;
    }

    protected PsiElement calcPsiElement() {
      final int offset = getOffset();
      if (offset < 0) {
        return null;
      }
      final PsiFile psiFile = getFile();
      if (psiFile instanceof PsiCompiledElement) {
        return psiFile.findElementAt(offset);
      }
      final FileViewProvider viewProvider = psiFile.getViewProvider();
      return viewProvider.findElementAt(offset, StdLanguages.JAVA);
    }
  }

  public static SourcePosition createFromLineComputable(final PsiFile file, final Computable<Integer> line) {
    return new SourcePositionCache(file) {
      protected int calcLine() {
        return line.compute();
      }
    };
  }

  public static SourcePosition createFromLine(final PsiFile file, final int line) {
    return new SourcePositionCache(file) {
      protected int calcLine() {
        return line;
      }
    };
  }

  public static SourcePosition createFromOffset(final PsiFile file, final int offset) {
    return new SourcePositionCache(file) {

      protected int calcOffset() {
        return offset;
      }
    };
  }
     
  public static SourcePosition createFromElement(PsiElement element) {
    final PsiElement navigationElement = element.getNavigationElement();
    final PsiFile psiFile;
    if (PsiUtil.isInJspFile(navigationElement)) {
      psiFile = PsiUtil.getJspFile(navigationElement);
    }
    else {
      psiFile = navigationElement.getContainingFile();
    }
    return new SourcePositionCache(psiFile) {
      protected PsiElement calcPsiElement() {
        return navigationElement;
      }

      protected int calcOffset() {
        return navigationElement.getTextOffset();
      }
    };
  }

  public boolean equals(Object o) {
    if(o instanceof SourcePosition) {
      SourcePosition sourcePosition = ((SourcePosition)o);
      return Comparing.equal(sourcePosition.getFile(), getFile()) && sourcePosition.getOffset() == getOffset();
    }

    return false;
  }
}
