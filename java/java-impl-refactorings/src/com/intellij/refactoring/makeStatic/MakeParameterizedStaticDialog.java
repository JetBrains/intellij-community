// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.makeStatic;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.lang.findUsages.DescriptiveNameUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.ParameterTablePanel;
import com.intellij.refactoring.util.VariableData;
import com.intellij.ui.DocumentAdapter;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;

public class MakeParameterizedStaticDialog extends AbstractMakeStaticDialog {
  private final String[] myNameSuggestions;

  private final JCheckBox myMakeClassParameter = new JCheckBox();
  private JComponent myClassParameterNameInputField;
  private final JCheckBox myMakeFieldParameters = new JCheckBox();

  private ParameterTablePanel myParameterPanel;
  private VariableData[] myVariableData;
  private final boolean myPreferOuterClassParameter;
  private JCheckBox myGenerateDelegateCb;


  public MakeParameterizedStaticDialog(Project project,
                                       PsiTypeParameterListOwner member,
                                       String[] nameSuggestions,
                                       InternalUsageInfo[] internalUsages) {
    super(project, member);
    myNameSuggestions = nameSuggestions;

    String type = UsageViewUtil.getType(myMember);
    setTitle(JavaRefactoringBundle.message("make.0.static", StringUtil.capitalize(type)));
    myPreferOuterClassParameter = buildVariableData(internalUsages) ||
                                  ContainerUtil.exists(myVariableData, data -> !data.variable.hasModifierProperty(PsiModifier.FINAL));
    init();
  }

  private boolean buildVariableData(InternalUsageInfo[] internalUsages) {
    ArrayList<VariableData> variableDatum = new ArrayList<>();
    boolean nonFieldUsages = MakeStaticUtil.collectVariableData(myMember, internalUsages, variableDatum);

    myVariableData = variableDatum.toArray(new VariableData[0]);
    return nonFieldUsages;
  }

  @Override
  public boolean isReplaceUsages() {
    return true;
  }

  @Override
  public boolean isMakeClassParameter() {
    if (myMakeClassParameter != null)
      return myMakeClassParameter.isSelected();
    else
      return false;
  }

  @Override
  public String getClassParameterName() {
    if (isMakeClassParameter()) {
      if (myClassParameterNameInputField instanceof JTextField) {
        return ((JTextField)myClassParameterNameInputField).getText();
      }
      else if(myClassParameterNameInputField instanceof JComboBox) {
        return (String)(((JComboBox<?>)myClassParameterNameInputField).getEditor().getItem());
      }
      else
        return null;
    }
    else {
      return null;
    }
  }

  /**
   *
   * @return null if field parameters are not selected
   */
  @Override
  public VariableData[] getVariableData() {
    if(myMakeFieldParameters != null && myMakeFieldParameters.isSelected()) {
      return myVariableData;
    }
    else {
      return null;
    }
  }

  @Override
  protected String getHelpId() {
    return HelpID.MAKE_METHOD_STATIC;
  }

  @Override
  protected JComponent createCenterPanel() {
    GridBagConstraints gbConstraints = new GridBagConstraints();

    JPanel panel = new JPanel(new GridBagLayout());

    gbConstraints.insets = JBInsets.create(4, 8);
    gbConstraints.weighty = 0;
    gbConstraints.weightx = 0;
    gbConstraints.gridx = 0;
    gbConstraints.gridy = GridBagConstraints.RELATIVE;
    gbConstraints.gridwidth = GridBagConstraints.REMAINDER;
    gbConstraints.fill = GridBagConstraints.NONE;
    gbConstraints.anchor = GridBagConstraints.WEST;
    panel.add(createDescriptionLabel(), gbConstraints);

    gbConstraints.weighty = 0;
    gbConstraints.weightx = 0;
    gbConstraints.gridwidth = GridBagConstraints.REMAINDER;
    gbConstraints.fill = GridBagConstraints.NONE;
    gbConstraints.anchor = GridBagConstraints.WEST;
    String text = myMember instanceof PsiMethod ? RefactoringBundle.message("add.object.as.a.parameter.with.name") : JavaRefactoringBundle.message("add.object.as.a.parameter.to.constructors.with.name");
    myMakeClassParameter.setText(text);
    panel.add(myMakeClassParameter, gbConstraints);
    myMakeClassParameter.setSelected(myPreferOuterClassParameter);

    gbConstraints.insets = JBUI.insets(0, 8, 4, 8);
    gbConstraints.weighty = 0;
    gbConstraints.weightx = 1;
    gbConstraints.gridwidth = 2;
    gbConstraints.fill = GridBagConstraints.HORIZONTAL;
    gbConstraints.anchor = GridBagConstraints.NORTHWEST;
    if(myNameSuggestions.length > 1) {
      myClassParameterNameInputField = createComboBoxForName();
    }
    else {
      JTextField textField = new JTextField();
      textField.setText(myNameSuggestions[0]);
      textField.getDocument().addDocumentListener(new DocumentAdapter() {
        @Override
        public void textChanged(@NotNull DocumentEvent event) {
          updateControls();
        }
      });
      myClassParameterNameInputField = textField;
    }
    panel.add(myClassParameterNameInputField, gbConstraints);

    gbConstraints.gridwidth = GridBagConstraints.REMAINDER;

    if(myVariableData.length > 0) {
      gbConstraints.insets = JBInsets.create(4, 8);
      gbConstraints.weighty = 0;
      gbConstraints.weightx = 0;
      gbConstraints.gridheight = 1;
      gbConstraints.fill = GridBagConstraints.NONE;
      gbConstraints.anchor = GridBagConstraints.WEST;
      text = myMember instanceof PsiMethod ? JavaRefactoringBundle.message("add.parameters.for.fields") : JavaRefactoringBundle.message("add.parameters.for.fields.to.constructors");
      myMakeFieldParameters.setText(text);
      panel.add(myMakeFieldParameters, gbConstraints);
      myMakeFieldParameters.setSelected(!myPreferOuterClassParameter);

      myParameterPanel = new ParameterTablePanel(myProject, myVariableData, myMember) {
        @Override
        protected void updateSignature() {
        }

        @Override
        protected void doEnterAction() {
          clickDefaultButton();
        }

        @Override
        protected void doCancelAction() {
        }
      };

      gbConstraints.insets = JBUI.insets(0, 8, 4, 8);
      gbConstraints.gridwidth = 2;
      gbConstraints.fill = GridBagConstraints.BOTH;
      gbConstraints.weighty = 1;
      panel.add(myParameterPanel, gbConstraints);
    }

    ActionListener inputFieldValidator = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        updateControls();
      }
    };

    myMakeClassParameter.addActionListener(inputFieldValidator);
    myMakeFieldParameters.addActionListener(inputFieldValidator);


    if (myMember instanceof PsiMethod) {
      myGenerateDelegateCb = new JCheckBox(RefactoringBundle.message("delegation.panel.delegate.via.overloading.method"));
      panel.add(myGenerateDelegateCb, gbConstraints);
    }

    updateControls();

    return panel;
  }

  @Override
  protected boolean isGenerateDelegate() {
    return myGenerateDelegateCb != null && myGenerateDelegateCb.isSelected();
  }

  @Override
  protected boolean validateData() {
    int ret = Messages.YES;
    if (isMakeClassParameter()) {
      final PsiMethod methodWithParameter = checkParameterDoesNotExist();
      if (methodWithParameter != null) {
        String who = methodWithParameter == myMember ? JavaRefactoringBundle.message("this.method") : DescriptiveNameUtil
          .getDescriptiveName(methodWithParameter);
        String message = JavaRefactoringBundle.message("0.already.has.parameter.named.1.use.this.name.anyway", who, getClassParameterName());
        ret = Messages.showYesNoDialog(myProject, message, RefactoringBundle.message("warning.title"), Messages.getWarningIcon());
        myClassParameterNameInputField.requestFocusInWindow();
      }
    }
    return ret == Messages.YES;
  }

  private PsiMethod checkParameterDoesNotExist() {
    String parameterName = getClassParameterName();
    if(parameterName == null) return null;
    PsiMethod[] methods = myMember instanceof PsiMethod ? new PsiMethod[]{(PsiMethod)myMember} : ((PsiClass)myMember).getConstructors();
    for (PsiMethod method : methods) {
      PsiParameterList parameterList = method.getParameterList();
      PsiParameter[] parameters = parameterList.getParameters();
      for (PsiParameter parameter : parameters) {
        if (parameterName.equals(parameter.getName())) return method;
      }
    }

    return null;
  }

  private void updateControls() {
    if (isMakeClassParameter()) {
      String classParameterName = getClassParameterName();
      if (classParameterName == null) {
        setOKActionEnabled(false);
      }
      else {
        setOKActionEnabled(PsiNameHelper.getInstance(myProject).isIdentifier(classParameterName.trim()));
      }
    }
    else
      setOKActionEnabled(true);

    if(myClassParameterNameInputField != null) {
      myClassParameterNameInputField.setEnabled(isMakeClassParameter());
    }

    if(myParameterPanel != null) {
      myParameterPanel.setEnabled(myMakeFieldParameters.isSelected());
    }
  }

  private JComboBox createComboBoxForName() {
    final ComboBox combobox = new ComboBox(myNameSuggestions);

    combobox.setEditable(true);
    combobox.setSelectedIndex(0);
    combobox.setMaximumRowCount(8);

    combobox.addItemListener(
      new ItemListener() {
        @Override
        public void itemStateChanged(ItemEvent e) {
          updateControls();
        }
      }
    );
    combobox.getEditor().getEditorComponent().addKeyListener(
      new KeyAdapter() {
        @Override
        public void keyPressed(KeyEvent e) {
          updateControls();
        }

        @Override
        public void keyReleased(KeyEvent e) {
          updateControls();
        }

        @Override
        public void keyTyped(KeyEvent e) {
          updateControls();
        }
      }
    );

    return combobox;
  }
}