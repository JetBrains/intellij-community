// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.actions.onSave;

import com.intellij.application.options.CodeStyleConfigurableWrapper;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.ide.actionsOnSave.ActionOnSaveContext;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.psi.codeStyle.arrangement.Rearranger;
import com.intellij.psi.codeStyle.arrangement.std.ArrangementStandardSettingsAware;
import com.intellij.psi.codeStyle.arrangement.std.ArrangementStandardSettingsAware.ArrangementTabInfo;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.ActionLink;
import com.intellij.util.KeyedLazyInstance;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@ApiStatus.Internal
public final class RearrangeCodeOnSaveActionInfo extends ActionOnSaveInfoBase {
  private static final String REARRANGE_CODE_ON_SAVE_PROPERTY = "rearrange.code.on.save";
  private static final boolean REARRANGE_CODE_ON_SAVE_DEFAULT = false;

  public static boolean isRearrangeCodeOnSaveEnabled(@NotNull Project project) {
    return PropertiesComponent.getInstance(project).getBoolean(REARRANGE_CODE_ON_SAVE_PROPERTY, REARRANGE_CODE_ON_SAVE_DEFAULT);
  }

  public RearrangeCodeOnSaveActionInfo(@NotNull ActionOnSaveContext context) {
    super(context,
          CodeInsightBundle.message("actions.on.save.page.checkbox.rearrange.code"),
          REARRANGE_CODE_ON_SAVE_PROPERTY,
          REARRANGE_CODE_ON_SAVE_DEFAULT);
  }

  @Override
  public @NotNull List<? extends ActionLink> getActionLinks() {
    ActionLink link = new ActionLink(CodeInsightBundle.message("actions.on.save.page.link.configure.arrangement.rules"));
    link.addActionListener(e -> showArrangementSettingsPopup(link));
    return List.of(link);
  }

  private void showArrangementSettingsPopup(@NotNull ActionLink link) {
    List<ArrangementTabInfo> arrangementTabInfos = getArrangementTabInfos();

    JBPopupFactory.getInstance().createPopupChooserBuilder(arrangementTabInfos)
      .setTitle(CodeInsightBundle.message("actions.on.save.page.popup.title.arrangement.settings"))
      .setRenderer(new DefaultListCellRenderer() {
        @Override
        public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
          Component result = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
          ArrangementTabInfo tabInfo = (ArrangementTabInfo)value;
          setText(tabInfo.languageDisplayName);
          setIcon(ObjectUtils.notNull(tabInfo.icon, EmptyIcon.ICON_16));
          setBorder(JBUI.Borders.empty(3));
          return result;
        }
      })
      .setItemChosenCallback(tabInfo -> {
        String configurableId = CodeStyleConfigurableWrapper.getConfigurableId(tabInfo.configurableId);
        Configurable configurable = getSettings().find(configurableId);
        getSettings().select(configurable).doWhenDone(() -> {
          if (configurable instanceof CodeStyleConfigurableWrapper) {
            ((CodeStyleConfigurableWrapper)configurable).selectTab(ApplicationBundle.message("arrangement.title.settings.tab"));
          }
        });
      })
      .createPopup()
      .show(new RelativePoint(link, new Point(0, link.getHeight() + JBUI.scale(4))));
  }

  private static @NotNull List<ArrangementTabInfo> getArrangementTabInfos() {
    List<ArrangementTabInfo> arrangementTabInfos = new ArrayList<>();
    ExtensionPoint<KeyedLazyInstance<Rearranger<?>>> extensionPoint = Rearranger.EXTENSION.getPoint();
    if (extensionPoint != null) {
      for (KeyedLazyInstance<Rearranger<?>> instance : extensionPoint.getExtensionList()) {
        Rearranger<?> rearranger = instance.getInstance();
        if (rearranger instanceof ArrangementStandardSettingsAware) {
          arrangementTabInfos.addAll(((ArrangementStandardSettingsAware)rearranger).getArrangementTabInfos());
        }
      }
    }

    arrangementTabInfos.sort(Comparator.comparing(info -> info.languageDisplayName));
    return arrangementTabInfos;
  }
}
