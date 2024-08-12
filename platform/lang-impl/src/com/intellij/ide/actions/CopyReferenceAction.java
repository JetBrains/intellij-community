// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.dnd.FileCopyPasteUtil;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.util.Collections;
import java.util.List;

import static com.intellij.ide.actions.CopyReferenceUtil.*;

/**
 * @author Alexey
 */
public class CopyReferenceAction extends DumbAwareAction {
  public static final DataFlavor ourFlavor = FileCopyPasteUtil.createJvmDataFlavor(CopyReferenceFQNTransferable.class);

  public CopyReferenceAction() {
    super();
    setEnabledInModalContext(true);
    setInjectedContext(true);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    boolean plural = false;
    boolean enabled;
    boolean paths = false;

    DataContext dataContext = e.getDataContext();
    Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
    boolean fileWithDocument = editor != null && FileDocumentManager.getInstance().getFile(editor.getDocument()) != null;
    boolean calcQualifiedName = ActionPlaces.COPY_REFERENCE_POPUP.equals(e.getPlace());
    try {
      List<PsiElement> elements = !fileWithDocument || calcQualifiedName ? getPsiElements(dataContext, editor) : null;
      if (fileWithDocument) {
        enabled = true;
      }
      else {
        enabled = !elements.isEmpty();
        plural = elements.size() > 1;
        paths = ContainerUtil.and(elements, el -> el instanceof PsiFileSystemItem && getQualifiedNameFromProviders(el) == null);
      }
      if (calcQualifiedName) {
        e.getPresentation().putClientProperty(CopyPathProvider.QUALIFIED_NAME, getQualifiedName(editor, elements));
      }
    }
    catch (IndexNotReadyException ex) {
      enabled = false;
    }

    e.getPresentation().setEnabled(enabled);
    if (ActionPlaces.isPopupPlace(e.getPlace())) {
      e.getPresentation().setVisible(enabled);
    }
    else {
      e.getPresentation().setVisible(true);
    }
    e.getPresentation().setText(
      paths ? plural ? IdeBundle.message("copy.relative.paths") : IdeBundle.message("copy.relative.path")
            : plural ? IdeBundle.message("copy.references") : IdeBundle.message("copy.reference"));

    if (paths) {
      e.getPresentation().setEnabledAndVisible(false);
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  protected @NotNull List<PsiElement> getPsiElements(DataContext dataContext, Editor editor) {
    return getElementsToCopy(editor, dataContext);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    List<PsiElement> elements = getPsiElements(dataContext, editor);

    String copy = getQualifiedName(editor, elements);
    if (copy != null) {
      CopyPasteManager.getInstance().setContents(new CopyReferenceFQNTransferable(copy));
      setStatusBarText(project, IdeBundle.message("message.reference.to.fqn.has.been.copied", copy));
    } else if (editor != null && project != null) {
      Document document = editor.getDocument();
      PsiFile file = PsiDocumentManager.getInstance(project).getCachedPsiFile(document);
      if (file != null) {
        String toCopy = getFileFqn(file) + ":" + (editor.getCaretModel().getLogicalPosition().line + 1);
        CopyPasteManager.getInstance().setContents(new StringSelection(toCopy));
        setStatusBarText(project, LangBundle.message("status.bar.text.reference.has.been.copied", toCopy));
      }
      return;
    }

    highlight(editor, project, elements);
  }

  protected @NlsSafe String getQualifiedName(Editor editor, List<? extends PsiElement> elements) {
    return CopyReferenceUtil.doCopy(elements, editor);
  }

  public static boolean doCopy(final PsiElement element, final Project project) {
    return doCopy(Collections.singletonList(element), project);
  }

  private static boolean doCopy(List<? extends PsiElement> elements, @Nullable Project project) {
    String toCopy = CopyReferenceUtil.doCopy(elements, null);
    CopyPasteManager.getInstance().setContents(new CopyReferenceFQNTransferable(toCopy));
    setStatusBarText(project, IdeBundle.message("message.reference.to.fqn.has.been.copied", toCopy));

    return true;
  }

  public static @Nullable String elementToFqn(final @Nullable PsiElement element) {
    return FqnUtil.elementToFqn(element, null);
  }
}
