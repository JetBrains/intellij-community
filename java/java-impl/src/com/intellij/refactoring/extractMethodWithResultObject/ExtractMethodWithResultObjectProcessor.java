// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethodWithResultObject;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.controlFlow.ControlFlowUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.refactoring.introduceField.ElementToWorkOn;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.EntryStream;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

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

  // in the order the expressions appear in the original code
  private final List<Pair.NonNull<String, PsiExpression>> myArguments = new ArrayList<>();

  private PsiClass myTargetClass;
  private PsiElement myAnchor;

  @NonNls static final String REFACTORING_NAME = "Extract Method With Result Object";

  private static final Key<PsiStatement> ORIGINAL_STATEMENT = Key.create("ExtractMethodWithResultObjectProcessor.OriginalStatement");

  public ExtractMethodWithResultObjectProcessor(@NonNls Project project, @NonNls Editor editor, @NonNls PsiElement[] elements) {
    if (elements.length == 0) {
      throw new IllegalArgumentException("elements.length");
    }
    myProject = project;
    myEditor = editor;
    myFactory = JavaPsiFacade.getElementFactory(project);

    PsiExpression expression = null;
    if (elements[0] instanceof PsiExpression) {
      expression = (PsiExpression)elements[0];
      if (expression.getParent() instanceof PsiExpressionStatement) {
        elements = new PsiElement[]{expression.getParent()};
        expression = null;
      }
    }

    myElements = elements;
    myExpression = expression;

    PsiElement codeFragment = ControlFlowUtil.findCodeFragment(elements[0]);
    myCodeFragmentMember = getCodeFragmentMember(codeFragment);
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

  public boolean prepare() {
    LOG.info("prepare");
    if (myElements.length == 0) {
      return false;
    }

    myContext = new ExtractionContextAnalyser(myElements, myCodeFragmentMember, myExpression).createContext();

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
      Map<Exit, Integer> distinctExits = myContext.getDistinctExits();
      List<ResultItem> resultItems = collectResultItems(distinctExits);
      PsiClass resultClass = createResultClass(resultItems);
      createMethod(resultItems, resultClass, distinctExits);

      PsiElement methodCall = createMethodCall(resultClass);
      createExitStatements(methodCall, resultItems, distinctExits);
      if (myExpression == null) {
        myElements[0].getParent().deleteChildRange(myElements[0], myElements[myElements.length - 1]);
      }
    }
  }

  private PsiElement createMethodCall(PsiClass resultClass) {
    PsiElement firstElement = myElements[0];
    PsiMethodCallExpression methodCall = (PsiMethodCallExpression)myFactory.createExpressionFromText(myMethodName + "()", firstElement);
    PsiExpressionList argumentList = methodCall.getArgumentList();

    for (Pair.NonNull<String, PsiExpression> argument : myArguments) {
      argumentList.add(argument.second);
    }

    if (myExpression != null) {
      PsiReferenceExpression referenceExpression =
        (PsiReferenceExpression)myFactory.createExpressionFromText("x." + ExpressionResultItem.EXPRESSION_RESULT, null);
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

    return firstElement.getParent().addBefore(methodResultDeclaration, firstElement);
  }

  private void createExitStatements(PsiElement methodCall, List<ResultItem> resultItems, Map<Exit, Integer> distinctExits) {
    if (myExpression != null) {
      return;
    }

    boolean haveResultKey = ContainerUtil.find(resultItems, item -> item instanceof ExitKeyResultItem) != null;
    boolean haveReturnResult = ContainerUtil.find(resultItems, item -> item instanceof ReturnResultItem) != null;
    boolean haveSequentialExit = ContainerUtil.find(distinctExits.keySet(), exit -> ExitType.SEQUENTIAL.equals(exit.getType())) != null;

    if (haveSequentialExit) {
      List<VariableResultItem> variableResultItems = StreamEx.of(resultItems)
        .select(VariableResultItem.class)
        .toList();
      Collections.reverse(variableResultItems); // to use with addAfter()

      for (VariableResultItem item : variableResultItems) {
        PsiVariable variable = item.myVariable;
        if (myContext.myWrittenOuterVariables.contains(variable)) {
          String text = item.myVariableName + " = " + myResultVariableName + '.' + item.myFieldName + ';';
          PsiStatement statement = myFactory.createStatementFromText(text, methodCall);
          methodCall.getParent().addAfter(statement, methodCall);
        }
        else {
          PsiExpression initializer = myFactory.createExpressionFromText(myResultVariableName + '.' + item.myFieldName, methodCall);
          PsiDeclarationStatement statement = myFactory.createVariableDeclarationStatement(item.myVariableName, item.myType, initializer);

          PsiVariable copy = (PsiVariable)variable.copy();
          copy.normalizeDeclaration();
          PsiModifierList modifierList = copy.getModifierList();
          if (modifierList != null) {
            PsiVariable redeclaredVariable = (PsiVariable)statement.getDeclaredElements()[0];
            notNull(redeclaredVariable.getModifierList()).replace(modifierList);
          }

          methodCall.getParent().addAfter(statement, methodCall);
        }
      }
    }

    EntryStream.of(distinctExits)
      .sorted(Comparator.comparing((Function<Map.Entry<Exit, Integer>, Integer>)Map.Entry::getValue).reversed())
      .forKeyValue((exit, exitKey) -> {
        PsiStatement exitStatement = null;
        switch (exit.getType()) {
          case RETURN: {
            String text = haveReturnResult
                          ? PsiKeyword.RETURN + ' ' + myResultVariableName + '.' + ReturnResultItem.FIELD_NAME + ';'
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
          String ifText = "if (" + myResultVariableName + '.' + ExitKeyResultItem.FIELD_NAME + " == " + exitKey + ");";
          PsiIfStatement ifStatement = (PsiIfStatement)myFactory.createStatementFromText(ifText, methodCall);
          ifStatement = (PsiIfStatement)methodCall.getParent().addAfter(ifStatement, methodCall);

          notNull(ifStatement.getThenBranch()).replace(exitStatement);
        }
        else {
          methodCall.getParent().addAfter(exitStatement, methodCall);
        }
      });
  }

  private List<ResultItem> collectResultItems(Map<Exit, Integer> distinctExits) {
    if (myExpression != null) {
      assert myContext.myExits.size() == 1 : "one exit with expression";
      PsiType expressionType = RefactoringUtil.getTypeByExpressionWithExpectedType(myExpression);
      if (expressionType == null) {
        expressionType = PsiType.getJavaLangObject(myExpression.getManager(), GlobalSearchScope.allScope(myProject));
      }
      return Collections.singletonList(new ExpressionResultItem(myExpression, expressionType));
    }

    List<ResultItem> resultItems = new ArrayList<>();

    if (distinctExits.size() > 1) {
      resultItems.add(new ExitKeyResultItem());
    }

    List<Exit> returnExits = ContainerUtil.filter(myContext.myExits.values(), exit -> ExitType.RETURN.equals(exit.getType()));
    if (!returnExits.isEmpty()) {
      PsiType returnType = null;
      PsiTypeElement returnTypeElement = null;
      if (myCodeFragmentMember instanceof PsiMethod) {
        returnType = ((PsiMethod)myCodeFragmentMember).getReturnType();
        returnTypeElement = ((PsiMethod)myCodeFragmentMember).getReturnTypeElement();
      }
      else if (myCodeFragmentMember instanceof PsiLambdaExpression) {
        returnType = LambdaUtil.getFunctionalInterfaceReturnType((PsiLambdaExpression)myCodeFragmentMember);
      }
      if (returnType == null) {
        returnType = PsiType.getJavaLangObject(myCodeFragmentMember.getManager(), GlobalSearchScope.allScope(myProject));
      }
      if (!PsiType.VOID.equals(returnType)) {
        resultItems.add(new ReturnResultItem(returnType, returnTypeElement));
      }
    }

    StreamEx.of(myContext.myOutputVariables)
      .mapToEntry(PsiNamedElement::getName, v -> v)
      .filterKeys(Objects::nonNull)
      .sorted(Comparator.comparing(
        (Map.Entry<String, PsiVariable> e) -> e.getValue() instanceof PsiLocalVariable ? e.getValue().getTextOffset() : 0)
                .thenComparing(e -> e.getKey()))
      .forKeyValue((name, variable) -> resultItems.add(new VariableResultItem(variable, name)));

    return resultItems;
  }

  private PsiClass createResultClass(List<ResultItem> resultItems) {
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

  private void createMethod(List<ResultItem> resultItems,
                            PsiClass resultClass, Map<Exit, Integer> distinctExits) {

    PsiMethod method = myFactory.createMethod(myMethodName, myFactory.createType(resultClass), myAnchor);
    method.getModifierList().setModifierProperty(PsiModifier.PACKAGE_LOCAL, true);
    if (myCodeFragmentMember instanceof PsiMethod && ((PsiMethod)myCodeFragmentMember).hasModifierProperty(PsiModifier.STATIC)) {
      method.getModifierList().setModifierProperty(PsiModifier.STATIC, true);
    }

    PsiTypeParameterList typeParameterList = createMethodTypeParameterList(resultItems);
    if (typeParameterList != null) {
      notNull(method.getTypeParameterList()).replace(typeParameterList);
    }

    PsiParameterList parameterList = method.getParameterList();

    Map<PsiVariable, Pair<PsiExpression, Integer>> orderedInputs = new HashMap<>();
    myContext.myInputs.forEach((expression, variable) -> {
      int offset = expression.getTextOffset();
      Pair<PsiExpression, Integer> pair = orderedInputs.get(variable);
      if (pair == null || pair.second > offset) {
        orderedInputs.put(variable, new Pair<>(expression, offset));
      }
    });

    EntryStream.of(orderedInputs)
      .sortedBy(entry -> entry.getValue().second)
      .forKeyValue((variable, pair) -> {
        String name = notNull(variable.getName());
        myArguments.add(Pair.createNonNull(name, pair.first));

        PsiParameter parameter = myFactory.createParameter(name, variable.getType(), method);
        parameter = (PsiParameter)parameterList.add(parameter);
        notNull(parameter.getModifierList()).setModifierProperty(PsiModifier.FINAL,
                                                                 variable.hasModifierProperty(PsiModifier.FINAL));
      });

    PsiCodeBlock body = method.getBody();
    assert body != null : "method body";

    redeclareWrittenOuterVariables(body);

    if (myExpression != null) {
      body.add(createReturnStatement(resultItems, -1, body, null));
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
      Integer exitKey = distinctExits.get(exit);
      assert exitKey != null : "exit key";

      PsiExpression expression = null;
      if (newExitStatement instanceof PsiReturnStatement) {
        expression = ((PsiReturnStatement)newExitStatement).getReturnValue();
      }
      else if (newExitStatement instanceof PsiBreakStatement) {
        expression = ((PsiBreakStatement)newExitStatement).getValueExpression();
      }

      PsiReturnStatement returnStatement = createReturnStatement(resultItems, exitKey, body, expression);
      newExitStatement.replace(returnStatement);
    }

    Set<ExitType> exitTypes = myContext.getExitTypes();
    if (exitTypes.contains(ExitType.SEQUENTIAL)) {
      body.add(createReturnStatement(resultItems, -1, body, null));
    }

    myAnchor.getParent().addAfter(method, myAnchor);
  }

  private PsiTypeParameterList createMethodTypeParameterList(List<ResultItem> resultItems) {
    PsiElement container = PsiTreeUtil.getParentOfType(myElements[0], PsiClass.class, PsiMethod.class);
    while (container instanceof PsiMethod && ((PsiMethod)container).getContainingClass() != myTargetClass) {
      container = PsiTreeUtil.getParentOfType(container, PsiMethod.class, true);
    }
    if (container instanceof PsiMethod) {
      List<PsiElement> elements = new ArrayList<>();
      ContainerUtil.addAll(elements, myElements);
      for (ResultItem resultItem : resultItems) {
        resultItem.contributeToTypeParameters(elements);
      }
      return RefactoringUtil.createTypeParameterListWithUsedTypeParameters(((PsiMethod)container).getTypeParameterList(),
                                                                           elements.toArray(PsiElement.EMPTY_ARRAY));
    }
    return null;
  }

  private void redeclareWrittenOuterVariables(PsiCodeBlock body) throws IncorrectOperationException {
    HashSet<PsiVariable> writtenVariables = new HashSet<>(myContext.myWrittenOuterVariables);
    for (PsiVariable inputVariable : myContext.myInputs.values()) {
      writtenVariables.remove(inputVariable);
    }
    StreamEx.of(writtenVariables)
      .mapToEntry(PsiElement::getTextOffset, v -> v)
      .sortedBy(Map.Entry::getKey)
      .values()
      .forEach(variable -> {
        String name = variable.getName();
        LOG.assertTrue(name != null, "variable name is null");
        PsiType type = variable.getType();
        PsiExpression initializer = myFactory // todo check if definitely assigned
          .createExpressionFromText(type instanceof PsiPrimitiveType ? PsiTypesUtil.getDefaultValueOfType(type) : PsiKeyword.NULL, null);
        PsiDeclarationStatement statement = myFactory.createVariableDeclarationStatement(name, type, initializer);
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
  private PsiReturnStatement createReturnStatement(List<ResultItem> resultItems,
                                                   int exitKey,
                                                   PsiCodeBlock body,
                                                   PsiExpression expression) {
    PsiReturnStatement returnStatement = (PsiReturnStatement)myFactory.createStatementFromText("return 0;", body.getRBrace());
    PsiNewExpression returnExpression = (PsiNewExpression)notNull(returnStatement.getReturnValue())
      .replace(myFactory.createExpressionFromText("new NewMethodResult()", returnStatement));
    PsiExpressionList returnArgumentList = notNull(returnExpression.getArgumentList());
    for (ResultItem resultItem : resultItems) {
      PsiExpression resultObjectArgument = resultItem.createResultObjectArgument(expression, exitKey, body, myFactory);
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

  private static abstract class ResultItem {
    protected final String myFieldName; // todo resolve name conflicts
    protected final PsiType myType;

    ResultItem(String fieldName, PsiType type) {
      myFieldName = fieldName;
      myType = type;
    }

    void createField(PsiClass resultClass, PsiElementFactory factory) {
      PsiField field = (PsiField)resultClass.add(factory.createField(myFieldName, myType));
      notNull(field.getModifierList()).setModifierProperty(PsiModifier.PRIVATE, true);
    }

    void createConstructorParameter(PsiParameterList parameterList, PsiElementFactory factory) {
      parameterList.add(factory.createParameter(myFieldName, myType));
    }

    void createAssignmentInConstructor(PsiCodeBlock body, PsiElementFactory factory) {
      body.add(factory.createStatementFromText(PsiKeyword.THIS + '.' + myFieldName + '=' + myFieldName + ';', body.getRBrace()));
    }

    void contributeToTypeParameters(List<PsiElement> elements) {}

    abstract PsiExpression createResultObjectArgument(PsiExpression expression, int exitKey, PsiCodeBlock body, PsiElementFactory factory);
  }

  private static class VariableResultItem extends ResultItem {
    private final PsiVariable myVariable;
    private final String myVariableName;

    VariableResultItem(@NotNull PsiVariable variable, @NotNull String variableName) {
      super(variableName, variable.getType());
      myVariable = variable;
      myVariableName = variableName;
    }

    @Override
    PsiExpression createResultObjectArgument(PsiExpression expression, int exitKey, PsiCodeBlock body, PsiElementFactory factory) {
      return factory.createExpressionFromText(myFieldName, body.getRBrace());
    }

    @Override
    void contributeToTypeParameters(List<PsiElement> elements) {
      elements.add(myVariable);
    }
  }

  private static class ExpressionResultItem extends ResultItem {
    static final String EXPRESSION_RESULT = "expressionResult";
    private final PsiExpression myExpression;

    ExpressionResultItem(@NotNull PsiExpression expression, PsiType type) {
      super(EXPRESSION_RESULT, type);
      myExpression = expression;
    }

    @Override
    PsiExpression createResultObjectArgument(PsiExpression expression, int exitKey, PsiCodeBlock body, PsiElementFactory factory) {
      return myExpression;
    }
  }

  private static class ReturnResultItem extends ResultItem {
    static final String FIELD_NAME = "returnResult";

    private final PsiTypeElement myReturnTypeElement;

    ReturnResultItem(@NotNull PsiType returnType, PsiTypeElement returnTypeElement) {
      super(FIELD_NAME, returnType);
      myReturnTypeElement = returnTypeElement;
    }

    @Override
    PsiExpression createResultObjectArgument(PsiExpression expression, int exitKey, PsiCodeBlock body, PsiElementFactory factory) {
      if (expression == null) {
        String text = myType instanceof PsiPrimitiveType ? PsiTypesUtil.getDefaultValueOfType(myType) : PsiKeyword.NULL;
        return factory.createExpressionFromText("(" + text + " /* missing value */)", body.getRBrace());
      }
      return expression;
    }

    @Override
    void contributeToTypeParameters(List<PsiElement> elements) {
      if (myReturnTypeElement != null) {
        elements.add(myReturnTypeElement);
      }
    }
  }

  private static class ExitKeyResultItem extends ResultItem {
    static final String FIELD_NAME = "exitKey";

    ExitKeyResultItem() {
      super(FIELD_NAME, PsiType.INT);
    }

    @Override
    PsiExpression createResultObjectArgument(PsiExpression expression, int exitKey, PsiCodeBlock body, PsiElementFactory factory) {
      return factory.createExpressionFromText("(" + exitKey + " /* exit key */)", body.getRBrace());
    }
  }
}
