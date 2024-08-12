// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.PasteProvider;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.editor.actions.PasteAction;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.Producer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.Transferable;

public final class PasteReferenceProvider implements PasteProvider {
  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void performPaste(@NotNull DataContext dataContext) {
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    final Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
    if (project == null || editor == null) return;

    final String fqn = getCopiedFqn(dataContext);
    if (fqn == null) return;

    QualifiedNameProvider theProvider = null;
    PsiElement element = null;
    for(QualifiedNameProvider provider: QualifiedNameProvider.EP_NAME.getExtensionList()) {
      element = provider.qualifiedNameToElement(fqn, project);
      if (element != null) {
        theProvider = provider;
        break;
      }
    }

    if (theProvider != null) {
      insert(fqn, element, editor, theProvider);
    }
  }

  @Override
  public boolean isPastePossible(@NotNull DataContext dataContext) {
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    final Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
    return project != null && editor != null && getCopiedFqn(dataContext) != null;
  }

  @Override
  public boolean isPasteEnabled(@NotNull DataContext dataContext) {
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    String fqn = getCopiedFqn(dataContext);
    return project != null && fqn != null && QualifiedNameProviderUtil.qualifiedNameToElement(fqn, project) != null;
  }

  private static void insert(
    @NotNull String fqn,
    @NotNull PsiElement element,
    @NotNull Editor editor,
    @NotNull QualifiedNameProvider provider
  ) {
    final Project project = editor.getProject();
    if (project == null) return;

    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
    documentManager.commitDocument(editor.getDocument());

    final PsiFile file = documentManager.getPsiFile(editor.getDocument());
    if (!FileModificationService.getInstance().prepareFileForWrite(file)) return;

    CommandProcessor.getInstance().executeCommand(project, () -> ApplicationManager.getApplication().runWriteAction(() -> {
      Document document = editor.getDocument();
      documentManager.doPostponedOperationsAndUnblockDocument(document);
      documentManager.commitDocument(document);
      EditorModificationUtil.deleteSelectedText(editor);
      provider.insertQualifiedName(fqn, element, editor, project);
    }), IdeBundle.message("command.pasting.reference"), null);
  }

  private static @Nullable String getCopiedFqn(final DataContext context) {
    Producer<Transferable> producer = PasteAction.TRANSFERABLE_PROVIDER.getData(context);

    if (producer != null) {
      Transferable transferable = producer.produce();
      if (transferable != null) {
        try {
          return (String)transferable.getTransferData(CopyReferenceAction.ourFlavor);
        }
        catch (Exception ignored) { }
      }
      return null;
    }

    return CopyPasteManager.getInstance().getContents(CopyReferenceAction.ourFlavor);
  }
}
