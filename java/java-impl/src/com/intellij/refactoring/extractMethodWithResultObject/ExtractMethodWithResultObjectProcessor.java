// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethodWithResultObject;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.controlFlow.ControlFlowUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.extractMethod.PrepareFailedException;
import com.intellij.refactoring.introduceField.ElementToWorkOn;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import one.util.streamex.EntryStream;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;

import static com.intellij.util.ObjectUtils.notNull;

/**
 * @author Pavel.Dolgov
 */
public class ExtractMethodWithResultObjectProcessor {
  private static final Logger LOG = Logger.getInstance(ExtractMethodWithResultObjectProcessor.class);

  private final Project myProject;
  private final Editor myEditor;
  private final PsiElementFactory myFactory;
  private final PsiElement[] myElements;
  private final PsiExpression myExpression;
  private final PsiElement myCodeFragmentMember;
  private ExtractionContext myContext;

  private String myMethodName = "newMethod";
  private String myResultClassName = "NewMethodResult";
  private String myResultVariableName = "x";

  private PsiClass myTargetClass;
  private PsiElement myAnchor;

  @NonNls static final String REFACTORING_NAME = "Extract Method With Result Object";

  private static final Key<PsiStatement> ORIGINAL_STATEMENT = Key.create("ExtractMethodWithResultObjectProcessor.OriginalStatement");
  private final PsiElement myCodeFragment;

  public ExtractMethodWithResultObjectProcessor(@NonNls Project project, @NonNls Editor editor, @NonNls PsiElement[] elements) {
    if (elements.length == 0) {
      throw new IllegalArgumentException("elements.length");
    }
    myProject = project;
    myEditor = editor;
    myFactory = JavaPsiFacade.getElementFactory(project);


    myElements = normalizeElements(elements);
    myExpression = ObjectUtils.tryCast(myElements[0], PsiExpression.class);

    myCodeFragment = ControlFlowUtil.findCodeFragment(elements[0]);
    myCodeFragmentMember = getCodeFragmentMember(myCodeFragment);
  }

  @NotNull
  private static PsiElement[] normalizeElements(PsiElement[] elements) {
    if (!(elements[0] instanceof PsiExpression)) {
      return elements;
    }

    PsiExpression expression = (PsiExpression)elements[0];
    if (expression.getParent() instanceof PsiExpressionStatement) {
      return new PsiElement[]{expression.getParent()};
    }

    PsiExpression rightmost = expression;
    while (true) {
      PsiAssignmentExpression assignment = ExpressionUtils.getAssignment(rightmost);
      PsiExpression rExpression = assignment != null ? assignment.getRExpression() : null;
      if (rExpression == null) break;
      rightmost = rExpression;
    }
    if (rightmost != expression) {
      return new PsiElement[]{rightmost};
    }

    return elements;
  }

  Project getProject() {
    return myProject;
  }

  Editor getEditor() {
    return myEditor;
  }

  private static PsiElement getCodeFragmentMember(@NotNull PsiElement codeFragment) {
    PsiElement codeFragmentMember = codeFragment.getUserData(ElementToWorkOn.PARENT);
    if (codeFragmentMember == null) {
      codeFragmentMember = codeFragment.getParent();
    }
    if (codeFragmentMember == null) {
      PsiElement context = codeFragment.getContext();
      LOG.assertTrue(context != null, "code fragment context is null");
      codeFragmentMember = ControlFlowUtil.findCodeFragment(context).getParent();
    }
    return codeFragmentMember;
  }

  public boolean prepare() throws PrepareFailedException {
    LOG.info("prepare");
    if (myElements.length == 0) {
      return false;
    }

    ControlFlowWrapper controlFlow = new ControlFlowWrapper(myProject, myElements, myCodeFragment);
    if (!controlFlow.prepare()) return false;
    myContext = new ExtractionContextAnalyser(myElements, myCodeFragmentMember, myExpression, controlFlow).createContext();

    chooseTargetClass();
    chooseAnchor();

    return true;
  }

  boolean showDialog() {
    return true;
  }

  void doRefactoring() {
    Set<ExitType> exitTypes = myContext.getExitTypes();
    if (!exitTypes.contains(ExitType.UNDEFINED) && !exitTypes.contains(ExitType.THROW)) {
      PsiClass resultClass = createResultClass();
      createMethod(resultClass);

      PsiElement methodCall = createMethodCall(resultClass);
      createExitStatements(methodCall);
      if (myExpression == null) {
        myElements[0].getParent().deleteChildRange(myElements[0], myElements[myElements.length - 1]);
      }
    }
  }

  private PsiElement createMethodCall(PsiClass resultClass) {
    PsiElement firstElement = myElements[0];
    PsiMethodCallExpression methodCall = (PsiMethodCallExpression)myFactory.createExpressionFromText(myMethodName + "()", firstElement);
    PsiExpressionList argumentList = methodCall.getArgumentList();

    myContext.getOrderedInputVariables()
      .forKeyValue((variable, name) -> {
        PsiExpression argument = null;
        Input input = myContext.myInputs.get(variable);
        if (input != null) {
          argument = input.getOccurrences().stream().min(Comparator.comparing(e -> e.getTextOffset())).orElse(null);
        }
        if (argument == null) {
          argument = myFactory.createExpressionFromText(name, firstElement);
        }
        argumentList.add(argument);
      });

    if (myExpression != null) {
      PsiReferenceExpression referenceExpression =
        (PsiReferenceExpression)myFactory.createExpressionFromText("x." + ResultItem.EXPRESSION_RESULT, null);
      referenceExpression.setQualifierExpression(methodCall);
      return myExpression.replace(referenceExpression);
    }

    final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(myProject);
    myResultVariableName = codeStyleManager.suggestUniqueVariableName(myResultVariableName, firstElement, true);
    PsiDeclarationStatement methodResultDeclaration =
      myFactory.createVariableDeclarationStatement(myResultVariableName, myFactory.createType(resultClass), null, firstElement);
    PsiElement[] declaredElements = methodResultDeclaration.getDeclaredElements();

    LOG.assertTrue(declaredElements.length == 1, "declaredElements.length");
    LOG.assertTrue(declaredElements[0] instanceof PsiLocalVariable, "declaredElements[0]");
    ((PsiLocalVariable)declaredElements[0]).setInitializer(methodCall);

    PsiElement parent = firstElement.getParent();
    if (isLoopToExtractBodyFrom(parent) || isIfToExtractBranchFrom(parent)) {
      PsiBlockStatement blockStatement = (PsiBlockStatement)myFactory.createStatementFromText("{\n}", parent);
      blockStatement = (PsiBlockStatement)parent.addBefore(blockStatement, firstElement);
      return blockStatement.getCodeBlock().add(methodResultDeclaration);
    }
    return parent.addBefore(methodResultDeclaration, firstElement);
  }

  private boolean isLoopToExtractBodyFrom(PsiElement parent) {
    if (parent instanceof PsiLoopStatement) {
      PsiStatement body = ((PsiLoopStatement)parent).getBody();
      return body != null && ArrayUtil.indexOf(myElements, body) >= 0;
    }
    return false;
  }

  private boolean isIfToExtractBranchFrom(PsiElement parent) {
    if (parent instanceof PsiIfStatement) {
      PsiStatement thenBranch = ((PsiIfStatement)parent).getThenBranch();
      PsiStatement elseBranch = ((PsiIfStatement)parent).getElseBranch();
      return thenBranch != null && ArrayUtil.indexOf(myElements, thenBranch) >= 0 ||
             elseBranch != null && ArrayUtil.indexOf(myElements, elseBranch) >= 0;
    }
    return false;
  }

  private void createExitStatements(PsiElement methodCall) {
    if (myExpression != null) {
      return;
    }

    List<ResultItem> resultItems = myContext.myResultItems;
    boolean haveResultKey = ContainerUtil.find(resultItems, item -> item instanceof ResultItem.ExitKey) != null;
    boolean haveReturnResult = ContainerUtil.find(resultItems, item -> item instanceof ResultItem.Return) != null;
    boolean haveSequentialExit = ContainerUtil.find(myContext.myExits.values(), exit -> ExitType.SEQUENTIAL.equals(exit.getType())) != null;

    if (haveSequentialExit) {
      List<ResultItem.Variable> variableResultItems = StreamEx.of(resultItems)
        .select(ResultItem.Variable.class)
        .toList();
      Collections.reverse(variableResultItems); // to use with addAfter()

      Output output = myContext.myOutputs.get(null);
      Map<PsiVariable, Output.Flags> variableFlags = output != null ? output.myFlags : Collections.emptyMap();
      for (ResultItem.Variable item : variableResultItems) {
        PsiVariable variable = item.myVariable;
        Output.Flags flags = variableFlags.get(variable);

        if (!myContext.myWrittenOuterVariables.contains(variable) &&
            flags != null && !flags.isValueUsedAfterExit && flags.isVisibleAtExit) { // todo check if there are any usages after the exit
          PsiDeclarationStatement statement = createVariableDeclaration(item, null);
          methodCall.getParent().addAfter(statement, methodCall);
          continue;
        }

        if (flags == null || !flags.isValueUsedAfterExit) {
          continue;
        }

        if (myContext.myWrittenOuterVariables.contains(variable)) {
          String text = item.myVariableName + " = " + myResultVariableName + '.' + item.myFieldName + ';';
          PsiStatement statement = myFactory.createStatementFromText(text, methodCall);
          methodCall.getParent().addAfter(statement, methodCall);
          continue;
        }

        PsiExpression initializer = myFactory.createExpressionFromText(myResultVariableName + '.' + item.myFieldName, methodCall);
        PsiDeclarationStatement statement = createVariableDeclaration(item, initializer);
        methodCall.getParent().addAfter(statement, methodCall);
      }
    }

    EntryStream.of(myContext.myDistinctExits)
      .sorted(Comparator.comparing((Function<Map.Entry<Exit, Integer>, Integer>)Map.Entry::getValue).reversed())
      .forKeyValue((exit, exitKey) -> {
        PsiStatement exitStatement = null;
        switch (exit.getType()) {
          case RETURN: {
            String text = haveReturnResult
                          ? PsiKeyword.RETURN + ' ' + myResultVariableName + '.' + ResultItem.RETURN_RESULT + ';'
                          : PsiKeyword.RETURN + ';';
            exitStatement = myFactory.createStatementFromText(text, methodCall);
            break;
          }
          case BREAK: {
            exitStatement = myFactory.createStatementFromText(PsiKeyword.BREAK + ';', methodCall); // todo labeled
            break;
          }
          case CONTINUE: {
            exitStatement = myFactory.createStatementFromText(PsiKeyword.CONTINUE + ';', methodCall); // todo labeled
            break;
          }
          case THROW:
            break; // todo
          case SEQUENTIAL:
            break; // do nothing
          default:
            throw new IllegalStateException(exit.toString());
        }
        if (exitStatement == null) {
          return;
        }
        if (haveResultKey) {
          String ifText = "if (" + myResultVariableName + '.' + ResultItem.EXIT_KEY + " == " + exitKey + ");";
          PsiIfStatement ifStatement = (PsiIfStatement)myFactory.createStatementFromText(ifText, methodCall);
          ifStatement = (PsiIfStatement)methodCall.getParent().addAfter(ifStatement, methodCall);

          notNull(ifStatement.getThenBranch()).replace(exitStatement);
        }
        else {
          methodCall.getParent().addAfter(exitStatement, methodCall);
        }
      });
  }

  @NotNull
  private PsiDeclarationStatement createVariableDeclaration(@NotNull ResultItem.Variable item, @Nullable PsiExpression initializer) {
    PsiDeclarationStatement statement = myFactory.createVariableDeclarationStatement(item.myVariableName, item.myType, initializer);

    PsiVariable copy = (PsiVariable)item.myVariable.copy();
    copy.normalizeDeclaration();
    PsiModifierList modifierList = copy.getModifierList();
    if (modifierList != null) {
      PsiVariable redeclaredVariable = (PsiVariable)statement.getDeclaredElements()[0];
      notNull(redeclaredVariable.getModifierList()).replace(modifierList);
    }
    return statement;
  }

  private PsiClass createResultClass() {
    PsiElement classAnchor = myAnchor;
    while (classAnchor != null) {
      PsiElement parent = classAnchor.getParent();
      if (parent instanceof PsiClass && !(parent instanceof PsiAnonymousClass)) {
        break;
      }
      classAnchor = parent;
    }
    assert classAnchor != null : "class anchor";

    PsiClass resultClass = (PsiClass)classAnchor.getParent().addAfter(myFactory.createClass(myResultClassName), classAnchor);
    notNull(resultClass.getModifierList()).setModifierProperty(PsiModifier.PACKAGE_LOCAL, true);
    PsiElement ancestor = PsiTreeUtil.getParentOfType(classAnchor, PsiClass.class, PsiMethod.class);
    if (ancestor instanceof PsiClass && (ancestor.getParent() instanceof PsiFile ||
                                         ((PsiClass)ancestor).hasModifierProperty(PsiModifier.STATIC))) {
      notNull(resultClass.getModifierList()).setModifierProperty(PsiModifier.STATIC, true);
    }

    List<ResultItem> resultItems = myContext.myResultItems;

    for (ResultItem resultItem : resultItems) {
      resultItem.createField(resultClass, myFactory);
    }

    PsiMethod constructor = (PsiMethod)resultClass.add(myFactory.createConstructor(notNull(resultClass.getName())));
    PsiParameterList constructorParameterList = notNull(constructor.getParameterList());
    for (ResultItem resultItem : resultItems) {
      resultItem.createConstructorParameter(constructorParameterList, myFactory);
    }

    PsiCodeBlock constructorBody = notNull(constructor.getBody());
    for (ResultItem resultItem : resultItems) {
      resultItem.createAssignmentInConstructor(constructorBody, myFactory);
    }

    return resultClass;
  }

  private void createMethod(PsiClass resultClass) {
    PsiMethod method = myFactory.createMethod(myMethodName, myFactory.createType(resultClass), myAnchor);
    method.getModifierList().setModifierProperty(PsiModifier.PACKAGE_LOCAL, true);
    if (myCodeFragmentMember instanceof PsiMethod && ((PsiMethod)myCodeFragmentMember).hasModifierProperty(PsiModifier.STATIC)) {
      method.getModifierList().setModifierProperty(PsiModifier.STATIC, true);
    }

    PsiTypeParameterList typeParameterList = createMethodTypeParameterList();
    if (typeParameterList != null) {
      notNull(method.getTypeParameterList()).replace(typeParameterList);
    }

    PsiReferenceList throwsList = method.getThrowsList();
    for (PsiClassType exception : myContext.myThrownCheckedExceptions) {
      throwsList.add(myFactory.createReferenceElementByType(exception));
    }

    PsiParameterList parameterList = method.getParameterList();

    myContext.getOrderedInputVariables()
      .forKeyValue((variable, name) -> {
        PsiParameter parameter = myFactory.createParameter(name, ExtractUtil.getVariableType(variable), method);
        parameter = (PsiParameter)parameterList.add(parameter);
        notNull(parameter.getModifierList()).setModifierProperty(PsiModifier.FINAL,
                                                                 variable.hasModifierProperty(PsiModifier.FINAL));
      });

    PsiCodeBlock body = method.getBody();
    assert body != null : "method body";

    redeclareWrittenOuterVariables(body);

    List<ResultItem> resultItems = myContext.myResultItems;
    if (myExpression != null) {
      body.add(createReturnStatement(resultItems, -1, body, null, null));
      myAnchor.getParent().addAfter(method, myAnchor);
      return;
    }

    for (PsiStatement statement : myContext.myExits.keySet()) {
      if (statement != null) {
        statement.putCopyableUserData(ORIGINAL_STATEMENT, statement);
      }
    }

    body.addRange(myElements[0], myElements[myElements.length - 1]);
    PsiStatement[] methodBodyStatements = body.getStatements();
    List<PsiStatement> newExitStatements = collectExitStatements(methodBodyStatements, body);

    for (PsiStatement newExitStatement : newExitStatements) {
      PsiStatement originalExitStatement = newExitStatement.getCopyableUserData(ORIGINAL_STATEMENT);
      if (originalExitStatement == null) {
        continue; // it's break or continue within the method
      }

      newExitStatement.putCopyableUserData(ORIGINAL_STATEMENT, null);
      originalExitStatement.putCopyableUserData(ORIGINAL_STATEMENT, null);

      Exit exit = myContext.myExits.get(originalExitStatement);
      Integer exitKey = myContext.myDistinctExits.get(exit);
      assert exitKey != null : "exit key";

      PsiExpression expression = null;
      if (newExitStatement instanceof PsiReturnStatement) {
        expression = ((PsiReturnStatement)newExitStatement).getReturnValue();
      }
      else if (newExitStatement instanceof PsiBreakStatement) {
        expression = ((PsiBreakStatement)newExitStatement).getValueExpression();
      }

      PsiReturnStatement returnStatement = createReturnStatement(resultItems, exitKey, body, expression, originalExitStatement);
      newExitStatement.replace(returnStatement);
    }

    Set<ExitType> exitTypes = myContext.getExitTypes();
    if (exitTypes.contains(ExitType.SEQUENTIAL)) {
      body.add(createReturnStatement(resultItems, -1, body, null, null));
    }

    myAnchor.getParent().addAfter(method, myAnchor);
  }

  private PsiTypeParameterList createMethodTypeParameterList() {
    PsiElement container = PsiTreeUtil.getParentOfType(myElements[0], PsiClass.class, PsiMethod.class);
    while (container instanceof PsiMethod && ((PsiMethod)container).getContainingClass() != myTargetClass) {
      container = PsiTreeUtil.getParentOfType(container, PsiMethod.class, true);
    }
    if (container instanceof PsiMethod) {
      List<PsiElement> elements = new ArrayList<>();
      ContainerUtil.addAll(elements, myElements);
      for (ResultItem resultItem : myContext.myResultItems) {
        resultItem.contributeToTypeParameters(elements);
      }
      return RefactoringUtil.createTypeParameterListWithUsedTypeParameters(((PsiMethod)container).getTypeParameterList(),
                                                                           elements.toArray(PsiElement.EMPTY_ARRAY));
    }
    return null;
  }

  private void redeclareWrittenOuterVariables(PsiCodeBlock body) throws IncorrectOperationException {
    HashSet<PsiVariable> writtenVariables = new HashSet<>(myContext.myWrittenOuterVariables);
    writtenVariables.removeAll(myContext.myInputs.keySet());

    StreamEx.of(writtenVariables)
      .mapToEntry(PsiElement::getTextOffset, v -> v)
      .sortedBy(Map.Entry::getKey)
      .values()
      .forEach(variable -> {
        String name = variable.getName();
        LOG.assertTrue(name != null, "variable name is null");
        PsiType type = ExtractUtil.getVariableType(variable);
        PsiDeclarationStatement statement = myFactory.createVariableDeclarationStatement(name, type, null);
        body.add(statement);
      });
  }

  @NotNull
  private static List<PsiStatement> collectExitStatements(PsiStatement[] bodyStatements, PsiElement topmostElement) {
    List<PsiStatement> exitStatements = new ArrayList<>();
    ExitStatementsVisitor visitor = new ExitStatementsVisitor(topmostElement) {
      @Override protected void processReferenceExpression(PsiReferenceExpression expression) {}
      @Override protected void processReturnExit(PsiReturnStatement statement) { exitStatements.add(statement);}
      @Override protected void processContinueExit(PsiContinueStatement statement) {exitStatements.add(statement);}
      @Override protected void processBreakExit(PsiBreakStatement statement) {exitStatements.add(statement);}
      @Override protected void processThrowExit(PsiThrowStatement statement) {exitStatements.add(statement);}
    };
    for (PsiStatement bodyStatement : bodyStatements) {
      bodyStatement.accept(visitor);
    }
    return exitStatements;
  }

  @NotNull
  private PsiReturnStatement createReturnStatement(@NotNull List<ResultItem> resultItems,
                                                   int exitKey,
                                                   @NotNull PsiCodeBlock body,
                                                   @Nullable PsiExpression expression,
                                                   @Nullable PsiStatement exitStatement) {
    PsiReturnStatement returnStatement = (PsiReturnStatement)myFactory.createStatementFromText("return 0;", body.getRBrace());
    PsiNewExpression returnExpression = (PsiNewExpression)notNull(returnStatement.getReturnValue())
      .replace(myFactory.createExpressionFromText("new NewMethodResult()", returnStatement));
    PsiExpressionList returnArgumentList = notNull(returnExpression.getArgumentList());
    Output output = myContext.myOutputs.get(exitStatement);
    for (ResultItem resultItem : resultItems) {
      PsiExpression resultObjectArgument =
        resultItem.createResultObjectArgument(exitKey, expression, body,
                                              v -> Optional.ofNullable(output)
                                                .map(o -> o.getFlags(v))
                                                .map(Output.Flags::isUndefined)
                                                .orElse(true),
                                              myFactory);
      assert resultObjectArgument != null : "resultObjectArgument";
      returnArgumentList.add(notNull(resultObjectArgument));
    }
    return returnStatement;
  }

  private void chooseTargetClass() {
    myTargetClass = myCodeFragmentMember instanceof PsiMember
                    ? ((PsiMember)myCodeFragmentMember).getContainingClass()
                    : PsiTreeUtil.getParentOfType(myCodeFragmentMember, PsiClass.class);
  }

  private void chooseAnchor() {
    myAnchor = myCodeFragmentMember;
    while (!myAnchor.getParent().equals(myTargetClass)) {
      myAnchor = myAnchor.getParent();
    }
  }
}
