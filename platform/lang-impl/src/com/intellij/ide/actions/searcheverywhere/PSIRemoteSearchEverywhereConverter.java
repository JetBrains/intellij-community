// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere;

import com.intellij.ide.actions.SearchEverywherePsiRenderer;
import com.intellij.ide.actions.searcheverywhere.remote.PresentationWithItem;
import com.intellij.ide.ui.UISettings;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.backend.presentation.TargetPresentation;
import com.intellij.platform.backend.presentation.TargetPresentationBuilder;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.psi.PsiElement;
import com.intellij.psi.presentation.java.SymbolPresentationUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.render.RendererPanelsUtils;
import com.intellij.ui.speedSearch.SpeedSearchUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.TextWithIcon;
import com.intellij.util.text.Matcher;
import com.intellij.util.text.MatcherHolder;
import com.intellij.util.ui.NamedColorUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Objects;

import static com.intellij.ide.util.PSIRenderingUtils.*;
import static com.intellij.ide.util.PsiElementListCellRenderer.getModuleTextWithIcon;
import static com.intellij.openapi.editor.colors.CodeInsightColors.ERRORS_ATTRIBUTES;
import static com.intellij.openapi.vfs.newvfs.VfsPresentationUtil.getFileBackgroundColor;

public class PSIRemoteSearchEverywhereConverter implements RemoteSearchEverywhereConverter<Object, PresentationWithItem<TargetPresentation, ?>> {

  @Override
  public PresentationWithItem<TargetPresentation, Object> convertToPresentation(Object val) {
    TargetPresentationBuilder builder = createBuilder(val);
    return new PresentationWithItem<>(builder.presentation(), val);
  }

  @Override
  public Object convertToItem(PresentationWithItem<TargetPresentation, ?> presentation) {
    return presentation.getItem();
  }

  @Override
  public ListCellRenderer<PresentationWithItem<TargetPresentation, ?>> getPresentationRenderer() {
    return new MyRenderer();
  }

  @NotNull
  private static TargetPresentationBuilder createBuilder(Object value) {
    TextAttributes textAttributes = getNavigationItemAttributesStatic(value);

    PsiElement target = getPsiElement(value);
    VirtualFile vFile = PsiUtilCore.getVirtualFile(target);
    if (vFile != null) {
      Project project = target.getProject();
      if (WolfTheProblemSolver.getInstance(project).isProblemFile(vFile)) {
        TextAttributes errorAttributes = EditorColorsManager.getInstance().getSchemeForCurrentUITheme().getAttributes(ERRORS_ATTRIBUTES);
        textAttributes = TextAttributes.merge(textAttributes, errorAttributes);
      }
      FileStatus status = FileStatusManager.getInstance(project).getStatus(vFile);
      Color fgColor = status.getColor();
      Color bgColor = getFileBackgroundColor(project, vFile);
      if (fgColor != null || bgColor != null) {
        TextAttributes colorAttributes = new TextAttributes(fgColor, bgColor, null, null, Font.PLAIN);
        textAttributes = TextAttributes.merge(textAttributes, colorAttributes);
      }
    }

    @NlsSafe String presentationText;
    @NlsSafe String containerText;
    Icon icon;
    if (value instanceof PsiElement psi) {
      presentationText = getPSIElementText(psi);
      String presentablePath = extractPresentablePath(psi);
      String text = ObjectUtils.chooseNotNull(presentablePath, SymbolPresentationUtil.getSymbolContainerText(psi));
      containerText = text != null && !text.equals(presentationText) ? normalizePsiElementContainerText(psi, text, presentablePath) : null;
      icon = psi.getIcon(Iconable.ICON_FLAG_READ_STATUS);
    }
    else if (value instanceof NavigationItem navItem) {
      ItemPresentation presentation = Objects.requireNonNull(navItem.getPresentation());
      presentationText = presentation.getPresentableText();
      containerText = presentation.getLocationString();
      icon = presentation.getIcon(true);
    }
    else {
      presentationText = value == null ? "" : value.toString();
      containerText = null;
      icon = null;
    }

    TargetPresentationBuilder builder = TargetPresentation.builder(StringUtil.notNullize(presentationText))
      .presentableTextAttributes(textAttributes)
      .containerText(containerText)
      .icon(icon);

    TextWithIcon locationInfo = getLocationInfo(value);
    if (locationInfo != null) builder = builder.locationText(locationInfo.getText(), locationInfo.getIcon());

    return builder;
  }

  private static @Nullable TextWithIcon getLocationInfo(Object value) {
    if (UISettings.getInstance().getShowIconInQuickNavigation()) {
      return getModuleTextWithIcon(value);
    }

    return null;
  }

  private static class MyRenderer extends JPanel implements ListCellRenderer<PresentationWithItem<TargetPresentation, ?>> {

    private MyRenderer() {
      super(new SearchEverywherePsiRenderer.SELayout());
    }

    @Override
    public Component getListCellRendererComponent(JList<? extends PresentationWithItem<TargetPresentation, ?>> list, PresentationWithItem<TargetPresentation, ?> value,
                                                  int index, boolean selected, boolean cellHasFocus) {
      TargetPresentation presentation = value.getPresentation();

      removeAll();

      JLabel rightComponent = null;
      if (StringUtil.isNotEmpty(presentation.getLocationText())) {
        rightComponent = new JLabel(presentation.getLocationText(), presentation.getLocationIcon(), SwingConstants.RIGHT);
        rightComponent.setHorizontalTextPosition(SwingConstants.LEFT);
        rightComponent.setIconTextGap(RendererPanelsUtils.getIconTextGap());
        rightComponent.setForeground(selected ? NamedColorUtil.getListSelectionForeground(true) : NamedColorUtil.getInactiveTextColor());
        add(rightComponent, BorderLayout.EAST);
      }

      SimpleColoredComponent leftComponent = new SimpleColoredComponent();
      leftComponent.setIpad(new Insets(0, 0, 0, leftComponent.getIpad().right)); // Border of top panel is used for around insets of renderer
      leftComponent.setIcon(presentation.getIcon());
      leftComponent.setIconTextGap(RendererPanelsUtils.getIconTextGap());
      leftComponent.setFont(list.getFont());
      SimpleTextAttributes nameAttributes = presentation.getPresentableTextAttributes() != null
                                            ? SimpleTextAttributes.fromTextAttributes(presentation.getPresentableTextAttributes())
                                            : new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, null);
      Matcher nameMatcher = MatcherHolder.getAssociatedMatcher(list);
      Color bgColor = selected ? UIUtil.getListSelectionBackground(true) : presentation.getBackgroundColor();
      setBackground(bgColor);
      SpeedSearchUtil.appendColoredFragmentForMatcher(presentation.getPresentableText(), leftComponent, nameAttributes, nameMatcher, bgColor, selected);
      if (presentation.getContainerText() != null) {
        Insets listInsets = list.getInsets();
        Insets rendererInsets = leftComponent.getInsets();
        FontMetrics fm = list.getFontMetrics(list.getFont());
        int containerMaxWidth = list.getWidth() - listInsets.left - listInsets.right
                                - rendererInsets.left - rendererInsets.right
                                - leftComponent.getPreferredSize().width;
        if (rightComponent != null) containerMaxWidth -= rightComponent.getPreferredSize().width;

        @NlsSafe String containerText = cutContainerText(presentation.getContainerText(), containerMaxWidth, fm);
        SimpleTextAttributes containerAttributes = presentation.getContainerTextAttributes() != null
                                                   ? SimpleTextAttributes.fromTextAttributes(presentation.getContainerTextAttributes())
                                                   : SimpleTextAttributes.GRAYED_ATTRIBUTES;
        SpeedSearchUtil.appendColoredFragmentForMatcher(" " + containerText, leftComponent, containerAttributes, null, bgColor, selected);
      }
      add(leftComponent, BorderLayout.WEST);
      accessibleContext = leftComponent.getAccessibleContext();

      return this;
    }
  }
}
