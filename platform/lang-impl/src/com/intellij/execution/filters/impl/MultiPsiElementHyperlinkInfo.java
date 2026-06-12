// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.filters.impl;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.filters.HyperlinkInfoBase;
import com.intellij.execution.filters.OpenFileHyperlinkInfo;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.gotoByName.GotoFileCellRenderer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.BinaryFileTypeDecompilers;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.EDT;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JFrame;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.intellij.openapi.actionSystem.ex.ActionUtil.underModalProgress;

@ApiStatus.Internal
public final class MultiPsiElementHyperlinkInfo extends HyperlinkInfoBase {
  private final Map<VirtualFile, SmartPsiElementPointer<?>> myMap;

  public Collection<SmartPsiElementPointer<?>> getElementVariants() {
    return myMap.values();
  }

  MultiPsiElementHyperlinkInfo(Collection<? extends PsiElement> elements) {
    SmartPointerManager manager = null;
    myMap = new LinkedHashMap<>();
    for (PsiElement element : elements) {
      if (manager == null) {
        manager = SmartPointerManager.getInstance(element.getProject());
      }
      myMap.put(element.getContainingFile().getVirtualFile(), manager.createSmartPsiElementPointer(element));
    }
  }

  @Override
  public void navigate(@NotNull Project project, @Nullable RelativePoint hyperlinkLocationPoint) {
    if (myMap.isEmpty()) return;
    if (myMap.size() == 1) {
      Map.Entry<VirtualFile, SmartPsiElementPointer<?>> entry = myMap.entrySet().iterator().next();
      navigateTo(project, entry.getKey(), entry.getValue());
      return;
    }
    JFrame frame = WindowManager.getInstance().getFrame(project);
    int width = frame != null ? frame.getSize().width : 200;
    JBPopup popup = JBPopupFactory.getInstance()
      .createPopupChooserBuilder(ContainerUtil.map(myMap.values(), ptr -> ptr.getContainingFile()))
      .setTitle(ExecutionBundle.message("popup.title.choose.target.file"))
      .setRenderer(new GotoFileCellRenderer(width))
      .setItemChosenCallback(selectedValue -> {
        VirtualFile file = selectedValue.getVirtualFile();
        navigateTo(project, file, myMap.get(file));
      })
      .createPopup();
    if (hyperlinkLocationPoint != null) {
      popup.show(hyperlinkLocationPoint);
    }
    else {
      popup.showInFocusCenter();
    }
  }

  private static void navigateTo(@NotNull Project project,
                                 VirtualFile file,
                                 SmartPsiElementPointer<?> pointer) {
    Document document = loadDocument(project, file);
    int line = 0, column = 0;
    PsiElement element = pointer.getElement();
    if (element != null && document != null) {
      int offset = element.getTextOffset();
      line = document.getLineNumber(offset);
      column = offset - document.getLineStartOffset(line);
    }
    new OpenFileHyperlinkInfo(project, file, line, column).navigate(project);
  }

  static @Nullable Document loadDocument(@NotNull Project project, @NotNull VirtualFile file) {
    // Loading the document may trigger a slow operation (e.g. decompiling a .class file),
    // which must not run on EDT
    if (Registry.is("hyperlink.ide.decompiler.open.file") &&
        BinaryFileTypeDecompilers.getInstance().hasDecompiler(file) &&
        EDT.isCurrentThreadEdt() &&
        !ApplicationManager.getApplication().isWriteAccessAllowed()) {
      return underModalProgress(project, IdeBundle.message("progress.title.preparing.navigation"),
                                () -> ReadAction.computeCancellable(() -> FileDocumentManager.getInstance().getDocument(file, project)));
    }
    return FileDocumentManager.getInstance().getDocument(file, project);
  }
}
  
