// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl;

import com.intellij.injected.editor.InjectedFileChangesHandler;
import com.intellij.injected.editor.InjectionEditService;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.impl.ImaginaryEditor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtilBase;
import com.intellij.psi.impl.source.tree.injected.Place;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public final class InjectionEditServiceImpl implements InjectionEditService {
  @Override
  public @NotNull Disposable synchronizeWithFragment(@NotNull PsiFile injectedFile, @NotNull Document copyDocument) {
    Place shreds = InjectedLanguageUtilBase.getShreds(injectedFile);
    Project project = injectedFile.getProject();
    PsiLanguageInjectionHost host = Objects.requireNonNull(InjectedLanguageManager.getInstance(project).getInjectionHost(injectedFile));
    Editor editor = new ImaginaryEditor(project, host.getContainingFile().getViewProvider().getDocument());
    InjectedFileChangesHandler handler = QuickEditHandler.getHandler(injectedFile, editor, shreds, copyDocument);
    copyDocument.addDocumentListener(new DocumentListener() {
      @Override
      public void documentChanged(@NotNull DocumentEvent event) {
        handler.commitToOriginal(event);
      }
    });
    return handler;
  }
}
