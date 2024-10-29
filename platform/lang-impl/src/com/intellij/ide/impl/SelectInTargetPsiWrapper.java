// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.impl;

import com.intellij.ide.SelectInContext;
import com.intellij.ide.SelectInTarget;
import com.intellij.ide.projectView.impl.SelectInProjectViewImplKt;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.SlowOperations;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class SelectInTargetPsiWrapper implements SelectInTarget {
  protected final Project myProject;

  protected SelectInTargetPsiWrapper(final @NotNull Project project) {
    myProject = project;
  }

  public abstract String toString();

  protected abstract boolean canSelect(PsiFileSystemItem file);

  @Override
  public final boolean canSelect(@NotNull SelectInContext context) {
    if (!isContextValid(context)) return false;

    return canSelectInner(context);
  }

  protected boolean canSelectInner(@NotNull SelectInContext context) {
    PsiFileSystemItem psiFile = getContextPsiFile(context);
    if (SelectInProjectViewImplKt.getLOG().isDebugEnabled()) {
      SelectInProjectViewImplKt.getLOG().debug("PSI file for context " + context + " is " + psiFile);
    }
    if (psiFile == null) {
      return false;
    }
    boolean canSelect = canSelect(psiFile);
    if (SelectInProjectViewImplKt.getLOG().isDebugEnabled()) {
      SelectInProjectViewImplKt.getLOG().debug("Can " + (canSelect ? "select" : "NOT select") + " file " + psiFile + " in " + this);
    }
    return canSelect;
  }

  private boolean isContextValid(SelectInContext context) {
    if (myProject.isDisposed() || !myProject.isInitialized()) return false;

    VirtualFile virtualFile = context.getVirtualFile();
    boolean valid = virtualFile.isValid();
    if (SelectInProjectViewImplKt.getLOG().isDebugEnabled()) {
      SelectInProjectViewImplKt.getLOG().debug("File " + virtualFile + " is " + (valid ? "valid" : "NOT valid"));
    }
    return valid;
  }

  protected @Nullable PsiFileSystemItem getContextPsiFile(@NotNull SelectInContext context) {
    VirtualFile virtualFile = context.getVirtualFile();
    PsiFileSystemItem psiFile = PsiManager.getInstance(myProject).findFile(virtualFile);
    if (psiFile != null) {
      return psiFile;
    }

    if (context.getSelectorInFile() instanceof PsiFile) {
      return (PsiFile)context.getSelectorInFile();
    }
    if (virtualFile.isDirectory()) {
      return PsiManager.getInstance(myProject).findDirectory(virtualFile);
    }
    return null;
  }

  @Override
  public final void selectIn(@NotNull SelectInContext context, boolean requestFocus) {
    if (SelectInProjectViewImplKt.getLOG().isDebugEnabled()) {
      SelectInProjectViewImplKt.getLOG().debug(
        "SelectInTargetPsiWrapper.selectIn: Select in " + this +
        ", requestFocus=" + requestFocus +
        " using context " + context
      );
    }
    ReadAction.nonBlocking(() -> {
        VirtualFile file = context.getVirtualFile();
        Object selector = context.getSelectorInFile();
        if (SelectInProjectViewImplKt.getLOG().isDebugEnabled()) {
          SelectInProjectViewImplKt.getLOG().debug("File is " + file + ", selector is " + selector);
        }
        if (selector == null) {
          selector = PsiUtilCore.findFileSystemItem(myProject, file);
          if (SelectInProjectViewImplKt.getLOG().isDebugEnabled()) {
            SelectInProjectViewImplKt.getLOG().debug("Falling back to selector " + selector);
          }
        }
        if (selector instanceof PsiElement) {
          PsiUtilCore.ensureValid((PsiElement)selector);
          PsiElement original = ((PsiElement)selector).getOriginalElement();
          if (original != null && !original.isValid()) {
            throw new PsiInvalidElementAccessException(original, "Returned by " + selector + " of " + selector.getClass());
          }
          return new ComputedContext(file, selector, original);
        }
        else {
          return new ComputedContext(file, selector, null);
        }
      })
      .expireWith(myProject)
      .finishOnUiThread(ModalityState.current(), computed -> {
        var file = computed.file;
        var selector = computed.selector;
        var original = computed.original;
        if (selector instanceof PsiElement) {
          try (var ignored = SlowOperations.startSection(SlowOperations.ACTION_PERFORM)) {
            if (SelectInProjectViewImplKt.getLOG().isDebugEnabled()) {
              SelectInProjectViewImplKt.getLOG().debug("Selecting " + original);
            }
            select(original, requestFocus);
          }
        }
        else {
          if (SelectInProjectViewImplKt.getLOG().isDebugEnabled()) {
            SelectInProjectViewImplKt.getLOG().debug("Selecting non-PSI selector " + selector + " in file " + file);
          }
          select(selector, file, requestFocus);
        }
      })
      .submit(AppExecutorUtil.getAppExecutorService());
  }

  private record ComputedContext(@Nullable VirtualFile file, @Nullable Object selector, @Nullable  PsiElement original) { }

  protected abstract void select(Object selector, VirtualFile virtualFile, boolean requestFocus);

  protected abstract void select(PsiElement element, boolean requestFocus);

  protected static @Nullable PsiElement findElementToSelect(PsiElement element, PsiElement candidate) {
    PsiElement toSelect = candidate;

    if (toSelect == null) {
      if (element instanceof PsiFile || element instanceof PsiDirectory) {
        toSelect = element;
        if (SelectInProjectViewImplKt.getLOG().isDebugEnabled()) {
          SelectInProjectViewImplKt.getLOG().debug("Will select PSI file/dir " + toSelect);
        }
      }
      else {
        PsiFile containingFile = element.getContainingFile();
        if (containingFile != null) {
          FileViewProvider viewProvider = containingFile.getViewProvider();
          toSelect = viewProvider.getPsi(viewProvider.getBaseLanguage());
          if (SelectInProjectViewImplKt.getLOG().isDebugEnabled()) {
            SelectInProjectViewImplKt.getLOG().debug("Will select PSI element " + toSelect + " provided by " + viewProvider);
          }
        }
      }
    }

    if (toSelect != null) {
      PsiElement originalElement = null;
      try {
        originalElement = toSelect.getOriginalElement();
        if (SelectInProjectViewImplKt.getLOG().isDebugEnabled()) {
          SelectInProjectViewImplKt.getLOG().debug("Original element to select is " + originalElement);
        }
      }
      catch (IndexNotReadyException ignored) { }
      if (originalElement != null) {
        toSelect = originalElement;
      }
    }

    return toSelect;
  }
}