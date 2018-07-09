// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor;
import com.intellij.codeInsight.daemon.MergeableLineMarkerInfo;
import com.intellij.codeInsight.daemon.NavigateAction;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ElementColorProvider;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.util.Function;
import com.intellij.util.FunctionUtil;
import com.intellij.util.ui.ColorIcon;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.TwoColorsIcon;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public final class ColorLineMarkerProvider extends LineMarkerProviderDescriptor {

  private final ElementColorProvider[] myExtensions = ElementColorProvider.EP_NAME.getExtensions();

  @Override
  public LineMarkerInfo getLineMarkerInfo(@NotNull PsiElement element) {
    for (ElementColorProvider colorProvider: myExtensions) {
      final Color color = colorProvider.getColorFrom(element);
      if (color != null) {
        MyInfo info = new MyInfo(element, color, colorProvider);
        NavigateAction.setNavigateAction(info, "Choose color", null);
        return info;
      }
    }
    return null;
  }

  @Override
  public void collectSlowLineMarkers(@NotNull List<PsiElement> elements, @NotNull Collection<LineMarkerInfo> result) {
  }

  @Override
  public String getName() {
    return "Color preview";
  }

  @NotNull
  @Override
  public Icon getIcon() {
    return AllIcons.Gutter.Colors;
  }

  private static class MyInfo extends MergeableLineMarkerInfo<PsiElement> {

    private final Color myColor;

    public MyInfo(@NotNull final PsiElement element, final Color color, final ElementColorProvider colorProvider) {
      super(element,
            element.getTextRange(),
            JBUI.scale(new ColorIcon(12, color)),
            Pass.LINE_MARKERS,
            FunctionUtil.<Object, String>nullConstant(),
            (e, elt) -> {
              if (!elt.isWritable()) return;

              final Editor editor = PsiUtilBase.findEditor(elt);
              assert editor != null;
              final Color c = ColorChooser.chooseColor(editor.getComponent(), "Choose Color", color, true);
              if (c != null) {
                WriteAction.run(() -> colorProvider.setColorTo(elt, c));
              }
            },
            GutterIconRenderer.Alignment.LEFT);
      myColor = color;
    }

    @Override
    public boolean canMergeWith(@NotNull MergeableLineMarkerInfo<?> info) {
      return info instanceof MyInfo;
    }

    @Override
    public Icon getCommonIcon(@NotNull List<MergeableLineMarkerInfo> infos) {
      if (infos.size() == 2 && infos.get(0) instanceof MyInfo && infos.get(1) instanceof MyInfo) {
        return JBUI.scale(new TwoColorsIcon(12, ((MyInfo)infos.get(0)).myColor, ((MyInfo)infos.get(1)).myColor));
      }
      return AllIcons.Gutter.Colors;
    }

    @NotNull
    @Override
    public Function<? super PsiElement, String> getCommonTooltip(@NotNull List<MergeableLineMarkerInfo> infos) {
      return FunctionUtil.nullConstant();
    }
  }
}
