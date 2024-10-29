// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.findUsages;

import com.intellij.find.FindBundle;
import com.intellij.find.FindSettings;
import com.intellij.find.impl.FindSettingsImpl;
import com.intellij.ide.util.scopeChooser.ScopeChooserCombo;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.panel.ComponentPanelBuilder;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.RelativeFont;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.StateRestoringCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.usageView.UsageViewContentManager;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.util.Objects;

public abstract class AbstractFindUsagesDialog extends DialogWrapper {
  private final Project myProject;
  protected final FindUsagesOptions myFindUsagesOptions;

  private final boolean myToShowInNewTab;
  private final boolean myIsShowInNewTabEnabled;
  private final boolean myIsShowInNewTabVisible;

  private final boolean mySearchForTextOccurrencesAvailable;

  private final boolean mySearchInLibrariesAvailable;

  private JCheckBox myCbToOpenInNewTab;

  protected StateRestoringCheckBox myCbToSearchForTextOccurrences;
  protected JCheckBox myCbToSkipResultsWhenOneUsage;

  private ScopeChooserCombo myScopeCombo;
  private int myFindOptionsCount;

  protected AbstractFindUsagesDialog(@NotNull Project project,
                                     @NotNull FindUsagesOptions findUsagesOptions,
                                     boolean toShowInNewTab,
                                     boolean mustOpenInNewTab,
                                     boolean isSingleFile,
                                     boolean searchForTextOccurrencesAvailable,
                                     boolean searchInLibrariesAvailable) {
    super(project, true);
    myProject = project;
    myFindUsagesOptions = findUsagesOptions;
    myToShowInNewTab = toShowInNewTab;
    myIsShowInNewTabEnabled = !mustOpenInNewTab && UsageViewContentManager.getInstance(myProject).getReusableContentsCount() > 0;
    myIsShowInNewTabVisible = !isSingleFile;
    mySearchForTextOccurrencesAvailable = searchForTextOccurrencesAvailable;
    mySearchInLibrariesAvailable = searchInLibrariesAvailable;
    if (myFindUsagesOptions instanceof PersistentFindUsagesOptions) {
      ((PersistentFindUsagesOptions)myFindUsagesOptions).setDefaults(myProject);
    }

    setOKButtonText(FindBundle.message("find.dialog.find.button"));
    setTitle(FindBundle.message(isSingleFile ? "find.usages.in.file.dialog.title" : "find.usages.dialog.title"));
  }

  @ApiStatus.Internal
  void waitWithModalProgressUntilInitialized() {
    if (myScopeCombo != null) { // some dialogs don't even initialize, consider initialization complete for them
      myScopeCombo.waitWithModalProgressUntilInitialized();
    }
  }

  @Override
  protected Action @NotNull [] createActions() {
    return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
  }

  protected boolean isInFileOnly() {
    return !myIsShowInNewTabVisible;
  }

  @Override
  protected JComponent createNorthPanel() {
    JPanel panel = new JPanel(new GridBagLayout());
    GridBagConstraints gbConstraints = new GridBagConstraints();

    gbConstraints.insets = JBUI.insetsBottom(UIUtil.DEFAULT_VGAP);
    gbConstraints.fill = GridBagConstraints.NONE;
    gbConstraints.weightx = 1;
    gbConstraints.weighty = 1;
    gbConstraints.anchor = GridBagConstraints.WEST;
    final SimpleColoredComponent coloredComponent = new SimpleColoredComponent();
    coloredComponent.setIpad(JBInsets.emptyInsets());
    coloredComponent.setMyBorder(null);
    configureLabelComponent(coloredComponent);
    panel.add(coloredComponent, gbConstraints);

    return panel;
  }

  public abstract void configureLabelComponent(@NotNull SimpleColoredComponent coloredComponent);

  @Override
  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new GridBagLayout());

    JPanel allOptionsPanel = createAllOptionsPanel();
    if (allOptionsPanel != null) {
      panel.add(allOptionsPanel, new GridBagConstraints(0, 0, 1, 1, 1.0, 1.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                                                        JBInsets.emptyInsets(), 0, 0));
    }

    if (myIsShowInNewTabVisible) {
      myCbToOpenInNewTab = new JCheckBox(FindBundle.message("find.open.in.new.tab.checkbox"));
      myCbToOpenInNewTab.setSelected(myToShowInNewTab);
      myCbToOpenInNewTab.setEnabled(myIsShowInNewTabEnabled);

      panel.add(myCbToOpenInNewTab, new GridBagConstraints(0, 1, 1, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL,
                                                           JBUI.insets(15, 0, 13, 0), 0, 0));
    }

    return panel;
  }

  public final @NotNull FindUsagesOptions calcFindUsagesOptions() {
    calcFindUsagesOptions(myFindUsagesOptions);
    if (myFindUsagesOptions instanceof PersistentFindUsagesOptions) {
      ((PersistentFindUsagesOptions)myFindUsagesOptions).storeDefaults(myProject);
    }
    FindUsagesStatisticsCollector.logOptions(myProject, myFindUsagesOptions, isShowInSeparateWindow());
    return myFindUsagesOptions;
  }

  @Override
  protected void init() {
    super.init();
    update();
  }

  public void calcFindUsagesOptions(FindUsagesOptions options) {
    var noUserSelectedScope = myScopeCombo == null || myScopeCombo.getSelectedScope() == null;
    if (noUserSelectedScope) {
      // This happens when the dialog doesn't even have a scope combo box, e.g., when searching for usages of a private method.
      // In this case, we use the "All" scope, and we don't save it, as it doesn't make any sense.
      options.searchScope = GlobalSearchScope.allScope(myProject);
    }
    else {
      options.searchScope = myScopeCombo.getSelectedScope();
      FindSettings.getInstance().setDefaultScopeName(options.searchScope.getDisplayName());
    }

    options.isSearchForTextOccurrences = isToChange(myCbToSearchForTextOccurrences) && isSelected(myCbToSearchForTextOccurrences);
  }

  protected void update() {
  }

  boolean isShowInSeparateWindow() {
    return myCbToOpenInNewTab != null && myCbToOpenInNewTab.isSelected();
  }

  private boolean isSkipResultsWhenOneUsage() {
    return myCbToSkipResultsWhenOneUsage != null && myCbToSkipResultsWhenOneUsage.isSelected();
  }

  @Override
  protected void doOKAction() {
    if (!shouldDoOkAction()) return;

    FindSettings settings = FindSettings.getInstance();

    if (myScopeCombo != null) {
      settings.setDefaultScopeName(myScopeCombo.getSelectedScopeName());
    }
    if (mySearchForTextOccurrencesAvailable && myCbToSearchForTextOccurrences != null && myCbToSearchForTextOccurrences.isEnabled()) {
      myFindUsagesOptions.isSearchForTextOccurrences = myCbToSearchForTextOccurrences.isSelected();
    }

    if (myCbToSkipResultsWhenOneUsage != null) {
      settings.setSkipResultsWithOneUsage(isSkipResultsWhenOneUsage());
    }

    super.doOKAction();
  }

  protected boolean shouldDoOkAction() {
    return myScopeCombo == null || myScopeCombo.getSelectedScope() != null;
  }

  protected static boolean isToChange(JCheckBox cb) {
    return cb != null && cb.getParent() != null;
  }

  protected static boolean isSelected(JCheckBox cb) {
    return cb != null && cb.getParent() != null && cb.isSelected();
  }

  protected @NotNull StateRestoringCheckBox addCheckboxToPanel(@NlsContexts.Checkbox String name, boolean toSelect, @NotNull JPanel panel, boolean toUpdate) {
    StateRestoringCheckBox cb = createCheckbox(name, toSelect, toUpdate);
    JComponent decoratedCheckbox = new ComponentPanelBuilder(cb).createPanel();
    decoratedCheckbox.setAlignmentX(Component.LEFT_ALIGNMENT);
    decoratedCheckbox.setBorder(JBUI.Borders.emptyBottom(8));

    panel.add(decoratedCheckbox);
    return cb;
  }

  protected @NotNull StateRestoringCheckBox createCheckbox(@NlsContexts.Checkbox String name, boolean toSelect, boolean toUpdate) {
    StateRestoringCheckBox cb = new StateRestoringCheckBox(name);
    cb.setSelected(toSelect);
    cb.setAlignmentX(Component.LEFT_ALIGNMENT);
    if (toUpdate) {
      cb.addActionListener(___ -> update());
    }
    myFindOptionsCount++;
    return cb;
  }

  protected JPanel createAllOptionsPanel() {
    JPanel findWhatPanel = createFindWhatPanel();
    if (findWhatPanel != null) {
      addUsagesOptions(findWhatPanel);

      if (myFindOptionsCount > 2) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new Title(FindBundle.message("find.what.group"), JBUI.Borders.empty(4, 0, 8, 0), null), BorderLayout.NORTH);
        findWhatPanel.setBorder(JBUI.Borders.emptyLeft(17));
        panel.add(findWhatPanel, BorderLayout.CENTER);

        findWhatPanel = panel;
      }
    }

    JComponent scopePanel = createSearchScopePanel();
    if (scopePanel != null) {
      JPanel panel = new JPanel(new BorderLayout());
      if (findWhatPanel != null) {
        panel.add(findWhatPanel, BorderLayout.NORTH);
        scopePanel.setBorder(JBUI.Borders.emptyTop(9));
      }
      panel.add(scopePanel, BorderLayout.SOUTH);
      return panel;
    }
    else if (findWhatPanel != null && myFindOptionsCount <= 2) {
      findWhatPanel.setBorder(JBUI.Borders.emptyTop(9));
    }

    return findWhatPanel;
  }

  protected @Nullable JPanel createFindWhatPanel() {
    if (mySearchForTextOccurrencesAvailable || myIsShowInNewTabVisible) {
      JPanel findWhatPanel = new JPanel();
      findWhatPanel.setLayout(new BoxLayout(findWhatPanel, BoxLayout.Y_AXIS));
      return findWhatPanel;
    }
    return null;
  }

  protected void addUsagesOptions(@NotNull JPanel panel) {
    if (mySearchForTextOccurrencesAvailable) {
      myCbToSearchForTextOccurrences = addCheckboxToPanel(FindBundle.message("find.options.search.for.text.occurrences.checkbox"),
                                                         myFindUsagesOptions.isSearchForTextOccurrences, panel, false);
    }

    if (myIsShowInNewTabVisible) {
      myCbToSkipResultsWhenOneUsage = addCheckboxToPanel(FindBundle.message("find.options.skip.results.tab.with.one.usage.checkbox"),
                                                         FindSettings.getInstance().isSkipResultsWithOneUsage(), panel, false);
    }
  }

  private @Nullable JComponent createSearchScopePanel() {
    if (isInFileOnly()) return null;
    JPanel optionsPanel = new JPanel(new BorderLayout());
    String scope = FindSettings.getInstance().getDefaultScopeName();
    // The default name means we have to fall back to whatever the default scope is set in FindUsagesOptions.
    // (The default name itself doesn't correspond to any real scope name anyway.)
    if (Objects.equals(scope, FindSettingsImpl.getDefaultSearchScope())) {
      scope = FindUsagesOptions.getDefaultScope(myProject).getDisplayName();
    }
    myScopeCombo = new ScopeChooserCombo(myProject, mySearchInLibrariesAvailable, true, scope);
    Disposer.register(myDisposable, myScopeCombo);
    optionsPanel.add(myScopeCombo, BorderLayout.CENTER);

    Title scopeTitle = new Title(FindBundle.message("find.scope.label"), JBUI.Borders.emptyBottom(4), myScopeCombo.getComboBox());
    optionsPanel.add(scopeTitle, BorderLayout.NORTH);
    return optionsPanel;
  }

  protected @Nullable JComponent getPreferredFocusedControl() {
    return null;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    if (myScopeCombo != null) {
      return myScopeCombo.getComboBox();
    }
    return getPreferredFocusedControl();
  }

  private static final class Title extends JPanel {
    private Title(@NlsContexts.Separator String text, @NotNull Border border, @Nullable JComponent labelFor) {
      setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
      setBorder(border);
      setAlignmentX(Component.LEFT_ALIGNMENT);

      add(new TitleLabel(text, labelFor));
      add(Box.createVerticalGlue());
    }
  }

  private static final class TitleLabel extends JBLabel {
    private @NlsContexts.Separator String originalText;

    private TitleLabel(@NlsContexts.Separator String text, @Nullable JComponent labelFor) {
      originalText = text;
      setLabelFor(labelFor);
      updateLabelFont();
    }

    @Override
    public void updateUI() {
      super.updateUI();
      updateLabelFont();
    }

    @Override
    public @NlsContexts.Separator String getText() {
      return originalText;
    }

    @Override
    public void setText(@NlsContexts.Separator String text) {
      originalText = text;
      super.setText(text != null && text.startsWith("<html>") ? text : UIUtil.replaceMnemonicAmpersand(originalText));
    }

    private void updateLabelFont() {
      Font labelFont = StartupUiUtil.getLabelFont();
      setFont(RelativeFont.NORMAL.fromResource("TitledSeparator.fontSizeOffset", 0).derive(labelFont));
    }
  }
}
