/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * User: anna
 * Date: 07-May-2008
 */
package com.intellij.refactoring.replaceConstructorWithBuilder;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidatorEx;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.refactoring.PackageWrapper;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.move.moveClassesOrPackages.DestinationFolderComboBox;
import com.intellij.refactoring.ui.PackageNameReferenceEditorCombo;
import com.intellij.refactoring.ui.RefactoringDialog;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.ui.*;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;

public class ReplaceConstructorWithBuilderDialog extends RefactoringDialog {
  private final PsiMethod[] myConstructors;

  private JRadioButton myCreateBuilderClassRadioButton;
  private JRadioButton myExistingBuilderClassRadioButton;

  private JPanel myWholePanel;
  private JTextField myNewClassName;
  private ReferenceEditorComboWithBrowseButton  myPackageTextField;
  private ReferenceEditorComboWithBrowseButton myExistentClassTF;
  private ComboboxWithBrowseButton myDestinationCb;
  private JPanel myCreateNewPanel;

  private static final Logger LOG = Logger.getInstance("#" + ReplaceConstructorWithBuilderDialog.class.getName());
  private final LinkedHashMap<String, ParameterData> myParametersMap;
  private MyTableModel myTableModel;
  private JBTable myTable;
  private String mySetterPrefix;

  private static final String RECENT_KEYS = "ReplaceConstructorWithBuilder.RECENT_KEYS";
  private static final String SETTER_PREFIX_KEY = "ConstructorWithBuilder.SetterPrefix";


  protected ReplaceConstructorWithBuilderDialog(@NotNull Project project, PsiMethod[] constructors) {
    super(project, false);
    myConstructors = constructors;
    myParametersMap = new LinkedHashMap<>();
    mySetterPrefix = PropertiesComponent.getInstance(project).getValue(SETTER_PREFIX_KEY, "set");
    for (PsiMethod constructor : constructors) {
      ParameterData.createFromConstructor(constructor, mySetterPrefix, myParametersMap);
    }
    init();
    setTitle(ReplaceConstructorWithBuilderProcessor.REFACTORING_NAME);
  }

  @Override
  protected String getHelpId() {
    return "replace_constructor_with_builder_dialog";
  }

  protected void doAction() {
    TableUtil.stopEditing(myTable);

    final String className;
    final String packageName;
    if (myCreateBuilderClassRadioButton.isSelected()) {
      className = myNewClassName.getText().trim();
      packageName = myPackageTextField.getText().trim();
    } else {
      final String fqName = myExistentClassTF.getText().trim();
      className = StringUtil.getShortName(fqName);
      packageName = StringUtil.getPackageName(fqName);
      final PsiClass builderClass = JavaPsiFacade.getInstance(myProject).findClass(StringUtil.getQualifiedName(packageName, className), GlobalSearchScope.projectScope(myProject));
      if (builderClass != null && !CommonRefactoringUtil.checkReadOnlyStatus(myProject, builderClass)) return;
    }
    invokeRefactoring(new ReplaceConstructorWithBuilderProcessor(getProject(), myConstructors, myParametersMap, className, packageName,
                                                                 ((DestinationFolderComboBox)myDestinationCb).selectDirectory(new PackageWrapper(myConstructors[0].getManager(), packageName), false),
                                                                 myCreateBuilderClassRadioButton.isSelected()));
  }


  @Nullable
  @Override
  protected JComponent createNorthPanel() {
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(new JLabel("Parameters to Pass to the Builder"), BorderLayout.CENTER);

    final DefaultActionGroup actionGroup = new DefaultActionGroup(null, false);
    actionGroup.addAction(new AnAction("Rename Setters Prefix") {
      @Override
      public void actionPerformed(AnActionEvent e) {
        applyNewSetterPrefix();
      }
    }).setAsSecondary(true);

    panel.add(ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, actionGroup, true).getComponent(), BorderLayout.EAST);
    final Box box = Box.createHorizontalBox();
    box.add(panel);
    box.add(Box.createHorizontalGlue());
    return box;
  }

  private void applyNewSetterPrefix() {
    final String setterPrefix = Messages.showInputDialog(myTable, "New setter prefix:", "Rename Setters Prefix", null,
                                                         mySetterPrefix, new MySetterPrefixInputValidator());
    if (setterPrefix != null) {
      mySetterPrefix = setterPrefix;
      PropertiesComponent.getInstance(myProject).setValue(SETTER_PREFIX_KEY, setterPrefix);
      final JavaCodeStyleManager javaCodeStyleManager = JavaCodeStyleManager.getInstance(myProject);
      for (String paramName : myParametersMap.keySet()) {
        final ParameterData data = myParametersMap.get(paramName);
        paramName = data.getParamName();
        final String propertyName = javaCodeStyleManager.variableNameToPropertyName(paramName, VariableKind.PARAMETER);
        data.setSetterName(PropertyUtil.suggestSetterName(propertyName, setterPrefix));
      }
      myTable.revalidate();
      myTable.repaint();
    }
  }

  protected JComponent createCenterPanel() {
    final Splitter splitter = new Splitter(true);
    splitter.setFirstComponent(createTablePanel());
    splitter.setSecondComponent(myWholePanel);
    final ActionListener enableDisableListener = new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        setEnabled(myCreateBuilderClassRadioButton.isSelected());
        IdeFocusManager.getInstance(myProject).requestFocus(
          myCreateBuilderClassRadioButton.isSelected() ? myNewClassName : myExistentClassTF.getChildComponent(), true);
        validateButtons();
      }
    };
    myCreateBuilderClassRadioButton.addActionListener(enableDisableListener);
    myExistingBuilderClassRadioButton.addActionListener(enableDisableListener);
    myCreateBuilderClassRadioButton.setSelected(true);
    setEnabled(true);

    final DocumentAdapter validateButtonsListener = new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        validateButtons();
      }
    };
    myNewClassName.getDocument().addDocumentListener(validateButtonsListener);
    final PsiClass psiClass = myConstructors[0].getContainingClass();
    LOG.assertTrue(psiClass != null);
    myNewClassName.setText(psiClass.getName() + "Builder");

    return splitter;
  }

  private void setEnabled(final boolean createNew) {
    UIUtil.setEnabled(myCreateNewPanel, createNew, true);
    myExistentClassTF.setEnabled(!createNew);
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myNewClassName;
  }

  @Override
  protected void canRun() throws ConfigurationException {
    final PsiNameHelper nameHelper = PsiNameHelper.getInstance(myProject);
    for (ParameterData parameterData : myParametersMap.values()) {
      if (!nameHelper.isIdentifier(parameterData.getFieldName())) throw new ConfigurationException("\'" + parameterData.getFieldName() + "\' is not a valid field name");
      if (!nameHelper.isIdentifier(parameterData.getSetterName())) throw new ConfigurationException("\'" + parameterData.getSetterName() + "\' is not a valid setter name");
    }
    if (myCreateBuilderClassRadioButton.isSelected()) {
      final String className = myNewClassName.getText().trim();
      if (className.length() == 0 || !nameHelper.isQualifiedName(className)) throw new ConfigurationException("\'" + className + "\' is invalid builder class name");
      final String packageName = myPackageTextField.getText().trim();
      if (packageName.length() > 0 && !nameHelper.isQualifiedName(packageName)) throw new ConfigurationException("\'" + packageName + "\' is invalid builder package name");
    } else {
      final String qualifiedName = myExistentClassTF.getText().trim();
      if (qualifiedName.length() == 0 || !nameHelper.isQualifiedName(qualifiedName)) throw new ConfigurationException("\'" + qualifiedName + "\' is invalid builder qualified class name");
    }
  }

  private JScrollPane createTablePanel() {
    myTableModel = new MyTableModel();
    myTable = new JBTable(myTableModel);
    myTable.setSurrendersFocusOnKeystroke(true);
    myTable.getTableHeader().setReorderingAllowed(false);

    final TableColumnModel columnModel = myTable.getColumnModel();
    columnModel.getColumn(SKIP_SETTER).setCellRenderer(new BooleanTableCellRenderer());

    myTable.registerKeyboardAction(
      new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          final int[] selectedRows = myTable.getSelectedRows();
          for (final int selectedRow : selectedRows) {
            final ParameterData parameterData = myTableModel.getParamData(selectedRow);
            if (parameterData.getDefaultValue() != null) {
              parameterData.setInsertSetter(!parameterData.isInsertSetter());
            }
          }
          TableUtil.selectRows(myTable, selectedRows);
        }
      },
      KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0),
      JComponent.WHEN_FOCUSED
    );

    myTable.setPreferredScrollableViewportSize(new Dimension(550, myTable.getRowHeight() * 12));
    myTable.getSelectionModel().setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

    return ScrollPaneFactory.createScrollPane(myTable);
  }

  private static final int PARAM = 0;
  private static final int FIELD = 1;
  private static final int SETTER = 2;
  private static final int DEFAULT_VALUE = 3;
  private static final int SKIP_SETTER = 4;

  private void createUIComponents() {
    final com.intellij.openapi.editor.event.DocumentAdapter adapter = new com.intellij.openapi.editor.event.DocumentAdapter() {
      public void documentChanged(com.intellij.openapi.editor.event.DocumentEvent e) {
        validateButtons();
      }
    };

    myPackageTextField =
      new PackageNameReferenceEditorCombo(((PsiJavaFile)myConstructors[0].getContainingFile()).getPackageName(), myProject, RECENT_KEYS, RefactoringBundle.message("choose.destination.package"));
    myPackageTextField.getChildComponent().getDocument().addDocumentListener(adapter);

    myDestinationCb = new DestinationFolderComboBox() {
      @Override
      public String getTargetPackage() {
        return myPackageTextField.getText().trim();
      }
    };
    ((DestinationFolderComboBox)myDestinationCb).setData(myProject, myConstructors[0].getContainingFile().getContainingDirectory(), myPackageTextField.getChildComponent());

    myExistentClassTF = new ReferenceEditorComboWithBrowseButton(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final TreeClassChooser chooser = TreeClassChooserFactory.getInstance(getProject())
          .createWithInnerClassesScopeChooser("Select Builder Class", GlobalSearchScope.projectScope(myProject), null, null);
        final String classText = myExistentClassTF.getText();
        final PsiClass currentClass = JavaPsiFacade.getInstance(myProject).findClass(classText, GlobalSearchScope.allScope(myProject));
        if (currentClass != null) {
          chooser.select(currentClass);
        }
        chooser.showDialog();
        final PsiClass selectedClass = chooser.getSelected();
        if (selectedClass != null) {
          myExistentClassTF.setText(selectedClass.getQualifiedName());
        }
      }
    }, "", myProject, true, RECENT_KEYS);
    myExistentClassTF.getChildComponent().getDocument().addDocumentListener(adapter);
  }

  private class MyTableModel extends AbstractTableModel {

    public Class getColumnClass(int columnIndex) {
      if (columnIndex == SKIP_SETTER) {
        return Boolean.class;
      }
      return String.class;
    }

    public int getRowCount() {
      return myParametersMap.size();
    }

    public int getColumnCount() {
      return 5;
    }

    public Object getValueAt(int rowIndex, int columnIndex) {
      final ParameterData data = getParamData(rowIndex);
      switch (columnIndex) {
        case PARAM:
          return data.getType().getCanonicalText() + " " + data.getParamName();
        case FIELD:
          return data.getFieldName();
        case SETTER:
          return data.getSetterName();
        case DEFAULT_VALUE:
          return data.getDefaultValue();
        case SKIP_SETTER:
          return !data.isInsertSetter();
      }
      return null;
    }

    private ParameterData getParamData(int rowIndex) {
      return myParametersMap.get(new ArrayList<>(myParametersMap.keySet()).get(rowIndex));
    }

    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
      final ParameterData data = getParamData(rowIndex);
      switch (columnIndex) {
        case FIELD:
          data.setFieldName((String)aValue);
          break;
        case SETTER:
          data.setSetterName((String)aValue);
          break;
        case DEFAULT_VALUE:
          data.setDefaultValue((String)aValue);
          break;
        case SKIP_SETTER:
          data.setInsertSetter(!((Boolean)aValue).booleanValue());
          break;
        default:
          assert false;
      }
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
      if (columnIndex == PARAM) return false;
      if (columnIndex == SKIP_SETTER) {
        final ParameterData data = getParamData(rowIndex);
        if (data.getDefaultValue() == null) return false;
      }
      return true;
    }

    @Override
    public String getColumnName(int column) {
      switch (column) {
        case PARAM:
          return "Parameter";
        case FIELD:
          return "Field Name";
        case SETTER:
          return "Setter Name";
        case DEFAULT_VALUE:
          return "Default Value";
        case SKIP_SETTER:
          return "Optional Setter";
      }
      assert false: "unknown column " + column;
      return null;
    }
  }

  private class MySetterPrefixInputValidator implements InputValidatorEx {
    @Override
    public boolean checkInput(String inputString) {
      return getErrorText(inputString) == null;
    }

    @Override
    public boolean canClose(String inputString) {
      return checkInput(inputString);
    }

    @Nullable
    @Override
    public String getErrorText(String inputString) {
      if (StringUtil.isEmpty(inputString)) {
        return null;
      }
      return !PsiNameHelper.getInstance(myProject).isIdentifier(inputString)
             ? "Identifier \'" + inputString + "\' is invalid" : null;
    }
  }
}
