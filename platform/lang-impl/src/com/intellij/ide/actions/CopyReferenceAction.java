/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.ide.actions;

import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.codeInsight.daemon.impl.IdentifierUtil;
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.dnd.FileCopyPasteUtil;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.psi.*;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.LogicalRoot;
import com.intellij.util.LogicalRootsManager;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;

/**
 * @author Alexey
 */
public class CopyReferenceAction extends DumbAwareAction {
  public static final DataFlavor ourFlavor = FileCopyPasteUtil.createJvmDataFlavor(MyTransferable.class);

  public CopyReferenceAction() {
    super();
    setEnabledInModalContext(true);
    setInjectedContext(true);
  }

  @Override
  public void update(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
    boolean enabled = (editor != null && FileDocumentManager.getInstance().getFile(editor.getDocument()) != null) ||
                      getElementToCopy(editor, dataContext) != null;
    e.getPresentation().setEnabled(enabled);
    if (ActionPlaces.isPopupPlace(e.getPlace())) {
      e.getPresentation().setVisible(enabled);
    }
    else {
      e.getPresentation().setVisible(true);
    }
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    PsiElement element = getElementToCopy(editor, dataContext);

    if (!doCopy(element, project, editor) && editor != null && project != null) {
      Document document = editor.getDocument();
      PsiFile file = PsiDocumentManager.getInstance(project).getCachedPsiFile(document);
      if (file != null) {
        String toCopy = getFileFqn(file) + ":" + (editor.getCaretModel().getLogicalPosition().line + 1);
        CopyPasteManager.getInstance().setContents(new StringSelection(toCopy));
        setStatusBarText(project, toCopy + " has been copied");
      }
      return;
    }

    HighlightManager highlightManager = HighlightManager.getInstance(project);
    EditorColorsManager manager = EditorColorsManager.getInstance();
    TextAttributes attributes = manager.getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
    if (element != null && editor != null && project != null) {
      PsiElement nameIdentifier = IdentifierUtil.getNameIdentifier(element);
      if (nameIdentifier != null) {
        highlightManager.addOccurrenceHighlights(editor, new PsiElement[]{nameIdentifier}, attributes, true, null);
      } else {
        PsiReference reference = TargetElementUtilBase.findReference(editor, editor.getCaretModel().getOffset());
        if (reference != null) {
          highlightManager.addOccurrenceHighlights(editor, new PsiReference[]{reference}, attributes, true, null);
        } else if (element != PsiDocumentManager.getInstance(project).getCachedPsiFile(editor.getDocument())) {
          highlightManager.addOccurrenceHighlights(editor, new PsiElement[]{element}, attributes, true, null);
        }
      }
    }
  }

  @Nullable
  private static PsiElement getElementToCopy(@Nullable final Editor editor, final DataContext dataContext) {
    PsiElement element = null;
    if (editor != null) {
      PsiReference reference = TargetElementUtilBase.findReference(editor);
      if (reference != null) {
        element = reference.getElement();
      }
    }

    if (element == null) {
      element = CommonDataKeys.PSI_ELEMENT.getData(dataContext);
    }
    if (element == null && editor == null) {
      VirtualFile virtualFile = CommonDataKeys.VIRTUAL_FILE.getData(dataContext);
      Project project = CommonDataKeys.PROJECT.getData(dataContext);
      if (virtualFile != null && project != null) {
        element = PsiManager.getInstance(project).findFile(virtualFile);
      }
    }
    if (element instanceof PsiFile && !((PsiFile)element).getViewProvider().isPhysical()) {
      return null;
    }

    for (QualifiedNameProvider provider : Extensions.getExtensions(QualifiedNameProvider.EP_NAME)) {
      PsiElement adjustedElement = provider.adjustElementToCopy(element);
      if (adjustedElement != null) return adjustedElement;
    }
    return element;
  }

  public static boolean doCopy(final PsiElement element, final Project project) {
    return doCopy(element, project, null);
  }

  private static boolean doCopy(final PsiElement element, @Nullable final Project project, @Nullable Editor editor) {
    String fqn = elementToFqn(element, editor);
    if (fqn == null) return false;

    CopyPasteManager.getInstance().setContents(new MyTransferable(fqn));

    setStatusBarText(project, IdeBundle.message("message.reference.to.fqn.has.been.copied", fqn));
    return true;
  }

  private static void setStatusBarText(Project project, String message) {
    if (project != null) {
      final StatusBarEx statusBar = (StatusBarEx)WindowManager.getInstance().getStatusBar(project);
      if (statusBar != null) {
        statusBar.setInfo(message);
      }
    }
  }

  private static class MyTransferable implements Transferable {
    private final String fqn;

    public MyTransferable(String fqn) {
      this.fqn = fqn;
    }

    @Override
    public DataFlavor[] getTransferDataFlavors() {
      return new DataFlavor[]{ourFlavor, DataFlavor.stringFlavor};
    }

    @Override
    public boolean isDataFlavorSupported(DataFlavor flavor) {
      return ArrayUtilRt.find(getTransferDataFlavors(), flavor) != -1;
    }

    @Override
    @Nullable
    public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
      if (isDataFlavorSupported(flavor)) {
        return fqn;
      }
      return null;
    }
  }

  @Nullable
  public static String elementToFqn(final PsiElement element) {
    return elementToFqn(element, null);
  }

  @Nullable
  public static String elementToFqn(final PsiElement element, @Nullable Editor editor) {
    String result = getQualifiedNameFromProviders(element);
    if (result != null) return result;

    if (editor != null) { //IDEA-70346
      PsiReference reference = TargetElementUtilBase.findReference(editor, editor.getCaretModel().getOffset());
      if (reference != null) {
        result = getQualifiedNameFromProviders(reference.resolve());
        if (result != null) return result;
      }
    }

    if (element instanceof PsiFile) {
      return FileUtil.toSystemIndependentName(getFileFqn((PsiFile)element));
    }
    if (element instanceof PsiDirectory) {
      return FileUtil.toSystemIndependentName(getVirtualFileFqn(((PsiDirectory)element).getVirtualFile(), element.getProject()));
    }

    return null;
  }

  @Nullable
  private static String getQualifiedNameFromProviders(@Nullable PsiElement element) {
    if (element == null) return null;
    for (QualifiedNameProvider provider : Extensions.getExtensions(QualifiedNameProvider.EP_NAME)) {
      String result = provider.getQualifiedName(element);
      if (result != null) return result;
    }
    return null;
  }

  @NotNull
  private static String getFileFqn(final PsiFile file) {
    final VirtualFile virtualFile = file.getVirtualFile();
    return virtualFile == null ? file.getName() : getVirtualFileFqn(virtualFile, file.getProject());
  }

  private static String getVirtualFileFqn(@NotNull VirtualFile virtualFile, @NotNull Project project) {
    final LogicalRoot logicalRoot = LogicalRootsManager.getLogicalRootsManager(project).findLogicalRoot(virtualFile);
    if (logicalRoot != null && logicalRoot.getVirtualFile() != null) {
      return ObjectUtils.assertNotNull(VfsUtilCore.getRelativePath(virtualFile, logicalRoot.getVirtualFile(), '/'));
    }

    final VirtualFile contentRoot = ProjectRootManager.getInstance(project).getFileIndex().getContentRootForFile(virtualFile);
    if (contentRoot != null) {
      return ObjectUtils.assertNotNull(VfsUtilCore.getRelativePath(virtualFile, contentRoot, '/'));
    }
    return virtualFile.getPath();
  }
}
