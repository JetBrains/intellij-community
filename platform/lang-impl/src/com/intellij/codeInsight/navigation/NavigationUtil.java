/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.ui.JBListWithHintProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author ven
 */
public final class NavigationUtil {

  private NavigationUtil() {
  }

  @NotNull
  public static JBPopup getPsiElementPopup(@NotNull PsiElement[] elements, String title) {
    return getPsiElementPopup(elements, new DefaultPsiElementCellRenderer(), title);
  }

  @NotNull
  public static JBPopup getPsiElementPopup(@NotNull PsiElement[] elements,
                                           @NotNull final PsiElementListCellRenderer<PsiElement> renderer,
                                           final String title) {
    return getPsiElementPopup(elements, renderer, title, new PsiElementProcessor<PsiElement>() {
      @Override
      public boolean execute(@NotNull final PsiElement element) {
        Navigatable descriptor = EditSourceUtil.getDescriptor(element);
        if (descriptor != null && descriptor.canNavigate()) {
          descriptor.navigate(true);
        }
        return true;
      }
    });
  }

  @NotNull
  public static <T extends PsiElement> JBPopup getPsiElementPopup(@NotNull T[] elements,
                                                                  @NotNull final PsiElementListCellRenderer<T> renderer,
                                                                  final String title,
                                                                  @NotNull final PsiElementProcessor<T> processor) {
    return getPsiElementPopup(elements, renderer, title, processor, null);
  }

  @NotNull
  public static <T extends PsiElement> JBPopup getPsiElementPopup(@NotNull T[] elements,
                                                                  @NotNull final PsiElementListCellRenderer<T> renderer,
                                                                  @Nullable final String title,
                                                                  @NotNull final PsiElementProcessor<T> processor,
                                                                  @Nullable final T selection) {
    final JList list = new JBListWithHintProvider(elements) {
      @Nullable
      @Override
      protected PsiElement getPsiElementForHint(Object selectedValue) {
        return (PsiElement)selectedValue;
      }
    };
    list.setCellRenderer(renderer);
    if (selection != null) {
      list.setSelectedValue(selection, true);
    }

    final Runnable runnable = new Runnable() {
      @Override
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

  public static boolean activateFileWithPsiElement(@NotNull PsiElement elt) {
    return activateFileWithPsiElement(elt, true);
  }

  public static boolean activateFileWithPsiElement(@NotNull PsiElement elt, boolean searchForOpen) {
    return openFileWithPsiElement(elt, searchForOpen, true);
  }

  public static boolean openFileWithPsiElement(PsiElement element, boolean searchForOpen, boolean requestFocus) {
    boolean openAsNative = false;
    if (element instanceof PsiFile) {
      VirtualFile virtualFile = ((PsiFile)element).getVirtualFile();
      if (virtualFile != null) {
        openAsNative = ElementBase.isNativeFileType(virtualFile.getFileType());
      }
    }

    if (searchForOpen) {
      element.putUserData(FileEditorManager.USE_CURRENT_WINDOW, null);
    }
    else {
      element.putUserData(FileEditorManager.USE_CURRENT_WINDOW, true);
    }

    if (openAsNative || !activatePsiElementIfOpen(element, searchForOpen, requestFocus)) {
      ((NavigationItem)element).navigate(requestFocus);
      return true;
    }

    element.putUserData(FileEditorManager.USE_CURRENT_WINDOW, null);
    return false;
  }


  private static boolean activatePsiElementIfOpen(@NotNull PsiElement elt, boolean searchForOpen, boolean requestFocus) {
    if (!elt.isValid()) return false;
    elt = elt.getNavigationElement();
    final PsiFile file = elt.getContainingFile();
    if (file == null || !file.isValid()) return false;

    VirtualFile vFile = file.getVirtualFile();
    if (vFile == null) return false;

    if (!EditorHistoryManager.getInstance(elt.getProject()).hasBeenOpen(vFile)) return false;

    final FileEditorManager fem = FileEditorManager.getInstance(elt.getProject());
    if (!fem.isFileOpen(vFile)) {
      fem.openFile(vFile, requestFocus, searchForOpen);
    }

    final TextRange range = elt.getTextRange();
    if (range == null) return false;

    final FileEditor[] editors = fem.getEditors(vFile);
    for (FileEditor editor : editors) {
      if (editor instanceof TextEditor) {
        final Editor text = ((TextEditor)editor).getEditor();
        final int offset = text.getCaretModel().getOffset();

        if (range.containsOffset(offset)) {
          // select the file
          fem.openFile(vFile, requestFocus, searchForOpen);
          return true;
        }
      }
    }

    return false;
  }
}
