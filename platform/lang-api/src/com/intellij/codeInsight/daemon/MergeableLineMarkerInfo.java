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
package com.intellij.codeInsight.daemon;

import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.util.Function;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.MouseEvent;
import java.util.*;

/**
 * @author Konstantin Bulenkov
 */
public abstract class MergeableLineMarkerInfo<T extends PsiElement> extends LineMarkerInfo<T> {
  public MergeableLineMarkerInfo(@NotNull T element,
                                 @NotNull TextRange textRange,
                                 Icon icon,
                                 int updatePass,
                                 @Nullable Function<? super T, String> tooltipProvider,
                                 @Nullable GutterIconNavigationHandler<T> navHandler,
                                 @NotNull GutterIconRenderer.Alignment alignment) {
    super(element, textRange, icon, updatePass, tooltipProvider, navHandler, alignment);
  }

  public abstract boolean canMergeWith(@NotNull MergeableLineMarkerInfo<?> info);

  public abstract Icon getCommonIcon(@NotNull List<MergeableLineMarkerInfo> infos);

  @NotNull
  public Function<? super PsiElement, String> getCommonTooltip(@NotNull final List<MergeableLineMarkerInfo> infos) {
    return (Function<PsiElement, String>)element -> {
      Set<String> tooltips = new HashSet<>(ContainerUtil.mapNotNull(infos, info -> info.getLineMarkerTooltip()));
      StringBuilder tooltip = new StringBuilder();
      for (String info : tooltips) {
        if (tooltip.length() > 0) {
          tooltip.append(UIUtil.BORDER_LINE);
        }
        tooltip.append(UIUtil.getHtmlBody(info));
      }
      return XmlStringUtil.wrapInHtml(tooltip);
    };
  }


  public GutterIconRenderer.Alignment getCommonIconAlignment(@NotNull List<MergeableLineMarkerInfo> infos) {
    return GutterIconRenderer.Alignment.LEFT;
  }

  public String getElementPresentation(PsiElement element) {
    return element.getText();
  }

  public int getCommonUpdatePass(@NotNull List<MergeableLineMarkerInfo> infos) {
    return updatePass;
  }

  public boolean configurePopupAndRenderer(@NotNull PopupChooserBuilder builder,
                                           @NotNull JBList list,
                                           @NotNull List<MergeableLineMarkerInfo> markers) {
    return false;
  }

  @NotNull
  public static List<LineMarkerInfo> merge(@NotNull List<MergeableLineMarkerInfo> markers) {
    List<LineMarkerInfo> result = new SmartList<>();
    for (int i = 0; i < markers.size(); i++) {
      MergeableLineMarkerInfo marker = markers.get(i);
      List<MergeableLineMarkerInfo> toMerge = new SmartList<>();
      for (int k = markers.size() - 1; k > i; k--) {
        MergeableLineMarkerInfo current = markers.get(k);
        if (marker.canMergeWith(current)) {
          toMerge.add(0, current);
          markers.remove(k);
        }
      }
      if (toMerge.isEmpty()) {
        result.add(marker);
      }
      else {
        toMerge.add(0, marker);
        result.add(new MyLineMarkerInfo(toMerge));
      }
    }
    return result;
  }

  private static class MyLineMarkerInfo extends LineMarkerInfo<PsiElement> {
    private MyLineMarkerInfo(@NotNull List<MergeableLineMarkerInfo> markers) {
      this(markers, markers.get(0));
    }

    private MyLineMarkerInfo(@NotNull List<MergeableLineMarkerInfo> markers, @NotNull MergeableLineMarkerInfo template) {
      //noinspection ConstantConditions
      super(template.getElement(),
            getCommonTextRange(markers),
            template.getCommonIcon(markers),
            template.getCommonUpdatePass(markers),
            template.getCommonTooltip(markers),
            getCommonNavigationHandler(markers),
            template.getCommonIconAlignment(markers));
    }

    private static TextRange getCommonTextRange(List<MergeableLineMarkerInfo> markers) {
      int startOffset = Integer.MAX_VALUE;
      int endOffset = Integer.MIN_VALUE;
      for (MergeableLineMarkerInfo marker : markers) {
        startOffset = Math.min(startOffset, marker.startOffset);
        endOffset = Math.max(endOffset, marker.endOffset);
      }
      return TextRange.create(startOffset, endOffset);
    }

    private static GutterIconNavigationHandler<PsiElement> getCommonNavigationHandler(@NotNull final List<MergeableLineMarkerInfo> markers) {
      return new GutterIconNavigationHandler<PsiElement>() {
        @Override
        public void navigate(final MouseEvent e, PsiElement elt) {
          final List<LineMarkerInfo> infos = new ArrayList<>(markers);
          Collections.sort(infos, (o1, o2) -> o1.startOffset - o2.startOffset);
          final JBList list = new JBList(infos);
          list.setFixedCellHeight(UIUtil.LIST_FIXED_CELL_HEIGHT);
          PopupChooserBuilder builder = JBPopupFactory.getInstance().createListPopupBuilder(list);
          if (!markers.get(0).configurePopupAndRenderer(builder, list, infos)) {
            list.installCellRenderer(dom -> {
              if (dom instanceof LineMarkerInfo) {
                Icon icon = null;
                final GutterIconRenderer renderer = ((LineMarkerInfo)dom).createGutterRenderer();
                if (renderer != null) {
                  icon = renderer.getIcon();
                }
                PsiElement element = ((LineMarkerInfo)dom).getElement();
                assert element != null;
                final String elementPresentation =
                  dom instanceof MergeableLineMarkerInfo
                  ? ((MergeableLineMarkerInfo)dom).getElementPresentation(element)
                  : element.getText();
                String text = StringUtil.first(elementPresentation, 100, true).replace('\n', ' ');

                final JBLabel label = new JBLabel(text, icon, SwingConstants.LEFT);
                label.setBorder(IdeBorderFactory.createEmptyBorder(2));
                return label;
              }

              return new JBLabel();
            });
          }
          builder.setItemChoosenCallback(() -> {
            final Object value = list.getSelectedValue();
            if (value instanceof LineMarkerInfo) {
              final GutterIconNavigationHandler handler = ((LineMarkerInfo)value).getNavigationHandler();
              if (handler != null) {
                //noinspection unchecked
                handler.navigate(e, ((LineMarkerInfo)value).getElement());
              }
            }
          }).createPopup().show(new RelativePoint(e));
        }
      };
    }
  }
}
