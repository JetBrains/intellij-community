// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor;
import com.intellij.codeInsight.daemon.MergeableLineMarkerInfo;
import com.intellij.codeInsight.daemon.NavigateAction;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ElementColorProvider;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiEditorUtil;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.Function;
import com.intellij.util.FunctionUtil;
import com.intellij.util.ui.ColorIcon;
import com.intellij.util.ui.ColorsIcon;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public final class ColorLineMarkerProvider extends LineMarkerProviderDescriptor {
  public static final ColorLineMarkerProvider INSTANCE = new ColorLineMarkerProvider();

  @Override
  public LineMarkerInfo<?> getLineMarkerInfo(@NotNull PsiElement element) {
    return null;
  }

  @Override
  public void collectSlowLineMarkers(@NotNull List<? extends PsiElement> elements,
                                     @NotNull Collection<? super LineMarkerInfo<?>> result) {
    for (PsiElement element : elements) {
      ElementColorProvider.EP_NAME.computeSafeIfAny(provider -> {
        Color color = provider.getColorFrom(element);
        if (color == null) {
          return null;
        }

        MyInfo info = new MyInfo(element, color, provider);
        NavigateAction.setNavigateAction(info, IdeBundle.message("dialog.title.choose.color"), null, AllIcons.Actions.Colors);
        result.add(info);
        return info;
      });
    }
  }

  @Override
  public String getName() {
    return CodeInsightBundle.message("gutter.color.preview");
  }

  @NotNull
  @Override
  public Icon getIcon() {
    return AllIcons.Gutter.Colors;
  }

  private static final class MyInfo extends MergeableLineMarkerInfo<PsiElement> {

    private final Color myColor;

    MyInfo(@NotNull final PsiElement element, final Color color, final ElementColorProvider colorProvider) {
      super(element,
            element.getTextRange(),
            JBUIScale.scaleIcon(new ColorIcon(12, color)),
            FunctionUtil.<Object, String>nullConstant(),
            (e, elt) -> {
              if (!elt.isWritable()) return;

              final Editor editor = PsiEditorUtil.findEditor(elt);
              assert editor != null;

              if (Registry.is("ide.new.color.picker")) {
                RelativePoint relativePoint = new RelativePoint(e.getComponent(), e.getPoint());
                ColorChooserService.getInstance().showPopup(element.getProject(), color, (c, l) -> WriteAction.run(() -> colorProvider.setColorTo(elt, c)), relativePoint, true);
              } else {
                final Color c = ColorChooserService.getInstance().showDialog(editor.getProject(), editor.getComponent(),
                                                         IdeBundle.message("dialog.title.choose.color"), color, true);
                if (c != null) {
                  WriteAction.run(() -> colorProvider.setColorTo(elt, c));
                }
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
    public Icon getCommonIcon(@NotNull List<? extends MergeableLineMarkerInfo<?>> infos) {
      return JBUIScale.scaleIcon(new ColorsIcon(12, infos.stream().map(_info -> ((MyInfo)_info).myColor).toArray(Color[]::new)));
    }

    @NotNull
    @Override
    public Function<? super PsiElement, String> getCommonTooltip(@NotNull List<? extends MergeableLineMarkerInfo<?>> infos) {
      return FunctionUtil.nullConstant();
    }
  }
}
