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

package com.intellij.codeInsight.navigation;

import com.intellij.ide.util.DefaultPsiElementCellRenderer;
import com.intellij.ide.util.EditSourceUtil;
import com.intellij.ide.util.PsiElementListCellRenderer;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.ElementBase;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.ui.components.JBList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author ven
 */
public final class NavigationUtil {

  private NavigationUtil() {
  }

  public static JBPopup getPsiElementPopup(PsiElement[] elements, String title) {
    return getPsiElementPopup(elements, new DefaultPsiElementCellRenderer(), title);
  }

  public static JBPopup getPsiElementPopup(final PsiElement[] elements, final PsiElementListCellRenderer<PsiElement> renderer, final String title) {
    return getPsiElementPopup(elements, renderer, title, new PsiElementProcessor<PsiElement>() {
      public boolean execute(final PsiElement element) {
        Navigatable descriptor = EditSourceUtil.getDescriptor(element);
        if (descriptor != null && descriptor.canNavigate()) {
          descriptor.navigate(true);
        }
        return true;
      }
    });
  }

  public static <T extends PsiElement> JBPopup getPsiElementPopup(final T[] elements, final PsiElementListCellRenderer<T> renderer,
                                                                  final String title, final PsiElementProcessor<T> processor) {
    return getPsiElementPopup(elements, renderer, title, processor, null);
  }

  public static <T extends PsiElement> JBPopup getPsiElementPopup(final T[] elements,
                                                                  final PsiElementListCellRenderer<T> renderer,
                                                                  final String title,
                                                                  final PsiElementProcessor<T> processor,
                                                                  @Nullable final T selection) {
    final JList list = new JBList(elements);
    list.setCellRenderer(renderer);
    if (selection != null) {
      list.setSelectedValue(selection, true);
    }

    final Runnable runnable = new Runnable() {
      public void run() {
        int[] ids = list.getSelectedIndices();
        if (ids == null || ids.length == 0) return;
        for (Object element : list.getSelectedValues()) {
          if (element != null) {
            processor.execute((T)element);
          }
        }
      }
    };

    PopupChooserBuilder builder = new PopupChooserBuilder(list);
    if (title != null) {
      builder.setTitle(title);
    }
    renderer.installSpeedSearch(builder, true);

    return builder.setItemChoosenCallback(runnable).createPopup();
  }

  public static void activateFileWithPsiElement(@NotNull PsiElement elt) {
    activateFileWithPsiElement(elt, true);
  }

  public static void activateFileWithPsiElement(@NotNull PsiElement elt, boolean searchForOpen) {
    boolean openAsNative = false;
    if (elt instanceof PsiFile) {
      VirtualFile virtualFile = ((PsiFile)elt).getVirtualFile();
      if (virtualFile != null) {
        openAsNative = ElementBase.isNativeFileType(virtualFile.getFileType());
      }
    }

    if (searchForOpen) {
      elt.putUserData(FileEditorManager.USE_CURRENT_WINDOW, null);
    } else {
      elt.putUserData(FileEditorManager.USE_CURRENT_WINDOW, true);
    }

    if (openAsNative || !activatePsiElementIfOpen(elt, searchForOpen)) {
      ((NavigationItem)elt).navigate(true);
    }

    elt.putUserData(FileEditorManager.USE_CURRENT_WINDOW, null);
  }



  private static boolean activatePsiElementIfOpen(@NotNull PsiElement elt, boolean searchForOpen) {
    if (!elt.isValid()) return false;
    elt = elt.getNavigationElement();
    if (elt == null) return false;
    final PsiFile file = elt.getContainingFile();
    if (file == null || !file.isValid()) return false;

    VirtualFile vFile = file.getVirtualFile();
    if (vFile == null) return false;

    if (!EditorHistoryManager.getInstance(elt.getProject()).hasBeenOpen(vFile)) return false;

    final FileEditorManager fem = FileEditorManager.getInstance(elt.getProject());
    if (!fem.isFileOpen(vFile)) {
      fem.openFile(vFile, true, searchForOpen);
      return true;
    }

    final TextRange range = elt.getTextRange();
    if (range == null) return false;

    final FileEditor[] editors = fem.getEditors(vFile);
    for (FileEditor editor : editors) {
      if (editor instanceof TextEditor) {
        final Editor text = ((TextEditor)editor).getEditor();
        final int offset = text.getCaretModel().getOffset();

        if (range.contains(offset)) {
          fem.openFile(vFile, true, searchForOpen);
          return true;
        }
      }
    }

    return false;
  }
}
