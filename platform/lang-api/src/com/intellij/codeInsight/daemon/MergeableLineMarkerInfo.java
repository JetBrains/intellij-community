// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
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
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.Function;
import com.intellij.util.IconUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

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
    private final DefaultActionGroup myCommonActionGroup;
    private final List<? extends MergeableLineMarkerInfo<?>> myMarkers;

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
            getCommonNavigationHandler(markers), template.getCommonIconAlignment(markers));
      myCommonActionGroup = getCommonActionGroup(markers);
      myMarkers = markers;
      updatePass = passId;
    }


    public @NotNull List<? extends MergeableLineMarkerInfo<?>> getInfos() {
      return myMarkers;
    }

    private static DefaultActionGroup getCommonActionGroup(@NotNull List<? extends MergeableLineMarkerInfo<?>> markers) {
      DefaultActionGroup commonActionGroup = null;
      boolean first = true;
      for (MergeableLineMarkerInfo<?> marker : markers) {
        GutterIconRenderer renderer = marker.createGutterRenderer();
        if (renderer != null) {
          ActionGroup actions = renderer.getPopupMenuActions();
          boolean popup = actions != null;
          if (actions == null) {
            actions = new DefaultActionGroup(marker.getNavigateAction());
          }
          if (commonActionGroup == null) {
            commonActionGroup = new DefaultActionGroup();
          }
          if (!first && popup) {
            commonActionGroup.add(Separator.getInstance());
          }
          first = false;
          commonActionGroup.addAll(actions);
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

    private static @NotNull GutterIconNavigationHandler<PsiElement> getCommonNavigationHandler(@NotNull List<? extends MergeableLineMarkerInfo<?>> markers) {
      return new MergedGutterIconNavigationHandler(markers);
    }

    @Override
    public GutterIconRenderer createGutterRenderer() {
      if (myCommonActionGroup == null) return super.createGutterRenderer();

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
        public ActionGroup getPopupMenuActions() {
          return myCommonActionGroup;
        }
      };
    }
  }

  @TestOnly
  public static @Nullable List<LineMarkerInfo<?>> getMergedLineMarkerInfos(@NotNull GutterIconNavigationHandler<?> handler) {
    if (handler instanceof MergedGutterIconNavigationHandler mergedGutterIconNavigationHandler) {
      return mergedGutterIconNavigationHandler.getMergedLineMarkersInfos();
    }
    return null;
  }

  private static class MergedGutterIconNavigationHandler implements GutterIconNavigationHandler<PsiElement> {
    private final List<LineMarkerInfo<?>> myInfos;

    private MergedGutterIconNavigationHandler(@NotNull List<? extends MergeableLineMarkerInfo<?>> markers) {
      List<LineMarkerInfo<?>> infos = new ArrayList<>(markers);
      infos.sort(Comparator.comparingInt(o -> o.startOffset));
      myInfos = Collections.unmodifiableList(infos);
    }

    private @NotNull List<LineMarkerInfo<?>> getMergedLineMarkersInfos() {
      return myInfos;
    }

    @Override
    public void navigate(MouseEvent e, PsiElement __) {
      //list.setFixedCellHeight(UIUtil.LIST_FIXED_CELL_HEIGHT); // TODO[jetzajac]: do we need it?
      IPopupChooserBuilder<LineMarkerInfo<?>> builder = JBPopupFactory.getInstance().createPopupChooserBuilder(myInfos);
      builder.setRenderer(new SelectionAwareListCellRenderer<>(dom -> {
        Icon icon = null;
        GutterIconRenderer renderer = dom.createGutterRenderer();
        if (renderer != null) {
          Icon originalIcon = renderer.getIcon();
          icon = IconUtil.scale(originalIcon, null, JBUIScale.scale(16.0f) / originalIcon.getIconWidth());
        }
        var elementPresentation = ReadAction.compute(() -> {
          PsiElement element = dom.getElement();
          if (element == null) {
            return IdeBundle.message("node.structureview.invalid");
          }
          else if (dom instanceof MergeableLineMarkerInfo) {
            return ((MergeableLineMarkerInfo<?>)dom).getElementPresentation(element);
          }
          else {
            return element.getText();
          }
        });
        String text = StringUtil.first(elementPresentation, 100, true).replace('\n', ' ');

        JBLabel label = new JBLabel(text, icon, SwingConstants.LEFT);
        label.setBorder(JBUI.Borders.empty(2));
        return label;
      }));
      builder.setItemChosenCallback(value -> {
        //noinspection unchecked
        GutterIconNavigationHandler<PsiElement> handler = (GutterIconNavigationHandler<PsiElement>)value.getNavigationHandler();
        if (handler != null) {
          handler.navigate(e, value.getElement());
        }
      }).createPopup().show(new RelativePoint(e));
    }
  }

  private AnAction getNavigateAction() {
    return new AnAction() {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        InputEvent event = e.getInputEvent();
        MouseEvent mouseEvent = event instanceof MouseEvent ? (MouseEvent)event : null;
        getNavigationHandler().navigate(mouseEvent, getElement());
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
