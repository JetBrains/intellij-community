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
package com.intellij.refactoring.anonymousToInner;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.ui.NameSuggestionsField;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.ParameterTablePanel;
import com.intellij.refactoring.util.RefactoringMessageUtil;
import com.intellij.refactoring.util.VariableData;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.NonFocusableCheckBox;
import com.intellij.util.Function;
import com.intellij.util.containers.HashMap;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

class AnonymousToInnerDialog extends DialogWrapper{
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.anonymousToInner.AnonymousToInnerDialog");

  private final Project myProject;
  private final PsiAnonymousClass myAnonClass;
  private final boolean myShowCanBeStatic;

  private NameSuggestionsField myNameField;
  private final VariableData[] myVariableData;
  private final Map<PsiVariable,VariableInfo> myVariableToInfoMap = new HashMap<>();
  private JCheckBox myCbMakeStatic;

  public AnonymousToInnerDialog(Project project, PsiAnonymousClass anonClass, final VariableInfo[] variableInfos,
                                boolean showCanBeStatic) {
    super(project, true);
    myProject = project;
    myAnonClass = anonClass;
    myShowCanBeStatic = showCanBeStatic;

    setTitle(AnonymousToInnerHandler.REFACTORING_NAME);

    for (VariableInfo info : variableInfos) {
      myVariableToInfoMap.put(info.variable, info);
    }
    myVariableData = new VariableData[variableInfos.length];

    final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(myProject);
    for(int idx = 0; idx < variableInfos.length; idx++){
      VariableInfo info = variableInfos[idx];
      String name = info.variable.getName();
      VariableKind kind = codeStyleManager.getVariableKind(info.variable);
      name = codeStyleManager.variableNameToPropertyName(name, kind);
      name = codeStyleManager.propertyNameToVariableName(name, VariableKind.PARAMETER);
      VariableData data = new VariableData(info.variable);
      data.name = name;
      data.passAsParameter = true;
      myVariableData[idx] = data;
    }

    init();

    final String[] names;
    String name = myAnonClass.getBaseClassReference().getReferenceName();
    PsiType[] typeParameters = myAnonClass.getBaseClassReference().getTypeParameters();

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

    if (!typeParamsList.equals(name)) {
      names = new String[]{typeParamsList, "My" + name};
    } else {
      names = new String[]{"My" + name};
    }
    myNameField.setSuggestions(names);
    myNameField.selectNameWithoutExtension();
  }

  @NotNull
  protected Action[] createActions(){
    return new Action[]{getOKAction(),getCancelAction(),getHelpAction()};
  }

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
    JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(myProject);
    VariableInfo[] infos = new VariableInfo[myVariableData.length];
    for (int idx = 0; idx < myVariableData.length; idx++) {
      VariableData data = myVariableData[idx];
      VariableInfo info = myVariableToInfoMap.get(data.variable);

      info.passAsParameter = data.passAsParameter;
      info.parameterName = data.name;
      info.parameterName = data.name;
      String propertyName = codeStyleManager.variableNameToPropertyName(data.name, VariableKind.PARAMETER);
      info.fieldName = codeStyleManager.propertyNameToVariableName(propertyName, VariableKind.FIELD);

      infos[idx] = info;
    }
    return infos;
  }

  protected void doOKAction(){
    String errorString = null;
    final String innerClassName = getClassName();
    final PsiManager manager = PsiManager.getInstance(myProject);
    if ("".equals(innerClassName)) {
      errorString = RefactoringBundle.message("anonymousToInner.no.inner.class.name");
    }
    else {
      if (!PsiNameHelper.getInstance(manager.getProject()).isIdentifier(innerClassName)) {
        errorString = RefactoringMessageUtil.getIncorrectIdentifierMessage(innerClassName);
      }
      else{
        PsiElement targetContainer = AnonymousToInnerHandler.findTargetContainer(myAnonClass);
        if (targetContainer instanceof PsiClass) {
          PsiClass targetClass = (PsiClass)targetContainer;
          PsiClass[] innerClasses = targetClass.getInnerClasses();
          for (PsiClass innerClass : innerClasses) {
            if (innerClassName.equals(innerClass.getName())) {
              errorString = RefactoringBundle.message("inner.class.exists", innerClassName, targetClass.getName());
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
        AnonymousToInnerHandler.REFACTORING_NAME,
        errorString,
        HelpID.ANONYMOUS_TO_INNER,
        myProject);
      myNameField.requestFocusInWindow();
      return;
    }
    super.doOKAction();
    myNameField.requestFocusInWindow();
  }

  protected JComponent createNorthPanel() {
    myNameField = new NameSuggestionsField(myProject);

    FormBuilder formBuilder = FormBuilder.createFormBuilder()
      .addLabeledComponent(RefactoringBundle.message("anonymousToInner.class.name.label.text"), myNameField);

    if(!myShowCanBeStatic) {
      myCbMakeStatic = new NonFocusableCheckBox(RefactoringBundle.message("anonymousToInner.make.class.static.checkbox.text"));
      myCbMakeStatic.setSelected(true);
      formBuilder.addComponent(myCbMakeStatic);
    }

    return formBuilder.getPanel();
  }

  private JComponent createParametersPanel() {
    JPanel panel = new ParameterTablePanel(myProject, myVariableData, myAnonClass) {
      protected void updateSignature() {
      }

      protected void doEnterAction() {
        clickDefaultButton();
      }

      protected void doCancelAction() {
        AnonymousToInnerDialog.this.doCancelAction();
      }
    };
    panel.setBorder(IdeBorderFactory.createTitledBorder(
      RefactoringBundle.message("anonymousToInner.parameters.panel.border.title"), false));
    return panel;
  }

  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(createParametersPanel(), BorderLayout.CENTER);
    return panel;
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(HelpID.ANONYMOUS_TO_INNER);
  }
}
