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
package com.intellij.refactoring.extractMethodObject;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.extractMethod.AbstractExtractDialog;
import com.intellij.refactoring.extractMethod.InputVariables;
import com.intellij.refactoring.extractMethod.PrepareFailedException;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.refactoring.util.VariableData;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ExtractLightMethodObjectHandler {
  private static final Logger LOG = Logger.getInstance(ExtractLightMethodObjectHandler.class);

  public static class ExtractedData {
    private final String myGeneratedCallText;
    private final PsiClass myGeneratedInnerClass;
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
                                                       @Nullable PsiElement originalContext,
                                                       @NotNull final PsiCodeFragment fragment,
                                                       final String methodName) throws PrepareFailedException {
    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
    PsiElement[] elements = completeToStatementArray(fragment, elementFactory);
    if (elements == null) {
      elements = CodeInsightUtil.findStatementsInRange(fragment, 0, fragment.getTextLength());
    }
    if (elements.length == 0) {
      return null;
    }

    if (originalContext == null) {
      return null;
    }

    PsiFile file = originalContext.getContainingFile();

    final PsiFile copy = PsiFileFactory.getInstance(project)
      .createFileFromText(file.getName(), file.getFileType(), file.getText(), file.getModificationStamp(), false);

    if (originalContext instanceof PsiKeyword && PsiModifier.PRIVATE.equals(originalContext.getText())) {
      final PsiNameIdentifierOwner identifierOwner = PsiTreeUtil.getParentOfType(originalContext, PsiNameIdentifierOwner.class);
      if (identifierOwner != null) {
        final PsiElement identifier = identifierOwner.getNameIdentifier();
        if (identifier != null) {
          originalContext = identifier;
        }
      }
    }

    final TextRange range = originalContext.getTextRange();
    PsiElement originalAnchor = CodeInsightUtil.findElementInRange(copy, range.getStartOffset(), range.getEndOffset(), originalContext.getClass());
    if (originalAnchor == null) {
      final PsiElement elementAt = copy.findElementAt(range.getStartOffset());
      if (elementAt != null && elementAt.getClass() == originalContext.getClass()) {
        originalAnchor = PsiTreeUtil.skipWhitespacesForward(elementAt);
      }
    }

    final PsiClass containingClass = PsiTreeUtil.getParentOfType(originalAnchor, PsiClass.class, false);
    if (containingClass == null) {
      return null;
    }

    // expand lambda to code block if needed
    PsiElement containingMethod = PsiTreeUtil.getParentOfType(originalAnchor, PsiMember.class, PsiLambdaExpression.class);
    if (containingMethod instanceof PsiLambdaExpression) {
      PsiCodeBlock newBody = RefactoringUtil.expandExpressionLambdaToCodeBlock((PsiLambdaExpression)containingMethod);
      originalAnchor = newBody.getStatements()[0];
    }

    PsiElement anchor = RefactoringUtil.getParentStatement(originalAnchor, false);
    if (anchor == null) {
      if (PsiTreeUtil.getParentOfType(originalAnchor, PsiCodeBlock.class) != null) {
        anchor = originalAnchor;
      }
    }

    PsiElement container;
    if (anchor == null) {
      container = ((PsiClassInitializer)containingClass.add(elementFactory.createClassInitializer())).getBody();
      anchor = container.getLastChild();
    }
    else {
      container = anchor.getParent();
    }

    // add code blocks for ifs and loops if needed
    if (anchor instanceof PsiStatement && RefactoringUtil.isLoopOrIf(container)) {
      PsiBlockStatement codeBlockStatement =
        (PsiBlockStatement)JavaPsiFacade.getElementFactory(project).createStatementFromText("{}", container);
      codeBlockStatement.getCodeBlock().add(anchor);
      PsiCodeBlock codeBlock = ((PsiBlockStatement)anchor.replace(codeBlockStatement)).getCodeBlock();
      anchor = codeBlock.getStatements()[0];
      originalAnchor = anchor;
      container = codeBlock;
    }

    final PsiElement firstElementCopy = container.addRangeBefore(elements[0], elements[elements.length - 1], anchor);
    final PsiElement[] elementsCopy = CodeInsightUtil.findStatementsInRange(copy,
                                                                            firstElementCopy.getTextRange().getStartOffset(),
                                                                            anchor.getTextRange().getStartOffset());
    if (elementsCopy.length == 0) {
      return null;
    }
    if (elementsCopy[elementsCopy.length - 1] instanceof PsiExpressionStatement) {
      final PsiExpression expr = ((PsiExpressionStatement)elementsCopy[elementsCopy.length - 1]).getExpression();
      if (!(expr instanceof PsiAssignmentExpression)) {
        PsiType expressionType = GenericsUtil.getVariableTypeByExpressionType(expr.getType());
        if (expressionType instanceof PsiDisjunctionType) {
          expressionType = ((PsiDisjunctionType)expressionType).getLeastUpperBound();
        }
        if (isValidVariableType(expressionType)) {
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
      controlFlow = ControlFlowFactory.getInstance(project).getControlFlow(container, LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance(), false, false);
    }
    catch (AnalysisCanceledException e) {
      return null;
    }

    List<PsiVariable> variables = ControlFlowUtil.getUsedVariables(controlFlow,
                                                                   controlFlow.getStartOffset(elementsCopy[0]),
                                                                   controlFlow.getEndOffset(elementsCopy[elementsCopy.length - 1]));

    variables = ContainerUtil.filter(variables, variable -> {
      PsiElement variableScope = PsiUtil.getVariableCodeBlock(variable, null);
      return variableScope != null && PsiTreeUtil.isAncestor(variableScope, elementsCopy[elementsCopy.length - 1], true);
    });


    final String outputVariables = StringUtil.join(variables, variable -> "\"variable: \" + " + variable.getName(), " +");
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

      @Override
      protected boolean isFoldingApplicable() {
        return false;
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

  @Nullable
  private static PsiElement[] completeToStatementArray(PsiCodeFragment fragment, PsiElementFactory elementFactory) {
    PsiExpression expression = CodeInsightUtil.findExpressionInRange(fragment, 0, fragment.getTextLength());
    if (expression != null) {
      String completeExpressionText = null;
      if (expression instanceof PsiArrayInitializerExpression) {
        final PsiExpression[] initializers = ((PsiArrayInitializerExpression)expression).getInitializers();
        if (initializers.length > 0) {
          final PsiType type = initializers[0].getType();
          if (type != null) {
            completeExpressionText = "new " + type.getCanonicalText() + "[]" + expression.getText();
          }
        }
      } else {
        completeExpressionText = expression.getText();
      }

      if (completeExpressionText != null) {
        return new PsiElement[] {elementFactory.createStatementFromText(completeExpressionText + ";", expression)};
      }
    }
    return null;
  }

  private static boolean isValidVariableType(PsiType type) {
    if (type instanceof PsiClassType ||
        type instanceof PsiArrayType ||
        type instanceof PsiPrimitiveType && !PsiType.VOID.equals(type)) {
      return true;
    }
    return false;
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

    @NotNull
    @Override
    public String getVisibility() {
      return PsiModifier.PACKAGE_LOCAL;
    }

    @Override
    public boolean isMakeStatic() {
      return myProcessor.getExtractProcessor().isCanBeStatic() && !myProcessor.getExtractProcessor().getInputVariables().hasInstanceFields();
    }

    @Override
    public boolean isChainedConstructor() {
      return false;
    }

    @Override
    public PsiType getReturnType() {
      return null;
    }

    @Override
    public void show() {}

    @Override
    public boolean isOK() {
      return true;
    }
  }
}
