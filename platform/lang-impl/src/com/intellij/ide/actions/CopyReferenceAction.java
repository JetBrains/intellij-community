// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.copyReference.CopyReferenceTarget;
import com.intellij.ide.actions.copyReference.CopyReferenceTargetsDataRule;
import com.intellij.ide.actions.copyReference.CopyReferenceTransferable;
import com.intellij.ide.dnd.FileCopyPasteUtil;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.WeakReferenceDisposableWrapper;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.platform.backend.presentation.TargetPresentation;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.SequencedMap;
import java.util.function.Consumer;

import static com.intellij.ide.actions.CopyReferenceUtil.getElementsToCopy;
import static com.intellij.ide.actions.CopyReferenceUtil.highlight;
import static com.intellij.ide.actions.CopyReferenceUtil.setStatusBarText;
import static com.intellij.openapi.util.text.StringUtil.ELLIPSIS;
import static com.intellij.ui.list.TargetPopup.createTargetPopup;
import static com.intellij.util.ObjectUtils.coalesce;
import static com.intellij.util.containers.ContainerUtil.and;
import static com.intellij.util.containers.ContainerUtil.getOnlyItem;

/**
 * @author Alexey
 */
public class CopyReferenceAction extends DumbAwareAction {
  public static final DataFlavor ourFlavor = FileCopyPasteUtil.createJvmDataFlavor(CopyReferenceFQNTransferable.class);

  /**
   * @see CopyReferenceTargetsDataRule
   */
  @ApiStatus.Internal
  public static final DataKey<Collection<CopyReferenceTarget>> COPY_REFERENCE_TARGETS = DataKey.create("copy.reference.targets");

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
      Collection<CopyReferenceTarget> targets =
        !fileWithDocument || calcQualifiedName ? coalesce(COPY_REFERENCE_TARGETS.getData(dataContext), Collections.emptyList()) : null;
      List<PsiElement> elements = !fileWithDocument || calcQualifiedName ? getPsiElements(dataContext, editor) : null;
      if (fileWithDocument) {
        enabled = true;
      }
      else {
        enabled = !targets.isEmpty() || !elements.isEmpty();
        plural = targets.isEmpty() && elements.size() > 1;
        paths =
          targets.isEmpty() && and(elements, el -> el instanceof PsiFileSystemItem && FqnUtil.getQualifiedNameFromProviders(el) == null);
      }

      if (calcQualifiedName) {
        final String reference = !targets.isEmpty() ? targets.iterator().next().getReference() : getQualifiedName(editor, elements);
        if (targets.size() > 1) {
          e.getPresentation().putClientProperty(CopyPathProvider.QUALIFIED_NAME, "%s, %s".formatted(reference, ELLIPSIS));
        }
        else {
          e.getPresentation().putClientProperty(CopyPathProvider.QUALIFIED_NAME, reference);
        }
      }
    }
    catch (IndexNotReadyException ex) {
      enabled = false;
    }

    e.getPresentation().setEnabled(enabled);
    if (e.isFromContextMenu()) {
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

  protected @Unmodifiable @NotNull List<PsiElement> getPsiElements(DataContext dataContext, Editor editor) {
    return getElementsToCopy(editor, dataContext);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
    Project project = CommonDataKeys.PROJECT.getData(dataContext);

    if (project == null) {
      return;
    }

    ReadAction.nonBlocking(() -> {
      try {
        SequencedMap<CopyReferenceTransferable, TargetPresentation> transferables = new LinkedHashMap<>();
        for (CopyReferenceTarget target : coalesce(COPY_REFERENCE_TARGETS.getData(dataContext),
                                                   Collections.<CopyReferenceTarget>emptyList())) {
          transferables.put(new CopyReferenceTransferable(target), target.presentation());
        }
        return transferables;
      }
      catch (IndexNotReadyException _) {
        // Ignored
      }

      return Collections.<CopyReferenceTransferable, TargetPresentation>emptyMap();
    }).expireWith(project).finishOnUiThread(ModalityState.defaultModalityState(), transferables -> {
      if (!transferables.isEmpty()) {
        Consumer<CopyReferenceTransferable> action = transferable -> {
          TargetPresentation presentation = transferables.get(transferable);

          Disposer.register(project, new WeakReferenceDisposableWrapper(transferable));

          CopyPasteManager.getInstance().setContents(transferable);
          setStatusBarText(project, IdeBundle.message("message.reference.to.fqn.has.been.copied", presentation.getPresentableText()));
        };

        if (transferables.size() == 1) {
          action.accept(getOnlyItem(transferables.keySet()));
        }
        else {
          String title = IdeBundle.message("copy.reference.popup.title");
          JBPopup popup = createTargetPopup(title, List.copyOf(transferables.keySet()), transferables::get, action);

          if (editor != null) {
            popup.showInBestPositionFor(editor);
          }
          else {
            popup.showInBestPositionFor(dataContext);
          }
        }
      }
      else {
        List<PsiElement> elements = getPsiElements(dataContext, editor);

        String copy = getQualifiedName(editor, elements);
        if (copy != null) {
          CopyPasteManager.getInstance().setContents(new CopyReferenceFQNTransferable(copy));
          setStatusBarText(project, IdeBundle.message("message.reference.to.fqn.has.been.copied", copy));
        }
        else if (editor != null) {
          Document document = editor.getDocument();
          PsiFile file = PsiDocumentManager.getInstance(project).getCachedPsiFile(document);
          if (file != null) {
            String toCopy = FqnUtil.getFileFqn(file) + ":" + (editor.getCaretModel().getLogicalPosition().line + 1);
            CopyPasteManager.getInstance().setContents(new StringSelection(toCopy));
            setStatusBarText(project, LangBundle.message("status.bar.text.reference.has.been.copied", toCopy));
          }
          return;
        }

        highlight(editor, project, elements);
      }
    }).submit(AppExecutorUtil.getAppExecutorService());
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
