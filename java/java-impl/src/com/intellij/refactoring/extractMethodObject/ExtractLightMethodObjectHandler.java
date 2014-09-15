/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.refactoring.extractMethodObject;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.extractMethod.AbstractExtractDialog;
import com.intellij.refactoring.extractMethod.InputVariables;
import com.intellij.refactoring.extractMethod.PrepareFailedException;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.refactoring.util.VariableData;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ExtractLightMethodObjectHandler {
  private static final Logger LOG = Logger.getInstance("#" + ExtractLightMethodObjectHandler.class.getName());

  public static class ExtractedData {
    private String myGeneratedCallText;
    private PsiClass myGeneratedInnerClass;
    private final PsiElement myAnchor;

    public ExtractedData(String generatedCallText, PsiClass generatedInnerClass, PsiElement anchor) {
      myGeneratedCallText = generatedCallText;
      myGeneratedInnerClass = generatedInnerClass;
      myAnchor = anchor;
    }

    public PsiElement getAnchor() {
      return myAnchor;
    }

    public String getGeneratedCallText() {
      return myGeneratedCallText;
    }

    public PsiClass getGeneratedInnerClass() {
      return myGeneratedInnerClass;
    }
  }

  @Nullable
  public static ExtractedData extractLightMethodObject(final Project project,
                                                       final PsiFile file,
                                                       @NotNull final PsiCodeFragment fragment,
                                                       final String methodName) throws PrepareFailedException {
    PsiExpression expression = CodeInsightUtil.findExpressionInRange(fragment, 0, fragment.getTextLength());
    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
    final PsiElement[] elements;
    if (expression != null) {
      elements = new PsiElement[] {elementFactory.createStatementFromText(expression.getText() + ";", expression)};
    } else {
      elements = CodeInsightUtil.findStatementsInRange(fragment, 0, fragment.getTextLength());
    }
    if (elements.length == 0) {
      return null;
    }

    final PsiFile copy = PsiFileFactory.getInstance(project)
      .createFileFromText(file.getName(), file.getFileType(), file.getText(), file.getModificationStamp(), false);

    final PsiElement originalContext = fragment.getContext();
    if (originalContext == null) {
      return null;
    }
    final TextRange range = originalContext.getTextRange();
    final PsiElement originalAnchor =
      CodeInsightUtil.findElementInRange(copy, range.getStartOffset(), range.getEndOffset(), originalContext.getClass());
    //todo before this or super, not found etc
    final PsiElement anchor = RefactoringUtil.getParentStatement(originalAnchor, false);
    final PsiElement container = anchor.getParent();
    final PsiElement firstElementCopy = container.addRangeBefore(elements[0], elements[elements.length - 1], anchor);
    final PsiElement[] elementsCopy = CodeInsightUtil.findStatementsInRange(copy,
                                                                            firstElementCopy.getTextRange().getStartOffset(),
                                                                            anchor.getTextRange().getStartOffset());
    if (elementsCopy[elementsCopy.length - 1] instanceof PsiExpressionStatement) {
      final PsiExpression expr = ((PsiExpressionStatement)elementsCopy[elementsCopy.length - 1]).getExpression();
      if (!(expr instanceof PsiAssignmentExpression)) {
        final PsiType expressionType = expr.getType();
        if (expressionType != null && expressionType != PsiType.VOID) {
          final String uniqueResultName = JavaCodeStyleManager.getInstance(project).suggestUniqueVariableName("result", elementsCopy[0], true);
          final String statementText = expressionType.getCanonicalText() + " " + uniqueResultName + " = " + expr.getText() + ";";
          elementsCopy[elementsCopy.length - 1] = elementsCopy[elementsCopy.length - 1]
            .replace(elementFactory.createStatementFromText(statementText, elementsCopy[elementsCopy.length - 1]));
        }
      }
    }

    LOG.assertTrue(elementsCopy[0].getParent() == container, "element: " +  elementsCopy[0].getText() + "; container: " + container.getText());
    final int startOffsetInContainer = elementsCopy[0].getStartOffsetInParent();

    final ControlFlow controlFlow;
    try {
      controlFlow = ControlFlowFactory.getInstance(project).getControlFlow(container, LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance());
    }
    catch (AnalysisCanceledException e) {
      return null;
    }

    List<PsiVariable> variables = ControlFlowUtil.getUsedVariables(controlFlow,
                                                                   controlFlow.getStartOffset(elementsCopy[0]),
                                                                   controlFlow.getEndOffset(elementsCopy[elementsCopy.length - 1]));

    variables = ContainerUtil.filter(variables, new Condition<PsiVariable>() {
      @Override
      public boolean value(PsiVariable variable) {
        final PsiElement variableScope = variable instanceof PsiParameter ? ((PsiParameter)variable).getDeclarationScope()
                                                                          : PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class, PsiForStatement.class);
        return variableScope != null && PsiTreeUtil.isAncestor(variableScope, elementsCopy[elementsCopy.length - 1], false);
      }
    });


    final String outputVariables = StringUtil.join(variables, new Function<PsiVariable, String>() {
                                          @Override
                                          public String fun(PsiVariable variable) {
                                            return "\"variable: \" + " + variable.getName();
                                          }
                                        }, " +");
    PsiStatement outStatement = elementFactory.createStatementFromText("System.out.println(" + outputVariables + ");", anchor);
    outStatement = (PsiStatement)container.addAfter(outStatement, elementsCopy[elementsCopy.length - 1]);

    copy.accept(new JavaRecursiveElementWalkingVisitor() {
      private void makePublic(PsiMember method) {
        if (method.hasModifierProperty(PsiModifier.PRIVATE)) {
          VisibilityUtil.setVisibility(method.getModifierList(), PsiModifier.PUBLIC);
        }
      }

      @Override
      public void visitMethod(PsiMethod method) {
        super.visitMethod(method);
        makePublic(method);
      }

      @Override
      public void visitField(PsiField field) {
        super.visitField(field);
        makePublic(field);
      }
    });

    final ExtractMethodObjectProcessor extractMethodObjectProcessor = new ExtractMethodObjectProcessor(project, null, elementsCopy, "") {
      @Override
      protected AbstractExtractDialog createExtractMethodObjectDialog(MyExtractMethodProcessor processor) {
        return new LightExtractMethodObjectDialog(this, methodName);
      }
    };
    extractMethodObjectProcessor.getExtractProcessor().setShowErrorDialogs(false);

    final ExtractMethodObjectProcessor.MyExtractMethodProcessor extractProcessor = extractMethodObjectProcessor.getExtractProcessor();
    if (extractProcessor.prepare()) {
      if (extractProcessor.showDialog()) {
        try {
          extractProcessor.doExtract();
          final UsageInfo[] usages = extractMethodObjectProcessor.findUsages();
          extractMethodObjectProcessor.performRefactoring(usages);
          extractMethodObjectProcessor.runChangeSignature();
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
        if (extractMethodObjectProcessor.isCreateInnerClass()) {
          extractMethodObjectProcessor.changeInstanceAccess(project);
        }
        final PsiElement method = extractMethodObjectProcessor.getMethod();
        LOG.assertTrue(method != null);
        method.delete();
      }
    } else {
      return null;
    }

    final int startOffset = startOffsetInContainer + container.getTextRange().getStartOffset();
    final String generatedCall = copy.getText().substring(startOffset, outStatement.getTextOffset());
    return new ExtractedData(generatedCall,
                             (PsiClass)CodeStyleManager.getInstance(project).reformat(extractMethodObjectProcessor.getInnerClass()),
                             originalAnchor);
  }


  private static class LightExtractMethodObjectDialog implements AbstractExtractDialog {
    private final ExtractMethodObjectProcessor myProcessor;
    private final String myMethodName;

    public LightExtractMethodObjectDialog(ExtractMethodObjectProcessor processor, String methodName) {
      myProcessor = processor;
      myMethodName = methodName;
    }

    @Override
    public String getChosenMethodName() {
      return myMethodName;
    }

    @Override
    public VariableData[] getChosenParameters() {
      final InputVariables inputVariables = myProcessor.getExtractProcessor().getInputVariables();
      return inputVariables.getInputVariables().toArray(new VariableData[inputVariables.getInputVariables().size()]);
    }

    @Override
    public String getVisibility() {
      return PsiModifier.PUBLIC;
    }

    @Override
    public boolean isMakeStatic() {
      return false;
    }

    @Override
    public boolean isChainedConstructor() {
      return false;
    }

    @Override
    public void show() {}

    @Override
    public boolean isOK() {
      return true;
    }
  }
}
