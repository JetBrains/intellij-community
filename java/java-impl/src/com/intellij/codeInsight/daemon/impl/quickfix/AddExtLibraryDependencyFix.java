// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.ExternalLibraryDescriptor;
import com.intellij.openapi.roots.JavaProjectModelModificationService;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ModalityUiUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class AddExtLibraryDependencyFix extends OrderEntryFix {
  private final @NotNull Module myCurrentModule;
  private final @NotNull ExternalLibraryDescriptor myLibraryDescriptor;
  private final @NotNull DependencyScope myScope;
  private final @Nullable String myQualifiedClassName;

  AddExtLibraryDependencyFix(@NotNull PsiReference reference,
                             @NotNull Module currentModule,
                             @NotNull ExternalLibraryDescriptor descriptor,
                             @NotNull DependencyScope scope,
                             @Nullable String qName) {
    super(reference);
    myCurrentModule = currentModule;
    myLibraryDescriptor = descriptor;
    myScope = scope;
    myQualifiedClassName = qName;
  }

  @Nls
  @NotNull
  @Override
  public String getText() {
    return QuickFixBundle.message("add.0.to.classpath", myLibraryDescriptor.getPresentableName());
  }

  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return getText();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return !project.isDisposed() && !myCurrentModule.isDisposed();
  }

  @Override
  public void invoke(@NotNull Project project, final Editor editor, PsiFile file) throws IncorrectOperationException {
    ModalityState modality = ModalityState.defaultModalityState();
    JavaProjectModelModificationService.getInstance(project)
      .addDependency(myCurrentModule, myLibraryDescriptor, myScope)
      .onSuccess(__ -> ModalityUiUtil.invokeLaterIfNeeded(modality, ___ -> editor.isDisposed() || myCurrentModule.isDisposed(), () -> {
        try {
          importClass(myCurrentModule, editor, restoreReference(), myQualifiedClassName);
        }
        catch (IndexNotReadyException e) {
          Logger.getInstance(AddExtLibraryDependencyFix.class).info(e);
        }
      }));
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    if (myQualifiedClassName == null) {
      return new IntentionPreviewInfo.Html(
        HtmlChunk.text(
          JavaBundle.message("adds.ext.library.preview", myLibraryDescriptor.getPresentableName(), myCurrentModule.getName())));
    }
    else {
      return new IntentionPreviewInfo.Html(
        HtmlChunk.text(
          JavaBundle.message("adds.ext.library.preview.import", myLibraryDescriptor.getPresentableName(), myCurrentModule.getName(),
                             myQualifiedClassName)));
    }
  }
}
