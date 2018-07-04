// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.introduceVariable;

import com.intellij.codeInsight.ChangeContextUtil;
import com.intellij.codeInsight.FunctionalInterfaceSuggester;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.codeInsight.navigation.NavigationUtil;
import com.intellij.codeInspection.AnonymousCanBeLambdaInspection;
import com.intellij.ide.util.PsiClassListCellRenderer;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pass;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.actions.IntroduceFunctionalVariableAction;
import com.intellij.refactoring.extractMethod.*;
import com.intellij.refactoring.introduceParameter.IntroduceParameterHandler;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.RefactoringChangeUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.refactoring.util.VariableData;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.*;

public class IntroduceFunctionalVariableHandler extends IntroduceVariableHandler {

  @Override
  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file, DataContext dataContext) {
    ExtractMethodHandler.selectAndPass(project, editor, file, new Pass<PsiElement[]>() {
      @Override
      public void pass(PsiElement[] elements) {
        if (elements.length == 0) {
          String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("selected.block.should.represent.a.set.of.statements.or.an.expression"));
          showErrorMessage(project , editor, message);
          return;
        }
        PsiElement anchorStatement = elements[0] instanceof PsiComment ? elements[0] : RefactoringUtil.getParentStatement(elements[0], false);
        PsiElement tempContainer = checkAnchorStatement(project, editor, anchorStatement);
        if (tempContainer == null) return;

        PsiElement[] elementsInCopy = IntroduceParameterHandler.getElementsInCopy(project, file, elements);
        MyExtractMethodProcessor processor =
          new MyExtractMethodProcessor(project, editor, elementsInCopy, null, IntroduceFunctionalVariableAction.REFACTORING_NAME, null,
                                       HelpID.INTRODUCE_VARIABLE);
        processor.setShowErrorDialogs(false);
        try {
          if (!processor.prepare()) {
            showErrorMessage(project, editor);
            return;
          }
        }
        catch (PrepareFailedException e) {
          showErrorMessage(project, editor, e.getMessage());
          return;
        }

        if (!processor.showDialog()) return;

        final PsiMethod emptyMethod = processor.generateEmptyMethod("name", elements[0]);
        Collection<? extends PsiType> types = FunctionalInterfaceSuggester.suggestFunctionalInterfaces(emptyMethod);
        if (types.isEmpty()) {
          types = FunctionalInterfaceSuggester.suggestFunctionalInterfaces(emptyMethod, true);
        }

        if (types.isEmpty()) {
          showErrorMessage(project, editor, "No applicable functional interfaces found");
          return;
        }
        if (types.size() == 1 || ApplicationManager.getApplication().isUnitTestMode()) {
          functionalInterfaceSelected(ContainerUtil.getFirstItem(types), project, editor, processor, elements);
        }
        else {
          final Map<PsiClass, PsiType> classes = new LinkedHashMap<>();
          for (PsiType type : types) {
            classes.put(PsiUtil.resolveClassInType(type), type);
          }
          final PsiClass[] psiClasses = classes.keySet().toArray(PsiClass.EMPTY_ARRAY);
          final String methodSignature =
            PsiFormatUtil.formatMethod(emptyMethod, PsiSubstitutor.EMPTY, PsiFormatUtilBase.SHOW_PARAMETERS, PsiFormatUtilBase.SHOW_TYPE);
          final PsiType returnType = emptyMethod.getReturnType();
          assert returnType != null;
          final String title = "Choose Applicable Functional Interface: " + methodSignature + " -> " + returnType.getPresentableText();
          NavigationUtil.getPsiElementPopup(psiClasses, new PsiClassListCellRenderer(), title,
                                            new PsiElementProcessor<PsiClass>() {
                                              @Override
                                              public boolean execute(@NotNull PsiClass psiClass) {
                                                functionalInterfaceSelected(classes.get(psiClass), project, editor, processor, elements);
                                                return true;
                                              }
                                            }).showInBestPositionFor(editor);
        }
      }
    });
  }

  private  void functionalInterfaceSelected(PsiType type,
                                            Project project,
                                            Editor editor,
                                            MyExtractMethodProcessor processor,
                                            PsiElement[] elements) {
    if (!elements[0].isValid()) return;
    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, elements[0])) return;
    MyExtractMethodProcessor physicalProcessor =
      new MyExtractMethodProcessor(project, editor, elements, 
                                   null, IntroduceFunctionalVariableAction.REFACTORING_NAME, null, HelpID.INTRODUCE_VARIABLE);
    try {
      physicalProcessor.prepare();
    }
    catch (PrepareFailedException e) {
      showErrorMessage(project, editor);
    }

    physicalProcessor.copyParameters(processor);
    physicalProcessor.setMethodVisibility(PsiModifier.PUBLIC);
    CommandProcessor.getInstance().executeCommand(project, () -> {
      PsiMethodCallExpression expression = WriteAction.compute(() -> createReplacement(project, type, physicalProcessor));
      invokeImpl(project, expression.getMethodExpression().getQualifierExpression(), editor);
    }, IntroduceFunctionalVariableAction.REFACTORING_NAME, null);
  }

  private static PsiMethodCallExpression createReplacement(Project project,
                                                           PsiType selectedType,
                                                           ExtractMethodProcessor processor) {
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
    PsiMethodCallExpression psiExpression = (PsiMethodCallExpression)factory
      .createExpressionFromText("new " + selectedType.getCanonicalText() + "() {" + "}." + methodCall.getText(),
                                methodCall);
    
    PsiExpression qualifierExpression = psiExpression.getMethodExpression().getQualifierExpression();
    assert qualifierExpression != null;
    PsiAnonymousClass anonymousClass = ((PsiNewExpression)qualifierExpression).getAnonymousClass();
    assert anonymousClass != null;
    ChangeContextUtil.encodeContextInfo(extractedMethod, true);
    PsiClass aClass = extractedMethod.getContainingClass();
    ChangeContextUtil.decodeContextInfo(anonymousClass.add(extractedMethod), aClass, 
                                        RefactoringChangeUtil.createThisExpression(anonymousClass.getManager(), aClass));
    if (AnonymousCanBeLambdaInspection.canBeConvertedToLambda(anonymousClass, false, Collections.emptySet())) {
      PsiExpression castExpression = JavaPsiFacade.getElementFactory(project)
        .createExpressionFromText("((" +
                                  selectedType.getCanonicalText() + ")" +
                                  AnonymousCanBeLambdaInspection.replaceAnonymousWithLambda(qualifierExpression, selectedType).getText() +
                                  ")", qualifierExpression);
      qualifierExpression.replace(castExpression);
    }
      
    extractedMethod.delete();
    return (PsiMethodCallExpression)JavaCodeStyleManager.getInstance(project).shortenClassReferences(methodCall.replace(psiExpression));
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
    protected void showMultipleOutputMessage(PsiType expressionType) throws PrepareFailedException {
      throw new PrepareFailedException(buildMultipleOutputMessageError(expressionType), myElements[0]);
    }

    @Override
    protected AbstractExtractDialog createExtractMethodDialog(boolean direct) {
      setDataFromInputVariables();
      return new ExtractMethodDialog(myProject, myTargetClass, myInputVariables, null, getTypeParameterList(),
                                     getThrownExceptions(), isStatic(), isCanBeStatic(), false,
                                     IntroduceFunctionalVariableAction.REFACTORING_NAME, HelpID.INTRODUCE_VARIABLE, null, myElements, null) {
        @Override
        protected JComponent createNorthPanel() {
          if (!myInputVariables.hasInstanceFields()) {
            return null;
          }
          JPanel optionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 5));
          createStaticOptions(optionsPanel, RefactoringBundle.message("introduce.functional.variable.pass.fields.checkbox"));
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

        @Override
        protected void checkMethodConflicts(MultiMap<PsiElement, String> conflicts) {
          checkParametersConflicts(conflicts);
          for (VariableData data : getChosenParameters()) {
            if (!data.passAsParameter) {
              PsiElement scope = PsiUtil.getVariableCodeBlock(data.variable, null);
              if (PsiUtil.isLanguageLevel8OrHigher(data.variable) 
                  ? scope != null && !HighlightControlFlowUtil.isEffectivelyFinal(data.variable, scope, null) 
                  : data.variable.hasModifierProperty(PsiModifier.FINAL)) {
                conflicts.putValue(null, "Variable " + data.name + " is not effectively final and won't be accessible inside functional expression");
              }
            }
          }
        }

        @NotNull
        @Override
        public String getVisibility() {
          return PsiModifier.PUBLIC;
        }
      };
    }

    @Override
    public boolean prepare(@Nullable Pass<ExtractMethodProcessor> pass) throws PrepareFailedException {
      final boolean prepare = super.prepare(pass);
      if (prepare) {
        if (myNotNullConditionalCheck || myNullConditionalCheck) {
          return false;
        }
      }
      return prepare;
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

    public void copyParameters(MyExtractMethodProcessor processor) {
      myInputVariables.setPassFields(processor.myInputVariables.isPassFields());
      VariableData[] variables = processor.myVariableDatum;
      myVariableDatum = new VariableData[variables.length];
      for (int i = 0; i < variables.length; i++) {
        VariableData data = variables[i];
        String variableName = data.variable.getName();
        assert variableName != null;
        VariableData dataByVName =
          myInputVariables.getInputVariables()
            .stream()
            .filter(vData -> variableName.equals(vData.variable.getName())).findFirst()
            .orElse(null);
        if (dataByVName != null) {
          dataByVName.passAsParameter = data.passAsParameter;
          dataByVName.name = data.name;
          myVariableDatum[i] = dataByVName;
        }
      }
    }
  }
}
