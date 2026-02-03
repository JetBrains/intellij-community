// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.codeStyle.arrangement;

import com.intellij.application.options.CodeStyleAbstractPanel;
import com.intellij.application.options.codeStyle.arrangement.additional.ForceArrangementPanel;
import com.intellij.application.options.codeStyle.arrangement.color.ArrangementColorsProvider;
import com.intellij.application.options.codeStyle.arrangement.color.ArrangementColorsProviderImpl;
import com.intellij.application.options.codeStyle.arrangement.group.ArrangementGroupingRulesPanel;
import com.intellij.application.options.codeStyle.arrangement.match.ArrangementMatchingRulesPanel;
import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NlsContexts.TabTitle;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.arrangement.Rearranger;
import com.intellij.psi.codeStyle.arrangement.group.ArrangementGroupingRule;
import com.intellij.psi.codeStyle.arrangement.match.ArrangementSectionRule;
import com.intellij.psi.codeStyle.arrangement.std.*;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.GridBag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;


public class ArrangementSettingsPanel extends CodeStyleAbstractPanel {

  private final @NotNull JPanel myContent = new JPanel(new GridBagLayout());

  private final @NotNull Language                         myLanguage;
  private final @NotNull ArrangementStandardSettingsAware mySettingsAware;
  private final @NotNull ArrangementGroupingRulesPanel    myGroupingRulesPanel;
  private final @NotNull ArrangementMatchingRulesPanel    myMatchingRulesPanel;
  private final @Nullable ForceArrangementPanel myForceArrangementPanel;

  public ArrangementSettingsPanel(@NotNull CodeStyleSettings settings, @NotNull Language language) {
    super(settings);
    myLanguage = language;
    Rearranger<?> rearranger = Rearranger.EXTENSION.forLanguage(language);

    if (!(rearranger instanceof ArrangementStandardSettingsAware)) {
      throw new IllegalArgumentException("Incorrect rearranger for " + language.getID() + " language: " + rearranger);
    }
    mySettingsAware = (ArrangementStandardSettingsAware)rearranger;

    final ArrangementColorsProvider colorsProvider;
    if (rearranger instanceof ArrangementColorsAware) {
      colorsProvider = new ArrangementColorsProviderImpl((ArrangementColorsAware)rearranger);
    }
    else {
      colorsProvider = new ArrangementColorsProviderImpl(null);
    }

    ArrangementStandardSettingsManager settingsManager = new ArrangementStandardSettingsManager(mySettingsAware, colorsProvider);

    myGroupingRulesPanel = new ArrangementGroupingRulesPanel(settingsManager, colorsProvider);
    myMatchingRulesPanel = new ArrangementMatchingRulesPanel(myLanguage, settingsManager, colorsProvider);

    myContent.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));
    myContent.add(myGroupingRulesPanel, new GridBag().coverLine().fillCellHorizontally().weightx(1));
    myContent.add(myMatchingRulesPanel, new GridBag().fillCell().weightx(1).weighty(1).coverLine());

    if (settings.getCommonSettings(myLanguage).isForceArrangeMenuAvailable()) {
      myForceArrangementPanel = new ForceArrangementPanel();
      myForceArrangementPanel.setForceRearrangeMode(settings.getCommonSettings(language).FORCE_REARRANGE_MODE);
      myContent.add(myForceArrangementPanel.getPanel(), new GridBag().anchor(GridBagConstraints.WEST).coverLine().fillCellHorizontally());
    }
    else {
      myForceArrangementPanel = null;
    }

    final List<CompositeArrangementSettingsToken> groupingTokens = settingsManager.getSupportedGroupingTokens();
    myGroupingRulesPanel.setVisible(groupingTokens != null && !groupingTokens.isEmpty());
  }

  @Override
  public @Nullable JComponent getPanel() {
    return myContent;
  }

  @Override
  protected @Nullable EditorHighlighter createHighlighter(@NotNull EditorColorsScheme scheme) {
    return null;
  }

  private @Nullable StdArrangementSettings getSettings(@NotNull CodeStyleSettings settings) {
    StdArrangementSettings result = (StdArrangementSettings)settings.getCommonSettings(myLanguage).getArrangementSettings();
    if (result == null) {
      result = mySettingsAware.getDefaultSettings();
    }
    return result;
  }

  @Override
  public void apply(@NotNull CodeStyleSettings settings) {
    CommonCodeStyleSettings commonSettings = settings.getCommonSettings(myLanguage);
    commonSettings.setArrangementSettings(createSettings());
    if (myForceArrangementPanel != null) {
      commonSettings.FORCE_REARRANGE_MODE = myForceArrangementPanel.getForceRearrangeMode();
    }
  }

  @Override
  public boolean isModified(CodeStyleSettings settings) {
    final StdArrangementSettings s = createSettings();
    return !Comparing.equal(getSettings(settings), s)
           || myForceArrangementPanel != null && settings.getCommonSettings(myLanguage).FORCE_REARRANGE_MODE != myForceArrangementPanel.getForceRearrangeMode();
  }

  private StdArrangementSettings createSettings() {
    final List<ArrangementGroupingRule> groupingRules = myGroupingRulesPanel.getRules();
    final List<ArrangementSectionRule> sections = myMatchingRulesPanel.getSections();
    final Collection<StdArrangementRuleAliasToken> tokens = myMatchingRulesPanel.getRulesAliases();
    if (tokens != null) {
      return new StdArrangementExtendableSettings(groupingRules, sections, tokens);
    }
    return new StdArrangementSettings(groupingRules, sections);
  }

  @Override
  protected void resetImpl(@NotNull CodeStyleSettings settings) {
    StdArrangementSettings s = getSettings(settings);
    if (s == null) {
      myGroupingRulesPanel.setRules(null);
      myMatchingRulesPanel.setSections(null);
    }
    else {
      List<ArrangementGroupingRule> groupings = s.getGroupings();
      myGroupingRulesPanel.setRules(new ArrayList<>(groupings));
      myMatchingRulesPanel.setSections(copy(s.getSections()));
      if (s instanceof StdArrangementExtendableSettings) {
        myMatchingRulesPanel.setRulesAliases(((StdArrangementExtendableSettings)s).getRuleAliases());
      }

      if (myForceArrangementPanel != null) {
        myForceArrangementPanel.setForceRearrangeMode(settings.getCommonSettings(myLanguage).FORCE_REARRANGE_MODE);
      }
    }
  }

  @Override
  protected @Nullable String getPreviewText() {
    return null;
  }

  @Override
  protected @TabTitle @NotNull String getTabTitle() {
    return ApplicationBundle.message("arrangement.title.settings.tab");
  }

  @Override
  protected @NotNull FileType getFileType() {
    Logger.getInstance(ArrangementSettingsPanel.class).error("This method should not be called because getPreviewText() returns null");
    return ObjectUtils.notNull(myLanguage.getAssociatedFileType(), FileTypes.UNKNOWN);
  }

  @Override
  protected int getRightMargin() {
    Logger.getInstance(ArrangementSettingsPanel.class).error("This method should not be called because getPreviewText() returns null");
    return 0;
  }

  private static @NotNull List<ArrangementSectionRule> copy(@NotNull List<ArrangementSectionRule> rules) {
    List<ArrangementSectionRule> result = new ArrayList<>();
    for (ArrangementSectionRule rule : rules) {
      result.add(rule.clone());
    }
    return result;
  }
}
