/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInspection.dataFlow.Nullness;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.ui.*;
import com.intellij.refactoring.util.ConflictsUtil;
import com.intellij.refactoring.util.ParameterTablePanel;
import com.intellij.refactoring.util.VariableData;
import com.intellij.ui.NonFocusableCheckBox;
import com.intellij.ui.SeparatorFactory;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.ui.DialogUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.HashSet;
import java.util.Set;


/**
 * @author Konstantin Bulenkov
 */
public class ExtractMethodDialog extends DialogWrapper implements AbstractExtractDialog {
  private static final String EXTRACT_METHOD_DEFAULT_VISIBILITY = "extract.method.default.visibility";
  public static final String EXTRACT_METHOD_GENERATE_ANNOTATIONS = "extractMethod.generateAnnotations";
  private final Project myProject;
  private final PsiType myReturnType;
  private final PsiTypeParameterList myTypeParameterList;
  private final PsiType[] myExceptions;
  private final boolean myStaticFlag;
  private final boolean myCanBeStatic;
  private final Nullness myNullness;
  private final PsiElement[] myElementsToExtract;
  private final String myHelpId;

  private final NameSuggestionsField myNameField;
  private final MethodSignatureComponent mySignature;
  private final JCheckBox myMakeStatic;
  protected JCheckBox myMakeVarargs;
  protected JCheckBox myGenerateAnnotations;
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
  private TypeSelector mySelector;

  public ExtractMethodDialog(Project project,
                             PsiClass targetClass, final InputVariables inputVariables, PsiType returnType,
                             PsiTypeParameterList typeParameterList, PsiType[] exceptions, boolean isStatic, boolean canBeStatic,
                             final boolean canBeChainedConstructor,
                             String title,
                             String helpId,
                             Nullness nullness,
                             final PsiElement[] elementsToExtract) {
    super(project, true);
    myProject = project;
    myTargetClass = targetClass;
    myReturnType = returnType;
    myTypeParameterList = typeParameterList;
    myExceptions = exceptions;
    myStaticFlag = isStatic;
    myCanBeStatic = canBeStatic;
    myNullness = nullness;
    myElementsToExtract = elementsToExtract;
    myVariableData = inputVariables;
    myHelpId = helpId;
    mySignature = new MethodSignatureComponent("", project, JavaFileType.INSTANCE);
    mySignature.setPreferredSize(JBUI.size(500, 100));
    mySignature.setMinimumSize(JBUI.size(500, 100));
    setTitle(title);

    myNameField = new NameSuggestionsField(suggestMethodNames(), myProject);
    
    myMakeStatic = new NonFocusableCheckBox();
    myMakeStatic.setText(RefactoringBundle.message("declare.static.checkbox"));
    if (canBeChainedConstructor) {
      myCbChainedConstructor = new NonFocusableCheckBox(RefactoringBundle.message("extract.chained.constructor.checkbox"));
    }
    myInputVariables = myVariableData.getInputVariables().toArray(new VariableData[myVariableData.getInputVariables().size()]);

    init();
  }

  protected String[] suggestMethodNames() {
    return ArrayUtil.EMPTY_STRING_ARRAY;
  }
  
  protected boolean areTypesDirected() {
    return true;
  }

  @Override
  public boolean isMakeStatic() {
    if (myStaticFlag) return true;
    return myCanBeStatic && myMakeStatic.isSelected();
  }

  @Override
  public boolean isChainedConstructor() {
    return myCbChainedConstructor != null && myCbChainedConstructor.isSelected();
  }

  @Override
  @NotNull
  protected Action[] createActions() {
    if (myHelpId != null) {
      return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
    } else {
      return new Action[]{getOKAction(), getCancelAction()};
    }
  }

  @Override
  public String getChosenMethodName() {
    return myNameField.getEnteredName().trim();
  }

  @Override
  public VariableData[] getChosenParameters() {
    return myInputVariables;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myNameField;
  }

  @Override
  protected String getHelpId() {
    return myHelpId;
  }

  @Override
  protected void doOKAction() {
    MultiMap<PsiElement, String> conflicts = new MultiMap<>();
    checkMethodConflicts(conflicts);
    if (!conflicts.isEmpty()) {
      final ConflictsDialog conflictsDialog = new ConflictsDialog(myProject, conflicts);
      if (!conflictsDialog.showAndGet()) {
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

    if (myGenerateAnnotations != null && myGenerateAnnotations.isEnabled()) {
      PropertiesComponent.getInstance(myProject).setValue(EXTRACT_METHOD_GENERATE_ANNOTATIONS, myGenerateAnnotations.isSelected(), true);
    }
    super.doOKAction();
  }

  @Override
  protected JComponent createNorthPanel() {
    final JPanel main = new JPanel(new BorderLayout());
    final JPanel namePanel = new JPanel(new BorderLayout(0, 2));
    final JLabel nameLabel = new JLabel();
    nameLabel.setText(RefactoringBundle.message("changeSignature.name.prompt"));
    namePanel.add(nameLabel, BorderLayout.NORTH);
    namePanel.add(myNameField, BorderLayout.SOUTH);
    nameLabel.setLabelFor(myNameField);

    myNameField.addDataChangedListener(this::update);

    myVisibilityPanel = createVisibilityPanel();
    if (!myNameField.hasSuggestions()) {
      myVisibilityPanel.registerUpDownActionsFor(myNameField);
    }
    final JPanel visibilityAndReturnType = new JPanel(new BorderLayout(2, 0));
    if (!myTargetClass.isInterface()) {
      visibilityAndReturnType.add(myVisibilityPanel, BorderLayout.WEST);
    }
    final JPanel returnTypePanel = createReturnTypePanel();
    if (returnTypePanel != null) {
      visibilityAndReturnType.add(returnTypePanel, BorderLayout.EAST);
    }

    final JPanel visibilityAndName = new JPanel(new BorderLayout(2, 0));
    visibilityAndName.add(visibilityAndReturnType, BorderLayout.WEST);
    visibilityAndName.add(namePanel, BorderLayout.CENTER);
    main.add(visibilityAndName, BorderLayout.CENTER);
    setOKActionEnabled(false);

    setOKActionEnabled(PsiNameHelper.getInstance(myProject).isIdentifier(myNameField.getEnteredName()));
    final JPanel options = new JPanel(new BorderLayout());
    options.add(createOptionsPanel(), BorderLayout.WEST);
    main.add(options, BorderLayout.SOUTH);
    return main;
  }

  protected boolean isVoidReturn() {
    return false;
  }
  
  @Nullable
  private JPanel createReturnTypePanel() {
    if (TypeConversionUtil.isPrimitiveWrapper(myReturnType) && myNullness == Nullness.NULLABLE) {
      return null;
    }
    final TypeSelectorManagerImpl manager = new TypeSelectorManagerImpl(myProject, myReturnType, findOccurrences(), areTypesDirected()) {
      @Override
      public PsiType[] getTypesForAll(boolean direct) {
        final PsiType[] types = super.getTypesForAll(direct);
        return !isVoidReturn() ? types : ArrayUtil.prepend(PsiType.VOID, types);
      }
    };
    mySelector = manager.getTypeSelector();
    final JComponent component = mySelector.getComponent();
    if (component instanceof ComboBox) {
      if (isVoidReturn()) {
        mySelector.selectType(PsiType.VOID);
      }
      final JPanel returnTypePanel = new JPanel(new BorderLayout(2, 0));
      final JLabel label = new JLabel(RefactoringBundle.message("changeSignature.return.type.prompt"));
      returnTypePanel.add(label, BorderLayout.NORTH);
      returnTypePanel.add(component, BorderLayout.SOUTH);
      DialogUtil.registerMnemonic(label, component);
      ((JComboBox)component).addActionListener(e -> {
        final PsiType selectedType = mySelector.getSelectedType();
        if (myGenerateAnnotations != null) {
          final boolean enabled = PsiUtil.resolveClassInType(selectedType) != null;
          if (!enabled) {
            myGenerateAnnotations.setSelected(false);
          }
          myGenerateAnnotations.setEnabled(enabled);
        }
        resizeReturnCombo(component, selectedType);
        returnTypePanel.revalidate();
        returnTypePanel.repaint();
        updateSignature();
      });
      resizeReturnCombo(component, mySelector.getSelectedType());
      return returnTypePanel;
    }
    return null;
  }

  private static void resizeReturnCombo(JComponent component, PsiType selectedType) {
    if (selectedType != null) {
      final String presentableText = selectedType.getPresentableText();
      final int presentableTextWidth = component.getFontMetrics(component.getFont()).stringWidth(presentableText);
      ((ComboBox)component).setMinimumAndPreferredWidth(presentableTextWidth);
    }
  }

  protected PsiExpression[] findOccurrences() {
    return PsiExpression.EMPTY_ARRAY;
  }

  protected JPanel createOptionsPanel() {
    final JPanel optionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 5));

    //optionsPanel.add(new JLabel("Options: "));

    createStaticOptions(optionsPanel, RefactoringBundle.message("declare.static.pass.fields.checkbox"));

    myFoldParameters.setSelected(myVariableData.isFoldingSelectedByDefault());
    myFoldParameters.setVisible(myVariableData.isFoldable());
    myVariableData.setFoldingAvailable(myFoldParameters.isSelected());
    myInputVariables = myVariableData.getInputVariables().toArray(new VariableData[myVariableData.getInputVariables().size()]);
    myFoldParameters.addActionListener(e -> {
      myVariableData.setFoldingAvailable(myFoldParameters.isSelected());
      myInputVariables =
        myVariableData.getInputVariables().toArray(new VariableData[myVariableData.getInputVariables().size()]);
      updateVarargsEnabled();
      createParametersPanel();
      updateSignature();
    });
    optionsPanel.add(myFoldParameters);
    final Border emptyBorder = JBUI.Borders.empty(5, 0, 5, 4);
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
      myMakeVarargs.addItemListener(e -> updateSignature());
      myMakeVarargs.setSelected(false);
      optionsPanel.add(myMakeVarargs);
    }

    if (myNullness != null && myNullness != Nullness.UNKNOWN) {
      final boolean isSelected = PropertiesComponent.getInstance(myProject).getBoolean(EXTRACT_METHOD_GENERATE_ANNOTATIONS, true);
      myGenerateAnnotations = new JCheckBox(RefactoringBundle.message("declare.generated.annotations"), isSelected);
      myGenerateAnnotations.addItemListener(e -> updateSignature());
      optionsPanel.add(myGenerateAnnotations);
    }

    if (myCbChainedConstructor != null) {
      optionsPanel.add(myCbChainedConstructor);
      myCbChainedConstructor.setBorder(emptyBorder);
      myCbChainedConstructor.addItemListener(e -> {
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
      });
    }
    return optionsPanel;
  }

  protected void createStaticOptions(JPanel optionsPanel, String passFieldsAsParamsLabel) {
    if (myStaticFlag || myCanBeStatic) {
      myMakeStatic.setEnabled(!myStaticFlag);
      myMakeStatic.setSelected(myStaticFlag);
      if (myVariableData.hasInstanceFields()) {
        myMakeStatic.setText(passFieldsAsParamsLabel);
      }
      myMakeStatic.addItemListener(e -> {
        if (myVariableData.hasInstanceFields()) {
          myVariableData.setPassFields(myMakeStatic.isSelected());
          myInputVariables = myVariableData.getInputVariables().toArray(new VariableData[myVariableData.getInputVariables().size()]);
          updateVarargsEnabled();
          createParametersPanel();
        }
        updateSignature();
      });
      optionsPanel.add(myMakeStatic);
    } else {
      myMakeStatic.setSelected(false);
      myMakeStatic.setEnabled(false);
    }
    myMakeStatic.setBorder(JBUI.Borders.empty(5, 0, 5, 4));
  }

  private ComboBoxVisibilityPanel<String> createVisibilityPanel() {
    final JavaComboBoxVisibilityPanel panel = new JavaComboBoxVisibilityPanel();
    final PsiMethod containingMethod = getContainingMethod();
    panel.setVisibility(containingMethod != null && containingMethod.hasModifierProperty(PsiModifier.PUBLIC) 
                        ? PropertiesComponent.getInstance(myProject).getValue(EXTRACT_METHOD_DEFAULT_VISIBILITY, PsiModifier.PRIVATE)
                        : PsiModifier.PRIVATE);
    panel.addListener(e -> {
      updateSignature();
      if (!myChangingVisibility) {
        myDefaultVisibility = false;
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
    setOKActionEnabled(PsiNameHelper.getInstance(myProject).isIdentifier(myNameField.getEnteredName()) ||
                       isChainedConstructor());
  }

  @Override
  @NotNull
  public String getVisibility() {
    return myTargetClass.isInterface() || myVisibilityPanel == null 
           ? PsiModifier.PUBLIC : ObjectUtils.notNull(myVisibilityPanel.getVisibility(), PsiModifier.PUBLIC);
  }

  @Override
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
    myParamTable.setMinimumSize(JBUI.size(500, 100));
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
      @Override
      protected void updateSignature() {
        updateVarargsEnabled();
        ExtractMethodDialog.this.updateSignature();
      }

      @Override
      protected void doEnterAction() {
        clickDefaultButton();
      }

      @Override
      protected void doCancelAction() {
        ExtractMethodDialog.this.doCancelAction();
      }

      @Override
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
    @NonNls final StringBuilder buffer = new StringBuilder();
    if (myGenerateAnnotations != null && myGenerateAnnotations.isSelected()) {
      final NullableNotNullManager nullManager = NullableNotNullManager.getInstance(myProject);
      buffer.append("@");
      buffer.append(StringUtil.getShortName(myNullness == Nullness.NULLABLE ? nullManager.getDefaultNullable() : nullManager.getDefaultNotNull()));
      buffer.append("\n");
    }
    final String visibilityString = VisibilityUtil.getVisibilityString(getVisibility());
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
      buffer.append(PsiFormatUtil.formatType(mySelector != null ? mySelector.getSelectedType() : myReturnType, 0, PsiSubstitutor.EMPTY));
      buffer.append(" ");
      buffer.append(myNameField.getEnteredName());
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
    checkParametersConflicts(conflicts);
    PsiMethod prototype;
    try {
      PsiElementFactory factory = JavaPsiFacade.getInstance(myProject).getElementFactory();
      prototype = factory.createMethod(getChosenMethodName(), myReturnType);
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

  protected void checkParametersConflicts(MultiMap<PsiElement, String> conflicts) {
    Set<String> usedNames = new HashSet<>();
    for (VariableData data : myInputVariables) {
      if (data.passAsParameter && !usedNames.add(data.name)) {
        conflicts.putValue(null, "Conflicting parameter name: " + data.name);
      }
    }
  }

  @Override
  public PsiType getReturnType() {
    return mySelector != null ? mySelector.getSelectedType() : myReturnType;
  }
}
