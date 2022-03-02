// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.extractMethodObject;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
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
import com.intellij.refactoring.extractMethodObject.reflect.ReflectionAccessorToEverything;
import com.intellij.refactoring.util.VariableData;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class ExtractLightMethodObjectHandler {
  /**
   * @deprecated use LightMethodObjectExtractedData.REFERENCE_METHOD instead
   */
  @Deprecated
  public static final Key<PsiMethod> REFERENCE_METHOD = LightMethodObjectExtractedData.REFERENCE_METHOD;
  /**
   * @deprecated use LightMethodObjectExtractedData.REFERENCED_TYPE instead
   */
  @Deprecated
  public static final Key<PsiType> REFERENCED_TYPE = LightMethodObjectExtractedData.REFERENCED_TYPE;

  private static final Logger LOG = Logger.getInstance(ExtractLightMethodObjectHandler.class);

  @Nullable
  public static LightMethodObjectExtractedData extractLightMethodObject(final Project project,
                                                                        @Nullable PsiElement originalContext,
                                                                        @NotNull final PsiCodeFragment fragment,
                                                                        @NotNull String methodName,
                                                                        @Nullable JavaSdkVersion javaVersion) throws PrepareFailedException {
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
      PsiLambdaExpression lambdaExpression = (PsiLambdaExpression)containingMethod;
      if (lambdaExpression.getBody() instanceof PsiExpression) {
        PsiCodeBlock newBody = CommonJavaRefactoringUtil.expandExpressionLambdaToCodeBlock(lambdaExpression);
        originalAnchor = newBody.getStatements()[0];
      }
    }

    PsiElement anchor = CommonJavaRefactoringUtil.getParentStatement(originalAnchor, false);
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
    if (anchor instanceof PsiStatement && CommonJavaRefactoringUtil.isLoopOrIf(container)) {
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
      controlFlow = ControlFlowFactory.getControlFlow(container, LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance(),
                                                      ControlFlowOptions.NO_CONST_EVALUATE);
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

    boolean useMagicAccessor = Registry.is("debugger.compiling.evaluator.magic.accessor") &&
                               javaVersion != null && !javaVersion.isAtLeast(JavaSdkVersion.JDK_1_9);
    if (useMagicAccessor) {
      LOG.info("Magic accessor available");
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
    }

    final ExtractMethodObjectProcessor extractMethodObjectProcessor = new ExtractMethodObjectProcessor(project, null, elementsCopy, "") {
      @Override
      protected AbstractExtractDialog createExtractMethodObjectDialog(MyExtractMethodProcessor processor) {
        return new LightExtractMethodObjectDialog(this, methodName);
      }

      @Override
      protected PsiElement addInnerClass(PsiClass containingClass, PsiClass innerClass) {
        return containingClass.addBefore(innerClass, containingClass.getLastChild());
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

    final PsiClass inner = extractMethodObjectProcessor.getInnerClass();
    final PsiMethod[] methods = inner.findMethodsByName("invoke", false);

    boolean useReflection = javaVersion == null || javaVersion.isAtLeast(JavaSdkVersion.JDK_1_9) ||
                            Registry.is("debugger.compiling.evaluator.reflection.access.with.java8");
    PsiClass generatedClass = extractMethodObjectProcessor.getInnerClass();
    if (useReflection && methods.length == 1) {
      final PsiMethod method = methods[0];
      PsiMethodCallExpression callExpression = findCallExpression(copy, method);
      if (callExpression != null) {
        LOG.info("Use reflection to evaluate inaccessible members");
        new ReflectionAccessorToEverything(generatedClass, elementFactory).grantAccessThroughReflection(callExpression);
        boolean isJdkAtLeast11 = javaVersion == null || javaVersion.isAtLeast(JavaSdkVersion.JDK_11);
        if (isJdkAtLeast11 || Registry.is("debugger.compiling.evaluator.extract.generated.class")) {
          generatedClass = ExtractGeneratedClassUtil.extractGeneratedClass(generatedClass, elementFactory, anchor);
        }
      }
      else {
        LOG.warn("Generated method call expression not found");
      }
    }

    final String generatedCall = copy.getText().substring(startOffset, outStatement.getTextOffset());
    return new LightMethodObjectExtractedData(generatedCall,
                                              (PsiClass)CodeStyleManager.getInstance(project).reformat(generatedClass),
                                              originalAnchor, useMagicAccessor);
  }

  @Nullable
  private static PsiMethodCallExpression findCallExpression(@NotNull PsiFile copy, @NotNull PsiMethod method) {
    PsiMethodCallExpression[] result = new PsiMethodCallExpression[1];
    copy.accept(new JavaRecursiveElementVisitor() {
      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression expression) {
        if (method.equals(expression.resolveMethod())) {
          if (result[0] != null) {
            LOG.error("To many generated method invocations found");
          }
          else {
            result[0] = expression;
          }
        }
      }
    });
    return result[0];
  }

  private static PsiElement @Nullable [] completeToStatementArray(PsiCodeFragment fragment, PsiElementFactory elementFactory) {
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
    @NotNull
    private final String myMethodName;

    LightExtractMethodObjectDialog(ExtractMethodObjectProcessor processor, @NotNull String methodName) {
      myProcessor = processor;
      myMethodName = methodName;
    }

    @NotNull
    @Override
    public String getChosenMethodName() {
      return myMethodName;
    }

    @Override
    public VariableData[] getChosenParameters() {
      final InputVariables inputVariables = myProcessor.getExtractProcessor().getInputVariables();
      return inputVariables.getInputVariables().toArray(new VariableData[0]);
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
