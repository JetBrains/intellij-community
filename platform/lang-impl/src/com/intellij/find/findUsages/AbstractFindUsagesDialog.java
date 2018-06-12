/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.find.findUsages;

import com.intellij.find.FindBundle;
import com.intellij.find.FindSettings;
import com.intellij.ide.util.scopeChooser.ScopeChooserCombo;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SeparatorFactory;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.StateRestoringCheckBox;
import com.intellij.usageView.UsageViewManager;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author peter
 */
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
    myIsShowInNewTabEnabled = !mustOpenInNewTab && UsageViewManager.getInstance(myProject).getReusableContentsCount() > 0;
    myIsShowInNewTabVisible = !isSingleFile;
    mySearchForTextOccurrencesAvailable = searchForTextOccurrencesAvailable;
    mySearchInLibrariesAvailable = searchInLibrariesAvailable;

    setOKButtonText(FindBundle.message("find.dialog.find.button"));
    setTitle(FindBundle.message(isSingleFile ? "find.usages.in.file.dialog.title" : "find.usages.dialog.title"));
  }

  @NotNull
  @Override
  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
  }

  protected boolean isInFileOnly() {
    return !myIsShowInNewTabVisible;
  }

  @Override
  protected JComponent createNorthPanel() {
    JPanel panel = new JPanel(new GridBagLayout());
    GridBagConstraints gbConstraints = new GridBagConstraints();

    gbConstraints.insets = new Insets(0, 0, UIUtil.DEFAULT_VGAP, 0);
    gbConstraints.fill = GridBagConstraints.NONE;
    gbConstraints.weightx = 1;
    gbConstraints.weighty = 1;
    gbConstraints.anchor = GridBagConstraints.WEST;
    final SimpleColoredComponent coloredComponent = new SimpleColoredComponent();
    coloredComponent.setIpad(new Insets(0,0,0,0));
    coloredComponent.setMyBorder(null);
    configureLabelComponent(coloredComponent);
    panel.add(coloredComponent, gbConstraints);

    return panel;
  }

  public abstract void configureLabelComponent(@NotNull SimpleColoredComponent coloredComponent);

  @Override
  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new GridBagLayout());

    JPanel _panel = new JPanel(new BorderLayout());
    panel.add(_panel, new GridBagConstraints(0, 1, 1, 1, 1.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
                                             new Insets(0, 0, 0, 0), 0, 0));

    if (myIsShowInNewTabVisible) {
      myCbToOpenInNewTab = new JCheckBox(FindBundle.message("find.open.in.new.tab.checkbox"));
      myCbToOpenInNewTab.setSelected(myToShowInNewTab);
      myCbToOpenInNewTab.setEnabled(myIsShowInNewTabEnabled);
      _panel.add(myCbToOpenInNewTab, BorderLayout.EAST);
    }

    JPanel allOptionsPanel = createAllOptionsPanel();
    if (allOptionsPanel != null) {
      panel.add(allOptionsPanel, new GridBagConstraints(0, 0, 1, 1, 1.0, 1.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                                                        new Insets(0, 0, 0, 0), 0, 0));
    }
    return panel;
  }

  @NotNull
  public final FindUsagesOptions calcFindUsagesOptions() {
    calcFindUsagesOptions(myFindUsagesOptions);
    return myFindUsagesOptions;
  }

  @Override
  protected void init() {
    super.init();
    update();
  }

  public void calcFindUsagesOptions(FindUsagesOptions options) {
    options.searchScope = myScopeCombo == null ? GlobalSearchScope.allScope(myProject) : myScopeCombo.getSelectedScope();

    options.isSearchForTextOccurrences = isToChange(myCbToSearchForTextOccurrences) && isSelected(myCbToSearchForTextOccurrences);
  }

  protected void update() {
  }

  public boolean isShowInSeparateWindow() {
    return myCbToOpenInNewTab != null && myCbToOpenInNewTab.isSelected();
  }

  public boolean isSkipResultsWhenOneUsage() {
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

  protected StateRestoringCheckBox addCheckboxToPanel(String name, boolean toSelect, JPanel panel, boolean toUpdate) {
    StateRestoringCheckBox cb = new StateRestoringCheckBox(name);
    cb.setSelected(toSelect);
    panel.add(cb);
    if (toUpdate) {
      cb.addActionListener(___ -> update());
    }
    return cb;
  }

  protected JPanel createAllOptionsPanel() {
    JPanel allOptionsPanel = new JPanel();

    JPanel findWhatPanel = createFindWhatPanel();
    JPanel usagesOptionsPanel = createUsagesOptionsPanel();
    int grids = 0;
    if (findWhatPanel != null) {
      grids++;
    }
    if (usagesOptionsPanel != null) {
      grids++;
    }
    if (grids != 0) {
      allOptionsPanel.setLayout(new GridLayout(1, grids, 8, 0));
      if (findWhatPanel != null) {
        allOptionsPanel.add(findWhatPanel);
      }
      if (usagesOptionsPanel != null) {
        allOptionsPanel.add(usagesOptionsPanel);
      }
    }

    JComponent scopePanel = createSearchScopePanel();
    if (scopePanel != null) {
      JPanel panel = new JPanel(new BorderLayout());
      panel.add(allOptionsPanel, BorderLayout.NORTH);
      panel.add(scopePanel, BorderLayout.SOUTH);
      return panel;
    }

    return allOptionsPanel;
  }

  @Nullable
  protected abstract JPanel createFindWhatPanel();

  protected void addUsagesOptions(JPanel optionsPanel) {
    if (mySearchForTextOccurrencesAvailable) {
      myCbToSearchForTextOccurrences = addCheckboxToPanel(FindBundle.message("find.options.search.for.text.occurences.checkbox"),
                                                         myFindUsagesOptions.isSearchForTextOccurrences, optionsPanel, false);

    }

    if (myIsShowInNewTabVisible) {
      myCbToSkipResultsWhenOneUsage = addCheckboxToPanel(FindBundle.message("find.options.skip.results.tab.with.one.usage.checkbox"),
                                                         FindSettings.getInstance().isSkipResultsWithOneUsage(), optionsPanel, false);

    }
  }

  @Nullable
  protected JPanel createUsagesOptionsPanel() {
    JPanel optionsPanel = new JPanel();
    optionsPanel.setBorder(IdeBorderFactory.createTitledBorder(FindBundle.message("find.options.group"), true));
    optionsPanel.setLayout(new BoxLayout(optionsPanel, BoxLayout.Y_AXIS));
    addUsagesOptions(optionsPanel);
    return optionsPanel.getComponents().length == 0 ? null : optionsPanel;
  }

  @Nullable
  private JComponent createSearchScopePanel() {
    if (isInFileOnly()) return null;
    JPanel optionsPanel = new JPanel(new BorderLayout());
    String scope = myFindUsagesOptions.searchScope.getDisplayName();
    myScopeCombo = new ScopeChooserCombo(myProject, mySearchInLibrariesAvailable, true, scope);
    Disposer.register(myDisposable, myScopeCombo);
    optionsPanel.add(myScopeCombo, BorderLayout.CENTER);
    JComponent separator = SeparatorFactory.createSeparator(FindBundle.message("find.scope.label"), myScopeCombo.getComboBox());
    optionsPanel.add(separator, BorderLayout.NORTH);
    return optionsPanel;
  }

  @Nullable
  protected JComponent getPreferredFocusedControl() {
    return null;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    if (myScopeCombo != null) {
      return myScopeCombo.getComboBox();
    }
    return getPreferredFocusedControl();
  }


}
