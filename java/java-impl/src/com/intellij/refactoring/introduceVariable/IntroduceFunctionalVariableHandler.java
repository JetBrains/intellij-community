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
package com.intellij.refactoring.introduceVariable;

import com.intellij.codeInsight.FunctionalInterfaceSuggester;
import com.intellij.codeInsight.navigation.NavigationUtil;
import com.intellij.ide.util.PsiClassListCellRenderer;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pass;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.actions.IntroduceFunctionalVariableAction;
import com.intellij.refactoring.extractMethod.*;
import com.intellij.refactoring.introduceParameter.IntroduceParameterHandler;
import com.intellij.refactoring.rename.inplace.VariableInplaceRenamer;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class IntroduceFunctionalVariableHandler extends IntroduceVariableHandler {

  @Override
  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file, DataContext dataContext) {
    ExtractMethodHandler.selectAndPass(project, editor, file, new Pass<PsiElement[]>() {
      @Override
      public void pass(PsiElement[] elements) {
        PsiElement anchorStatement = RefactoringUtil.getParentStatement(elements[0], false);
        PsiElement tempContainer = checkAnchorStatement(project, editor, anchorStatement);
        if (tempContainer == null) return;

        PsiElement[] elementsInCopy = IntroduceParameterHandler.getElementsInCopy(project, file, elements);
        MyExtractMethodProcessor processor =
          new MyExtractMethodProcessor(project, editor, elementsInCopy, null, IntroduceFunctionalVariableAction.REFACTORING_NAME, null,
                                       HelpID.INTRODUCE_VARIABLE);
        try {
          processor.prepare();
        }
        catch (PrepareFailedException e) {
          showErrorMessage(project, editor);
        }

        if (!processor.showDialog()) return;

        final PsiMethod emptyMethod = JavaPsiFacade.getElementFactory(project)
          .createMethodFromText(processor.generateEmptyMethod("name").getText(), elements[0]);
        final Collection<? extends PsiType> types = FunctionalInterfaceSuggester.suggestFunctionalInterfaces(emptyMethod);
        if (types.isEmpty()) {
          showErrorMessage(project, editor, "No applicable functional interfaces found");
          return;
        }
        if (types.size() == 1 || ApplicationManager.getApplication().isUnitTestMode()) {
          functionalInterfaceSelected(ContainerUtil.getFirstItem(types), project, editor, processor, elements, anchorStatement);
        }
        else {
          final Map<PsiClass, PsiType> classes = new LinkedHashMap<>();
          for (PsiType type : types) {
            classes.put(PsiUtil.resolveClassInType(type), type);
          }
          final PsiClass[] psiClasses = classes.keySet().toArray(new PsiClass[classes.size()]);
          final String methodSignature =
            PsiFormatUtil.formatMethod(emptyMethod, PsiSubstitutor.EMPTY, PsiFormatUtilBase.SHOW_PARAMETERS, PsiFormatUtilBase.SHOW_TYPE);
          final PsiType returnType = emptyMethod.getReturnType();
          assert returnType != null;
          final String title = "Choose Applicable Functional Interface: " + methodSignature + " -> " + returnType.getPresentableText();
          NavigationUtil.getPsiElementPopup(psiClasses, new PsiClassListCellRenderer(), title,
                                            new PsiElementProcessor<PsiClass>() {
                                              @Override
                                              public boolean execute(@NotNull PsiClass psiClass) {
                                                functionalInterfaceSelected(classes.get(psiClass), project, editor, processor, elements,
                                                                            anchorStatement);
                                                return true;
                                              }
                                            }).showInBestPositionFor(editor);
        }
      }
    });
  }

  private static void functionalInterfaceSelected(PsiType type,
                                                  Project project,
                                                  Editor editor,
                                                  MyExtractMethodProcessor processor,
                                                  PsiElement[] elements,
                                                  PsiElement anchorStatement) {
    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, elements[0])) return;
    PsiMethodCallExpression functionalExpression = createReplacement(project, type, processor, elements);
    
    PsiExpression qualifier = functionalExpression.getMethodExpression().getQualifierExpression();

    assert qualifier != null;
    
    SuggestedNameInfo uniqueNames = getSuggestedName(type, qualifier, anchorStatement);

    WriteCommandAction.runWriteCommandAction(project, () -> {
      PsiDeclarationStatement declaration =
        replaceSelectionWithFunctionalCall(type, elements, anchorStatement, functionalExpression, qualifier, uniqueNames.names[0]);

      PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.getDocument());

      PsiLocalVariable localVariable = (PsiLocalVariable)declaration.getDeclaredElements()[0];
      PsiIdentifier nameIdentifier = localVariable.getNameIdentifier();
      final int textOffset = ObjectUtils.notNull(nameIdentifier, localVariable).getTextOffset();
      editor.getCaretModel().moveToOffset(textOffset);
      new VariableInplaceRenamer(localVariable, editor) {
        @Override
        protected boolean shouldSelectAll() {
          return true;
        }

        @Override
        protected void moveOffsetAfter(boolean success) {
          super.moveOffsetAfter(success);
          if (success) {
            final PsiNamedElement renamedVariable = getVariable();
            if (renamedVariable != null) {
              editor.getCaretModel().moveToOffset(renamedVariable.getTextRange().getEndOffset());
            }
          }
        }
      }.performInplaceRename();
    });
  }

  private static PsiDeclarationStatement replaceSelectionWithFunctionalCall(PsiType type,
                                                                            PsiElement[] elements,
                                                                            PsiElement anchorStatement,
                                                                            PsiMethodCallExpression functionalExpression,
                                                                            PsiExpression qualifier,
                                                                            String variableName) {
    PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(functionalExpression.getProject());
    PsiElement tempContainer = anchorStatement.getParent();

    boolean singleExpression = elements.length == 1 && elements[0] instanceof PsiExpression;
    PsiDeclarationStatement declaration = elementFactory
      .createVariableDeclarationStatement(variableName, type, qualifier, anchorStatement);
    String callExpressionText = variableName + "." +
                                functionalExpression.getMethodExpression().getReferenceName() +
                                functionalExpression.getArgumentList().getText();
    if (singleExpression) {
      elements[0].replace(elementFactory.createExpressionFromText(callExpressionText, declaration));
    }
    if (RefactoringUtil.isLoopOrIf(tempContainer)) {
      declaration = (PsiDeclarationStatement)RefactoringUtil.putStatementInLoopBody(declaration, tempContainer, anchorStatement, !singleExpression);
      tempContainer = declaration.getParent();
    }
    else {
      declaration = (PsiDeclarationStatement)tempContainer.addBefore(declaration, anchorStatement);
      if (!singleExpression) {
        tempContainer.deleteChildRange(elements[0], elements[elements.length - 1]);
      }
    }

    if (!singleExpression) {
      tempContainer.addAfter(elementFactory.createStatementFromText(callExpressionText + ";", declaration), declaration);
    }
    return (PsiDeclarationStatement)JavaCodeStyleManager.getInstance(declaration.getProject()).shortenClassReferences(declaration);
  }

  private static PsiMethodCallExpression createReplacement(Project project,
                                                           PsiType selectedType,
                                                           ExtractMethodProcessor processor,
                                                           PsiElement[] elements) {
    final PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(selectedType);
    final PsiClass wrapperClass = resolveResult.getElement();

    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    final PsiMethod method = LambdaUtil.getFunctionalInterfaceMethod(wrapperClass);
    assert method != null : "not functional class";
    final String interfaceMethodName = method.getName();
    processor.setMethodName(interfaceMethodName);
    processor.doExtract();

    final PsiMethod extractedMethod = processor.getExtractedMethod();
    final PsiParameter[] parameters = extractedMethod.getParameterList().getParameters();
    final PsiParameter[] interfaceParameters = method.getParameterList().getParameters();
    final PsiSubstitutor substitutor = resolveResult.getSubstitutor();
    for (int i = 0; i < interfaceParameters.length; i++) {
      final PsiTypeElement typeAfterInterface = factory.createTypeElement(substitutor.substitute(interfaceParameters[i].getType()));
      final PsiTypeElement typeElement = parameters[i].getTypeElement();
      if (typeElement != null) {
        typeElement.replace(typeAfterInterface);
      }
    }
    final PsiMethodCallExpression methodCall = processor.getMethodCall();
    PsiExpression psiExpression = factory
      .createExpressionFromText("new " + selectedType.getCanonicalText() + "() {" + extractedMethod.getText() + "}." + methodCall.getText(),
                                elements[0]);
    return (PsiMethodCallExpression)JavaCodeStyleManager.getInstance(project).shortenClassReferences(psiExpression);
  }


  @Override
  protected void showErrorMessage(Project project, Editor editor, String message) {
    CommonRefactoringUtil
      .showErrorHint(project, editor, message, IntroduceFunctionalVariableAction.REFACTORING_NAME, HelpID.INTRODUCE_VARIABLE);
  }

  private void showErrorMessage(@NotNull Project project, Editor editor) {
    final String message = RefactoringBundle
      .getCannotRefactorMessage(
        RefactoringBundle.message("is.not.supported.in.the.current.context", IntroduceFunctionalVariableAction.REFACTORING_NAME));
    showErrorMessage(project, editor, message);
  }

  protected void setupProcessorWithoutDialog(ExtractMethodProcessor processor, InputVariables inputVariables) {
    processor.setDataFromInputVariables();
    processor.setMethodVisibility(PsiModifier.PUBLIC);
  }
  
  private class MyExtractMethodProcessor extends ExtractMethodProcessor {

    public MyExtractMethodProcessor(Project project,
                                    Editor editor,
                                    PsiElement[] elements,
                                    PsiType forcedReturnType,
                                    String refactoringName, String initialMethodName, String helpId) {
      super(project, editor, elements, forcedReturnType, refactoringName, initialMethodName, helpId);
    }

    @Override
    public boolean isStatic() {
      return false;
    }
    
    @Override
    protected boolean isFoldingApplicable() {
      return false;
    }

    @Override
    protected AbstractExtractDialog createExtractMethodDialog(boolean direct) {
      setDataFromInputVariables();
      return new ExtractMethodDialog(myProject, myTargetClass, myInputVariables, null, getTypeParameterList(),
                                     getThrownExceptions(), isStatic(), isCanBeStatic(), false,
                                     IntroduceFunctionalVariableAction.REFACTORING_NAME, HelpID.INTRODUCE_VARIABLE, null, myElements) {
        @Override
        protected JComponent createNorthPanel() {
          if (!myInputVariables.hasInstanceFields()) {
            return null;
          }
          JPanel optionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 5));
          createStaticOptions(optionsPanel, "Pass fields as params");
          return optionsPanel;
        }

        @Override
        public JComponent getPreferredFocusedComponent() {
          return myParamTable;
        }

        @Override
        protected String getSignature() {
          String parametersList =
            Arrays.stream(getChosenParameters())
              .filter(data -> data.passAsParameter)
              .map(data -> data.type.getPresentableText())
              .reduce((result, item) -> result + ", " + item)
              .orElse("");
          String returnTypeString = myReturnType == null || PsiType.VOID.equals(myReturnType) 
                                    ? "{}" : myReturnType.getPresentableText();
          return "(" + parametersList + ") -> " + returnTypeString;
        }

        @NotNull
        @Override
        public String getVisibility() {
          return PsiModifier.PUBLIC;
        }
      };
    }

    @Override
    public boolean showDialog() {
      if (!myInputVariables.hasInstanceFields() && myInputVariables.getInputVariables().isEmpty() ||
           ApplicationManager.getApplication().isUnitTestMode()) {
        setupProcessorWithoutDialog(this, myInputVariables);
        return true;
      }
      return super.showDialog();
    }

    @Override
    protected boolean defineVariablesForUnselectedParameters() {
      return false;
    }
  }
}
