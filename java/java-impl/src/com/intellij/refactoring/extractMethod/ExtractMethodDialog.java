/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.refactoring.extractMethod;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.ui.ComboBoxVisibilityPanel;
import com.intellij.refactoring.ui.ConflictsDialog;
import com.intellij.refactoring.ui.JavaComboBoxVisibilityPanel;
import com.intellij.refactoring.ui.MethodSignatureComponent;
import com.intellij.refactoring.util.ConflictsUtil;
import com.intellij.refactoring.util.ParameterTablePanel;
import com.intellij.refactoring.util.VariableData;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.NonFocusableCheckBox;
import com.intellij.ui.SeparatorFactory;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.*;


/**
 * @author Konstantin Bulenkov
 */
@SuppressWarnings("MethodMayBeStatic")
public class ExtractMethodDialog extends AbstractExtractDialog {
  public static final String EXTRACT_METHOD_DEFAULT_VISIBILITY = "extract.method.default.visibility";
  private final Project myProject;
  private final PsiType myReturnType;
  private final PsiTypeParameterList myTypeParameterList;
  private final PsiType[] myExceptions;
  private final boolean myStaticFlag;
  private boolean myCanBeStatic;
  private final PsiElement[] myElementsToExtract;
  private final String myHelpId;

  private final EditorTextField myNameField;
  private final MethodSignatureComponent mySignature;
  private final JCheckBox myMakeStatic;
  protected JCheckBox myMakeVarargs;
  private JCheckBox myCbChainedConstructor;

  private final InputVariables myVariableData;
  private final PsiClass myTargetClass;
  private ComboBoxVisibilityPanel<String> myVisibilityPanel;

  private boolean myDefaultVisibility = true;
  private boolean myChangingVisibility;

  private final JCheckBox myFoldParameters = new NonFocusableCheckBox(RefactoringBundle.message("declare.folded.parameters"));
  public JPanel myCenterPanel;
  public JPanel myParamTable;
  private VariableData[] myInputVariables;

  public ExtractMethodDialog(Project project,
                             PsiClass targetClass, final InputVariables inputVariables, PsiType returnType,
                             PsiTypeParameterList typeParameterList, PsiType[] exceptions, boolean isStatic, boolean canBeStatic,
                             final boolean canBeChainedConstructor,
                             String initialMethodName,
                             String title,
                             String helpId,
                             final PsiElement[] elementsToExtract) {
    super(project);
    myProject = project;
    myTargetClass = targetClass;
    myReturnType = returnType;
    myTypeParameterList = typeParameterList;
    myExceptions = exceptions;
    myStaticFlag = isStatic;
    myCanBeStatic = canBeStatic;
    myElementsToExtract = elementsToExtract;
    myVariableData = inputVariables;
    myHelpId = helpId;
    mySignature = new MethodSignatureComponent("", project, JavaFileType.INSTANCE);
    mySignature.setPreferredSize(new Dimension(500, 100));
    mySignature.setMinimumSize(new Dimension(500, 100));
    setTitle(title);

    // Create UI components

    myNameField = createNameField(initialMethodName);

    int height = myVariableData.getInputVariables().size() + 2;
    if (myExceptions.length > 0) {
      height += myExceptions.length + 1;
    }
    myMakeStatic = new NonFocusableCheckBox();
    myMakeStatic.setText(RefactoringBundle.message("declare.static.checkbox"));
    if (canBeChainedConstructor) {
      myCbChainedConstructor = new NonFocusableCheckBox(RefactoringBundle.message("extract.chained.constructor.checkbox"));
    }

    init();
  }

  protected EditorTextField createNameField(String initialMethodName) {
    EditorTextField field = new EditorTextField(initialMethodName);
    field.selectAll();
    return field;
  }

  protected boolean areTypesDirected() {
    return true;
  }

  public boolean isMakeStatic() {
    if (myStaticFlag) return true;
    return myCanBeStatic && myMakeStatic.isSelected();
  }

  public boolean isChainedConstructor() {
    return myCbChainedConstructor != null && myCbChainedConstructor.isSelected();
  }

  @NotNull
  protected Action[] createActions() {
    if (myHelpId != null) {
      return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
    } else {
      return new Action[]{getOKAction(), getCancelAction()};
    }
  }

  public String getChosenMethodName() {
    return myNameField.getText();
  }

  public VariableData[] getChosenParameters() {
    return myInputVariables;
  }

  public JComponent getPreferredFocusedComponent() {
    return myNameField;
  }

  @Override
  protected String getHelpId() {
    return myHelpId;
  }

  protected void doOKAction() {
    MultiMap<PsiElement, String> conflicts = new MultiMap<PsiElement, String>();
    checkMethodConflicts(conflicts);
    if (!conflicts.isEmpty()) {
      final ConflictsDialog conflictsDialog = new ConflictsDialog(myProject, conflicts);
      conflictsDialog.show();
      if (!conflictsDialog.isOK()){
        if (conflictsDialog.isShowConflicts()) close(CANCEL_EXIT_CODE);
        return;
      }
    }

    if (myMakeVarargs != null && myMakeVarargs.isSelected()) {
      final VariableData data = myInputVariables[myInputVariables.length - 1];
      if (data.type instanceof PsiArrayType) {
        data.type = new PsiEllipsisType(((PsiArrayType)data.type).getComponentType());
      }
    }
    final PsiMethod containingMethod = getContainingMethod();
    if (containingMethod != null && containingMethod.hasModifierProperty(PsiModifier.PUBLIC)) {
      PropertiesComponent.getInstance(myProject).setValue(EXTRACT_METHOD_DEFAULT_VISIBILITY, getVisibility());
    }
    super.doOKAction();
  }

  protected JComponent createNorthPanel() {
    final JPanel main = new JPanel(new BorderLayout());
    final JPanel namePanel = new JPanel(new VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 2, true, false));
    final JLabel nameLabel = new JLabel();
    nameLabel.setText(RefactoringBundle.message("changeSignature.name.prompt"));
    namePanel.add(nameLabel);
    namePanel.add(myNameField);
    nameLabel.setLabelFor(myNameField);

    myNameField.getDocument().addDocumentListener(new DocumentAdapter() {
      public void documentChanged(DocumentEvent e) {
        update();
      }
    });

    myVisibilityPanel = createVisibilityPanel();
    final JPanel visibilityAndName = new JPanel(new BorderLayout(2, 0));
    visibilityAndName.add(myVisibilityPanel, BorderLayout.WEST);
    visibilityAndName.add(namePanel, BorderLayout.CENTER);
    main.add(visibilityAndName, BorderLayout.CENTER);
    setOKActionEnabled(false);

    setOKActionEnabled(JavaPsiFacade.getInstance(myProject).getNameHelper().isIdentifier(myNameField.getText()));
    final JPanel options = new JPanel(new BorderLayout());
    options.add(createOptionsPanel(), BorderLayout.WEST);
    main.add(options, BorderLayout.SOUTH);
    return main;
  }

  protected JPanel createOptionsPanel() {
    final JPanel optionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 5));

    //optionsPanel.add(new JLabel("Options: "));

    if (myStaticFlag || myCanBeStatic) {
      myMakeStatic.setEnabled(!myStaticFlag);
      myMakeStatic.setSelected(myStaticFlag);
      myMakeStatic.addItemListener(new ItemListener() {
        public void itemStateChanged(ItemEvent e) {
          updateSignature();
        }
      });
      optionsPanel.add(myMakeStatic);
    } else {
      myMakeStatic.setSelected(false);
      myMakeStatic.setEnabled(false);
    }
    final Border emptyBorder = IdeBorderFactory.createEmptyBorder(5, 0, 5, 4);
    myMakeStatic.setBorder(emptyBorder);

    myFoldParameters.setSelected(myVariableData.isFoldingSelectedByDefault());
    myFoldParameters.setVisible(myVariableData.isFoldable());
    myVariableData.setFoldingAvailable(myFoldParameters.isSelected());
    myInputVariables = myVariableData.getInputVariables().toArray(new VariableData[myVariableData.getInputVariables().size()]);
    myFoldParameters.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myVariableData.setFoldingAvailable(myFoldParameters.isSelected());
        myInputVariables =
          myVariableData.getInputVariables().toArray(new VariableData[myVariableData.getInputVariables().size()]);
        updateVarargsEnabled();
        createParametersPanel();
        updateSignature();
      }
    });
    optionsPanel.add(myFoldParameters);
    myFoldParameters.setBorder(emptyBorder);

    boolean canBeVarargs = false;
    for (VariableData data : myInputVariables) {
      canBeVarargs |= data.type instanceof PsiArrayType;
    }
    if (myVariableData.isFoldable()) {
      canBeVarargs |= myVariableData.isFoldingSelectedByDefault();
    }

    if (canBeVarargs) {
      myMakeVarargs = new NonFocusableCheckBox(RefactoringBundle.message("declare.varargs.checkbox"));
      myMakeVarargs.setBorder(emptyBorder);
      updateVarargsEnabled();
      myMakeVarargs.addItemListener(new ItemListener() {
        public void itemStateChanged(ItemEvent e) {
          updateSignature();
        }
      });
      myMakeVarargs.setSelected(false);
      optionsPanel.add(myMakeVarargs);
    }

    if (myCbChainedConstructor != null) {
      optionsPanel.add(myCbChainedConstructor);
      myCbChainedConstructor.setBorder(emptyBorder);
      myCbChainedConstructor.addItemListener(new ItemListener() {
        public void itemStateChanged(final ItemEvent e) {
          if (myDefaultVisibility) {
            myChangingVisibility = true;
            try {
              if (isChainedConstructor()) {
                myVisibilityPanel.setVisibility(VisibilityUtil.getVisibilityModifier(myTargetClass.getModifierList()));
              }
              else {
                myVisibilityPanel.setVisibility(PsiModifier.PRIVATE);
              }
            }
            finally {
              myChangingVisibility = false;
            }
          }
          update();
        }
      });
    }
    return optionsPanel;
  }

  private ComboBoxVisibilityPanel<String> createVisibilityPanel() {
    final JavaComboBoxVisibilityPanel panel = new JavaComboBoxVisibilityPanel();
    final PsiMethod containingMethod = getContainingMethod();
    panel.setVisibility(containingMethod != null && containingMethod.hasModifierProperty(PsiModifier.PUBLIC) 
                        ? PropertiesComponent.getInstance(myProject).getOrInit( EXTRACT_METHOD_DEFAULT_VISIBILITY, PsiModifier.PRIVATE)
                        : PsiModifier.PRIVATE);
    panel.addListener(new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        updateSignature();
        if (!myChangingVisibility) {
          myDefaultVisibility = false;
        }
      }
    });
    return panel;
  }

  private PsiMethod getContainingMethod() {
    return PsiTreeUtil.getParentOfType(PsiTreeUtil.findCommonParent(myElementsToExtract), PsiMethod.class);
  }

  private void updateVarargsEnabled() {
    if (myMakeVarargs != null) {
      myMakeVarargs.setEnabled(myInputVariables[myInputVariables.length - 1].type instanceof PsiArrayType);
    }
  }

  private void update() {
    myNameField.setEnabled(!isChainedConstructor());
    if (myMakeStatic != null) {
      myMakeStatic.setEnabled(!myStaticFlag && myCanBeStatic && !isChainedConstructor());
    }
    updateSignature();
    setOKActionEnabled(JavaPsiFacade.getInstance(myProject).getNameHelper().isIdentifier(myNameField.getText()) ||
                       isChainedConstructor());
  }

  public String getVisibility() {
    return myVisibilityPanel.getVisibility();
  }


  protected JComponent createCenterPanel() {
    myCenterPanel = new JPanel(new BorderLayout());
    createParametersPanel();

    final Splitter splitter = new Splitter(true);
    splitter.setShowDividerIcon(false);
    splitter.setFirstComponent(myCenterPanel);
    splitter.setSecondComponent(createSignaturePanel());
    return splitter;
  }

  protected boolean isOutputVariable(PsiVariable var) {
    return false;
  }

  protected void createParametersPanel() {
    if (myParamTable != null) {
      myCenterPanel.remove(myParamTable);
    }

    myParamTable = createParameterTableComponent();
    myParamTable.setMinimumSize(new Dimension(500, 100));
    myCenterPanel.add(myParamTable, BorderLayout.CENTER);
    final JTable table = UIUtil.findComponentOfType(myParamTable, JTable.class);
    myCenterPanel.add(SeparatorFactory.createSeparator("&Parameters", table), BorderLayout.NORTH);
    if (table != null) {
      table.addFocusListener(new FocusAdapter() {
        @Override
        public void focusGained(FocusEvent e) {
          if (table.getRowCount() > 0) {
            final int col = table.getSelectedColumn();
            final int row = table.getSelectedRow();
            if (col == -1 || row == -1) {
              table.getSelectionModel().setSelectionInterval(0, 0);
              table.getColumnModel().getSelectionModel().setSelectionInterval(0, 0);
            }
          }
        }
      });
    }
  }

  protected ParameterTablePanel createParameterTableComponent() {
    return new ParameterTablePanel(myProject, myInputVariables, myElementsToExtract) {
      protected void updateSignature() {
        updateVarargsEnabled();
        ExtractMethodDialog.this.updateSignature();
      }

      protected void doEnterAction() {
        clickDefaultButton();
      }

      protected void doCancelAction() {
        ExtractMethodDialog.this.doCancelAction();
      }

      protected boolean areTypesDirected() {
        return ExtractMethodDialog.this.areTypesDirected();
      }

      @Override
      protected boolean isUsedAfter(PsiVariable variable) {
        return isOutputVariable(variable);
      }
    };
  }

  protected JComponent createSignaturePanel() {
    final JPanel panel = new JPanel(new BorderLayout());
    panel.add(SeparatorFactory.createSeparator(RefactoringBundle.message("signature.preview.border.title"), null), BorderLayout.NORTH);
    panel.add(mySignature, BorderLayout.CENTER);

    updateSignature();
    return panel;
  }

  protected void updateSignature() {
    if (mySignature != null) {
      mySignature.setSignature(getSignature());
    }
  }

  protected String getSignature() {
    final @NonNls StringBuilder buffer = new StringBuilder();
    final String visibilityString = VisibilityUtil.getVisibilityString(myVisibilityPanel.getVisibility());
    buffer.append(visibilityString);
    if (buffer.length() > 0) {
      buffer.append(" ");
    }
    if (isMakeStatic() && !isChainedConstructor()) {
      buffer.append("static ");
    }
    if (myTypeParameterList != null) {
      final String typeParamsText = myTypeParameterList.getText();
      if (!typeParamsText.isEmpty()) {
        buffer.append(typeParamsText);
        buffer.append(" ");
      }
    }

    if (isChainedConstructor()) {
      buffer.append(myTargetClass.getName());
    }
    else {
      buffer.append(PsiFormatUtil.formatType(myReturnType, 0, PsiSubstitutor.EMPTY));
      buffer.append(" ");
      buffer.append(myNameField.getText());
    }
    buffer.append("(");

    final String INDENT = StringUtil.repeatSymbol(' ', buffer.length());

    final VariableData[] datas = myInputVariables;
    int count = 0;
    for (int i = 0; i < datas.length;i++) {
      VariableData data = datas[i];
      if (data.passAsParameter) {
        //String typeAndModifiers = PsiFormatUtil.formatVariable(data.variable,
        //  PsiFormatUtil.SHOW_MODIFIERS | PsiFormatUtil.SHOW_TYPE);
        PsiType type = data.type;
        if (i == datas.length - 1 && type instanceof PsiArrayType && myMakeVarargs != null && myMakeVarargs.isSelected()) {
          type = new PsiEllipsisType(((PsiArrayType)type).getComponentType());
        }

        String typeText = type.getPresentableText();
        if (count > 0) {
          buffer.append(",\n");
          buffer.append(INDENT);
        }
        buffer.append(typeText);
        buffer.append(" ");
        buffer.append(data.name);
        count++;
      }
    }
    buffer.append(")");
    if (myExceptions.length > 0) {
      buffer.append("\n");
      buffer.append("throws\n");
      for (PsiType exception : myExceptions) {
        buffer.append(INDENT);
        buffer.append(PsiFormatUtil.formatType(exception, 0, PsiSubstitutor.EMPTY));
        buffer.append("\n");
      }
    }
    return buffer.toString();
  }

  @Override
  protected String getDimensionServiceKey() {
    return "extract.method.dialog";
  }

  protected void checkMethodConflicts(MultiMap<PsiElement, String> conflicts) {
    PsiMethod prototype;
    try {
      PsiElementFactory factory = JavaPsiFacade.getInstance(myProject).getElementFactory();
      prototype = factory.createMethod(myNameField.getText().trim(), myReturnType);
      if (myTypeParameterList != null) prototype.getTypeParameterList().replace(myTypeParameterList);
      for (VariableData data : myInputVariables) {
        if (data.passAsParameter) {
          prototype.getParameterList().add(factory.createParameter(data.name, data.type));
        }
      }
      // set the modifiers with which the method is supposed to be created
      PsiUtil.setModifierProperty(prototype, PsiModifier.PRIVATE, true);
    } catch (IncorrectOperationException e) {
      return;
    }

    ConflictsUtil.checkMethodConflicts(myTargetClass, null, prototype, conflicts);
  }
}
