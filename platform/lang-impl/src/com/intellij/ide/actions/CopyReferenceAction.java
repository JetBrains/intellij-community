/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/**
 * @author Alexey
 */
package com.intellij.ide.actions;

import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.codeInsight.highlighting.HighlightUsagesHandler;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.util.LogicalRoot;
import com.intellij.util.LogicalRootsManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;

public class CopyReferenceAction extends AnAction {
  public void update(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    boolean enabled = isEnabled(dataContext);
    e.getPresentation().setEnabled(enabled);
    if (ActionPlaces.isPopupPlace(e.getPlace())) {
      e.getPresentation().setVisible(enabled);
    }
    else {
      e.getPresentation().setVisible(true);
    }
  }

  private static boolean isEnabled(final DataContext dataContext) {
    Editor editor = PlatformDataKeys.EDITOR.getData(dataContext);
    PsiElement element = getElementToCopy(editor, dataContext);
    return elementToFqn(element) != null;
  }

  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    Editor editor = PlatformDataKeys.EDITOR.getData(dataContext);
    Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    PsiElement element = getElementToCopy(editor, dataContext);

    if (!doCopy(element, project)) return;
    HighlightManager highlightManager = HighlightManager.getInstance(project);
    EditorColorsManager manager = EditorColorsManager.getInstance();
    TextAttributes attributes = manager.getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
    if (editor != null) {
      PsiElement toHighlight = HighlightUsagesHandler.getNameIdentifier(element);
      if (toHighlight == null) toHighlight = element;
      highlightManager.addOccurrenceHighlights(editor, new PsiElement[]{toHighlight}, attributes, true, null);
    }
  }

  @Nullable
  private static PsiElement getElementToCopy(final Editor editor, final DataContext dataContext) {
    PsiElement element = null;
    if (editor != null) {
      PsiReference reference = TargetElementUtilBase.findReference(editor, editor.getCaretModel().getOffset());
      if (reference != null) {
        element = reference.getElement();
      }
    }

    if (element == null) {
      element = LangDataKeys.PSI_ELEMENT.getData(dataContext);
    }
    if (element instanceof PsiFile && !((PsiFile)element).getViewProvider().isPhysical()) {
      return null;
    }

    for(QualifiedNameProvider provider: Extensions.getExtensions(QualifiedNameProvider.EP_NAME)) {
      PsiElement adjustedElement = provider.adjustElementToCopy(element);
      if (adjustedElement != null) return adjustedElement;
    }
    return element;
  }

  public static boolean doCopy(final PsiElement element, final Project project) {
    String fqn = elementToFqn(element);
    if (fqn == null) return false;

    CopyPasteManager.getInstance().setContents(new MyTransferable(fqn));

    final StatusBarEx statusBar = (StatusBarEx)WindowManager.getInstance().getStatusBar(project);
    statusBar.setInfo(IdeBundle.message("message.reference.to.fqn.has.been.copied", fqn));
    return true;
  }

  private static DataFlavor ourFlavor;

  @Nullable
  static DataFlavor getFlavor() {
    if (ourFlavor != null) {
      return ourFlavor;
    }
    try {
      ourFlavor = new DataFlavor(DataFlavor.javaJVMLocalObjectMimeType + ";class=" + MyTransferable.class.getName());
    }
    catch (ClassNotFoundException e) {
      return null;
    }
    return ourFlavor;
  }


  private static class MyTransferable implements Transferable {
    private final String fqn;

    public MyTransferable(String fqn) {
      this.fqn = fqn;
    }

    public DataFlavor[] getTransferDataFlavors() {
      final DataFlavor flavor = getFlavor();
      if (flavor != null) {
        return new DataFlavor[]{flavor, DataFlavor.stringFlavor};
      }
      return new DataFlavor[] { DataFlavor.stringFlavor };
    }

    public boolean isDataFlavorSupported(DataFlavor flavor) {
      return flavor.equals(getFlavor()) || DataFlavor.stringFlavor.equals(flavor);
    }

    @Nullable
    public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
      if (!isDataFlavorSupported(flavor)) return null;
      return fqn;
    }

  }

  @Nullable
  private static String elementToFqn(final PsiElement element) {
    for(QualifiedNameProvider provider: Extensions.getExtensions(QualifiedNameProvider.EP_NAME)) {
      String result = provider.getQualifiedName(element);
      if (result != null) return result;
    }

    String fqn = null;
    if (element instanceof PsiFile) {
      final PsiFile file = (PsiFile)element;
      fqn = FileUtil.toSystemIndependentName(getFileFqn(file));
    }
    return fqn;
  }

  @NotNull
  private static String getFileFqn(final PsiFile file) {
    final VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null) {
      return file.getName();
    }
    final Project project = file.getProject();
    final LogicalRoot logicalRoot = LogicalRootsManager.getLogicalRootsManager(project).findLogicalRoot(virtualFile);
    if (logicalRoot != null) {
      String logical = FileUtil.toSystemIndependentName(VfsUtil.virtualToIoFile(logicalRoot.getVirtualFile()).getPath());
      String path = FileUtil.toSystemIndependentName(VfsUtil.virtualToIoFile(virtualFile).getPath());
      return "/" + FileUtil.getRelativePath(logical, path, '/');
    }

    final VirtualFile contentRoot = ProjectRootManager.getInstance(project).getFileIndex().getContentRootForFile(virtualFile);
    if (contentRoot != null) {
      return "/"+FileUtil.getRelativePath(VfsUtil.virtualToIoFile(contentRoot), VfsUtil.virtualToIoFile(virtualFile));
    }
    return virtualFile.getPath();
  }
}
