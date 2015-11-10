/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.execution.testDiscovery;

import com.intellij.application.options.ModulesComboBox;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.MethodBrowser;
import com.intellij.execution.ui.*;
import com.intellij.ide.util.ClassFilter;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.psi.JavaCodeFragment;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.EditorTextFieldWithBrowseButton;
import com.intellij.ui.PanelWithAnchor;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;


public class TestDiscoveryConfigurable<T extends TestDiscoveryConfiguration> extends SettingsEditor<T> implements PanelWithAnchor {
  private final ConfigurationModuleSelector myModuleSelector;
  // Fields
  private JPanel myWholePanel = new JPanel(new BorderLayout());
  private LabeledComponent<ModulesComboBox> myModule = new LabeledComponent<ModulesComboBox>();
  private CommonJavaParametersPanel myCommonJavaParameters = new CommonJavaParametersPanel();
  private JrePathEditor myJrePathEditor;
  private LabeledComponent<EditorTextFieldWithBrowseButton> myClass = new LabeledComponent<EditorTextFieldWithBrowseButton>();
  private LabeledComponent<EditorTextFieldWithBrowseButton> myMethod = new LabeledComponent<EditorTextFieldWithBrowseButton>();

  private ComboBox myChangeLists = new ComboBox();
  private JRadioButton myPositionRb = new JRadioButton("Tests for method:");
  private JRadioButton myChangesRb = new JRadioButton("Tests for change list:");
  private JComponent anchor;

  public TestDiscoveryConfigurable(final Project project) {
    myModule.setText(ExecutionBundle.message("application.configuration.use.classpath.and.jdk.of.module.label"));
    myModule.setLabelLocation(BorderLayout.WEST);
    myModule.setComponent(new ModulesComboBox());
    myModuleSelector = new ConfigurationModuleSelector(project, getModulesComponent());
    myCommonJavaParameters.setModuleContext(myModuleSelector.getModule());
    myCommonJavaParameters.setHasModuleMacro();
    myModule.getComponent().addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myCommonJavaParameters.setModuleContext(myModuleSelector.getModule());
      }
    });
    final JPanel panelWithSettings = new JPanel(new GridBagLayout());
    final GridBagConstraints gc = new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1, 0,
                                                         GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL,
                                                         new Insets(0, 0, 0, 0), 0, 0);
    panelWithSettings.add(myPositionRb, gc);
    myClass.setText("Class:");
    final ClassBrowser classBrowser = new ClassBrowser(project, "Choose Class") {
      @Override
      protected ClassFilter.ClassFilterWithScope getFilter() throws NoFilterException {
        return new ClassFilter.ClassFilterWithScope() {
          @Override
          public GlobalSearchScope getScope() {
            return GlobalSearchScope.allScope(project);
          }

          @Override
          public boolean isAccepted(PsiClass aClass) {
            return true;
          }
        };
      }

      @Override
      protected PsiClass findClass(String className) {
        return JavaPsiFacade.getInstance(project).findClass(className, GlobalSearchScope.allScope(project));
      }
    };
    final EditorTextFieldWithBrowseButton classComponent = new EditorTextFieldWithBrowseButton(project, true);
    myClass.setComponent(classComponent);
    classBrowser.setField(classComponent);
    panelWithSettings.add(myClass, gc);
    myMethod.setText("Method:");
    final EditorTextFieldWithBrowseButton textFieldWithBrowseButton = new EditorTextFieldWithBrowseButton(project, true,
                                                                                                          JavaCodeFragment.VisibilityChecker.EVERYTHING_VISIBLE,
                                                                                                          PlainTextLanguage.INSTANCE.getAssociatedFileType());
    myMethod.setComponent(textFieldWithBrowseButton);
    final MethodBrowser methodBrowser = new MethodBrowser(project) {
      protected Condition<PsiMethod> getFilter(final PsiClass testClass) {
        return new Condition<PsiMethod>() {
          @Override
          public boolean value(PsiMethod method) {
            return method.getContainingClass() == testClass;
          }
        };
      }

      @Override
      protected String getClassName() {
        return myClass.getComponent().getText().trim();
      }

      @Override
      protected ConfigurationModuleSelector getModuleSelector() {
        return myModuleSelector;
      }
    };
    methodBrowser.setField(textFieldWithBrowseButton);
    methodBrowser.installCompletion(textFieldWithBrowseButton.getChildComponent());
    
    panelWithSettings.add(myMethod, gc);
    panelWithSettings.add(myChangesRb, gc);
    panelWithSettings.add(myChangeLists, gc);

    ButtonGroup gr = new ButtonGroup();
    gr.add(myPositionRb);
    gr.add(myChangesRb);

    final ActionListener l = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        updateComponents();
      }
    };
    myPositionRb.addActionListener(l);
    myChangesRb.addActionListener(l);


    final List<LocalChangeList> changeLists = ChangeListManager.getInstance(project).getChangeLists();
    final DefaultComboBoxModel model = new DefaultComboBoxModel();
    model.addElement("All");
    for (LocalChangeList changeList : changeLists) {
      model.addElement(changeList.getName());
    }
    myChangeLists.setModel(model);
    ChangeListManager changeListManager = ChangeListManager.getInstance(project);
    if (changeListManager.getAffectedFiles().isEmpty()) {
      myChangesRb.setEnabled(false);
    }

    myWholePanel.add(panelWithSettings, BorderLayout.NORTH);
    myWholePanel.add(myCommonJavaParameters, BorderLayout.CENTER);
    final JPanel classpathPanel = new JPanel(new BorderLayout());
    myWholePanel.add(classpathPanel, BorderLayout.SOUTH);

    classpathPanel.add(myModule, BorderLayout.NORTH);
    myJrePathEditor = new JrePathEditor(DefaultJreSelector.fromModuleDependencies(getModulesComponent(), false));
    classpathPanel.add(myJrePathEditor, BorderLayout.CENTER);
    UIUtil.setEnabled(myCommonJavaParameters.getProgramParametersComponent(), false, true);

    setAnchor(myModule.getLabel());
    myJrePathEditor.setAnchor(myModule.getLabel());
    myCommonJavaParameters.setAnchor(myModule.getLabel());
  }

  private void updateComponents() {
    myClass.setEnabled(myPositionRb.isSelected());
    myMethod.setEnabled(myPositionRb.isSelected());
    myChangeLists.setEnabled(myChangesRb.isSelected());
  }

  public void applyEditorTo(final TestDiscoveryConfiguration configuration) {
    applyHelpersTo(configuration);
    configuration.setAlternativeJrePath(myJrePathEditor.getJrePathOrName());
    configuration.setAlternativeJrePathEnabled(myJrePathEditor.isAlternativeJreSelected());
    configuration.setPosition(myPositionRb.isSelected() ? Pair.create(myClass.getComponent().getText().trim(), 
                                                                      myMethod.getComponent().getText().trim()) : null);
    if (myChangesRb.isSelected()) {
      final Object selectedItem = myChangeLists.getSelectedItem();
      configuration.setChangeList("All".equals(selectedItem) ? null : (String)selectedItem);
    }
    else {
      configuration.setChangeList(null);
    }
    myCommonJavaParameters.applyTo(configuration);
  }

  public void resetEditorFrom(final TestDiscoveryConfiguration configuration) {
    myCommonJavaParameters.reset(configuration);
    getModuleSelector().reset(configuration);
    myJrePathEditor
      .setPathOrName(configuration.getAlternativeJrePath(), configuration.isAlternativeJrePathEnabled());
    final Pair<String, String> position = configuration.getPosition();
    if (position != null) {
      myPositionRb.setSelected(true);
      myClass.getComponent().setText(position.first);
      myMethod.getComponent().setText(position.second);
    }
    else if (myChangesRb.isEnabled()) {
      myChangesRb.setSelected(true);
    }
    else {
      myPositionRb.setSelected(true);
    }
    final String changeList = configuration.getChangeList();
    if (changeList != null) {
      myChangeLists.setSelectedItem(changeList);
    }
    else if (myChangesRb.isEnabled()) {
      myChangeLists.setSelectedIndex(0);
    }
    updateComponents();
  }

  public ModulesComboBox getModulesComponent() {
    return myModule.getComponent();
  }

  public ConfigurationModuleSelector getModuleSelector() {
    return myModuleSelector;
  }

  @Override
  public JComponent getAnchor() {
    return anchor;
  }

  @Override
  public void setAnchor(JComponent anchor) {
    this.anchor = anchor;
  }


  @NotNull
  public JComponent createEditor() {
    return myWholePanel;
  }

  private void applyHelpersTo(final TestDiscoveryConfiguration currentState) {
    myCommonJavaParameters.applyTo(currentState);
    getModuleSelector().applyTo(currentState);
  }
}
