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
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.codeInsight.highlighting.HighlightUsagesHandler;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.dnd.FileCopyPasteUtil;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileDocumentManager;
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
import com.intellij.util.ArrayUtil;
import com.intellij.util.LogicalRoot;
import com.intellij.util.LogicalRootsManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

/**
 * @author Alexey
 */
public class CopyReferenceAction extends AnAction {
  public static final DataFlavor ourFlavor;
  static {
    try {
      ourFlavor = FileCopyPasteUtil.createJvmDataFlavor(MyTransferable.class);
    }
    catch (Exception e) {
      // todo[r.sh] delete in IDEA 12
      final StringBuilder msg = new StringBuilder();
      final ClassLoader loader = CopyReferenceAction.class.getClassLoader();
      msg.append("loader=").append(loader);
      if (loader != null) {
        final URL url = loader.getResource("com/intellij/ide/actions/CopyReferenceAction.class");
        msg.append(" url=").append(url);
        if (url != null) {
          if ("jar".equals(url.getProtocol())) {
            String path = url.getFile();
            msg.append(" path=").append(path);
            if (path != null && !path.isEmpty()) {
              if (path.startsWith("file:") && path.length() > 5) path = path.substring(5);
              if (path.startsWith("//") && path.length() > 2) path = path.substring(2);
              final String[] parts = path.split("!/");
              if (parts.length == 2) {
                try {
                  final JarFile jar = new JarFile(parts[0]);
                  try {
                    msg.append(" jar=").append(jar);
                    final ZipEntry entry = jar.getEntry(parts[1].replace("CopyReferenceAction.class", "CopyReferenceAction$MyTransferable.class"));
                    msg.append(" entry=").append(entry);
                  }
                  finally {
                    jar.close();
                  }
                }
                catch (IOException e1) {
                  msg.append(" io=").append(e1.getMessage());
                  throw new RuntimeException(msg.toString(), e);
                }
              }
            }
          }
          else {
            final String path = url.getFile();
            msg.append(" path=").append(path);
            if (path != null && !path.isEmpty()) {
              final boolean exists = new File(path.replace("CopyReferenceAction.class", "CopyReferenceAction$MyTransferable.class")).exists();
              msg.append(" exists=").append(exists);
            }
          }
        }
      }
      throw new RuntimeException(msg.toString(), e);
    }
  }

  public CopyReferenceAction() {
    super();
    setEnabledInModalContext(true);
    setInjectedContext(true);
  }

  public void update(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    Editor editor = PlatformDataKeys.EDITOR.getData(dataContext);
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

  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    Editor editor = PlatformDataKeys.EDITOR.getData(dataContext);
    assert editor != null;
    Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    PsiElement element = getElementToCopy(editor, dataContext);

    if (!doCopy(element, project, editor)) {
      Document document = editor.getDocument();
      VirtualFile file = FileDocumentManager.getInstance().getFile(document);
      if (file != null) {
        String toCopy = FileUtil.toSystemDependentName(file.getPath()) + ":" + (editor.getCaretModel().getLogicalPosition().line + 1);
        CopyPasteManager.getInstance().setContents(new StringSelection(toCopy));
        setStatusBarText(project, toCopy + " has been copied");
      }
      return;
    }

    HighlightManager highlightManager = HighlightManager.getInstance(project);
    EditorColorsManager manager = EditorColorsManager.getInstance();
    TextAttributes attributes = manager.getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
    if (element != null) {
      PsiElement nameIdentifier = HighlightUsagesHandler.getNameIdentifier(element);
      if (nameIdentifier != null) {
        highlightManager.addOccurrenceHighlights(editor, new PsiElement[]{nameIdentifier}, attributes, true, null);
      } else {
        PsiReference reference = TargetElementUtilBase.findReference(editor, editor.getCaretModel().getOffset());
        if (reference != null) {
          highlightManager.addOccurrenceHighlights(editor, new PsiReference[]{reference}, attributes, true, null);
        } else {
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
      element = LangDataKeys.PSI_ELEMENT.getData(dataContext);
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

    public DataFlavor[] getTransferDataFlavors() {
      return new DataFlavor[]{ourFlavor, DataFlavor.stringFlavor};
    }

    public boolean isDataFlavorSupported(DataFlavor flavor) {
      return ArrayUtil.find(getTransferDataFlavors(), flavor) != -1;
    }

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

    String fqn = null;
    if (element instanceof PsiFile) {
      final PsiFile file = (PsiFile)element;
      fqn = FileUtil.toSystemIndependentName(getFileFqn(file));
    }
    return fqn;
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
      return "/" + FileUtil.getRelativePath(VfsUtil.virtualToIoFile(contentRoot), VfsUtil.virtualToIoFile(virtualFile));
    }
    return virtualFile.getPath();
  }
}
