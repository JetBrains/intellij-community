// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.util.Function;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.function.Supplier;

/**
 * @author Konstantin Bulenkov
 */
public abstract class MergeableLineMarkerInfo<T extends PsiElement> extends LineMarkerInfo<T> {

  private static final Logger LOG = Logger.getInstance(MergeableLineMarkerInfo.class);

  private @Nullable Function<? super PsiElement, @Nls(capitalization = Nls.Capitalization.Title) String> myPresentationProvider;

  /**
   * @deprecated Use {@link #MergeableLineMarkerInfo(PsiElement, TextRange, Icon, Function, GutterIconNavigationHandler, GutterIconRenderer.Alignment, Supplier)} instead
   */
  @Deprecated
  public MergeableLineMarkerInfo(@NotNull T element,
                                 @NotNull TextRange textRange,
                                 @Nullable Icon icon,
                                 int __,
                                 @Nullable Function<? super T, String> tooltipProvider,
                                 @Nullable GutterIconNavigationHandler<T> navHandler,
                                 @NotNull GutterIconRenderer.Alignment alignment) {
    super(element, textRange, icon, tooltipProvider, navHandler, alignment);
  }

  /**
   * @deprecated Use {@link #MergeableLineMarkerInfo(PsiElement, TextRange, Icon, Function, GutterIconNavigationHandler, GutterIconRenderer.Alignment, Supplier)} instead
   */
  @Deprecated
  public MergeableLineMarkerInfo(@NotNull T element,
                                 @NotNull TextRange textRange,
                                 @Nullable Icon icon,
                                 @Nullable Function<? super T, String> tooltipProvider,
                                 @Nullable GutterIconNavigationHandler<T> navHandler,
                                 @NotNull GutterIconRenderer.Alignment alignment) {
    super(element, textRange, icon, tooltipProvider, navHandler, alignment);
  }

  /**
   * @param accessibleNameProvider callback to calculate the icon's accessible name (used by screen reader), see also {@link #getCommonAccessibleNameProvider(List)}
   */
  public MergeableLineMarkerInfo(@NotNull T element,
                                 @NotNull TextRange textRange,
                                 @NotNull Icon icon,
                                 @Nullable Function<? super T, String> tooltipProvider,
                                 @Nullable GutterIconNavigationHandler<T> navHandler,
                                 @NotNull GutterIconRenderer.Alignment alignment,
                                 @NotNull Supplier<@NotNull @Nls String> accessibleNameProvider) {
    super(element, textRange, icon, tooltipProvider, navHandler, alignment, accessibleNameProvider);
  }

  public MergeableLineMarkerInfo(@NotNull T element,
                                 @NotNull TextRange textRange,
                                 @NotNull Icon icon,
                                 @Nullable Function<? super T, String> tooltipProvider,
                                 @Nullable Function<? super PsiElement, @Nls(capitalization = Nls.Capitalization.Title) String> presentationProvider,
                                 @Nullable GutterIconNavigationHandler<T> navHandler,
                                 @NotNull GutterIconRenderer.Alignment alignment,
                                 @NotNull Supplier<@NotNull @Nls String> accessibleNameProvider) {
    super(element, textRange, icon, tooltipProvider, navHandler, alignment, accessibleNameProvider);
    myPresentationProvider = presentationProvider;
  }

  public abstract boolean canMergeWith(@NotNull MergeableLineMarkerInfo<?> info);

  public abstract Icon getCommonIcon(@NotNull List<? extends MergeableLineMarkerInfo<?>> infos);

  public static @NotNull List<? extends MergeableLineMarkerInfo<?>> getMergedMarkers(LineMarkerInfo<?> info) {
    if (info instanceof MyLineMarkerInfo) {
      return ((MyLineMarkerInfo)info).getInfos();
    }
    return Collections.emptyList();
  }

  public @NotNull Function<? super PsiElement, String> getCommonTooltip(@NotNull List<? extends MergeableLineMarkerInfo<?>> infos) {
    return element -> {
      Set<String> tooltips = new LinkedHashSet<>(ContainerUtil.mapNotNull(infos, info -> info.getLineMarkerTooltip()));
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


  public @NotNull GutterIconRenderer.Alignment getCommonIconAlignment(@NotNull List<? extends MergeableLineMarkerInfo<?>> infos) {
    return GutterIconRenderer.Alignment.LEFT;
  }

  private static Supplier<@NotNull @Nls String> getCommonAccessibleNameProvider(@NotNull List<? extends MergeableLineMarkerInfo<?>> infos) {
    return infos.get(0).getAccessibleNameProvider();
  }

  public @NotNull @Nls(capitalization = Nls.Capitalization.Title) String getElementPresentation(@NotNull PsiElement element) {
    return myPresentationProvider != null ? myPresentationProvider.fun(element) : element.getText();
  }

  public static @NotNull List<LineMarkerInfo<?>> merge(@NotNull List<? extends MergeableLineMarkerInfo<?>> markers, int passId) {
    List<LineMarkerInfo<?>> result = new SmartList<>();
    for (int i = 0; i < markers.size(); i++) {
      MergeableLineMarkerInfo<?> marker = markers.get(i);
      List<MergeableLineMarkerInfo<?>> toMerge = new SmartList<>();
      for (int k = markers.size() - 1; k > i; k--) {
        MergeableLineMarkerInfo<?> current = markers.get(k);
        boolean canMergeWith = marker.canMergeWith(current);
        if (ApplicationManager.getApplication().isUnitTestMode() && !canMergeWith && current.canMergeWith(marker)) {
          LOG.error(current.getClass() + "[" + current.getLineMarkerTooltip() + "] can merge " +
                    marker.getClass() + "[" + marker.getLineMarkerTooltip() + "], but not vice versa");
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
        result.add(new MyLineMarkerInfo(toMerge, passId));
      }
    }
    return result;
  }

  private static final class MyLineMarkerInfo extends LineMarkerInfo<PsiElement> {
    private final List<? extends MergeableLineMarkerInfo<?>> myMarkers;
    private final List<ActionGroup> myGroups;

    private MyLineMarkerInfo(@NotNull List<? extends MergeableLineMarkerInfo<?>> markers, int passId) {
      this(markers, markers.get(0), passId);
    }

    /**
     *  An 'updatePass' field is explicitly set here to avoid duplicated markers
     *  @see com.intellij.codeHighlighting.Pass.SLOW_LINE_MARKERS
     */
    private MyLineMarkerInfo(@NotNull List<? extends MergeableLineMarkerInfo<?>> markers, @NotNull MergeableLineMarkerInfo<?> template, int passId) {
      //noinspection ConstantConditions
      super(template.getElement(), getCommonTextRange(markers), template.getCommonIcon(markers),
            getCommonAccessibleNameProvider(markers), template.getCommonTooltip(markers),
            null, template.getCommonIconAlignment(markers));
      myMarkers = markers;
      myGroups = ContainerUtil.map(markers, info -> info.createGutterRenderer().getPopupMenuActions());
      updatePass = passId;
    }

    public @NotNull List<? extends MergeableLineMarkerInfo<?>> getInfos() {
      return myMarkers;
    }

    private @NotNull DefaultActionGroup getCommonActionGroup() {
      DefaultActionGroup commonActionGroup = new DefaultActionGroup();
      for (int i = 0; i < myGroups.size(); i++) {
        ActionGroup popupActions = myGroups.get(i);
        if (popupActions != null) {
          commonActionGroup.addSeparator();
          commonActionGroup.addAll(popupActions);
        }
        else {
          commonActionGroup.add(myMarkers.get(i).getNavigateAction());
        }
      }
      return commonActionGroup;
    }

    private static @NotNull TextRange getCommonTextRange(@NotNull List<? extends MergeableLineMarkerInfo<?>> markers) {
      int startOffset = Integer.MAX_VALUE;
      int endOffset = Integer.MIN_VALUE;
      for (MergeableLineMarkerInfo<?> marker : markers) {
        startOffset = Math.min(startOffset, marker.startOffset);
        endOffset = Math.max(endOffset, marker.endOffset);
      }
      return TextRange.create(startOffset, endOffset);
    }

    @Override
    public GutterIconRenderer createGutterRenderer() {
      return new LineMarkerGutterIconRenderer<>(this) {
        @Override
        public AnAction getClickAction() {
          return null;
        }

        @Override
        public boolean isNavigateAction() {
          return true;
        }

        @Override
        public @NotNull ActionGroup getPopupMenuActions() {
          return getCommonActionGroup();
        }
      };
    }
  }

  protected AnAction getNavigateAction() {
    return new AnAction() {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        MouseEvent mouseEvent = getMouseEvent(e);
        getNavigationHandler().navigate(mouseEvent, getElement());
      }

      private static @NotNull MouseEvent getMouseEvent(@NotNull AnActionEvent e) {
        InputEvent inputEvent = e.getInputEvent();
        if (inputEvent instanceof MouseEvent) {
          return (MouseEvent)inputEvent;
        }
        else {
          return JBPopupFactory.getInstance().guessBestPopupLocation(e.getDataContext()).toMouseEvent();
        }
      }

      @Override
      public void update(@NotNull AnActionEvent e) {
        PsiElement element = getElement();
        if (element != null) {
          Presentation presentation = e.getPresentation();
          presentation.setIcon(getIcon());
          presentation.setText(getElementPresentation(element));
        }
      }

      @Override
      public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
      }
    };
  }
}
