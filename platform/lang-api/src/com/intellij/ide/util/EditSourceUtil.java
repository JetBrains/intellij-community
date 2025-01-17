// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util;

import com.intellij.ide.ui.UISettings;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.GeneratedSourcesFilter;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VFileProperty;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.ide.navigation.NavigationOptions;
import com.intellij.platform.ide.navigation.NavigationServiceKt;
import com.intellij.pom.Navigatable;
import com.intellij.pom.PomTargetPsiElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

public final class EditSourceUtil {
  private EditSourceUtil() { }

  public static @Nullable Navigatable getDescriptor(@NotNull PsiElement element) {
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
    if (UISettings.getInstance().getOpenInPreviewTabIfPossible() && Registry.is("editor.preview.tab.navigation")) {
      desc.setUsePreviewTab(true);
    }
    return desc;
  }

  @Internal
  public static PsiElement getNavigatableOriginalElement(@NotNull PsiElement element) {
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
    for (GeneratedSourcesFilter filter : GeneratedSourcesFilter.EP_NAME.getExtensionList()) {
      for (PsiElement originalElement: filter.getOriginalElements(element)) {
        PsiElement apply = processor.apply(originalElement);
        if (apply != null) return apply;
      }
    }
    return null;
  }

  public static boolean navigateToPsiElement(@NotNull PsiElement element) {
    Navigatable descriptor = getDescriptor(element);
    if (descriptor != null && descriptor.canNavigate()) {
      Project project = element.getProject();
      NavigationServiceKt.navigateBlocking(project, descriptor, NavigationOptions.requestFocus(), null);
    }
    return true;
  }
}
