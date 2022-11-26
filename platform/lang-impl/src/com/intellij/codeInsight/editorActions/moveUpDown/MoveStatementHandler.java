// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.editorActions.moveUpDown;

import com.intellij.lang.ASTNode;
import com.intellij.lang.DependentLanguage;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class MoveStatementHandler extends BaseMoveHandler {

  MoveStatementHandler(boolean down) {
    super(down);
  }

  @Override
  public void executeWriteAction(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
    super.executeWriteAction(editor, caret, dataContext);

    if (editor.getCaretModel().getPrimaryCaret() == caret) {
      editor.getScrollingModel().scrollToCaret(ScrollType.CENTER);
    }
  }

  @Override
  @Nullable
  protected MoverWrapper getSuitableMover(@NotNull final Editor editor, @Nullable final PsiFile file) {
    if (file == null) return null;
    // order is important!
    final StatementUpDownMover.MoveInfo info = new StatementUpDownMover.MoveInfo();
    for (final StatementUpDownMover mover : StatementUpDownMover.STATEMENT_UP_DOWN_MOVER_EP.getExtensionList()) {
      if (mover.checkAvailable(editor, file, info, isDown)) {
        return new MoverWrapper(mover, info, isDown);
      }
    }

    // order is important
    //Mover[] movers = new Mover[]{new StatementMover(isDown), new DeclarationMover(isDown), new XmlMover(isDown), new LineMover(isDown)};
    return null;
  }

  @Override
  @Nullable
  protected PsiFile getPsiFile(@NotNull Project project, @NotNull Editor editor) {
    final Document document = editor.getDocument();
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
    documentManager.commitDocument(document);
    return getRoot(documentManager.getPsiFile(document), editor);
  }

  @Nullable
  private static PsiFile getRoot(final PsiFile file, final Editor editor) {
    if (file == null) return null;
    int offset = editor.getCaretModel().getOffset();
    if (offset == editor.getDocument().getTextLength()) offset--;
    if (offset < 0) return null;
    PsiElement leafElement = file.findElementAt(offset);
    if (leafElement == null) return null;
    if (leafElement.getLanguage() instanceof DependentLanguage) {
      leafElement = file.getViewProvider().findElementAt(offset, file.getViewProvider().getBaseLanguage());
      if (leafElement == null) return null;
    }
    ASTNode node = leafElement.getNode();
    if (node == null) return null;
    return (PsiFile)PsiUtilBase.getRoot(node).getPsi();
  }
}

