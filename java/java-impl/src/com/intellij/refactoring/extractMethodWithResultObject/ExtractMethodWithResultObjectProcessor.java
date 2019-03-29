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
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.introduceField.ElementToWorkOn;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import one.util.streamex.EntryStream;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.intellij.util.ObjectUtils.notNull;
import static com.intellij.util.ObjectUtils.tryCast;

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
  private final Map<PsiReferenceExpression, PsiVariable> myInputs = new HashMap<>();
  private final Set<PsiVariable> myOutputVariables = new HashSet<>();
  private final Map<PsiStatement, Exit> myExits = new HashMap<>();
  private final Set<PsiVariable> myWrittenOuterVariables = new HashSet<>();

  private String myMethodName = "newMethod";
  private String myResultClassName = "NewMethodResult";
  private String myResultVariableName = "x";

  // in the order the expressions appear in the original code
  private final List<Pair.NonNull<String, PsiExpression>> myArguments = new ArrayList<>();

  private PsiClass myTargetClass;
  private PsiElement myAnchor;

  @NonNls static final String REFACTORING_NAME = "Extract Method With Result Object";

  private static final Key<Integer> EXIT_STATEMENT_INDEX_KEY = Key.create("ExtractMethodWithResultObjectProcessor.ExitStatementIndex");

  private enum ExitType {
    EXPRESSION,
    RETURN,
    BREAK,
    CONTINUE,
    THROW,
    SEQUENTIAL,
    UNDEFINED
  }

  private static class Exit {
    private final ExitType myType;
    private final PsiElement myExitedElement;

    private Exit(@NotNull ExitType type, @Nullable PsiElement element) {
      myType = type;
      myExitedElement = element;
    }

    ExitType getType() {
      return myType;
    }

    PsiElement getExitedElement() {
      return myExitedElement;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof Exit)) return false;

      Exit exit = (Exit)o;
      return myType == exit.myType && Objects.equals(myExitedElement, exit.myExitedElement);
    }

    @Override
    public int hashCode() {
      return 31 * myType.hashCode() + (myExitedElement != null ? myExitedElement.hashCode() : 0);
    }

    @Override
    public String toString() {
      return myExitedElement != null ? myType + " " + myExitedElement : myType.toString();
    }
  }

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

    collectInputsAndOutputs();
    collectDeclaredInsideUsedAfter();
    findSequentialExit();

    chooseTargetClass();
    chooseAnchor();

    return true;
  }

  private void collectInputsAndOutputs() {
    // todo take care of surrounding try-catch
    JavaRecursiveElementWalkingVisitor elementsVisitor = new JavaRecursiveElementWalkingVisitor() {
      private final Set<PsiElement> mySkippedContexts = new HashSet<>();

      @Override
      public void visitReferenceExpression(PsiReferenceExpression expression) {
        super.visitReferenceExpression(expression);

        PsiExpression qualifier = expression.getQualifierExpression();
        if (qualifier == null || qualifier instanceof PsiQualifiedExpression) {
          PsiElement resolved = expression.resolve();
          if (resolved instanceof PsiVariable) {
            PsiVariable variable = (PsiVariable)resolved;
            PsiAssignmentExpression assignment = getAssignmentOf(expression);
            if (assignment != null) {
              if (assignment.getOperationTokenType() != JavaTokenType.EQ) {
                processPossibleInput(expression, variable);
              }
              processPossibleOutput(variable);
            }
            else if (PsiUtil.isIncrementDecrementOperation(expression)) {
              processPossibleInput(expression, variable);
              processPossibleOutput(variable);
            }
            else {
              processPossibleInput(expression, variable);
            }
          }
        }
      }

      private PsiAssignmentExpression getAssignmentOf(PsiReferenceExpression expression) {
        PsiElement element = expression;
        while (element.getParent() instanceof PsiParenthesizedExpression) {
          element = element.getParent();
        }
        PsiElement parent = element.getParent();
        if (parent instanceof PsiAssignmentExpression && ((PsiAssignmentExpression)parent).getLExpression() == element) {
          return (PsiAssignmentExpression)parent;
        }
        return null;
      }

      @Override
      public void visitReturnStatement(PsiReturnStatement statement) {
        super.visitReturnStatement(statement);

        if (!isInSkippedContext(statement)) {
          myExits.put(statement, new Exit(ExitType.RETURN, myCodeFragmentMember));
        }
      }

      @Override
      public void visitContinueStatement(PsiContinueStatement statement) {
        super.visitContinueStatement(statement);

        if (!isInSkippedContext(statement)) {
          PsiStatement continuedStatement = statement.findContinuedStatement();
          if (continuedStatement instanceof PsiLoopStatement && !isInside(continuedStatement)) {
            myExits.put(statement, new Exit(ExitType.CONTINUE, ((PsiLoopStatement)continuedStatement).getBody()));
          }
        }
      }

      @Override
      public void visitBreakStatement(PsiBreakStatement statement) {
        super.visitBreakStatement(statement);

        if (!isInSkippedContext(statement)) {
          PsiElement exitedElement = statement.findExitedElement();
          if (exitedElement != null && !isInside(exitedElement)) {
            PsiElement outermostExited = findOutermostExitedElement(statement);
            myExits.put(statement, new Exit(ExitType.BREAK, outermostExited != null ? outermostExited : exitedElement));
          }
        }
      }

      @Override
      public void visitThrowStatement(PsiThrowStatement statement) {
        super.visitThrowStatement(statement);

        if (!isInSkippedContext(statement)) {
          PsiTryStatement throwTarget = findThrowTarget(statement);
          if (throwTarget == null) {
            myExits.put(statement, new Exit(ExitType.THROW, myCodeFragmentMember));
          }
          else if (!isInside(throwTarget)) {
            myExits.put(statement, new Exit(ExitType.THROW, throwTarget));
          }
        }
      }

      @Override
      public void visitClass(PsiClass aClass) {
        mySkippedContexts.add(aClass);
        super.visitClass(aClass);
      }

      @Override
      public void visitLambdaExpression(PsiLambdaExpression expression) {
        mySkippedContexts.add(expression);
        super.visitLambdaExpression(expression);
      }

      private boolean isInSkippedContext(PsiElement element) {
        while (true) {
          if (element == myCodeFragmentMember) {
            return false;
          }
          if (element == null || mySkippedContexts.contains(element)) {
            return true;
          }
          element = element.getContext();
        }
      }
    };

    for (PsiElement element : myElements) {
      element.accept(elementsVisitor);
    }
    if (myExpression != null) {
      myExits.put(null, new Exit(ExitType.EXPRESSION, myExpression));
    }
  }

  private void collectDeclaredInsideUsedAfter() {
    if (myExpression != null) {
      return;
    }
    Set<PsiLocalVariable> declaredInside = new HashSet<>();
    for (PsiElement element : myElements) {
      if (element instanceof PsiDeclarationStatement) {
        PsiElement[] declaredElements = ((PsiDeclarationStatement)element).getDeclaredElements();
        for (PsiElement declaredElement : declaredElements) {
          if (declaredElement instanceof PsiLocalVariable) {
            declaredInside.add((PsiLocalVariable)declaredElement);
          }
        }
      }
    }

    if (!declaredInside.isEmpty()) {
      for (PsiElement next = myElements[myElements.length - 1].getNextSibling(); next != null; next = next.getNextSibling()) {
        next.accept(new JavaRecursiveElementWalkingVisitor() {
          @Override
          public void visitReferenceExpression(PsiReferenceExpression expression) {
            super.visitReferenceExpression(expression);

            if (expression.getQualifier() == null) {
              PsiElement resolved = expression.resolve();
              if (resolved instanceof PsiLocalVariable && declaredInside.contains(resolved)) {
                myOutputVariables.add((PsiLocalVariable)resolved);
              }
            }
          }
        });
      }
    }
  }

  @Nullable("null means there's an error in the code")
  private PsiElement findOutermostExitedElement(@NotNull PsiElement startLocation) {
    PsiElement location = startLocation;
    while (true) {
      if (location == null || location instanceof PsiParameterListOwner || location instanceof PsiClassInitializer) {
        return location;
      }
      if (location instanceof PsiBreakStatement) {
        PsiElement exited = ((PsiBreakStatement)location).findExitedElement();
        if (exited == null) {
          return null;
        }
        location = exited;
        continue;
      }
      if (location instanceof PsiContinueStatement) {
        PsiStatement continued = ((PsiContinueStatement)location).findContinuedStatement();
        if (continued instanceof PsiLoopStatement && !ControlFlowUtils.statementMayCompleteNormally(continued)) {
          return ((PsiLoopStatement)continued).getBody();
        }
        if (continued == null) {
          return null;
        }
        location = continued;
        continue;
      }
      if (location instanceof PsiThrowStatement) {
        PsiTryStatement target = findThrowTarget((PsiThrowStatement)location);
        return target != null ? target.getTryBlock() : myCodeFragmentMember;
      }
      if (location instanceof PsiSwitchLabelStatementBase) {
        location = ((PsiSwitchLabelStatementBase)location).getEnclosingSwitchBlock();
        continue;
      }

      PsiElement parent = location.getParent();
      if (parent instanceof PsiLoopStatement && !ControlFlowUtils.statementMayCompleteNormally((PsiLoopStatement)parent)) {
        return ((PsiLoopStatement)parent).getBody();
      }
      if (parent instanceof PsiCodeBlock) {
        PsiStatement next = PsiTreeUtil.getNextSiblingOfType(location, PsiStatement.class);
        if (next != null) {
          return location;
        }
      }
      location = parent;
    }
  }

  @Nullable
  private PsiTryStatement findThrowTarget(@NotNull PsiThrowStatement statement) {
    PsiExpression exception = PsiUtil.skipParenthesizedExprDown(statement.getException());
    if (exception == null) {
      return null;
    }
    PsiClassType exactType = null;
    PsiClassType lowerBoundType = null;
    if (exception instanceof PsiNewExpression) {
      PsiType type = exception.getType();
      if (type instanceof PsiClassType) {
        PsiClass resolved = ((PsiClassType)type).resolve();
        if (resolved != null && !(resolved instanceof PsiAnonymousClass)) {
          exactType = lowerBoundType = (PsiClassType)type;
        }
      }
    }
    if (lowerBoundType == null) {
      lowerBoundType = tryCast(exception.getType(), PsiClassType.class);
    }
    if (lowerBoundType == null) {
      return null;
    }
    for (PsiElement element = statement; element != null && element != myCodeFragmentMember; element = element.getContext()) {
      PsiElement parent = element.getContext();
      if (parent instanceof PsiTryStatement && element == ((PsiTryStatement)parent).getTryBlock()) {
        for (PsiParameter parameter : ((PsiTryStatement)parent).getCatchBlockParameters()) {
          PsiType catchType = parameter.getType();
          if (exactType != null && catchType.isAssignableFrom(exactType)) {
            return ((PsiTryStatement)parent);
          }
          else if (exactType == null && ControlFlowUtil.isCaughtExceptionType(lowerBoundType, catchType)) {
            return ((PsiTryStatement)parent);
          }
        }
      }
    }
    return null;
  }

  private void findSequentialExit() {
    if (myExpression != null) {
      return;
    }
    for (int i = myElements.length - 1; i >= 0; i--) {
      if (!(myElements[i] instanceof PsiStatement)) {
        continue; // skip comments and white spaces
      }
      PsiStatement statement = (PsiStatement)myElements[i];
      if (!ControlFlowUtils.statementMayCompleteNormally(statement)) {
        return; // sequential exit can't happen
      }
      break;
    }

    PsiElement exitedElement = findOutermostExitedElement(myElements[myElements.length - 1]);
    if (exitedElement != null) {
      myExits.put(null, new Exit(ExitType.SEQUENTIAL, exitedElement));
    }else {
      myExits.put(null, new Exit(ExitType.UNDEFINED, null));
    }
  }

  private void processPossibleOutput(PsiVariable variable) {
    if (variable instanceof PsiLocalVariable || variable instanceof PsiParameter) {
      if (PsiTreeUtil.isAncestor(myCodeFragmentMember, variable, true)) {
        myOutputVariables.add(variable);
        if (!isInside(variable)) {
          myWrittenOuterVariables.add(variable);
        }
      }
    }
    else if (variable instanceof PsiField && variable.hasModifierProperty(PsiModifier.FINAL)) {
      if (myCodeFragmentMember.getParent() != null && myCodeFragmentMember.getParent() == variable.getParent()) {
        myOutputVariables.add(variable);
      }
    }
  }

  private void processPossibleInput(PsiReferenceExpression expression, PsiVariable variable) {
    if (variable instanceof PsiLocalVariable || variable instanceof PsiParameter) {
      if (!isInside(variable)) {
        myInputs.put(expression, variable);
      }
    }
  }

  private boolean isInside(@NotNull PsiElement element) {
    PsiElement context = PsiTreeUtil.findFirstContext(element, false,
                                                      e -> e == myCodeFragmentMember || ArrayUtil.find(myElements, e) >= 0);
    return context != myCodeFragmentMember;
  }

  boolean showDialog() {
    LOG.info("showDialog");
    return true;
  }

  void doRefactoring() {
    LOG.warn("doRefactoring");

    Set<ExitType> exitTypes = myExits.values().stream().map(e -> e.getType()).collect(Collectors.toSet());
    if (!exitTypes.contains(ExitType.UNDEFINED) && !exitTypes.contains(ExitType.THROW)) {
      Map<Exit, Integer> distinctExits = getDistinctExits();
      List<ResultItem> resultItems = collectResultItems(distinctExits);
      PsiClass resultClass = createResultClass(resultItems);
      createMethod(resultItems, resultClass, distinctExits);

      createMethodCall(resultClass);
    }
    else {
      dumpText("too complex exits: " + exitTypes.stream().sorted().collect(Collectors.toList()));
    }

    dumpTexts(myExits.entrySet().stream().map(ExtractMethodWithResultObjectProcessor::getExitText), "exit");
    dumpElements(myOutputVariables, "out");
    dumpElements(new HashSet<>(myInputs.values()), "in");
    dumpText("ins and outs");
  }

  private void createMethodCall(PsiClass resultClass) {
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
      myExpression.replace(referenceExpression);

    } else {

      final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(myProject);
      myResultVariableName = codeStyleManager.suggestUniqueVariableName(myResultVariableName, firstElement, true);
      PsiDeclarationStatement methodResultDeclaration =
        myFactory.createVariableDeclarationStatement(myResultVariableName, myFactory.createType(resultClass), null, firstElement);
      PsiElement[] declaredElements = methodResultDeclaration.getDeclaredElements();

      LOG.assertTrue(declaredElements.length == 1, "declaredElements.length");
      LOG.assertTrue(declaredElements[0] instanceof PsiLocalVariable, "declaredElements[0]");
      ((PsiLocalVariable)declaredElements[0]).setInitializer(methodCall);

      firstElement.getParent().addBefore(methodResultDeclaration, firstElement);
    }
  }

  private List<ResultItem> collectResultItems(Map<Exit, Integer> distinctExits) {
    if (myExpression != null) {
      assert myExits.size() == 1 : "one exit with expression";
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

    List<Exit> returnExits = ContainerUtil.filter(myExits.values(), exit -> ExitType.RETURN.equals(exit.getType()));
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

    StreamEx.of(myOutputVariables)
      .mapToEntry(v -> v.getName(), v -> v)
      .filterKeys(Objects::nonNull)
      .sortedBy(e -> e.getKey())
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
    PsiTypeParameterList typeParameterList = createMethodTypeParameterList(resultItems);
    if (typeParameterList != null) {
      notNull(method.getTypeParameterList()).replace(typeParameterList);
    }

    PsiParameterList parameterList = method.getParameterList();

    Map<PsiVariable, Pair<PsiExpression, Integer>> orderedInputs = new HashMap<>();
    myInputs.forEach((expression, variable) -> {
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

    List<PsiStatement> oldExitStatements = new ArrayList<>();
    for (PsiStatement statement : myExits.keySet()) {
      if (statement != null) {
        Integer index = oldExitStatements.size();
        oldExitStatements.add(statement);
        statement.putCopyableUserData(EXIT_STATEMENT_INDEX_KEY, index);
      }
    }

    body.addRange(myElements[0], myElements[myElements.length - 1]);
    PsiStatement[] methodBodyStatements = body.getStatements();
    List<PsiStatement> newExitStatements = collectExitStatements(methodBodyStatements);

    Map<PsiStatement, PsiStatement> newToOldExitStatements = new HashMap<>();
    for (PsiStatement newExitStatement : newExitStatements) {
      Integer index = newExitStatement.getCopyableUserData(EXIT_STATEMENT_INDEX_KEY);
      if (index == null) {
        continue; // break or continue within the method
      }
      assert index >= 0 && index < oldExitStatements.size() : "exit statement index";
      PsiStatement oldExitStatement = oldExitStatements.get(index);
      newToOldExitStatements.put(newExitStatement, oldExitStatement);

      newExitStatement.putCopyableUserData(EXIT_STATEMENT_INDEX_KEY, null);
      oldExitStatement.putCopyableUserData(EXIT_STATEMENT_INDEX_KEY, null);
    }

    Set<ExitType> exitTypes = myExits.values().stream().map(Exit::getType).collect(Collectors.toSet());

    for (PsiStatement newExitStatement : newExitStatements) {
      PsiStatement oldExitStatement = newToOldExitStatements.get(newExitStatement);
      if (oldExitStatement == null) {
        continue; // break or continue within the method
      }
      Exit exit = myExits.get(oldExitStatement);
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
      assert returnStatement != null : "returnStatement ";
      newExitStatement.replace(returnStatement);
    }

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
    HashSet<PsiVariable> writtenVariables = new HashSet<>(myWrittenOuterVariables);
    for (PsiVariable inputVariable : myInputs.values()) {
      writtenVariables.remove(inputVariable);
    }
    StreamEx.of(writtenVariables)
      .mapToEntry(v -> v.getTextOffset(), v -> v)
      .sortedBy(e -> e.getKey())
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
  private Map<Exit, Integer> getDistinctExits() {
    Map<Exit, Integer> distinctExits = new HashMap<>();
    EntryStream.of(myExits)
      .mapKeys(s -> s != null ? s.getTextOffset() : 0)
      .sorted(Comparator.comparing((Map.Entry<Integer, Exit> e) -> e.getKey()).thenComparing(e -> e.getValue().getType()))
      .forKeyValue((offset, exit) -> {
        if (!distinctExits.containsKey(exit)) {
          distinctExits.put(exit, distinctExits.size());
        }
      });
    return distinctExits;
  }

  @NotNull
  private static List<PsiStatement> collectExitStatements(PsiStatement[] bodyStatements) {
    List<PsiStatement> exitStatements = new ArrayList<>();
    JavaRecursiveElementWalkingVisitor visitor = new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitReturnStatement(PsiReturnStatement statement) {
        super.visitReturnStatement(statement);
        exitStatements.add(statement);
      }

      @Override
      public void visitBreakStatement(PsiBreakStatement statement) {
        super.visitBreakStatement(statement);
        exitStatements.add(statement);
      }

      @Override
      public void visitContinueStatement(PsiContinueStatement statement) {
        super.visitContinueStatement(statement);
        exitStatements.add(statement);
      }

      @Override
      public void visitClass(PsiClass aClass) {}

      @Override
      public void visitLambdaExpression(PsiLambdaExpression expression) {}
    };
    for (PsiStatement bodyStatement : bodyStatements) {
      bodyStatement.accept(visitor);
    }
    return exitStatements;
  }

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

  private void dumpElements(Collection<? extends PsiElement> elements, String prefix) {
    dumpTexts(elements.stream().map(PsiElement::toString), prefix);
  }

  private void dumpTexts(Stream<String> elements, String prefix) {
    elements
      .sorted(Comparator.reverseOrder())
      .forEach(text -> dumpText(prefix + ": " + text));
  }

  private void dumpText(String text) {
    PsiElement comment = myFactory.createCommentFromText("//" + text, null);
    myAnchor.getParent().addAfter(comment, myAnchor);
    LOG.warn(text);
  }

  private static String getExitText(Map.Entry<PsiStatement, Exit> e) {
    Exit exit = e.getValue();
    PsiStatement statement = e.getKey();
    if (statement instanceof PsiReturnStatement) {
      PsiExpression value = ((PsiReturnStatement)statement).getReturnValue();
      return exit + "<-" + (value != null ? value.toString() : PsiKeyword.VOID);
    }
    if (statement != null) {
      return exit + "<-" + statement;
    }
    return exit.toString();
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

    VariableResultItem(@NotNull PsiVariable variable, @NotNull String variableName) {
      super(variableName, variable.getType());
      myVariable = variable;
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
    private final PsiTypeElement myReturnTypeElement;

    ReturnResultItem(@NotNull PsiType returnType, PsiTypeElement returnTypeElement) {
      super("returnResult", returnType);
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
    ExitKeyResultItem() {
      super("exitKey", PsiType.INT);
    }

    @Override
    PsiExpression createResultObjectArgument(PsiExpression expression, int exitKey, PsiCodeBlock body, PsiElementFactory factory) {
      return factory.createExpressionFromText("(" + exitKey + " /* exit key */)", body.getRBrace());
    }
  }
}
