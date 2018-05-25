// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
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
import com.intellij.reference.SoftReference;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.List;

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
    @Nullable private final SmartPsiElementPointer<PsiFile> myFilePointer;
    private long myModificationStamp = -1L;

    private WeakReference<PsiElement> myPsiElementRef;
    private Integer myLine;
    private Integer myOffset;

    public SourcePositionCache(@NotNull PsiFile file) {
      myFile = file;
      myFilePointer = ReadAction.compute(
        () -> file.isValid() ? SmartPointerManager.getInstance(file.getProject()).createSmartPsiElementPointer(file) : null);
      updateData();
    }

    @Override
    @NotNull
    public PsiFile getFile() {
      PsiFile file = myFilePointer != null ? ReadAction.compute(myFilePointer::getElement) : null;
      return file != null ? file : myFile; // in case of full invalidation, rollback to the original psiFile
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
      ApplicationManager.getApplication().invokeLater(() -> {
        if (!canNavigate()) {
          return;
        }
        openEditor(requestFocus);
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
        myModificationStamp = getFile().getModificationStamp();
        myLine = null;
        myOffset = null;
        myPsiElementRef = null;
      }
    }

    private boolean dataUpdateNeeded() {
      if (myModificationStamp != getFile().getModificationStamp()) {
        return true;
      }
      PsiElement psiElement = SoftReference.dereference(myPsiElementRef);
      return psiElement != null && !ReadAction.compute(psiElement::isValid);
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
      PsiElement element = SoftReference.dereference(myPsiElementRef);
      if (element == null) {
        element = ReadAction.compute(this::calcPsiElement);
        myPsiElementRef = new WeakReference<>(element);
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
      PsiFile psiFile = getFile();
      if (!psiFile.isValid()) {
        return null;
      }

      int lineNumber = getLine();
      if (lineNumber < 0) {
        return psiFile;
      }

      Document document = getDocument(psiFile);
      if (document == null) {
        return null;
      }
      if (lineNumber >= document.getLineCount()) {
        return psiFile;
      }
      int startOffset = document.getLineStartOffset(lineNumber);
      if (startOffset == -1) {
        return null;
      }

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
        return pointer.getElement();
      }

      @Override
      protected int calcOffset() {
        return ReadAction.compute(() -> {
            PsiElement elem = pointer.getElement();
            return elem != null ? elem.getTextOffset() : -1;
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
