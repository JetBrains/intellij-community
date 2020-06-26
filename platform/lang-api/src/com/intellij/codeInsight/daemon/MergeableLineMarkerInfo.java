// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.ui.popup.IPopupChooserBuilder;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.SelectionAwareListCellRenderer;
import com.intellij.util.Function;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
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

  private static final Logger LOG = Logger.getInstance(MergeableLineMarkerInfo.class);

  /**
   * @deprecated Use {@link #MergeableLineMarkerInfo(PsiElement, TextRange, Icon, Function, GutterIconNavigationHandler, GutterIconRenderer.Alignment)} instead
   */
  @Deprecated
  public MergeableLineMarkerInfo(@NotNull T element,
                                 @NotNull TextRange textRange,
                                 Icon icon,
                                 int updatePass,
                                 @Nullable Function<? super T, String> tooltipProvider,
                                 @Nullable GutterIconNavigationHandler<T> navHandler,
                                 @NotNull GutterIconRenderer.Alignment alignment) {
    super(element, textRange, icon, tooltipProvider, navHandler, alignment);
  }

  public MergeableLineMarkerInfo(@NotNull T element,
                                 @NotNull TextRange textRange,
                                 Icon icon,
                                 @Nullable Function<? super T, String> tooltipProvider,
                                 @Nullable GutterIconNavigationHandler<T> navHandler,
                                 @NotNull GutterIconRenderer.Alignment alignment) {
    super(element, textRange, icon, tooltipProvider, navHandler, alignment);
  }

  public abstract boolean canMergeWith(@NotNull MergeableLineMarkerInfo<?> info);

  public abstract Icon getCommonIcon(@NotNull List<? extends MergeableLineMarkerInfo<?>> infos);

  @NotNull
  public Function<? super PsiElement, String> getCommonTooltip(final @NotNull List<? extends MergeableLineMarkerInfo<?>> infos) {
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


  public GutterIconRenderer.Alignment getCommonIconAlignment(@NotNull List<? extends MergeableLineMarkerInfo<?>> infos) {
    return GutterIconRenderer.Alignment.LEFT;
  }

  @NotNull
  public String getElementPresentation(@NotNull PsiElement element) {
    return element.getText();
  }

  private int getCommonUpdatePass(@NotNull List<? extends MergeableLineMarkerInfo<?>> infos) {
    return updatePass;
  }

  @NotNull
  public static List<LineMarkerInfo<?>> merge(@NotNull List<? extends MergeableLineMarkerInfo<?>> markers) {
    List<LineMarkerInfo<?>> result = new SmartList<>();
    for (int i = 0; i < markers.size(); i++) {
      MergeableLineMarkerInfo<?> marker = markers.get(i);
      List<MergeableLineMarkerInfo<?>> toMerge = new SmartList<>();
      for (int k = markers.size() - 1; k > i; k--) {
        MergeableLineMarkerInfo<?> current = markers.get(k);
        boolean canMergeWith = marker.canMergeWith(current);
        if (ApplicationManager.getApplication().isUnitTestMode() && !canMergeWith && current.canMergeWith(marker)) {
          LOG.error(current.getClass() +
                    "[" +
                    current.getLineMarkerTooltip() +
                    "]" +
                    " can merge " +
                    marker.getClass() +
                    "[" +
                    marker.getLineMarkerTooltip() +
                    "]" +
                    ", but not contrariwise");
        }
        if (canMergeWith) {
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

  private static final class MyLineMarkerInfo extends LineMarkerInfo<PsiElement> {
    private MyLineMarkerInfo(@NotNull List<? extends MergeableLineMarkerInfo<?>> markers) {
      this(markers, markers.get(0));
    }

    private MyLineMarkerInfo(@NotNull List<? extends MergeableLineMarkerInfo<?>> markers, @NotNull MergeableLineMarkerInfo<?> template) {
      //noinspection ConstantConditions
      super(template.getElement(), getCommonTextRange(markers), template.getCommonIcon(markers), template.getCommonTooltip(markers),
            getCommonNavigationHandler(markers), template.getCommonIconAlignment(markers));
    }

    @NotNull
    private static TextRange getCommonTextRange(@NotNull List<? extends MergeableLineMarkerInfo<?>> markers) {
      int startOffset = Integer.MAX_VALUE;
      int endOffset = Integer.MIN_VALUE;
      for (MergeableLineMarkerInfo<?> marker : markers) {
        startOffset = Math.min(startOffset, marker.startOffset);
        endOffset = Math.max(endOffset, marker.endOffset);
      }
      return TextRange.create(startOffset, endOffset);
    }

    @NotNull
    private static GutterIconNavigationHandler<PsiElement> getCommonNavigationHandler(@NotNull final List<? extends MergeableLineMarkerInfo<?>> markers) {
      return new MergedGutterIconNavigationHandler(markers);
    }
  }

  static class MergedGutterIconNavigationHandler implements GutterIconNavigationHandler<PsiElement> {
    private final List<LineMarkerInfo<?>> myInfos;

    MergedGutterIconNavigationHandler(List<? extends MergeableLineMarkerInfo<?>> markers) {
      final List<LineMarkerInfo<?>> infos = new ArrayList<>(markers);
      infos.sort(Comparator.comparingInt(o -> o.startOffset));
      myInfos = Collections.unmodifiableList(infos);
    }

    @NotNull
    List<LineMarkerInfo<?>> getMergedLineMarkersInfos() {
      return myInfos;
    }

    @Override
    public void navigate(MouseEvent e, PsiElement elt) {
      //list.setFixedCellHeight(UIUtil.LIST_FIXED_CELL_HEIGHT); // TODO[jetzajac]: do we need it?
      IPopupChooserBuilder<LineMarkerInfo<?>> builder = JBPopupFactory.getInstance().createPopupChooserBuilder(myInfos);
      builder.setRenderer(new SelectionAwareListCellRenderer<>(dom -> {
        Icon icon = null;
        final GutterIconRenderer renderer = dom.createGutterRenderer();
        if (renderer != null) {
          icon = renderer.getIcon();
        }
        PsiElement element = dom.getElement();
        final String elementPresentation;
        if (element == null) {
          elementPresentation = IdeBundle.message("node.structureview.invalid");
        }
        else if (dom instanceof MergeableLineMarkerInfo) {
          elementPresentation = ((MergeableLineMarkerInfo<?>)dom).getElementPresentation(element);
        }
        else {
          elementPresentation = element.getText();
        }
        String text = StringUtil.first(elementPresentation, 100, true).replace('\n', ' ');

        final JBLabel label = new JBLabel(text, icon, SwingConstants.LEFT);
        label.setBorder(JBUI.Borders.empty(2));
        return label;
      }));
      builder.setItemChosenCallback(value -> {
        final GutterIconNavigationHandler handler = value.getNavigationHandler();
        if (handler != null) {
          //noinspection unchecked
          handler.navigate(e, value.getElement());
        }
      }).createPopup().show(new RelativePoint(e));
    }
  }
}
