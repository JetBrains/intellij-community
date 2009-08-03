package com.intellij.analysis;

import com.intellij.find.FindSettings;
import com.intellij.ide.util.scopeChooser.ScopeChooserCombo;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.SearchScope;
import com.intellij.ui.TitledSeparator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.List;

/**
 * User: anna
 * Date: Jul 6, 2005
 */
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
  private JComboBox myChangeLists;
  private TitledSeparator myTitledSeparator;
  private final Project myProject;
  private final boolean myRememberScope;
  private final String myAnalysisNoon;
  private ButtonGroup myGroup;

  private static final String ALL = AnalysisScopeBundle.message("scope.option.uncommited.files.all.changelists.choice");
  private final AnalysisUIOptions myAnalysisOptions;

  public BaseAnalysisActionDialog(@NotNull String title,
                                  @NotNull String analysisNoon,
                                  @NotNull Project project,
                                  @NotNull final AnalysisScope scope,
                                  final String moduleName,
                                  final boolean rememberScope,
                                  @NotNull AnalysisUIOptions analysisUIOptions) {
    super(true);
    myAnalysisOptions = analysisUIOptions;
    myProject = project;
    myFileName = scope.getShortenName();
    myModuleName = moduleName;
    myRememberScope = rememberScope;
    myAnalysisNoon = analysisNoon;
    init();
    setTitle(title);
    onScopeRadioButtonPressed();
  }

  public void setOKActionEnabled(boolean isEnabled) {
    super.setOKActionEnabled(isEnabled);
  }

  protected JComponent createCenterPanel() {
    final AnalysisUIOptions uiOptions = myAnalysisOptions;

    myTitledSeparator.setText(myAnalysisNoon);

    //include test option
    myInspectTestSource.setSelected(uiOptions.ANALYZE_TEST_SOURCES);

    //module scope if applicable
    myModuleButton.setText(AnalysisScopeBundle.message("scope.option.module.with.mnemonic", myModuleName));
    boolean useModuleScope = false;
    if (myModuleName != null) {
      useModuleScope = uiOptions.SCOPE_TYPE == AnalysisScope.MODULE;
      myModuleButton.setSelected(myRememberScope && useModuleScope);
    }

    myModuleButton.setVisible(myModuleName != null && ModuleManager.getInstance(myProject).getModules().length > 1);

    boolean useUncommitedFiles = false;
    final ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);
    final boolean hasVCS = !changeListManager.getAffectedFiles().isEmpty();
    if (hasVCS){
      useUncommitedFiles = uiOptions.SCOPE_TYPE == AnalysisScope.UNCOMMITED_FILES;
      myUncommitedFilesButton.setSelected(myRememberScope && useUncommitedFiles);
    }
    myUncommitedFilesButton.setVisible(hasVCS);

    DefaultComboBoxModel model = new DefaultComboBoxModel();
    model.addElement(ALL);
    final List<? extends ChangeList> changeLists = changeListManager.getChangeListsCopy();
    for (ChangeList changeList : changeLists) {
      model.addElement(changeList.getName());
    }
    myChangeLists.setModel(model);
    myChangeLists.setEnabled(myUncommitedFilesButton.isSelected());
    myChangeLists.setVisible(hasVCS);

    //file/package/directory/module scope
    myFileButton.setText(myFileName);
    myFileButton.setMnemonic(myFileName.charAt(0));

    //custom scope
    myCustomScopeButton.setSelected(myRememberScope && uiOptions.SCOPE_TYPE == AnalysisScope.CUSTOM);

    myScopeCombo.init(myProject, uiOptions.CUSTOM_SCOPE_NAME.length() > 0 ? uiOptions.CUSTOM_SCOPE_NAME : FindSettings.getInstance().getDefaultScopeName());

    //correct selection
    myProjectButton.setSelected(myRememberScope && uiOptions.SCOPE_TYPE == AnalysisScope.PROJECT);
    myFileButton.setSelected(!myRememberScope ||
                             uiOptions.SCOPE_TYPE != AnalysisScope.PROJECT && !useModuleScope && uiOptions.SCOPE_TYPE != AnalysisScope.CUSTOM && !useUncommitedFiles);

    myScopeCombo.setEnabled(myCustomScopeButton.isSelected());

    final ActionListener radioButtonPressed = new ActionListener() {
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
    return wholePanel;
  }

  private void onScopeRadioButtonPressed() {
    myScopeCombo.setEnabled(myCustomScopeButton.isSelected());
    myChangeLists.setEnabled(myUncommitedFilesButton.isSelected());
    myInspectTestSource.setEnabled(!myFileButton.isSelected() && !myCustomScopeButton.isSelected());
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

  protected void doOKAction() {
    myAnalysisOptions.CUSTOM_SCOPE_NAME = myScopeCombo.getSelectedScopeName();
    super.doOKAction();
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
          files = new ArrayList<VirtualFile>();
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
        scope = new AnalysisScope(project, new HashSet<VirtualFile>(files));
        uiOptions.SCOPE_TYPE = AnalysisScope.UNCOMMITED_FILES;
      }
      else {
        scope = defaultScope;
        uiOptions.SCOPE_TYPE = defaultScope.getScopeType();//just not project scope
      }
    }
    uiOptions.ANALYZE_TEST_SOURCES = isInspectTestSources();
    scope.setIncludeTestSource(isInspectTestSources());
    return scope;
  }
}
