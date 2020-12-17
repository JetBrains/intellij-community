// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.ide.ui.LafManagerListener;
import com.intellij.ide.util.PlatformModuleRendererFactory;
import com.intellij.ide.util.PsiElementListCellRenderer;
import com.intellij.ide.util.gotoByName.GotoFileCellRenderer;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
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
import com.intellij.psi.presentation.java.SymbolPresentationUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.LinkedList;
import java.util.Optional;

/**
* @author Konstantin Bulenkov
*/
public class SearchEverywherePsiRenderer extends PsiElementListCellRenderer<PsiElement> {
  private EditorColorsScheme scheme = EditorColorsManager.getInstance().getSchemeForCurrentUITheme();

  public SearchEverywherePsiRenderer(Disposable parent) {
    setFocusBorderEnabled(false);
    setLayout(new BorderLayout() {
      @Override
      public void layoutContainer(Container target) {
        super.layoutContainer(target);
        final Component right = getLayoutComponent(EAST);
        final Component left = getLayoutComponent(WEST);

        //IDEA-140824
        if (right != null && left != null && left.getBounds().x + left.getBounds().width > right.getBounds().x) {
          final Rectangle bounds = right.getBounds();
          final int newX = left.getBounds().x + left.getBounds().width;
          right.setBounds(newX, bounds.y, bounds.width - (newX - bounds.x), bounds.height);
        }
      }
    });

    ApplicationManager.getApplication().getMessageBus().connect(parent).subscribe(LafManagerListener.TOPIC, __ -> {
      scheme = EditorColorsManager.getInstance().getSchemeForCurrentUITheme();
    });
  }

  @Override
  protected @NotNull SimpleTextAttributes getErrorAttributes() {
    SimpleTextAttributes schemeAttributes = SimpleTextAttributes.fromTextAttributes(scheme.getAttributes(CodeInsightColors.ERRORS_ATTRIBUTES));
    return new SimpleTextAttributes(schemeAttributes.getBgColor(), UIUtil.getInactiveTextColor(), schemeAttributes.getWaveColor(),
                                    schemeAttributes.getStyle() | SimpleTextAttributes.STYLE_USE_EFFECT_COLOR);
  }

  @Override
  public String getElementText(PsiElement element) {
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

  @Nullable
  @Override
  protected String getContainerText(PsiElement element, String name) {
    return getContainerTextForLeftComponent(element, name, -1, null);
  }

  @Nullable
  @Override
  protected String getContainerTextForLeftComponent(PsiElement element, String name, int maxWidth, FontMetrics fm) {
    String presentablePath = extractPresentablePath(element);
    String text = ObjectUtils.chooseNotNull(presentablePath, SymbolPresentationUtil.getSymbolContainerText(element));

    if (text == null || text.equals(name)) return null;

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

    boolean in = text.startsWith("in ");
    if (in) text = text.substring(3);
    String left = in ? "in " : "";
    String adjustedText = left + text;
    if (maxWidth < 0) return adjustedText;

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

  @Nullable
  private static String extractPresentablePath(@Nullable PsiElement element) {
    if (element == null) return null;

    PsiFile file = element.getContainingFile();
    if (file != null) {
      VirtualFile virtualFile = file.getVirtualFile();
      if (virtualFile instanceof VirtualFilePathWrapper) return ((VirtualFilePathWrapper)virtualFile).getPresentablePath();
    }

    return null;
  }

  @Override
  protected boolean customizeNonPsiElementLeftRenderer(ColoredListCellRenderer renderer,
                                                       JList list,
                                                       Object value,
                                                       int index,
                                                       boolean selected,
                                                       boolean hasFocus) {
    return GotoFileCellRenderer.doCustomizeNonPsiElementLeftRenderer(
      renderer, list, value, getNavigationItemAttributes(value));
  }

  @Override
  protected DefaultListCellRenderer getRightCellRenderer(final Object value) {
    final DefaultListCellRenderer rightRenderer = super.getRightCellRenderer(value);
    if (rightRenderer instanceof PlatformModuleRendererFactory.PlatformModuleRenderer) {
      // that renderer will display file path, but we're showing it ourselves - no need to show twice
      return null;
    }
    return rightRenderer;
  }

  @Override
  protected int getIconFlags() {
    return Iconable.ICON_FLAG_READ_STATUS;
  }
}
