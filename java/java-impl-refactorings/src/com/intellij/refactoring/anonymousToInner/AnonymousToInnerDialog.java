// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.anonymousToInner;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.ui.NameSuggestionsField;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.ParameterTablePanel;
import com.intellij.refactoring.util.RefactoringMessageUtil;
import com.intellij.refactoring.util.VariableData;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.NonFocusableCheckBox;
import com.intellij.util.ui.FormBuilder;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

class AnonymousToInnerDialog extends DialogWrapper{
  private static final Logger LOG = Logger.getInstance(AnonymousToInnerDialog.class);

  private final Project myProject;
  private final PsiAnonymousClass myAnonClass;
  private final boolean myShowCanBeStatic;

  private NameSuggestionsField myNameField;
  private final VariableData[] myVariableData;
  private final Map<PsiVariable,VariableInfo> myVariableToInfoMap = new HashMap<>();
  private JCheckBox myCbMakeStatic;

  AnonymousToInnerDialog(Project project, PsiAnonymousClass anonClass, final VariableInfo[] variableInfos, boolean showCanBeStatic) {
    super(project, true);
    myProject = project;
    myAnonClass = anonClass;
    myShowCanBeStatic = showCanBeStatic;

    setTitle(AnonymousToInnerHandler.getRefactoringName());

    for (VariableInfo info : variableInfos) {
      myVariableToInfoMap.put(info.variable, info);
    }
    myVariableData = new VariableData[variableInfos.length];

    fillVariableData(myProject, variableInfos, myVariableData);

    init();

    final String[] names = suggestNewClassNames(myAnonClass);
    myNameField.setSuggestions(names);
    myNameField.selectNameWithoutExtension();
  }

  public static String[] suggestNewClassNames(PsiAnonymousClass anonymousClass) {
    String name = anonymousClass.getBaseClassReference().getReferenceName();
    PsiType[] typeParameters = anonymousClass.getBaseClassReference().getTypeParameters();

    final String typeParamsList = StringUtil.join(typeParameters, psiType -> {
      PsiType type = psiType;
      if (psiType instanceof PsiClassType) {
        type = TypeConversionUtil.erasure(psiType);
      }
      if (type == null || type.equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) return "";
      if (type instanceof PsiArrayType) {
        type = type.getDeepComponentType();
      }
      return StringUtil.getShortName(type.getPresentableText());
    }, "") + name;

    return !typeParamsList.equals(name) ? new String[]{typeParamsList, "My" + name} : new String[]{"My" + name};
  }

  public static void fillVariableData(Project project, VariableInfo[] variableInfos, VariableData[] variableData) {
    final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);
    for (int idx = 0; idx < variableInfos.length; idx++) {
      VariableInfo info = variableInfos[idx];
      String name = info.variable.getName();
      VariableKind kind = codeStyleManager.getVariableKind(info.variable);
      name = codeStyleManager.variableNameToPropertyName(name, kind);
      name = codeStyleManager.propertyNameToVariableName(name, VariableKind.PARAMETER);
      VariableData data = new VariableData(info.variable);
      data.name = name;
      data.passAsParameter = true;
      variableData[idx] = data;
    }
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myNameField.getFocusableComponent();
  }

  public boolean isMakeStatic() {
    return myCbMakeStatic.isSelected();
  }

  public String getClassName() {
    return myNameField.getEnteredName();
  }

  public VariableInfo[] getVariableInfos() {
    return getVariableInfos(myProject, myVariableData, myVariableToInfoMap);
  }

  public static VariableInfo[] getVariableInfos(Project myProject,
                                                VariableData[] myVariableData,
                                                Map<PsiVariable, VariableInfo> myVariableToInfoMap) {
    JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(myProject);
    VariableInfo[] infos = new VariableInfo[myVariableData.length];
    for (int idx = 0; idx < myVariableData.length; idx++) {
      VariableData data = myVariableData[idx];
      VariableInfo info = myVariableToInfoMap.get(data.variable);

      info.passAsParameter = data.passAsParameter;
      info.parameterName = data.name;
      String propertyName = codeStyleManager.variableNameToPropertyName(data.name, VariableKind.PARAMETER);
      info.fieldName = codeStyleManager.propertyNameToVariableName(propertyName, VariableKind.FIELD);

      infos[idx] = info;
    }
    return infos;
  }

  @Override
  protected void doOKAction(){
    String errorString = null;
    final String innerClassName = getClassName();
    final PsiManager manager = PsiManager.getInstance(myProject);
    if ("".equals(innerClassName)) {
      errorString = JavaRefactoringBundle.message("anonymousToInner.no.inner.class.name");
    }
    else {
      if (!PsiNameHelper.getInstance(manager.getProject()).isIdentifier(innerClassName)) {
        errorString = RefactoringMessageUtil.getIncorrectIdentifierMessage(innerClassName);
      }
      else{
        PsiElement targetContainer = AnonymousToInnerHandler.findTargetContainer(myAnonClass);
        if (targetContainer instanceof PsiClass targetClass) {
          PsiClass[] innerClasses = targetClass.getInnerClasses();
          for (PsiClass innerClass : innerClasses) {
            if (innerClassName.equals(innerClass.getName())) {
              errorString = JavaRefactoringBundle.message("inner.class.exists", innerClassName, targetClass.getName());
              break;
            }
          }
        }
        else {
          LOG.assertTrue(false);
        }
      }
    }

    if (errorString != null) {
      CommonRefactoringUtil.showErrorMessage(
        AnonymousToInnerHandler.getRefactoringName(),
        errorString,
        HelpID.ANONYMOUS_TO_INNER,
        myProject);
      myNameField.requestFocusInWindow();
      return;
    }
    super.doOKAction();
    myNameField.requestFocusInWindow();
  }

  @Override
  protected JComponent createNorthPanel() {
    myNameField = new NameSuggestionsField(myProject);

    FormBuilder formBuilder = FormBuilder.createFormBuilder()
      .addLabeledComponent(JavaRefactoringBundle.message("anonymousToInner.class.name.label.text"), myNameField);

    if (myShowCanBeStatic) {
      myCbMakeStatic = new NonFocusableCheckBox(JavaRefactoringBundle.message("anonymousToInner.make.class.static.checkbox.text"));
      myCbMakeStatic.setSelected(true);
      formBuilder.addComponent(myCbMakeStatic);
    }

    return formBuilder.getPanel();
  }

  private JComponent createParametersPanel() {
    JPanel panel = new ParameterTablePanel(myProject, myVariableData, myAnonClass) {
      @Override
      protected void updateSignature() {
      }

      @Override
      protected void doEnterAction() {
        clickDefaultButton();
      }

      @Override
      protected void doCancelAction() {
        AnonymousToInnerDialog.this.doCancelAction();
      }
    };
    panel.setBorder(IdeBorderFactory.createTitledBorder(
      JavaRefactoringBundle.message("anonymousToInner.parameters.panel.border.title"), false));
    return panel;
  }

  @Override
  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(createParametersPanel(), BorderLayout.CENTER);
    return panel;
  }

  @Override
  protected String getHelpId() {
    return HelpID.ANONYMOUS_TO_INNER;
  }
}