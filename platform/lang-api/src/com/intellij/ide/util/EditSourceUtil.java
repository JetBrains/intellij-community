/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.ide.util;

import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.roots.GeneratedSourcesFilter;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.vfs.VFileProperty;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.pom.PomTargetPsiElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

public class EditSourceUtil {
  private EditSourceUtil() { }

  @Nullable
  public static Navigatable getDescriptor(@NotNull PsiElement element) {
    PsiElement original = getNavigatableOriginalElement(element);
    if (original != null) {
      element = original;
    }
    else if (!canNavigate(element)) {
      return null;
    }
    if (element instanceof PomTargetPsiElement) {
      return ((PomTargetPsiElement)element).getTarget();
    }
    final PsiElement navigationElement = element.getNavigationElement();
    if (navigationElement instanceof PomTargetPsiElement) {
      return ((PomTargetPsiElement)navigationElement).getTarget();
    }
    final int offset = navigationElement instanceof PsiFile ? -1 : navigationElement.getTextOffset();
    final VirtualFile virtualFile = PsiUtilCore.getVirtualFile(navigationElement);
    if (virtualFile == null || !virtualFile.isValid()) {
      return null;
    }
    OpenFileDescriptor desc = new OpenFileDescriptor(navigationElement.getProject(), virtualFile, offset);
    desc.setUseCurrentWindow(FileEditorManager.USE_CURRENT_WINDOW.isIn(navigationElement));
    return desc;
  }

  private static PsiElement getNavigatableOriginalElement(@NotNull PsiElement element) {
    return processAllOriginalElements(element, original -> canNavigate(original) ? original : null);
  }

  public static boolean canNavigate(PsiElement element) {
    if (element == null || !element.isValid()) {
      return false;
    }

    VirtualFile file = PsiUtilCore.getVirtualFile(element.getNavigationElement());
    return file != null && file.isValid() && !file.is(VFileProperty.SPECIAL) && !VfsUtilCore.isBrokenLink(file);
  }

  public static void navigate(@NotNull NavigationItem item, boolean requestFocus, boolean useCurrentWindow) {
    if (item instanceof UserDataHolder) {
      ((UserDataHolder)item).putUserData(FileEditorManager.USE_CURRENT_WINDOW, useCurrentWindow);
    }
    item.navigate(requestFocus);
    if (item instanceof UserDataHolder) {
      ((UserDataHolder)item).putUserData(FileEditorManager.USE_CURRENT_WINDOW, null);
    }
  }

  /**
   * Collect original elements from all filters.
   */
  private static PsiElement processAllOriginalElements(@NotNull PsiElement element, @NotNull Function<? super PsiElement, ? extends PsiElement> processor) {
    for (GeneratedSourcesFilter filter : GeneratedSourcesFilter.EP_NAME.getExtensions()) {
      for (PsiElement originalElement: filter.getOriginalElements(element)) {
        PsiElement apply = processor.apply(originalElement);
        if (apply != null) return apply;
      }
    }
    return null;
  }
}
