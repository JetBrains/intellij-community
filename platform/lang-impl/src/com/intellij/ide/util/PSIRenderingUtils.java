// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util;

import com.intellij.ide.util.gotoByName.GotoFileCellRenderer;
import com.intellij.navigation.ColoredItemPresentation;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.navigation.PsiElementNavigationItem;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFilePathWrapper;
import com.intellij.openapi.vfs.newvfs.VfsPresentationUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.File;
import java.util.LinkedList;
import java.util.Optional;

public final class PSIRenderingUtils {

  private PSIRenderingUtils() {}

  public static PsiElement getPsiElement(Object o) {
    return o instanceof PsiElement ? (PsiElement)o :
           o instanceof PsiElementNavigationItem ? ((PsiElementNavigationItem)o).getTargetElement() : null;
  }

  public static @Nullable TextAttributes getNavigationItemAttributesStatic(Object value) {
    TextAttributes attributes = null;

    if (value instanceof NavigationItem) {
      TextAttributesKey attributesKey = null;
      final ItemPresentation presentation = ((NavigationItem)value).getPresentation();
      if (presentation instanceof ColoredItemPresentation) attributesKey = ((ColoredItemPresentation) presentation).getTextAttributesKey();

      if (attributesKey != null) {
        attributes = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(attributesKey);
      }
    }
    return attributes;
  }

  @Contract("!null, _, _ -> !null")
  public static @Nullable String cutContainerText(@Nullable String text, int maxWidth, FontMetrics fm) {
    if (text == null) return null;

    if (text.startsWith("(") && text.endsWith(")")) {
      text = text.substring(1, text.length() - 1);
    }

    if (maxWidth < 0) return text;

    boolean in = text.startsWith("in ");
    if (in) text = text.substring(3);
    String left = in ? "in " : "";
    String adjustedText = left + text;

    int fullWidth = fm.stringWidth(adjustedText);
    if (fullWidth < maxWidth) return adjustedText;

    String separator = text.contains("/") ? "/" :
                       SystemInfo.isWindows && text.contains("\\") ? "\\" :
                       text.contains(".") ? "." :
                       text.contains("-") ? "-" : " ";
    LinkedList<String> parts = new LinkedList<>(StringUtil.split(text, separator));
    int index;
    while (parts.size() > 1) {
      index = parts.size() / 2 - 1;
      parts.remove(index);
      if (fm.stringWidth(left + StringUtil.join(parts, separator) + "...") < maxWidth) {
        parts.add(index, "...");
        return left + StringUtil.join(parts, separator);
      }
    }
    int adjustedWidth = Math.max(adjustedText.length() * maxWidth / fullWidth - 1, left.length() + 3);
    return StringUtil.trimMiddle(adjustedText, adjustedWidth);
  }

  public static @NotNull String normalizePsiElementContainerText(PsiElement element, String text, String presentablePath) {
    if (text.startsWith("(") && text.endsWith(")")) {
      text = text.substring(1, text.length() - 1);
    }

    if (presentablePath == null && (text.contains("/") || text.contains(File.separator)) && element instanceof PsiFileSystemItem) {
      Project project = element.getProject();
      String basePath = Optional.ofNullable(project.getBasePath())
        .map(FileUtil::toSystemDependentName)
        .orElse(null);
      VirtualFile file = ((PsiFileSystemItem)element).getVirtualFile();
      if (file != null) {
        text = FileUtil.toSystemDependentName(text);
        String filePath = FileUtil.toSystemDependentName(file.getPath());
        if (basePath != null && FileUtil.isAncestor(basePath, filePath, true)) {
          text = ObjectUtils.notNull(FileUtil.getRelativePath(basePath, text, File.separatorChar), text);
        }
        else {
          String rootPath = Optional.ofNullable(GotoFileCellRenderer.getAnyRoot(file, project))
            .map(root -> FileUtil.toSystemDependentName(root.getPath()))
            .filter(root -> basePath != null && FileUtil.isAncestor(basePath, root, true))
            .orElse(null);
          text = rootPath != null
                 ? ObjectUtils.notNull(FileUtil.getRelativePath(rootPath, text, File.separatorChar), text)
                 : FileUtil.getLocationRelativeToUserHome(text);
        }
      }
    }
    return text;
  }

  public static @NotNull String getPSIElementText(PsiElement element) {
    VirtualFile file = element instanceof PsiFile ? PsiUtilCore.getVirtualFile(element) :
                       element instanceof VirtualFile ? (VirtualFile)element : null;
    if (file != null) {
      return VfsPresentationUtil.getPresentableNameForUI(element.getProject(), file);
    }

    if (element instanceof NavigationItem) {
      String name = Optional.ofNullable(((NavigationItem)element).getPresentation())
        .map(presentation -> presentation.getPresentableText())
        .orElse(null);
      if (name != null) return name;
    }

    String name = element instanceof PsiNamedElement ? ((PsiNamedElement)element).getName() : null;
    return StringUtil.notNullize(name, "<unnamed>");
  }

  public static @Nullable String extractPresentablePath(@Nullable PsiElement element) {
    if (element == null) return null;

    PsiFile file = element.getContainingFile();
    if (file != null) {
      VirtualFile virtualFile = file.getVirtualFile();
      if (virtualFile instanceof VirtualFilePathWrapper) return ((VirtualFilePathWrapper)virtualFile).getPresentablePath();
    }

    return null;
  }
}
