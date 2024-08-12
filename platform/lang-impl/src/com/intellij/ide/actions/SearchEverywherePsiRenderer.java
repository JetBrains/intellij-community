// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.ide.ui.LafManagerListener;
import com.intellij.ide.util.PSIRenderingUtils;
import com.intellij.ide.util.PsiElementListCellRenderer;
import com.intellij.ide.util.gotoByName.GotoFileCellRenderer;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.presentation.java.SymbolPresentationUtil;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.NamedColorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.LinkedList;

/**
* @author Konstantin Bulenkov
*/
public class SearchEverywherePsiRenderer extends PsiElementListCellRenderer<PsiElement> {
  private EditorColorsScheme scheme = EditorColorsManager.getInstance().getSchemeForCurrentUITheme();

  public SearchEverywherePsiRenderer(Disposable parent) {
    setLayout(new SELayout());

    ApplicationManager.getApplication().getMessageBus().connect(parent).subscribe(LafManagerListener.TOPIC, __ -> {
      scheme = EditorColorsManager.getInstance().getSchemeForCurrentUITheme();
    });
  }

  @Override
  protected @NotNull SimpleTextAttributes getErrorAttributes() {
    SimpleTextAttributes schemeAttributes = SimpleTextAttributes.fromTextAttributes(scheme.getAttributes(CodeInsightColors.ERRORS_ATTRIBUTES));
    return new SimpleTextAttributes(schemeAttributes.getBgColor(), NamedColorUtil.getInactiveTextColor(), schemeAttributes.getWaveColor(),
                                    schemeAttributes.getStyle() | SimpleTextAttributes.STYLE_USE_EFFECT_COLOR);
  }

  @Override
  protected ColoredListCellRenderer<Object> createLeftRenderer(JList list, Object value) {
    ColoredListCellRenderer<Object> renderer = super.createLeftRenderer(list, value);
    renderer.setIpad(new Insets(0, 0, 0, renderer.getIpad().right));
    return renderer;
  }

  @Override
  public String getElementText(PsiElement element) {
    return PSIRenderingUtils.getPSIElementText(element);
  }

  @Override
  protected @Nullable String getContainerText(PsiElement element, String name) {
    return getContainerTextForLeftComponent(element, name, -1, null);
  }

  @Override
  protected @Nullable String getContainerTextForLeftComponent(PsiElement element, String name, int maxWidth, FontMetrics fm) {
    String presentablePath = PSIRenderingUtils.extractPresentablePath(element);
    String text = ObjectUtils.chooseNotNull(presentablePath, SymbolPresentationUtil.getSymbolContainerText(element));
    if (text == null || text.equals(name)) return null;
    text = PSIRenderingUtils.normalizePsiElementContainerText(element, text, presentablePath);

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
  protected int getIconFlags() {
    return Iconable.ICON_FLAG_READ_STATUS;
  }

  public static final class SELayout extends BorderLayout {
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
  }
}
