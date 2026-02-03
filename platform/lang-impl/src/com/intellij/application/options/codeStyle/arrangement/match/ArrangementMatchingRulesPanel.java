// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.codeStyle.arrangement.match;

import com.intellij.application.options.codeStyle.arrangement.ArrangementConstants;
import com.intellij.application.options.codeStyle.arrangement.color.ArrangementColorsProvider;
import com.intellij.application.options.codeStyle.arrangement.util.TitleWithToolbar;
import com.intellij.ide.ui.customization.CustomizationUtil;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.UiDataProvider;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.psi.codeStyle.arrangement.match.ArrangementSectionRule;
import com.intellij.psi.codeStyle.arrangement.std.ArrangementStandardSettingsManager;
import com.intellij.psi.codeStyle.arrangement.std.StdArrangementRuleAliasToken;
import com.intellij.ui.SideBorder;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.GridBag;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;
import java.util.List;

public final class ArrangementMatchingRulesPanel extends JPanel implements UiDataProvider {

  private final @NotNull ArrangementSectionRulesControl myControl;

  public ArrangementMatchingRulesPanel(@NotNull Language language,
                                       @NotNull ArrangementStandardSettingsManager settingsManager,
                                       @NotNull ArrangementColorsProvider colorsProvider)
  {
    super(new GridBagLayout());
    
    JBScrollPane scrollPane = new JBScrollPane();
    scrollPane.putClientProperty(UIUtil.KEEP_BORDER_SIDES, SideBorder.ALL);
    final JViewport viewport = scrollPane.getViewport();
    ArrangementSectionRulesControl.RepresentationCallback callback = new ArrangementSectionRulesControl.RepresentationCallback() {
      @Override
      public void ensureVisible(@NotNull Rectangle r) {
        Rectangle visibleRect = viewport.getViewRect();
        if (r.y <= visibleRect.y) {
          return;
        }

        int excessiveHeight = r.y + r.height - (visibleRect.y + visibleRect.height);
        if (excessiveHeight <= 0) {
          return;
        }

        int verticalShift = Math.min(r.y - visibleRect.y, excessiveHeight);
        if (verticalShift > 0) {
          viewport.setViewPosition(new Point(visibleRect.x, visibleRect.y + verticalShift));
        }
      }
    };
    myControl = createRulesControl(language, settingsManager, colorsProvider, callback);
    scrollPane.setViewportView(myControl);
    scrollPane.setViewportBorder(JBUI.Borders.emptyRight(scrollPane.getVerticalScrollBar().getPreferredSize().width));
    CustomizationUtil.installPopupHandler(
      myControl, ArrangementConstants.ACTION_GROUP_MATCHING_RULES_CONTEXT_MENU, ArrangementConstants.MATCHING_RULES_CONTROL_PLACE
    );

    TitleWithToolbar top = new TitleWithToolbar(
      ApplicationBundle.message("arrangement.settings.section.match"),
      ArrangementConstants.ACTION_GROUP_MATCHING_RULES_CONTROL_TOOLBAR,
      ArrangementConstants.MATCHING_RULES_CONTROL_TOOLBAR_PLACE,
      myControl
    );
    add(top, new GridBag().coverLine().fillCellHorizontally().weightx(1));
    add(scrollPane, new GridBag().fillCell().weightx(1).weighty(1).insets(0, ArrangementConstants.HORIZONTAL_PADDING, 0, 0));
  }

  private static ArrangementSectionRulesControl createRulesControl(@NotNull Language language,
                                                                   @NotNull ArrangementStandardSettingsManager settingsManager,
                                                                   @NotNull ArrangementColorsProvider colorsProvider,
                                                                   @NotNull ArrangementSectionRulesControl.RepresentationCallback callback) {
    return new ArrangementSectionRulesControl(language, settingsManager, colorsProvider, callback);
  }

  public @NotNull List<ArrangementSectionRule> getSections() {
    return myControl.getSections();
  }

  public void setSections(@Nullable List<? extends ArrangementSectionRule> rules) {
    myControl.setSections(rules);
  }

  public @Nullable Collection<StdArrangementRuleAliasToken> getRulesAliases() {
    return myControl.getRulesAliases();
  }

  public void setRulesAliases(@Nullable Collection<StdArrangementRuleAliasToken> aliases) {
    myControl.setRulesAliases(aliases);
  }

  public void hideEditor() {
    myControl.hideEditor();
  }

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink) {
    sink.set(ArrangementSectionRulesControl.KEY, myControl);
  }
}
