/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ContentRevision;
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

public class BaseAnalysisActionDialog extends DialogWrapper {
  private JPanel myPanel;
  private final String myFileName;
  private final String myModuleName;
  private JRadioButton myProjectButton;
  private JRadioButton myModuleButton;
  private JRadioButton myUncommitedFilesButton;
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
    boolean useModuleScope = false;
    if (myModuleName != null) {
      useModuleScope = myAnalysisOptions.SCOPE_TYPE == AnalysisScope.MODULE;
      myModuleButton.setSelected(myRememberScope && useModuleScope);
    }

    myModuleButton.setVisible(myModuleName != null && ModuleManager.getInstance(myProject).getModules().length > 1);

    boolean useUncommitedFiles = false;
    final ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);
    final boolean hasVCS = !changeListManager.getAffectedFiles().isEmpty();
    if (hasVCS){
      useUncommitedFiles = myAnalysisOptions.SCOPE_TYPE == AnalysisScope.UNCOMMITTED_FILES;
      myUncommitedFilesButton.setSelected(myRememberScope && useUncommitedFiles);
    }
    myUncommitedFilesButton.setVisible(hasVCS);

    DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
    model.addElement(ALL);
    final List<? extends ChangeList> changeLists = changeListManager.getChangeListsCopy();
    for (ChangeList changeList : changeLists) {
      model.addElement(changeList.getName());
    }
    myChangeLists.setRenderer(new ListCellRendererWrapper<String>() {
      @Override
      public void customize(JList list, String value, int index, boolean selected, boolean hasFocus) {
        int availableWidth = myPanel.getWidth() - myUncommitedFilesButton.getWidth() - JBUI.scale(10);
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
    myChangeLists.setEnabled(myUncommitedFilesButton.isSelected());
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

    //custom scope
    myCustomScopeButton.setSelected(myRememberScope && myAnalysisOptions.SCOPE_TYPE == AnalysisScope.CUSTOM);

    myScopeCombo.init(myProject, searchInLib, true, preselect);
    myScopeCombo.setCurrentSelection(false);

    //correct selection
    myFileButton.setSelected(myFileName != null &&
                             (!myRememberScope ||
                             myAnalysisOptions.SCOPE_TYPE != AnalysisScope.PROJECT && !useModuleScope && myAnalysisOptions.SCOPE_TYPE != AnalysisScope.CUSTOM && !useUncommitedFiles));
    if (!myFileButton.isSelected()) {
      myProjectButton.setSelected(myRememberScope && myAnalysisOptions.SCOPE_TYPE == AnalysisScope.PROJECT);
    }

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
    new RadioUpDownListener(myProjectButton, myModuleButton, myUncommitedFilesButton, myFileButton, myCustomScopeButton);
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
    myChangeLists.setEnabled(myUncommitedFilesButton.isSelected());
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

  public boolean isUncommitedFilesSelected(){
    return myUncommitedFilesButton != null && myUncommitedFilesButton.isSelected();
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
      else if (isUncommitedFilesSelected()) {
        final ChangeListManager changeListManager = ChangeListManager.getInstance(project);
        List<VirtualFile> files;
        if (myChangeLists.getSelectedItem() == ALL) {
          files = changeListManager.getAffectedFiles();
        }
        else {
          files = new ArrayList<>();
          for (ChangeList list : changeListManager.getChangeListsCopy()) {
            if (!Comparing.strEqual(list.getName(), (String)myChangeLists.getSelectedItem())) continue;
            final Collection<Change> changes = list.getChanges();
            for (Change change : changes) {
              final ContentRevision afterRevision = change.getAfterRevision();
              if (afterRevision != null) {
                final VirtualFile vFile = afterRevision.getFile().getVirtualFile();
                if (vFile != null) {
                  files.add(vFile);
                }
              }
            }
          }
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
}
