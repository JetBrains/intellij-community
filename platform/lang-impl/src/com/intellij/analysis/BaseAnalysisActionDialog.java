/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package com.intellij.analysis;

import com.intellij.find.FindSettings;
import com.intellij.ide.util.scopeChooser.ScopeChooserCombo;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.refactoring.util.RadioUpDownListener;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.ui.TitledSeparator;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaSourceRootType;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class BaseAnalysisActionDialog extends DialogWrapper {
  private JPanel myPanel;
  private final String myFileName;
  private final String myModuleName;
  private JRadioButton myProjectButton;
  private JRadioButton myModuleButton;
  private JRadioButton myUncommittedFilesButton;
  private JRadioButton myCustomScopeButton;
  private JRadioButton myFileButton;
  private ScopeChooserCombo myScopeCombo;
  private JCheckBox myInspectTestSource;
  private JComboBox<String> myChangeLists;
  private TitledSeparator myTitledSeparator;
  private final Project myProject;
  private final boolean myRememberScope;
  private final String myAnalysisNoon;
  private ButtonGroup myGroup;

  private static final String ALL = AnalysisScopeBundle.message("scope.option.uncommitted.files.all.changelists.choice");
  private final AnalysisUIOptions myAnalysisOptions;
  @Nullable private final PsiElement myContext;

  public BaseAnalysisActionDialog(@NotNull String title,
                                  @NotNull String analysisNoon,
                                  @NotNull Project project,
                                  @NotNull final AnalysisScope scope,
                                  @Nullable Module module,
                                  final boolean rememberScope,
                                  @NotNull AnalysisUIOptions analysisUIOptions,
                                  @Nullable PsiElement context) {
    //noinspection deprecation
    this(title, analysisNoon, project, scope, module == null ? null : module.getName(), rememberScope, analysisUIOptions, context);
  }

  @Deprecated
  public BaseAnalysisActionDialog(@NotNull String title,
                                  @NotNull String analysisNoon,
                                  @NotNull Project project,
                                  @NotNull final AnalysisScope scope,
                                  final String moduleName,
                                  final boolean rememberScope,
                                  @NotNull AnalysisUIOptions analysisUIOptions,
                                  @Nullable PsiElement context) {
    super(true);
    //noinspection BoundFieldAssignment
    myGroup = new ButtonGroup();
    myGroup.add(myProjectButton);
    myGroup.add(myModuleButton);
    myGroup.add(myUncommittedFilesButton);
    myGroup.add(myFileButton);
    myGroup.add(myCustomScopeButton);

    Disposer.register(myDisposable, myScopeCombo);
    myAnalysisOptions = analysisUIOptions;
    myContext = context;
    if (!analysisUIOptions.ANALYZE_TEST_SOURCES) {
      myAnalysisOptions.ANALYZE_TEST_SOURCES = scope.isAnalyzeTestsByDefault();
    }
    myProject = project;
    myFileName = scope.getScopeType() == AnalysisScope.PROJECT ? null : scope.getShortenName();
    myModuleName = moduleName;
    myRememberScope = rememberScope;
    myAnalysisNoon = analysisNoon;
    init();
    setTitle(title);
    onScopeRadioButtonPressed();
  }

  @Override
  protected JComponent createCenterPanel() {
    myTitledSeparator.setText(myAnalysisNoon);

    //include test option
    myInspectTestSource.setSelected(myAnalysisOptions.ANALYZE_TEST_SOURCES);
    myInspectTestSource.setVisible(ModuleUtil.isSupportedRootType(myProject, JavaSourceRootType.TEST_SOURCE));

    //module scope if applicable
    myModuleButton.setText(AnalysisScopeBundle.message("scope.option.module.with.mnemonic", myModuleName));
    myModuleButton.setVisible(myModuleName != null && ModuleManager.getInstance(myProject).getModules().length > 1);

    final ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);
    final boolean hasVCS = !changeListManager.getAffectedFiles().isEmpty();
    myUncommittedFilesButton.setVisible(hasVCS);

    DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
    model.addElement(ALL);
    final List<? extends ChangeList> changeLists = changeListManager.getChangeListsCopy();
    for (ChangeList changeList : changeLists) {
      model.addElement(changeList.getName());
    }
    myChangeLists.setRenderer(new ListCellRendererWrapper<String>() {
      @Override
      public void customize(JList list, String value, int index, boolean selected, boolean hasFocus) {
        int availableWidth = myPanel.getWidth() - myUncommittedFilesButton.getWidth() - JBUI.scale(10);
        if (availableWidth <= 0) {
          availableWidth = JBUI.scale(200);
        }
        if (list.getFontMetrics(list.getFont()).stringWidth(value) < availableWidth) {
          setText(value);
        }
        else {
          setText(StringUtil.trimLog(value, 50));
        }
      }
    });

    myChangeLists.setModel(model);
    myChangeLists.setEnabled(myUncommittedFilesButton.isSelected());
    myChangeLists.setVisible(hasVCS);

    //file/package/directory/module scope
    if (myFileName != null) {
      myFileButton.setText(myFileName);
      myFileButton.setMnemonic(myFileName.charAt(getSelectedScopeMnemonic()));
    } else {
      myFileButton.setVisible(false);
    }

    VirtualFile file = PsiUtilCore.getVirtualFile(myContext);
    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
    boolean searchInLib = file != null && (fileIndex.isInLibraryClasses(file) || fileIndex.isInLibrarySource(file));

    String preselect = StringUtil.isEmptyOrSpaces(myAnalysisOptions.CUSTOM_SCOPE_NAME)
                       ? FindSettings.getInstance().getDefaultScopeName()
                       : myAnalysisOptions.CUSTOM_SCOPE_NAME;
    if (searchInLib && GlobalSearchScope.projectScope(myProject).getDisplayName().equals(preselect)) {
      preselect = GlobalSearchScope.allScope(myProject).getDisplayName();
    }
    if (GlobalSearchScope.allScope(myProject).getDisplayName().equals(preselect) && myAnalysisOptions.SCOPE_TYPE == AnalysisScope.CUSTOM) {
      myAnalysisOptions.CUSTOM_SCOPE_NAME = preselect;
      searchInLib = true;
    }

    boolean someButtonEnabled = false;
    if (myRememberScope) {
      switch (myAnalysisOptions.SCOPE_TYPE) {
        case AnalysisScope.PROJECT:
          someButtonEnabled = select(myProjectButton);
          break;
        case AnalysisScope.MODULE:
          someButtonEnabled = select(myModuleButton);
          break;
        case AnalysisScope.FILE:
          someButtonEnabled = select(myFileButton);
          break;
        case AnalysisScope.UNCOMMITTED_FILES:
          someButtonEnabled = select(myUncommittedFilesButton);
          break;
        case AnalysisScope.CUSTOM:
          someButtonEnabled = select(myCustomScopeButton);
          break;
      }
    } else {
      if (!(someButtonEnabled = select(myFileButton))) {
        select(myModuleButton);
      }
    }
    if (!someButtonEnabled) {
      select(myProjectButton);
    }

    myScopeCombo.init(myProject, searchInLib, true, preselect);
    myScopeCombo.setCurrentSelection(false);
    myScopeCombo.setEnabled(myCustomScopeButton.isSelected());

    final ActionListener radioButtonPressed = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        onScopeRadioButtonPressed();
      }
    };
    final Enumeration<AbstractButton> enumeration = myGroup.getElements();
    while (enumeration.hasMoreElements()) {
      enumeration.nextElement().addActionListener(radioButtonPressed);
    }

    //additional panel - inspection profile chooser
    JPanel wholePanel = new JPanel(new BorderLayout());
    wholePanel.add(myPanel, BorderLayout.NORTH);
    final JComponent additionalPanel = getAdditionalActionSettings(myProject);
    if (additionalPanel!= null){
      wholePanel.add(additionalPanel, BorderLayout.CENTER);
    }
    new RadioUpDownListener(myProjectButton, myModuleButton, myUncommittedFilesButton, myFileButton, myCustomScopeButton);
    return wholePanel;
  }

  private int getSelectedScopeMnemonic() {

    final int fileIdx = StringUtil.indexOfIgnoreCase(myFileName, "file", 0);
    if (fileIdx > -1) {
      return fileIdx;
    }

    final int dirIdx = StringUtil.indexOfIgnoreCase(myFileName, "directory", 0);
    if (dirIdx > -1) {
      return dirIdx;
    }

    return 0;
  }

  private void onScopeRadioButtonPressed() {
    myScopeCombo.setEnabled(myCustomScopeButton.isSelected());
    myChangeLists.setEnabled(myUncommittedFilesButton.isSelected());
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    final Enumeration<AbstractButton> enumeration = myGroup.getElements();
    while (enumeration.hasMoreElements()) {
      final AbstractButton button = enumeration.nextElement();
      if (button.isSelected()) {
        return button;
      }
    }
    return myPanel;
  }

  @Nullable
  protected JComponent getAdditionalActionSettings(final Project project) {
    return null;
  }

  public boolean isProjectScopeSelected() {
    return myProjectButton.isSelected();
  }

  public boolean isModuleScopeSelected() {
    return myModuleButton != null && myModuleButton.isSelected();
  }

  public boolean isUncommittedFilesSelected(){
    return myUncommittedFilesButton != null && myUncommittedFilesButton.isSelected();
  }

  @Nullable
  public SearchScope getCustomScope(){
    if (myCustomScopeButton.isSelected()){
      return myScopeCombo.getSelectedScope();
    }
    return null;
  }

  public boolean isInspectTestSources(){
    return myInspectTestSource.isSelected();
  }

  @NotNull
  public AnalysisScope getScope(@NotNull AnalysisUIOptions uiOptions, @NotNull AnalysisScope defaultScope, @NotNull Project project, Module module) {
    AnalysisScope scope;
    if (isProjectScopeSelected()) {
      scope = new AnalysisScope(project);
      uiOptions.SCOPE_TYPE = AnalysisScope.PROJECT;
    }
    else {
      final SearchScope customScope = getCustomScope();
      if (customScope != null) {
        scope = new AnalysisScope(customScope, project);
        uiOptions.SCOPE_TYPE = AnalysisScope.CUSTOM;
        uiOptions.CUSTOM_SCOPE_NAME = customScope.getDisplayName();
      }
      else if (isModuleScopeSelected()) {
        scope = new AnalysisScope(module);
        uiOptions.SCOPE_TYPE = AnalysisScope.MODULE;
      }
      else if (isUncommittedFilesSelected()) {
        final ChangeListManager changeListManager = ChangeListManager.getInstance(project);
        List<VirtualFile> files;
        if (myChangeLists.getSelectedItem() == ALL) {
          files = changeListManager.getAffectedFiles();
        }
        else {
          files = changeListManager
            .getChangeListsCopy()
            .stream()
            .filter(l -> Comparing.strEqual(l.getName(), (String)myChangeLists.getSelectedItem()))
            .flatMap(l -> ChangesUtil.getAfterRevisionsFiles(l.getChanges().stream()))
            .collect(Collectors.toList());
        }
        scope = new AnalysisScope(project, new HashSet<>(files));
        uiOptions.SCOPE_TYPE = AnalysisScope.UNCOMMITTED_FILES;
      }
      else {
        scope = defaultScope;
        uiOptions.SCOPE_TYPE = defaultScope.getScopeType();//just not project scope
      }
    }
    uiOptions.ANALYZE_TEST_SOURCES = isInspectTestSources();
    scope.setIncludeTestSource(isInspectTestSources());

    FindSettings.getInstance().setDefaultScopeName(scope.getDisplayName());
    return scope;
  }

  private static boolean select(JRadioButton button) {
    if (button.isVisible()) {
      button.setSelected(true);
      return true;
    }
    return false;
  }
}
