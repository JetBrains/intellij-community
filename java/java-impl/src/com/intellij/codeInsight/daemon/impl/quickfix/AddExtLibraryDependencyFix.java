// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.actions.AddImportAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
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
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class AddExtLibraryDependencyFix extends OrderEntryFix {
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

  @Override
  public @Nls @NotNull String getText() {
    return QuickFixBundle.message("add.0.to.classpath", myLibraryDescriptor.getPresentableName());
  }

  @Override
  public @Nls @NotNull String getFamilyName() {
    return getText();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, @Nullable Editor editor, PsiFile psiFile) {
    return !project.isDisposed() && !myCurrentModule.isDisposed();
  }

  @Override
  public void invoke(@NotNull Project project, @Nullable Editor editor, PsiFile psiFile) throws IncorrectOperationException {
    ModalityState modality = ModalityState.defaultModalityState();
    JavaProjectModelModificationService.getInstance(project)
      .addDependency(myCurrentModule, myLibraryDescriptor, myScope)
      .onSuccess(__ -> {
        if (editor == null) return;
        ReadAction.nonBlocking(() -> {
            PsiReference reference = restoreReference();
            if (myQualifiedClassName == null || reference == null) return null;
            try {
              return AddImportAction.create(editor, myCurrentModule, reference, myQualifiedClassName);
            }
            catch (IndexNotReadyException e) {
              Logger.getInstance(AddExtLibraryDependencyFix.class).info(e);
              return null;
            }
          })
          .expireWhen(() -> editor.isDisposed() || myCurrentModule.isDisposed())
          .finishOnUiThread(modality, action -> {
            if (action != null) {
              action.execute();
            }
          })
          .submit(AppExecutorUtil.getAppExecutorService());
      });
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile) {
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
