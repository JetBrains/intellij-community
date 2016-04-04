/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.List;

/**
 * User: lex
 * Date: Oct 24, 2003
 * Time: 8:23:06 PM
 */
public abstract class SourcePosition implements Navigatable{
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.SourcePosition");
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
    @NotNull private final PsiFile myFile;
    private long myModificationStamp = -1L;

    private WeakReference<PsiElement> myPsiElementRef;
    private Integer myLine;
    private Integer myOffset;

    public SourcePositionCache(@NotNull PsiFile file) {
      myFile = file;
      updateData();
    }

    @Override
    @NotNull
    public PsiFile getFile() {
      return myFile;
    }

    @Override
    public boolean canNavigate() {
      return getFile().isValid();
    }

    @Override
    public boolean canNavigateToSource() {
      return canNavigate();
    }

    @Override
    public void navigate(final boolean requestFocus) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          if (!canNavigate()) {
            return;
          }
          openEditor(requestFocus);
        }
      });
    }

    @Override
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
      if(dataUpdateNeeded()) {
        myModificationStamp = myFile.getModificationStamp();
        myLine = null;
        myOffset = null;
        myPsiElementRef = null;
      }
    }

    private boolean dataUpdateNeeded() {
      if (myModificationStamp != myFile.getModificationStamp()) {
        return true;
      }
      final PsiElement psiElement = myPsiElementRef != null ? myPsiElementRef.get() : null;
      return psiElement != null && !ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
        @Override
        public Boolean compute() {
          return psiElement.isValid();
        }
      });
    }

    @Override
    public int getLine() {
      updateData();
      if (myLine == null) {
        myLine = calcLine();
      }
      return myLine.intValue();
    }

    @Override
    public int getOffset() {
      updateData();
      if (myOffset == null) {
        myOffset = calcOffset();
      }
      return myOffset.intValue();
    }

    @Override
    public PsiElement getElementAt() {
      updateData();
      PsiElement element = myPsiElementRef != null ? myPsiElementRef.get() : null;
      if (element == null) {
        element = calcPsiElement();
        myPsiElementRef = new WeakReference<PsiElement>(element);
        return element;
      }
      return element;
    }

    protected int calcLine() {
      final PsiFile file = getFile();
      Document document = null;
      try {
        document = getDocument(file);
        if (document == null) { // may be decompiled psi - try to get document for the original file
          document = getDocument(file.getOriginalFile());
        }
      }
      catch (ProcessCanceledException ignored) {}
      catch (Throwable e) {
        LOG.error(e);
      }
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

    @Nullable
    private static Document getDocument(@NotNull PsiFile file) {
      Project project = file.getProject();
      if (project.isDisposed()) {
        return null;
      }
      return PsiDocumentManager.getInstance(project).getDocument(file);
    }

    protected int calcOffset() {
      final PsiFile file = getFile();
      final Document document = getDocument(file);
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

    @Nullable
    protected PsiElement calcPsiElement() {
      // currently PsiDocumentManager does not store documents for mirror file, so we store original file
      PsiFile origPsiFile = getFile();
      final PsiFile psiFile = origPsiFile instanceof PsiCompiledFile ? ((PsiCompiledFile)origPsiFile).getDecompiledPsiFile() : origPsiFile;
      int lineNumber = getLine();
      if(lineNumber < 0) {
        return psiFile;
      }

      final Document document = getDocument(origPsiFile);
      if (document == null) {
        return null;
      }
      if (lineNumber >= document.getLineCount()) {
        return psiFile;
      }
      final int startOffset = document.getLineStartOffset(lineNumber);
      if(startOffset == -1) {
        return null;
      }

      return ApplicationManager.getApplication().runReadAction(new Computable<PsiElement>() {
        @Override
        public PsiElement compute() {
          PsiElement rootElement = psiFile;

          List<PsiFile> allFiles = psiFile.getViewProvider().getAllFiles();
          if (allFiles.size() > 1) { // jsp & gsp
            PsiClassOwner owner = ContainerUtil.findInstance(allFiles, PsiClassOwner.class);
            if (owner != null) {
              PsiClass[] classes = owner.getClasses();
              if (classes.length == 1 && classes[0] instanceof SyntheticElement) {
                rootElement = classes[0];
              }
            }
          }

          PsiElement element = null;
          int offset = getOffset();
          while (true) {
            final CharSequence charsSequence = document.getCharsSequence();
            for (; offset < charsSequence.length(); offset++) {
              char c = charsSequence.charAt(offset);
              if (c != ' ' && c != '\t') {
                break;
              }
            }
            if (offset >= charsSequence.length()) break;

            element = rootElement.findElementAt(offset);

            if (element instanceof PsiComment) {
              offset = element.getTextRange().getEndOffset() + 1;
            }
            else {
              break;
            }
          }
          if (element != null && element.getParent() instanceof PsiForStatement) {
            return ((PsiForStatement)element.getParent()).getInitialization();
          }
          return element;
        }
      });
    }
  }

  public static SourcePosition createFromLineComputable(@NotNull final PsiFile file, final Computable<Integer> line) {
    return new SourcePositionCache(file) {
      @Override
      protected int calcLine() {
        return line.compute();
      }
    };
  }

  public static SourcePosition createFromLine(@NotNull final PsiFile file, final int line) {
    return new SourcePositionCache(file) {
      @Override
      protected int calcLine() {
        return line;
      }

      @Override
      public String toString() {
        return getFile().getName() + ":" + line;
      }
    };
  }

  public static SourcePosition createFromOffset(@NotNull final PsiFile file, final int offset) {
    return new SourcePositionCache(file) {
      @Override
      protected int calcOffset() {
        return offset;
      }

      @Override
      public String toString() {
        return getFile().getName() + " offset " + offset;
      }
    };
  }

  @Nullable
  public static SourcePosition createFromElement(@NotNull PsiElement element) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    PsiElement navigationElement = element.getNavigationElement();
    final SmartPsiElementPointer<PsiElement> pointer =
      SmartPointerManager.getInstance(navigationElement.getProject()).createSmartPsiElementPointer(navigationElement);
    final PsiFile psiFile;
    if (JspPsiUtil.isInJspFile(navigationElement)) {
      psiFile = JspPsiUtil.getJspFile(navigationElement);
    }
    else {
      psiFile = navigationElement.getContainingFile();
    }
    if (psiFile == null) return null;
    return new SourcePositionCache(psiFile) {
      @Override
      protected PsiElement calcPsiElement() {
        ApplicationManager.getApplication().assertReadAccessAllowed();
        return pointer.getElement();
      }

      @Override
      protected int calcOffset() {
        return ApplicationManager.getApplication().runReadAction(new Computable<Integer>() {
          @Override
          public Integer compute() {
            PsiElement elem = pointer.getElement();
            return elem != null ? elem.getTextOffset() : -1;
          }
        });
      }
    };
  }

  public boolean equals(Object o) {
    if(o instanceof SourcePosition) {
      SourcePosition sourcePosition = (SourcePosition)o;
      return Comparing.equal(sourcePosition.getFile(), getFile()) && sourcePosition.getOffset() == getOffset();
    }

    return false;
  }
}
