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
package com.intellij.refactoring.extractMethod;

import com.intellij.codeInsight.*;
import com.intellij.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import com.intellij.codeInsight.daemon.impl.quickfix.AnonymousTargetClassPreselectionUtil;
import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.codeInsight.intention.AddAnnotationPsiFix;
import com.intellij.codeInsight.intention.impl.AddNullableNotNullAnnotationFix;
import com.intellij.codeInsight.navigation.NavigationUtil;
import com.intellij.codeInspection.dataFlow.*;
import com.intellij.codeInspection.dataFlow.instructions.BranchingInstruction;
import com.intellij.codeInspection.dataFlow.instructions.CheckReturnValueInstruction;
import com.intellij.codeInspection.dataFlow.instructions.Instruction;
import com.intellij.ide.DataManager;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.util.PsiClassListCellRenderer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Pass;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.*;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.controlFlow.ControlFlow;
import com.intellij.psi.impl.source.codeStyle.JavaCodeStyleManagerImpl;
import com.intellij.psi.scope.processor.VariablesProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.*;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.extractMethodObject.ExtractMethodObjectHandler;
import com.intellij.refactoring.introduceField.ElementToWorkOn;
import com.intellij.refactoring.introduceVariable.IntroduceVariableBase;
import com.intellij.refactoring.util.*;
import com.intellij.refactoring.util.classMembers.ElementNeedsThis;
import com.intellij.refactoring.util.duplicates.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;

public class ExtractMethodProcessor implements MatchProvider {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.extractMethod.ExtractMethodProcessor");

  protected final Project myProject;
  private final Editor myEditor;
  protected final PsiElement[] myElements;
  private final PsiBlockStatement myEnclosingBlockStatement;
  private final PsiType myForcedReturnType;
  private final String myRefactoringName;
  protected final String myInitialMethodName;
  private final String myHelpId;

  private final PsiManager myManager;
  private final PsiElementFactory myElementFactory;
  private final CodeStyleManager myStyleManager;

  private PsiExpression myExpression;

  private PsiElement myCodeFragmentMember; // parent of myCodeFragment

  protected String myMethodName; // name for extracted method
  protected PsiType myReturnType; // return type for extracted method
  protected PsiTypeParameterList myTypeParameterList; //type parameter list of extracted method
  protected VariableData[] myVariableDatum; // parameter data for extracted method
  protected PsiClassType[] myThrownExceptions; // exception to declare as thrown by extracted method
  protected boolean myStatic; // whether to declare extracted method static

  protected PsiClass myTargetClass; // class to create the extracted method in
  private PsiElement myAnchor; // anchor to insert extracted method after it

  protected ControlFlowWrapper myControlFlowWrapper;
  protected InputVariables myInputVariables; // input variables
  protected PsiVariable[] myOutputVariables; // output variables
  protected PsiVariable myOutputVariable; // the only output variable
  protected PsiVariable myArtificialOutputVariable;
  private Collection<PsiStatement> myExitStatements;

  private boolean myHasReturnStatement; // there is a return statement
  private boolean myHasReturnStatementOutput; // there is a return statement and its type is not void
  protected boolean myHasExpressionOutput; // extracted code is an expression with non-void type
  private boolean myNeedChangeContext; // target class is not immediate container of the code to be extracted

  private boolean myShowErrorDialogs = true;
  protected boolean myCanBeStatic;
  protected boolean myCanBeChainedConstructor;
  protected boolean myIsChainedConstructor;
  private List<Match> myDuplicates;
  @PsiModifier.ModifierConstant protected String myMethodVisibility = PsiModifier.PRIVATE;
  protected boolean myGenerateConditionalExit;
  protected PsiStatement myFirstExitStatementCopy;
  private PsiMethod myExtractedMethod;
  private PsiMethodCallExpression myMethodCall;
  protected boolean myNullConditionalCheck;
  protected boolean myNotNullConditionalCheck;
  private Nullness myNullness;

  public ExtractMethodProcessor(Project project,
                                Editor editor,
                                PsiElement[] elements,
                                PsiType forcedReturnType,
                                String refactoringName,
                                String initialMethodName,
                                String helpId) {
    myProject = project;
    myEditor = editor;
    if (elements.length != 1 || !(elements[0] instanceof PsiBlockStatement)) {
      myElements = elements.length == 1 && elements[0] instanceof PsiParenthesizedExpression
                   ? new PsiElement[] {PsiUtil.skipParenthesizedExprDown((PsiExpression)elements[0])} : elements;
      myEnclosingBlockStatement = null;
    }
    else {
      myEnclosingBlockStatement = (PsiBlockStatement)elements[0];
      PsiElement[] codeBlockChildren = myEnclosingBlockStatement.getCodeBlock().getChildren();
      myElements = processCodeBlockChildren(codeBlockChildren);
    }
    myForcedReturnType = forcedReturnType;
    myRefactoringName = refactoringName;
    myInitialMethodName = initialMethodName;
    myHelpId = helpId;

    myManager = PsiManager.getInstance(myProject);
    myElementFactory = JavaPsiFacade.getInstance(myManager.getProject()).getElementFactory();
    myStyleManager = CodeStyleManager.getInstance(myProject);
  }

  private static PsiElement[] processCodeBlockChildren(PsiElement[] codeBlockChildren) {
    int resultLast = codeBlockChildren.length;

    if (codeBlockChildren.length == 0) return PsiElement.EMPTY_ARRAY;

    final PsiElement first = codeBlockChildren[0];
    int resultStart = 0;
    if (PsiUtil.isJavaToken(first, JavaTokenType.LBRACE)) {
      resultStart++;
    }
    final PsiElement last = codeBlockChildren[codeBlockChildren.length - 1];
    if (PsiUtil.isJavaToken(last, JavaTokenType.RBRACE)) {
      resultLast--;
    }
    final ArrayList<PsiElement> result = new ArrayList<>();
    for (int i = resultStart; i < resultLast; i++) {
      PsiElement element = codeBlockChildren[i];
      if (!(element instanceof PsiWhiteSpace)) {
        result.add(element);
      }
    }

    return PsiUtilCore.toPsiElementArray(result);
  }

  /**
   * Method for test purposes
   */
  public void setShowErrorDialogs(boolean showErrorDialogs) {
    myShowErrorDialogs = showErrorDialogs;
  }

  public void setChainedConstructor(final boolean isChainedConstructor) {
    myIsChainedConstructor = isChainedConstructor;
  }


  public boolean prepare() throws PrepareFailedException {
    return prepare(null);
  }

  /**
   * Invoked in atomic action
   */
  public boolean prepare(@Nullable Pass<ExtractMethodProcessor> pass) throws PrepareFailedException {
    myExpression = null;
    if (myElements.length == 1 && myElements[0] instanceof PsiExpression) {
      final PsiExpression expression = (PsiExpression)myElements[0];
      if (expression instanceof PsiAssignmentExpression && expression.getParent() instanceof PsiExpressionStatement) {
        myElements[0] = expression.getParent();
      }
      else {
        myExpression = expression;
      }
    }

    final PsiElement codeFragment = ControlFlowUtil.findCodeFragment(myElements[0]);
    myCodeFragmentMember = codeFragment.getUserData(ElementToWorkOn.PARENT);
    if (myCodeFragmentMember == null) {
      myCodeFragmentMember = codeFragment.getParent();
    }
    if (myCodeFragmentMember == null) {
      myCodeFragmentMember = ControlFlowUtil.findCodeFragment(codeFragment.getContext()).getParent();
    }

    myControlFlowWrapper = new ControlFlowWrapper(myProject, codeFragment, myElements);

    try {
      myExitStatements = myControlFlowWrapper.prepareExitStatements(myElements, codeFragment);
      if (myControlFlowWrapper.isGenerateConditionalExit()) {
        myGenerateConditionalExit = true;
      } else {
        myHasReturnStatement = myExpression == null && myControlFlowWrapper.isReturnPresentBetween();
      }
      myFirstExitStatementCopy = myControlFlowWrapper.getFirstExitStatementCopy();
    }
    catch (ControlFlowWrapper.ExitStatementsNotSameException e) {
      myExitStatements = myControlFlowWrapper.getExitStatements();
      myNotNullConditionalCheck = areAllExitPointsNotNull(getExpectedReturnType());
      if (!myNotNullConditionalCheck) {
        showMultipleExitPointsMessage();
        return false;
      }
    }

    myOutputVariables = myControlFlowWrapper.getOutputVariables();

    return chooseTargetClass(codeFragment, pass);
  }

  private boolean checkExitPoints() throws PrepareFailedException {
    PsiType expressionType = null;
    if (myExpression != null) {
      if (myForcedReturnType != null) {
        expressionType = myForcedReturnType;
      }
      else {
        expressionType = RefactoringUtil.getTypeByExpressionWithExpectedType(myExpression);
        if (expressionType == null && !(myExpression.getParent() instanceof PsiExpressionStatement)) {
          expressionType = PsiType.getJavaLangObject(myExpression.getManager(), GlobalSearchScope.allScope(myProject));
        }
      }
    }
    if (expressionType == null) {
      expressionType = PsiType.VOID;
    }
    myHasExpressionOutput = !PsiType.VOID.equals(expressionType);

    final PsiType returnStatementType = getExpectedReturnType();
    myHasReturnStatementOutput = myHasReturnStatement && returnStatementType != null && !PsiType.VOID.equals(returnStatementType);

    if (myGenerateConditionalExit && myOutputVariables.length == 1) {
      if (!(myOutputVariables[0].getType() instanceof PsiPrimitiveType)) {
        myNullConditionalCheck = isNullInferred(myOutputVariables[0].getName()) && getReturnsNullability(true);
      }
      myNotNullConditionalCheck = areAllExitPointsNotNull(returnStatementType);
    }

    if (!myHasReturnStatementOutput && checkOutputVariablesCount() && !myNullConditionalCheck && !myNotNullConditionalCheck) {
      showMultipleOutputMessage(expressionType);
      return false;
    }

    myOutputVariable = myOutputVariables.length > 0 ? myOutputVariables[0] : null;
    if (myNotNullConditionalCheck) {
      myReturnType = returnStatementType instanceof PsiPrimitiveType ? ((PsiPrimitiveType)returnStatementType).getBoxedType(myCodeFragmentMember)
                                                                     : returnStatementType;
    } else if (myHasReturnStatementOutput) {
      myReturnType = returnStatementType;
    }
    else if (myOutputVariable != null) {
      myReturnType = myOutputVariable.getType();
    }
    else if (myGenerateConditionalExit) {
      myReturnType = PsiType.BOOLEAN;
    }
    else {
      myReturnType = expressionType;
    }

    PsiElement container = PsiTreeUtil.getParentOfType(myElements[0], PsiClass.class, PsiMethod.class);
    while (container instanceof PsiMethod && ((PsiMethod)container).getContainingClass() != myTargetClass) {
      container = PsiTreeUtil.getParentOfType(container, PsiMethod.class, true);
    }
    if (container instanceof PsiMethod) {
      PsiElement[] elements = myElements;
      if (myExpression == null) {
        if (myOutputVariable != null) {
          elements = ArrayUtil.append(myElements, myOutputVariable, PsiElement.class);
        }
        if (myCodeFragmentMember instanceof PsiMethod && myReturnType == ((PsiMethod)myCodeFragmentMember).getReturnType()) {
          elements = ArrayUtil.append(myElements, ((PsiMethod)myCodeFragmentMember).getReturnTypeElement(), PsiElement.class);
        }
      }
      myTypeParameterList = RefactoringUtil.createTypeParameterListWithUsedTypeParameters(((PsiMethod)container).getTypeParameterList(),
                                                                                          elements);
    }
    List<PsiClassType> exceptions = ExceptionUtil.getThrownCheckedExceptions(myElements);
    myThrownExceptions = exceptions.toArray(new PsiClassType[exceptions.size()]);

    if (container instanceof PsiMethod) {
      checkLocalClasses((PsiMethod) container);
    }
    return true;
  }

  private PsiType getExpectedReturnType() {
    return myCodeFragmentMember instanceof PsiMethod
                                        ? ((PsiMethod)myCodeFragmentMember).getReturnType()
                                        : myCodeFragmentMember instanceof PsiLambdaExpression
                                          ? LambdaUtil.getFunctionalInterfaceReturnType((PsiLambdaExpression)myCodeFragmentMember)
                                          : null;
  }

  @Nullable
  protected PsiVariable getArtificialOutputVariable() {
    if (myOutputVariables.length == 0 && myExitStatements.isEmpty()) {
      if (myCanBeChainedConstructor) {
        final Set<PsiField> fields = new HashSet<>();
        for (PsiElement element : myElements) {
          element.accept(new JavaRecursiveElementWalkingVisitor() {
            @Override
            public void visitReferenceExpression(PsiReferenceExpression expression) {
              super.visitReferenceExpression(expression);
              final PsiElement resolve = expression.resolve();
              if (resolve instanceof PsiField && ((PsiField)resolve).hasModifierProperty(PsiModifier.FINAL) &&
                  PsiUtil.isAccessedForWriting(expression)) {
                fields.add((PsiField)resolve);
              }
            }
          });
        }
        if (!fields.isEmpty()) {
          return fields.size() == 1 ? fields.iterator().next() : null;
        }
      }
      final VariablesProcessor processor = new VariablesProcessor(true) {
        @Override
        protected boolean check(PsiVariable var, ResolveState state) {
          return isDeclaredInside(var);
        }
      };
      PsiScopesUtil.treeWalkUp(processor, myElements[myElements.length - 1], myCodeFragmentMember);
      if (processor.size() == 1) {
        return processor.getResult(0);
      }
    }
    return null;
  }

  private boolean areAllExitPointsNotNull(PsiType returnStatementType) {
    if (insertNotNullCheckIfPossible() && myControlFlowWrapper.getOutputVariables(false).length == 0) {
      if (returnStatementType != null && !PsiType.VOID.equals(returnStatementType)) {
        return getReturnsNullability(false);
      }
    }
    return false;
  }

  /**
   * @param nullsExpected when true check that all returned values are null, when false check that all returned values can't be null
   */
  private boolean getReturnsNullability(boolean nullsExpected) {
    PsiElement body = null;
    if (myCodeFragmentMember instanceof PsiMethod) {
      body = ((PsiMethod)myCodeFragmentMember).getBody();
    }
    else if (myCodeFragmentMember instanceof PsiLambdaExpression) {
      body = ((PsiLambdaExpression)myCodeFragmentMember).getBody();
    }
    if (body == null) return false;

    Set<PsiExpression> returnedExpressions = StreamEx.of(myExitStatements)
      .select(PsiReturnStatement.class)
      .map(PsiReturnStatement::getReturnValue)
      .nonNull()
      .toSet();

    for (Iterator<PsiExpression> it = returnedExpressions.iterator(); it.hasNext(); ) {
      PsiType type = it.next().getType();
      if (nullsExpected) {
        if (type == PsiType.NULL) {
          it.remove(); // don't need to check
        }
        else if (type instanceof PsiPrimitiveType) {
          return false;
        }
      }
      else {
        if (type == PsiType.NULL) {
          return false;
        }
        else if (type instanceof PsiPrimitiveType) {
          it.remove(); // don't need to check
        }
      }
    }
    if (returnedExpressions.isEmpty()) return true;

    class ReturnChecker extends StandardInstructionVisitor {
      boolean myResult = true;

      @Override
      public DfaInstructionState[] visitCheckReturnValue(CheckReturnValueInstruction instruction,
                                                         DataFlowRunner runner,
                                                         DfaMemoryState memState) {
        PsiElement aReturn = instruction.getReturn();
        if (aReturn instanceof PsiExpression && returnedExpressions.contains(aReturn)) {
          myResult &= nullsExpected ? memState.isNull(memState.peek()) : memState.isNotNull(memState.peek());
        }
        return super.visitCheckReturnValue(instruction, runner, memState);
      }
    }
    final StandardDataFlowRunner dfaRunner = new StandardDataFlowRunner();
    final ReturnChecker returnChecker = new ReturnChecker();
    if (dfaRunner.analyzeMethod(body, returnChecker) == RunnerResult.OK) {
      return returnChecker.myResult;
    }
    return false;
  }

  protected boolean insertNotNullCheckIfPossible() {
    return true;
  }

  private boolean isNullInferred(String exprText) {
    final PsiCodeBlock block = myElementFactory.createCodeBlockFromText("{}", myElements[0]);
    for (PsiElement element : myElements) {
      block.add(element);
    }
    final PsiIfStatement statementFromText = (PsiIfStatement)myElementFactory.createStatementFromText("if (" + exprText + " == null);", null);
    block.add(statementFromText);

    final StandardDataFlowRunner dfaRunner = new StandardDataFlowRunner();
    final StandardInstructionVisitor visitor = new StandardInstructionVisitor();
    final RunnerResult rc = dfaRunner.analyzeMethod(block, visitor);
    if (rc == RunnerResult.OK) {
      final Pair<Set<Instruction>, Set<Instruction>> expressions = dfaRunner.getConstConditionalExpressions();
      final Set<Instruction> set = expressions.getSecond();
      for (Instruction instruction : set) {
        if (instruction instanceof BranchingInstruction) {
          if (((BranchingInstruction)instruction).getPsiAnchor().getText().equals(statementFromText.getCondition().getText())) {
            return true;
          }
        }
      }
    }
    return false;
  }

  protected boolean checkOutputVariablesCount() {
    int outputCount = (myHasExpressionOutput ? 1 : 0) + (myGenerateConditionalExit ? 1 : 0) + myOutputVariables.length;
    return outputCount > 1;
  }

  private void checkCanBeChainedConstructor() {
    if (!(myCodeFragmentMember instanceof PsiMethod)) {
      return;
    }
    final PsiMethod method = (PsiMethod)myCodeFragmentMember;
    if (!method.isConstructor() || !PsiType.VOID.equals(myReturnType)) {
      return;
    }
    final PsiCodeBlock body = method.getBody();
    if (body == null) return;
    final PsiStatement[] psiStatements = body.getStatements();
    if (psiStatements.length > 0 && myElements [0] == psiStatements [0]) {
      myCanBeChainedConstructor = true;
    }
  }

  private void checkLocalClasses(final PsiMethod container) throws PrepareFailedException {
    final List<PsiClass> localClasses = new ArrayList<>();
    container.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override public void visitClass(final PsiClass aClass) {
        localClasses.add(aClass);
      }

      @Override public void visitAnonymousClass(final PsiAnonymousClass aClass) {
        visitElement(aClass);
      }

      @Override public void visitTypeParameter(final PsiTypeParameter classParameter) {
        visitElement(classParameter);
      }
    });
    for(PsiClass localClass: localClasses) {
      final boolean classExtracted = isExtractedElement(localClass);
      final List<PsiElement> extractedReferences = Collections.synchronizedList(new ArrayList<PsiElement>());
      final List<PsiElement> remainingReferences = Collections.synchronizedList(new ArrayList<PsiElement>());
      ReferencesSearch.search(localClass).forEach(psiReference -> {
        final PsiElement element = psiReference.getElement();
        final boolean elementExtracted = isExtractedElement(element);
        if (elementExtracted && !classExtracted) {
          extractedReferences.add(element);
          return false;
        }
        if (!elementExtracted && classExtracted) {
          remainingReferences.add(element);
          return false;
        }
        return true;
      });
      if (!extractedReferences.isEmpty()) {
        throw new PrepareFailedException("Cannot extract method because the selected code fragment uses local classes defined outside of the fragment", extractedReferences.get(0));
      }
      if (!remainingReferences.isEmpty()) {
        throw new PrepareFailedException("Cannot extract method because the selected code fragment defines local classes used outside of the fragment", remainingReferences.get(0));
      }
      if (classExtracted) {
        for (PsiVariable variable : myControlFlowWrapper.getUsedVariables()) {
          if (isDeclaredInside(variable) && !variable.equals(myOutputVariable) && PsiUtil.resolveClassInType(variable.getType()) == localClass) {
            throw new PrepareFailedException("Cannot extract method because the selected code fragment defines variable of local class type used outside of the fragment", variable);
          }
        }
      }
    }
  }

  private boolean isExtractedElement(final PsiElement element) {
    boolean isExtracted = false;
    for(PsiElement psiElement: myElements) {
      if (PsiTreeUtil.isAncestor(psiElement, element, false)) {
        isExtracted = true;
        break;
      }
    }
    return isExtracted;
  }



  private boolean shouldBeStatic() {
    for(PsiElement element: myElements) {
      final PsiExpressionStatement statement = PsiTreeUtil.getParentOfType(element, PsiExpressionStatement.class);
      if (statement != null && JavaHighlightUtil.isSuperOrThisCall(statement, true, true)) {
        return true;
      }
    }
    PsiElement codeFragmentMember = myCodeFragmentMember;
    while (codeFragmentMember != null && PsiTreeUtil.isAncestor(myTargetClass, codeFragmentMember, true)) {
      if (codeFragmentMember instanceof PsiModifierListOwner && ((PsiModifierListOwner)codeFragmentMember).hasModifierProperty(PsiModifier.STATIC)) {
        return true;
      }
      codeFragmentMember = PsiTreeUtil.getParentOfType(codeFragmentMember, PsiModifierListOwner.class, true);
    }
    return false;
  }

  public boolean showDialog(final boolean direct) {
    AbstractExtractDialog dialog = createExtractMethodDialog(direct);
    dialog.show();
    if (!dialog.isOK()) return false;
    apply(dialog);
    return true;
  }

  protected void apply(final AbstractExtractDialog dialog) {
    myMethodName = dialog.getChosenMethodName();
    myVariableDatum = dialog.getChosenParameters();
    myStatic = isStatic() | dialog.isMakeStatic();
    myIsChainedConstructor = dialog.isChainedConstructor();
    myMethodVisibility = dialog.getVisibility();

    final PsiType returnType = dialog.getReturnType();
    if (returnType != null) {
      myReturnType = returnType;
    }
  }

  protected AbstractExtractDialog createExtractMethodDialog(final boolean direct) {
    setDataFromInputVariables();
    myNullness = initNullness();
    myArtificialOutputVariable = PsiType.VOID.equals(myReturnType) ? getArtificialOutputVariable() : null;
    final PsiType returnType = myArtificialOutputVariable != null ? myArtificialOutputVariable.getType() : myReturnType;
    return new ExtractMethodDialog(myProject, myTargetClass, myInputVariables, returnType, getTypeParameterList(),
                                   getThrownExceptions(), isStatic(), isCanBeStatic(), myCanBeChainedConstructor,
                                                         myRefactoringName, myHelpId, myNullness, myElements) {
      protected boolean areTypesDirected() {
        return direct;
      }

      @Override
      protected String[] suggestMethodNames() {
        return suggestInitialMethodName();
      }

      @Override
      protected PsiExpression[] findOccurrences() {
        return ExtractMethodProcessor.this.findOccurrences();
      }

      @Override
      protected boolean isOutputVariable(PsiVariable var) {
        return ExtractMethodProcessor.this.isOutputVariable(var);
      }

      protected boolean isVoidReturn() {
        return myArtificialOutputVariable != null && !(myArtificialOutputVariable instanceof PsiField);
      }

      @Override
      protected void checkMethodConflicts(MultiMap<PsiElement, String> conflicts) {
        super.checkMethodConflicts(conflicts);
        final VariableData[] parameters = getChosenParameters();
        final Map<String, PsiLocalVariable> vars = new HashMap<>();
        for (PsiElement element : myElements) {
          element.accept(new JavaRecursiveElementWalkingVisitor() {
            @Override
            public void visitLocalVariable(PsiLocalVariable variable) {
              super.visitLocalVariable(variable);
              vars.put(variable.getName(), variable);
            }

            @Override
            public void visitClass(PsiClass aClass) {}
          });
        }
        for (VariableData parameter : parameters) {
          final String paramName = parameter.name;
          final PsiLocalVariable variable = vars.get(paramName);
          if (variable != null) {
            conflicts.putValue(variable, "Variable with name " + paramName + " is already defined in the selected scope");
          }
        }
      }
    };
  }

  public void setDataFromInputVariables() {
    final List<VariableData> variables = myInputVariables.getInputVariables();
    myVariableDatum = variables.toArray(new VariableData[variables.size()]);
  }

  public PsiExpression[] findOccurrences() {
    if (myExpression != null) {
      return new PsiExpression[] {myExpression};
    }
    if (myOutputVariable != null) {
      final PsiElement scope = myOutputVariable instanceof PsiLocalVariable
                               ? RefactoringUtil.getVariableScope((PsiLocalVariable)myOutputVariable)
                               : PsiTreeUtil.findCommonParent(myElements);
      return CodeInsightUtil.findReferenceExpressions(scope, myOutputVariable);
    }
    final List<PsiStatement> filter = ContainerUtil.filter(myExitStatements, statement -> statement instanceof PsiReturnStatement && ((PsiReturnStatement)statement).getReturnValue() != null);
    final List<PsiExpression> map = ContainerUtil.map(filter, statement -> ((PsiReturnStatement)statement).getReturnValue());
    return map.toArray(new PsiExpression[map.size()]);
  }

  private Nullness initNullness() {
    if (!PsiUtil.isLanguageLevel5OrHigher(myElements[0]) || PsiUtil.resolveClassInType(myReturnType) == null) return null;
    final NullableNotNullManager manager = NullableNotNullManager.getInstance(myProject);
    final PsiClass nullableAnnotationClass = JavaPsiFacade.getInstance(myProject)
      .findClass(manager.getDefaultNullable(), myElements[0].getResolveScope());
    if (nullableAnnotationClass != null) {
      final PsiElement elementInCopy = myTargetClass.getContainingFile().copy().findElementAt(myTargetClass.getTextOffset());
      final PsiClass classCopy = PsiTreeUtil.getParentOfType(elementInCopy, PsiClass.class);
      if (classCopy == null) {
        return null;
      }
      final PsiMethod emptyMethod = (PsiMethod)classCopy.addAfter(generateEmptyMethod("name", null), classCopy.getLBrace());
      prepareMethodBody(emptyMethod, false);
      if (myNotNullConditionalCheck || myNullConditionalCheck) {
        return Nullness.NULLABLE;
      }
      return DfaUtil.inferMethodNullity(emptyMethod);
    }
    return null;
  }

  protected String[] suggestInitialMethodName() {
    if (StringUtil.isEmpty(myInitialMethodName)) {
      final Set<String> initialMethodNames = new LinkedHashSet<>();
      final JavaCodeStyleManagerImpl codeStyleManager = (JavaCodeStyleManagerImpl)JavaCodeStyleManager.getInstance(myProject);
      if (myExpression != null || !(myReturnType instanceof PsiPrimitiveType)) {
        final String[] names = codeStyleManager.suggestVariableName(VariableKind.FIELD, null, myExpression, myReturnType).names;
        for (String name : names) {
          initialMethodNames.add(codeStyleManager.variableNameToPropertyName(name, VariableKind.FIELD));
        }
      }

      if (myOutputVariable != null) {
        final VariableKind outKind = codeStyleManager.getVariableKind(myOutputVariable);
        final SuggestedNameInfo nameInfo = codeStyleManager
          .suggestVariableName(VariableKind.FIELD, codeStyleManager.variableNameToPropertyName(myOutputVariable.getName(), outKind), null, myOutputVariable.getType());
        for (String name : nameInfo.names) {
          initialMethodNames.add(codeStyleManager.variableNameToPropertyName(name, VariableKind.FIELD));
        }
      }

      final String nameByComment = getNameByComment();
      final PsiField field = JavaPsiFacade.getElementFactory(myProject).createField("fieldNameToReplace", myReturnType instanceof PsiEllipsisType ? ((PsiEllipsisType)myReturnType).toArrayType() : myReturnType);
      final List<String> getters = new ArrayList<>(ContainerUtil.map(initialMethodNames, propertyName -> {
        if (!PsiNameHelper.getInstance(myProject).isIdentifier(propertyName)) {
          LOG.info(propertyName + "; " + myExpression);
          return null;
        }
        field.setName(propertyName);
        return GenerateMembersUtil.suggestGetterName(field);
      }));
      ContainerUtil.addIfNotNull(getters, nameByComment);
      return ArrayUtil.toStringArray(getters);
    }
    return new String[] {myInitialMethodName};
  }

  private String getNameByComment() {
    PsiElement prevSibling = PsiTreeUtil.skipWhitespacesBackward(myElements[0]);
    if (prevSibling instanceof PsiComment && ((PsiComment)prevSibling).getTokenType() == JavaTokenType.END_OF_LINE_COMMENT) {
      final String text = StringUtil.decapitalize(StringUtil.capitalizeWords(prevSibling.getText().trim().substring(2), true)).replaceAll(" ", "");
      if (PsiNameHelper.getInstance(myProject).isIdentifier(text) && text.length() < 20) {
        return text;
      }
    }
    return null;
  }

  public boolean isOutputVariable(PsiVariable var) {
    return ArrayUtil.find(myOutputVariables, var) != -1;
  }

  public boolean showDialog() {
    return showDialog(true);
  }

  @TestOnly
  public void testRun() throws IncorrectOperationException {
    testPrepare();
    testNullness();
    ExtractMethodHandler.run(myProject, myEditor, this);
  }

  @TestOnly
  public void testNullness() {
    myNullness = initNullness();
  }

  @TestOnly
  public void testPrepare() {
    myInputVariables.setFoldingAvailable(myInputVariables.isFoldingSelectedByDefault());
    myMethodName = myInitialMethodName;
    myVariableDatum = new VariableData[myInputVariables.getInputVariables().size()];
    for (int i = 0; i < myInputVariables.getInputVariables().size(); i++) {
      myVariableDatum[i] = myInputVariables.getInputVariables().get(i);
    }
  }

  @TestOnly
  public void testTargetClass(PsiClass targetClass) {
    if (targetClass != null) {
      myTargetClass = targetClass;
      myNeedChangeContext = true;
    }
  }

  @TestOnly
  public void testPrepare(PsiType returnType, boolean makeStatic) throws PrepareFailedException{
    if (makeStatic) {
      if (!isCanBeStatic()) {
        throw new PrepareFailedException("Failed to make static", myElements[0]);
      }
      myInputVariables.setPassFields(true);
      myStatic = true;
    }
    if (PsiType.VOID.equals(myReturnType)) {
      myArtificialOutputVariable = getArtificialOutputVariable();
    }
    testPrepare();
    if (returnType != null) {
      myReturnType = returnType;
    }
  }

  @TestOnly
  public void doNotPassParameter(int i) {
    myVariableDatum[i].passAsParameter = false;
  }

  @TestOnly
  public void changeParamName(int i, String param) {
    myVariableDatum[i].name = param;
  }

  /**
   * Invoked in command and in atomic action
   */
  public void doRefactoring() throws IncorrectOperationException {
    initDuplicates();

    chooseAnchor();

    LogicalPosition pos1;
    if (myEditor != null) {
      int col = myEditor.getCaretModel().getLogicalPosition().column;
      int line = myEditor.getCaretModel().getLogicalPosition().line;
      pos1 = new LogicalPosition(line, col);
      LogicalPosition pos = new LogicalPosition(0, 0);
      myEditor.getCaretModel().moveToLogicalPosition(pos);
    } else {
      pos1 = null;
    }

    final SearchScope processConflictsScope = myMethodVisibility.equals(PsiModifier.PRIVATE) ?
                                        new LocalSearchScope(myTargetClass) :
                                        GlobalSearchScope.projectScope(myProject);

    final Map<PsiMethodCallExpression, PsiMethod> overloadsResolveMap = new HashMap<>();
    final Runnable collectOverloads = () -> ApplicationManager.getApplication().runReadAction(() -> {
      Map<PsiMethodCallExpression, PsiMethod> overloads =
        ExtractMethodUtil.encodeOverloadTargets(myTargetClass, processConflictsScope, myMethodName, myCodeFragmentMember);
      overloadsResolveMap.putAll(overloads);
    });
    final Runnable extract = () -> {
      doExtract();
      ExtractMethodUtil.decodeOverloadTargets(overloadsResolveMap, myExtractedMethod, myCodeFragmentMember);
    };
    if (ApplicationManager.getApplication().isWriteAccessAllowed()) {
      collectOverloads.run();
      extract.run();
    } else {
      if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(collectOverloads, "Collect overloads...", true, myProject)) return;
      ApplicationManager.getApplication().runWriteAction(extract);
    }

    if (myEditor != null) {
      myEditor.getCaretModel().moveToLogicalPosition(pos1);
      int offset = myMethodCall.getMethodExpression().getTextRange().getStartOffset();
      myEditor.getCaretModel().moveToOffset(offset);
      myEditor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
      myEditor.getSelectionModel().removeSelection();
    }
  }

  @Nullable
  private DuplicatesFinder initDuplicates() {
    List<PsiElement> elements = new ArrayList<>();
    for (PsiElement element : myElements) {
      if (!(element instanceof PsiWhiteSpace || element instanceof PsiComment)) {
        elements.add(element);
      }
    }

    if (myExpression != null) {
      DuplicatesFinder finder = new DuplicatesFinder(PsiUtilCore.toPsiElementArray(elements), myInputVariables.copy(),
                                                     new ArrayList<>());
      myDuplicates = finder.findDuplicates(myTargetClass);
      return finder;
    }
    else if (elements.size() > 0){
      DuplicatesFinder myDuplicatesFinder = new DuplicatesFinder(PsiUtilCore.toPsiElementArray(elements), myInputVariables.copy(),
                                                myOutputVariable != null ? new VariableReturnValue(myOutputVariable) : null,
                                                Arrays.asList(myOutputVariables));
      myDuplicates = myDuplicatesFinder.findDuplicates(myTargetClass);
      return myDuplicatesFinder;
    } else {
      myDuplicates = new ArrayList<>();
    }
    return null;
  }

  public void doExtract() throws IncorrectOperationException {

    PsiMethod newMethod = generateEmptyMethod();

    myExpression = myInputVariables.replaceWrappedReferences(myElements, myExpression);
    renameInputVariables();

    LOG.assertTrue(myElements[0].isValid());

    PsiCodeBlock body = newMethod.getBody();
    myMethodCall = generateMethodCall(null, true);

    LOG.assertTrue(myElements[0].isValid());

    final PsiStatement exitStatementCopy = prepareMethodBody(newMethod, true);

    if (myExpression == null) {
      if (myNeedChangeContext && isNeedToChangeCallContext()) {
        for (PsiElement element : myElements) {
          ChangeContextUtil.encodeContextInfo(element, false);
        }
      }

      if (myNullConditionalCheck) {
        final String varName = myOutputVariable.getName();
        if (isDeclaredInside(myOutputVariable)) {
          declareVariableAtMethodCallLocation(varName);
        }
        else {
          PsiExpressionStatement assignmentExpression =
            (PsiExpressionStatement)myElementFactory.createStatementFromText(varName + "=x;", null);
          assignmentExpression = (PsiExpressionStatement)addToMethodCallLocation(assignmentExpression);
          myMethodCall =
            (PsiMethodCallExpression)((PsiAssignmentExpression)assignmentExpression.getExpression()).getRExpression().replace(myMethodCall);
        }
        declareNecessaryVariablesAfterCall(myOutputVariable);
        PsiIfStatement ifStatement;
        if (myHasReturnStatementOutput) {
          ifStatement = (PsiIfStatement)myElementFactory.createStatementFromText("if (" + varName + "==null) return null;", null);
        }
        else if (myGenerateConditionalExit) {
          if (myFirstExitStatementCopy instanceof PsiReturnStatement && ((PsiReturnStatement)myFirstExitStatementCopy).getReturnValue() != null) {
            ifStatement = (PsiIfStatement)myElementFactory.createStatementFromText("if (" + varName + "==null) return null;", null);
          }
          else {
            ifStatement = (PsiIfStatement)myElementFactory.createStatementFromText("if (" + varName + "==null) " + myFirstExitStatementCopy.getText(), null);
          }
        }
        else {
          ifStatement = (PsiIfStatement)myElementFactory.createStatementFromText("if (" + varName + "==null) return;", null);
        }
        ifStatement = (PsiIfStatement)addToMethodCallLocation(ifStatement);
        CodeStyleManager.getInstance(myProject).reformat(ifStatement);
      }
      else if (myNotNullConditionalCheck) {
        String varName = myOutputVariable != null ? myOutputVariable.getName() : "x";
        varName = declareVariableAtMethodCallLocation(varName, myReturnType instanceof PsiPrimitiveType ? ((PsiPrimitiveType)myReturnType).getBoxedType(myCodeFragmentMember) : myReturnType);
        addToMethodCallLocation(myElementFactory.createStatementFromText("if (" + varName + " != null) return " + varName + ";", null));
      }
      else if (myGenerateConditionalExit) {
        PsiIfStatement ifStatement = (PsiIfStatement)myElementFactory.createStatementFromText("if (a) b;", null);
        ifStatement = (PsiIfStatement)addToMethodCallLocation(ifStatement);
        myMethodCall = (PsiMethodCallExpression)ifStatement.getCondition().replace(myMethodCall);
        myFirstExitStatementCopy = (PsiStatement)ifStatement.getThenBranch().replace(myFirstExitStatementCopy);
        CodeStyleManager.getInstance(myProject).reformat(ifStatement);
      }
      else if (myOutputVariable != null || isArtificialOutputUsed()) {
        boolean toDeclare = isArtificialOutputUsed() ? !(myArtificialOutputVariable instanceof PsiField) : isDeclaredInside(myOutputVariable);
        String name = isArtificialOutputUsed() ? myArtificialOutputVariable.getName() : myOutputVariable.getName();
        if (!toDeclare) {
          PsiExpressionStatement statement = (PsiExpressionStatement)myElementFactory.createStatementFromText(name + "=x;", null);
          statement = (PsiExpressionStatement)myStyleManager.reformat(statement);
          statement = (PsiExpressionStatement)addToMethodCallLocation(statement);
          PsiAssignmentExpression assignment = (PsiAssignmentExpression)statement.getExpression();
          myMethodCall = (PsiMethodCallExpression)assignment.getRExpression().replace(myMethodCall);
        }
        else {
          declareVariableAtMethodCallLocation(name);
        }
      }
      else if (myHasReturnStatementOutput) {
        PsiStatement statement = myElementFactory.createStatementFromText("return x;", null);
        statement = (PsiStatement)addToMethodCallLocation(statement);
        myMethodCall = (PsiMethodCallExpression)((PsiReturnStatement)statement).getReturnValue().replace(myMethodCall);
      }
      else {
        PsiStatement statement = myElementFactory.createStatementFromText("x();", null);
        statement = (PsiStatement)addToMethodCallLocation(statement);
        myMethodCall = (PsiMethodCallExpression)((PsiExpressionStatement)statement).getExpression().replace(myMethodCall);
      }
      if (myHasReturnStatement && !myHasReturnStatementOutput && !hasNormalExit()) {
        PsiStatement statement = myElementFactory.createStatementFromText("return;", null);
        addToMethodCallLocation(statement);
      }
      else if (!myGenerateConditionalExit && exitStatementCopy != null) {
        addToMethodCallLocation(exitStatementCopy);
      }

      if (!myNullConditionalCheck && !myNotNullConditionalCheck) {
        declareNecessaryVariablesAfterCall(myOutputVariable);
      }

      deleteExtracted();
    }
    else {
      PsiExpression expression2Replace = expressionToReplace(myExpression);
      myExpression = (PsiExpression)IntroduceVariableBase.replace(expression2Replace, myMethodCall, myProject);
      myMethodCall = PsiTreeUtil.getParentOfType(myExpression.findElementAt(myExpression.getText().indexOf(myMethodCall.getText())), PsiMethodCallExpression.class);
      declareNecessaryVariablesAfterCall(myOutputVariable);
    }

    if (myAnchor instanceof PsiField) {
      ((PsiField)myAnchor).normalizeDeclaration();
    }

    adjustFinalParameters(newMethod);
    int i = 0;
    for (VariableData data : myVariableDatum) {
      if (!data.passAsParameter) continue;
      final PsiParameter psiParameter = newMethod.getParameterList().getParameters()[i++];
      final PsiType paramType = psiParameter.getType();
      for (PsiReference reference : ReferencesSearch.search(psiParameter, new LocalSearchScope(body))){
        final PsiElement element = reference.getElement();
        if (element != null) {
          final PsiElement parent = element.getParent();
          if (parent instanceof PsiTypeCastExpression) {
            final PsiTypeCastExpression typeCastExpression = (PsiTypeCastExpression)parent;
            final PsiTypeElement castType = typeCastExpression.getCastType();
            if (castType != null && Comparing.equal(castType.getType(), paramType)) {
              RedundantCastUtil.removeCast(typeCastExpression);
            }
          }
        }
      }
    }

    if (myNullness != null &&
        PsiUtil.resolveClassInType(newMethod.getReturnType()) != null &&
        PropertiesComponent.getInstance(myProject).getBoolean(ExtractMethodDialog.EXTRACT_METHOD_GENERATE_ANNOTATIONS, true)) {
      final NullableNotNullManager notNullManager = NullableNotNullManager.getInstance(myProject);
      AddNullableNotNullAnnotationFix annotationFix;
      switch (myNullness) {
        case NOT_NULL:
          annotationFix = new AddNullableNotNullAnnotationFix(notNullManager.getDefaultNotNull(), newMethod);
          break;
        case NULLABLE:
          annotationFix = new AddNullableNotNullAnnotationFix(notNullManager.getDefaultNullable(), newMethod);
          break;
        default:
          annotationFix = null;
      }
      if (annotationFix != null) {
        annotationFix.invoke(myProject, myTargetClass.getContainingFile(), newMethod, newMethod);
      }
    }

    myExtractedMethod = addExtractedMethod(newMethod);
    if (isNeedToChangeCallContext() && myNeedChangeContext) {
      ChangeContextUtil.decodeContextInfo(myExtractedMethod, myTargetClass, RefactoringChangeUtil.createThisExpression(myManager, null));
      if (myMethodCall.resolveMethod() != myExtractedMethod) {
        final PsiReferenceExpression methodExpression = myMethodCall.getMethodExpression();
        RefactoringChangeUtil.qualifyReference(methodExpression, myExtractedMethod, PsiUtil.getEnclosingStaticElement(methodExpression, myTargetClass) != null ? myTargetClass : null);
      }
    }
  }

  protected PsiExpression expressionToReplace(PsiExpression expression) {
    if (expression instanceof PsiAssignmentExpression) {
      return ((PsiAssignmentExpression)expression).getRExpression();
    }
    return expression;
  }

  protected PsiMethod addExtractedMethod(PsiMethod newMethod) {
    return (PsiMethod)myTargetClass.addAfter(newMethod, myAnchor);
  }

  @Nullable
  private PsiStatement prepareMethodBody(PsiMethod newMethod, boolean doExtract) {
    PsiCodeBlock body = newMethod.getBody();
    if (myExpression != null) {
      declareNecessaryVariablesInsideBody(body);
      if (myHasExpressionOutput) {
        PsiReturnStatement returnStatement = (PsiReturnStatement)myElementFactory.createStatementFromText("return x;", null);
        final PsiExpression returnValue = RefactoringUtil.convertInitializerToNormalExpression(myExpression, myForcedReturnType);
        returnStatement.getReturnValue().replace(returnValue);
        body.add(returnStatement);
      }
      else {
        PsiExpressionStatement statement = (PsiExpressionStatement)myElementFactory.createStatementFromText("x;", null);
        statement.getExpression().replace(myExpression);
        body.add(statement);
      }
      return null;
    }

    final boolean hasNormalExit = hasNormalExit();
    String outVariableName = myOutputVariable != null ? getNewVariableName(myOutputVariable) : null;
    PsiReturnStatement returnStatement;
    if (myNullConditionalCheck) {
      returnStatement = (PsiReturnStatement)myElementFactory.createStatementFromText("return null;", null);
    } else if (myOutputVariable != null) {
      returnStatement = (PsiReturnStatement)myElementFactory.createStatementFromText("return " + outVariableName + ";", null);
    }
    else if (myGenerateConditionalExit) {
      returnStatement = (PsiReturnStatement)myElementFactory.createStatementFromText("return true;", null);
    }
    else {
      returnStatement = (PsiReturnStatement)myElementFactory.createStatementFromText("return;", null);
    }

    PsiStatement exitStatementCopy = !doExtract || myNotNullConditionalCheck ? null : myControlFlowWrapper.getExitStatementCopy(returnStatement, myElements);


    declareNecessaryVariablesInsideBody(body);

    body.addRange(myElements[0], myElements[myElements.length - 1]);
    if (myNullConditionalCheck) {
      body.add(myElementFactory.createStatementFromText("return " + myOutputVariable.getName() + ";", null));
    }
    else if (myNotNullConditionalCheck) {
      body.add(myElementFactory.createStatementFromText("return null;", null));
    }
    else if (myGenerateConditionalExit) {
      body.add(myElementFactory.createStatementFromText("return false;", null));
    }
    else if (!myHasReturnStatement && hasNormalExit && myOutputVariable != null) {
      final PsiReturnStatement insertedReturnStatement = (PsiReturnStatement)body.add(returnStatement);
      if (myOutputVariables.length == 1) {
        final PsiExpression returnValue = insertedReturnStatement.getReturnValue();
        if (returnValue instanceof PsiReferenceExpression) {
          final PsiElement resolved = ((PsiReferenceExpression)returnValue).resolve();
          if (resolved instanceof PsiLocalVariable && Comparing.strEqual(((PsiVariable)resolved).getName(), outVariableName)) {
            final PsiStatement statement = PsiTreeUtil.getPrevSiblingOfType(insertedReturnStatement, PsiStatement.class);
            if (statement instanceof PsiDeclarationStatement) {
              final PsiElement[] declaredElements = ((PsiDeclarationStatement)statement).getDeclaredElements();
              if (ArrayUtil.find(declaredElements, resolved) != -1) {
                InlineUtil.inlineVariable((PsiVariable)resolved, ((PsiVariable)resolved).getInitializer(),
                                          (PsiReferenceExpression)returnValue);
                resolved.delete();
              }
            }
          }
        }
      }
    }
    else if (isArtificialOutputUsed()) {
      body.add(myElementFactory.createStatementFromText("return " + myArtificialOutputVariable.getName() + ";", null));
    }
    return exitStatementCopy;
  }

  private boolean isArtificialOutputUsed() {
    return myArtificialOutputVariable != null && !PsiType.VOID.equals(myReturnType) && !myIsChainedConstructor;
  }

  private boolean hasNormalExit() {
    try {
      PsiCodeBlock block = JavaPsiFacade.getElementFactory(myProject).createCodeBlock();
      block.addRange(myElements[0], myElements[myElements.length - 1]);
      ControlFlow flow = ControlFlowFactory.getInstance(myProject).getControlFlow(block, new LocalsControlFlowPolicy(block), false, false);
      return ControlFlowUtil.canCompleteNormally(flow, 0, flow.getSize());
    }
    catch (AnalysisCanceledException e) {
      //check incomplete code as simple as possible
      PsiElement lastElement = myElements[myElements.length - 1];
      if (!(lastElement instanceof PsiReturnStatement || lastElement instanceof PsiBreakStatement ||
            lastElement instanceof PsiContinueStatement)) {
        return true;
      }
      return false;
    }
  }

  protected boolean isNeedToChangeCallContext() {
    return true;
  }

  private void declareVariableAtMethodCallLocation(String name) {
    declareVariableAtMethodCallLocation(name, myReturnType);
  }

  private String declareVariableAtMethodCallLocation(String name, PsiType type) {
    if (myControlFlowWrapper.getOutputVariables(false).length == 0) {
      PsiElement lastStatement = PsiTreeUtil.getNextSiblingOfType(myEnclosingBlockStatement != null ? myEnclosingBlockStatement : myElements[myElements.length - 1], PsiStatement.class);
      if (lastStatement != null) {
        name = JavaCodeStyleManager.getInstance(myProject).suggestUniqueVariableName(name, lastStatement, true);
      }
    }
    PsiDeclarationStatement statement = myElementFactory.createVariableDeclarationStatement(name, type, myMethodCall);
    statement =
      (PsiDeclarationStatement)JavaCodeStyleManager.getInstance(myProject).shortenClassReferences(addToMethodCallLocation(statement));
    PsiVariable var = (PsiVariable)statement.getDeclaredElements()[0];
    myMethodCall = (PsiMethodCallExpression)var.getInitializer();
    if (myOutputVariable != null) {
      var.getModifierList().replace(myOutputVariable.getModifierList());
    }
    return name;
  }

  private void adjustFinalParameters(final PsiMethod method) throws IncorrectOperationException {
    final IncorrectOperationException[] exc = new IncorrectOperationException[1];
    exc[0] = null;
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    if (parameters.length > 0) {
      if (CodeStyleSettingsManager.getSettings(myProject).getCustomSettings(JavaCodeStyleSettings.class).GENERATE_FINAL_PARAMETERS) {
        method.accept(new JavaRecursiveElementVisitor() {

          @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
            final PsiElement resolved = expression.resolve();
            if (resolved != null) {
              final int index = ArrayUtil.find(parameters, resolved);
              if (index >= 0) {
                final PsiParameter param = parameters[index];
                if (param.hasModifierProperty(PsiModifier.FINAL) && PsiUtil.isAccessedForWriting(expression)) {
                  try {
                    PsiUtil.setModifierProperty(param, PsiModifier.FINAL, false);
                  }
                  catch (IncorrectOperationException e) {
                    exc[0] = e;
                  }
                }
              }
            }
            super.visitReferenceExpression(expression);
          }
        });
      }
      else if (!PsiUtil.isLanguageLevel8OrHigher(myTargetClass)){
        method.accept(new JavaRecursiveElementVisitor() {
          @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
            final PsiElement resolved = expression.resolve();
            final int index = ArrayUtil.find(parameters, resolved);
            if (index >= 0) {
              final PsiParameter param = parameters[index];
              if (!param.hasModifierProperty(PsiModifier.FINAL) && RefactoringUtil.isInsideAnonymousOrLocal(expression, method)) {
                try {
                  PsiUtil.setModifierProperty(param, PsiModifier.FINAL, true);
                }
                catch (IncorrectOperationException e) {
                  exc[0] = e;
                }
              }
            }
            super.visitReferenceExpression(expression);
          }
        });
      }
      if (exc[0] != null) {
        throw exc[0];
      }
    }
  }

  public List<Match> getDuplicates() {
    if (myIsChainedConstructor) {
      return filterChainedConstructorDuplicates(myDuplicates);
    }
    return myDuplicates;
  }

  private static List<Match> filterChainedConstructorDuplicates(final List<Match> duplicates) {
    List<Match> result = new ArrayList<>();
    for(Match duplicate: duplicates) {
      final PsiElement matchStart = duplicate.getMatchStart();
      final PsiMethod method = PsiTreeUtil.getParentOfType(matchStart, PsiMethod.class);
      if (method != null && method.isConstructor()) {
        final PsiCodeBlock body = method.getBody();
        if (body != null) {
          final PsiStatement[] psiStatements = body.getStatements();
          if (psiStatements.length > 0 && matchStart == psiStatements [0]) {
            result.add(duplicate);
          }
        }
      }
    }
    return result;
  }

  @Override
  public void prepareSignature(Match match) {
    MatchUtil.changeSignature(match, myExtractedMethod);
  }

  public PsiElement processMatch(Match match) throws IncorrectOperationException {
    if (RefactoringUtil.isInStaticContext(match.getMatchStart(), myExtractedMethod.getContainingClass())) {
      PsiUtil.setModifierProperty(myExtractedMethod, PsiModifier.STATIC, true);
    }
    final PsiMethodCallExpression methodCallExpression = generateMethodCall(match.getInstanceExpression(), false);

    ArrayList<VariableData> datas = new ArrayList<>();
    for (final VariableData variableData : myVariableDatum) {
      if (variableData.passAsParameter) {
        datas.add(variableData);
      }
    }
    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(myProject);
    for (VariableData data : datas) {
      final List<PsiElement> parameterValue = match.getParameterValues(data.variable);
      if (parameterValue != null) {
        for (PsiElement val : parameterValue) {
          if (val instanceof PsiExpression) {
            final PsiType exprType = ((PsiExpression)val).getType();
            if (exprType != null && !TypeConversionUtil.isAssignable(data.type, exprType)) {
              final PsiTypeCastExpression cast = (PsiTypeCastExpression)elementFactory.createExpressionFromText("(A)a", val);
              cast.getCastType().replace(elementFactory.createTypeElement(data.type));
              cast.getOperand().replace(val.copy());
              val = cast;
            }
          }
          methodCallExpression.getArgumentList().add(val);
        }
      } else {
        methodCallExpression.getArgumentList().add(myElementFactory.createExpressionFromText(data.variable.getName(), methodCallExpression));
      }
    }
    return match.replace(myExtractedMethod, methodCallExpression, myOutputVariable);
  }

  protected void deleteExtracted() throws IncorrectOperationException {
    if (myEnclosingBlockStatement == null) {
      myElements[0].getParent().deleteChildRange(myElements[0], myElements[myElements.length - 1]);
    }
    else {
      myEnclosingBlockStatement.delete();
    }
  }

  protected PsiElement addToMethodCallLocation(PsiStatement statement) throws IncorrectOperationException {
    if (myEnclosingBlockStatement == null) {
      PsiElement containingStatement = myElements[0] instanceof PsiComment ? myElements[0] : PsiTreeUtil.getParentOfType(myExpression != null ? myExpression : myElements[0], PsiStatement.class, false);
      if (containingStatement == null) {
        containingStatement = PsiTreeUtil.getParentOfType(myExpression != null ? myExpression : myElements[0], PsiComment.class, false);
      }

      return containingStatement.getParent().addBefore(statement, containingStatement);
    }
    else {
      return myEnclosingBlockStatement.getParent().addBefore(statement, myEnclosingBlockStatement);
    }
  }

  private void renameInputVariables() throws IncorrectOperationException {
    //when multiple input variables should have the same name, unique names are generated
    //without reverse, the second rename would rename variable without a prefix into second one though it was already renamed
    LocalSearchScope localSearchScope = null;
    for (int i = myVariableDatum.length - 1; i >= 0;  i--) {
      VariableData data = myVariableDatum[i];
      PsiVariable variable = data.variable;
      if (!data.name.equals(variable.getName()) || variable instanceof PsiField) {
        if (localSearchScope == null) {
          localSearchScope = new LocalSearchScope(myElements);
        }

        for (PsiReference reference : ReferencesSearch.search(variable, localSearchScope)) {
          reference.handleElementRename(data.name);

          final PsiElement element = reference.getElement();
          if (element instanceof PsiReferenceExpression) {
            final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)element;
            final PsiExpression qualifierExpression = referenceExpression.getQualifierExpression();
            if (qualifierExpression instanceof PsiThisExpression || qualifierExpression instanceof PsiSuperExpression) {
              referenceExpression.setQualifierExpression(null);
            }
          }
        }
      }
    }
  }

  public PsiClass getTargetClass() {
    return myTargetClass;
  }

  public PsiType getReturnType() {
    return myReturnType;
  }

  private PsiMethod generateEmptyMethod() throws IncorrectOperationException {
    return generateEmptyMethod(myMethodName, null);
  }

  public PsiMethod generateEmptyMethod(String methodName, PsiElement context) throws IncorrectOperationException {
    PsiMethod newMethod;
    if (myIsChainedConstructor) {
      newMethod = myElementFactory.createConstructor();
    }
    else {
      newMethod = context != null ? myElementFactory.createMethod(methodName, myReturnType, context)
                                  : myElementFactory.createMethod(methodName, myReturnType);
      PsiUtil.setModifierProperty(newMethod, PsiModifier.STATIC, isStatic());
    }
    PsiUtil.setModifierProperty(newMethod, myMethodVisibility, true);
    if (getTypeParameterList() != null) {
      newMethod.getTypeParameterList().replace(getTypeParameterList());
    }
    PsiCodeBlock body = newMethod.getBody();
    LOG.assertTrue(body != null);

    boolean isFinal = CodeStyleSettingsManager.getSettings(myProject).getCustomSettings(JavaCodeStyleSettings.class).GENERATE_FINAL_PARAMETERS;
    PsiParameterList list = newMethod.getParameterList();
    for (VariableData data : myVariableDatum) {
      if (data.passAsParameter) {
        PsiParameter parm = myElementFactory.createParameter(data.name, data.type);
        copyParamAnnotations(parm);
        if (isFinal) {
          PsiUtil.setModifierProperty(parm, PsiModifier.FINAL, true);
        }
        list.add(parm);
      }
      else if (defineVariablesForUnselectedParameters()){
        @NonNls StringBuilder buffer = new StringBuilder();
        if (isFinal) {
          buffer.append("final ");
        }
        buffer.append("int ");
        buffer.append(data.name);
        buffer.append("=;");
        String text = buffer.toString();

        PsiDeclarationStatement declaration = (PsiDeclarationStatement)myElementFactory.createStatementFromText(text, null);
        declaration = (PsiDeclarationStatement)myStyleManager.reformat(declaration);
        final PsiTypeElement typeElement = myElementFactory.createTypeElement(data.type);
        ((PsiVariable)declaration.getDeclaredElements()[0]).getTypeElement().replace(typeElement);
        body.add(declaration);
      }
    }

    PsiReferenceList throwsList = newMethod.getThrowsList();
    for (PsiClassType exception : getThrownExceptions()) {
      throwsList.add(JavaPsiFacade.getInstance(myManager.getProject()).getElementFactory().createReferenceElementByType(exception));
    }

    if (myTargetClass.isInterface() && PsiUtil.isLanguageLevel8OrHigher(myTargetClass)) {
      final PsiMethod containingMethod = PsiTreeUtil.getParentOfType(myCodeFragmentMember, PsiMethod.class, false);
      if (containingMethod != null && containingMethod.hasModifierProperty(PsiModifier.DEFAULT)) {
        PsiUtil.setModifierProperty(newMethod, PsiModifier.DEFAULT, true);
      }
    }
    return (PsiMethod)myStyleManager.reformat(newMethod);
  }

  protected boolean defineVariablesForUnselectedParameters() {
    return true;
  }

  private void copyParamAnnotations(PsiParameter parm) {
    final PsiVariable variable = PsiResolveHelper.SERVICE.getInstance(myProject).resolveReferencedVariable(parm.getName(), myElements[0]);
    if (variable instanceof PsiParameter) {
      final PsiModifierList modifierList = variable.getModifierList();
      if (modifierList != null) {
        PsiModifierList parmModifierList = parm.getModifierList();
        LOG.assertTrue(parmModifierList != null);
        GenerateMembersUtil.copyAnnotations(modifierList, parmModifierList, SuppressWarnings.class.getName());

        updateNullabilityAnnotation(parm, variable);
      }
    }
  }

  private void updateNullabilityAnnotation(@NotNull PsiParameter parm, @NotNull PsiVariable variable) {
    final NullableNotNullManager nullabilityManager = NullableNotNullManager.getInstance(myProject);
    final List<String> notNullAnnotations = nullabilityManager.getNotNulls();
    final List<String> nullableAnnotations = nullabilityManager.getNullables();

    if (AnnotationUtil.isAnnotated(variable, nullableAnnotations) ||
        AnnotationUtil.isAnnotated(variable, notNullAnnotations) ||
        PropertiesComponent.getInstance(myProject).getBoolean(ExtractMethodDialog.EXTRACT_METHOD_GENERATE_ANNOTATIONS, false)) {

      final Boolean isNotNull = isNotNullAt(variable, myElements[0]);
      if (isNotNull != null) {
        final List<String> toKeep = isNotNull ? notNullAnnotations : nullableAnnotations;
        final String[] toRemove = (!isNotNull ? notNullAnnotations : nullableAnnotations).toArray(ArrayUtil.EMPTY_STRING_ARRAY);

        AddAnnotationPsiFix.removePhysicalAnnotations(parm, toRemove);
        if (!AnnotationUtil.isAnnotated(parm, toKeep)) {
          final String toAdd = isNotNull ? nullabilityManager.getDefaultNotNull() : nullabilityManager.getDefaultNullable();
          final PsiAnnotation added =
            AddAnnotationPsiFix.addPhysicalAnnotation(toAdd, PsiNameValuePair.EMPTY_ARRAY, parm.getModifierList());
          JavaCodeStyleManager.getInstance(myProject).shortenClassReferences(added);
        }
      }
    }
  }

  @Nullable
  private static PsiElement getSurroundingMethodOrLambdaBody(@NotNull PsiVariable variable) {
    if (variable instanceof PsiLocalVariable || variable instanceof PsiParameter) {
      final PsiElement methodOrLambda = PsiTreeUtil.getParentOfType(variable, PsiMethod.class, PsiLambdaExpression.class);
      if (methodOrLambda instanceof PsiMethod) {
        return ((PsiMethod)methodOrLambda).getBody();
      }
      else if (methodOrLambda instanceof PsiLambdaExpression) {
        return ((PsiLambdaExpression)methodOrLambda).getBody();
      }
    }
    return null;
  }

  @Nullable
  private static Boolean isNotNullAt(@NotNull PsiVariable variable, PsiElement startElement) {
    final PsiElement methodOrLambdaBody = getSurroundingMethodOrLambdaBody(variable);
    if (methodOrLambdaBody instanceof PsiCodeBlock) {
      final Set<PsiReferenceExpression> firstReadUsages = findFirstReadUsagesAt(variable, startElement);
      return firstReadUsages != null &&
             firstReadUsages.stream()
               .map(firstReadUsage -> DfaUtil.checkNullness(variable, firstReadUsage, methodOrLambdaBody))
               .allMatch(nullness -> nullness == Nullness.NOT_NULL);
    }
    return null;
  }

  @Nullable
  private static Set<PsiReferenceExpression> findFirstReadUsagesAt(@NotNull PsiVariable variable, PsiElement startElement) {
    final PsiCodeBlock closestCodeBlock = PsiTreeUtil.getParentOfType(startElement, PsiCodeBlock.class);
    if (closestCodeBlock != null) {
      try {
        final ControlFlow controlFlow = ControlFlowFactory.getInstance(closestCodeBlock.getProject())
          .getControlFlow(closestCodeBlock, AllVariablesControlFlowPolicy.getInstance(), false, false);

        final int startOffset = controlFlow.getStartOffset(startElement);
        final List<PsiReferenceExpression> readBeforeWrite = ControlFlowUtil.getReadBeforeWrite(controlFlow, startOffset);
        final Set<PsiReferenceExpression> result = StreamEx.of(readBeforeWrite)
          .filter(referenceExpression -> referenceExpression.isReferenceTo(variable))
          .toSet();
        return !result.isEmpty() ? result : null;
      }
      catch (AnalysisCanceledException e) {
        return null;
      }
    }
    return null;
  }

  @NotNull
  protected PsiMethodCallExpression generateMethodCall(PsiExpression instanceQualifier, final boolean generateArgs) throws IncorrectOperationException {
    @NonNls StringBuilder buffer = new StringBuilder();

    final boolean skipInstanceQualifier;
    if (myIsChainedConstructor) {
      skipInstanceQualifier = true;
      buffer.append(PsiKeyword.THIS);
    }
    else {
      skipInstanceQualifier = instanceQualifier == null || instanceQualifier instanceof PsiThisExpression;
      if (skipInstanceQualifier) {
        if (isNeedToChangeCallContext() && myNeedChangeContext) {
          boolean needsThisQualifier = false;
          PsiElement parent = myCodeFragmentMember;
          while (!myTargetClass.equals(parent)) {
            if (parent instanceof PsiMethod) {
              String methodName = ((PsiMethod)parent).getName();
              if (methodName.equals(myMethodName)) {
                needsThisQualifier = true;
                break;
              }
            }
            parent = parent.getParent();
          }
          if (needsThisQualifier) {
            buffer.append(myTargetClass.getName());
            buffer.append(".this.");
          }
        }
      }
      else {
        buffer.append("qqq.");
      }

      buffer.append(myMethodName);
    }
    buffer.append("(");
    if (generateArgs) {
      int count = 0;
      for (VariableData data : myVariableDatum) {
        if (data.passAsParameter) {
          if (count > 0) {
            buffer.append(",");
          }
          myInputVariables.appendCallArguments(data, buffer);
          count++;
        }
      }
    }
    buffer.append(")");
    String text = buffer.toString();

    PsiMethodCallExpression expr = (PsiMethodCallExpression)myElementFactory.createExpressionFromText(text, null);
    expr = (PsiMethodCallExpression)myStyleManager.reformat(expr);
    if (!skipInstanceQualifier) {
      PsiExpression qualifierExpression = expr.getMethodExpression().getQualifierExpression();
      LOG.assertTrue(qualifierExpression != null);
      qualifierExpression.replace(instanceQualifier);
    }
    return (PsiMethodCallExpression)JavaCodeStyleManager.getInstance(myProject).shortenClassReferences(expr);
  }

  private boolean chooseTargetClass(PsiElement codeFragment, final Pass<ExtractMethodProcessor> extractPass) throws PrepareFailedException {
    final List<PsiVariable> inputVariables = myControlFlowWrapper.getInputVariables(codeFragment, myElements, myOutputVariables);

    myNeedChangeContext = false;
    myTargetClass = myCodeFragmentMember instanceof PsiMember
                    ? ((PsiMember)myCodeFragmentMember).getContainingClass()
                    : PsiTreeUtil.getParentOfType(myCodeFragmentMember, PsiClass.class);
    if (myTargetClass == null) {
      LOG.error(myElements[0].getContainingFile());
    }
    if (!shouldAcceptCurrentTarget(extractPass, myTargetClass)) {

      final LinkedHashMap<PsiClass, List<PsiVariable>> classes = new LinkedHashMap<>();
      final PsiElementProcessor<PsiClass> processor = new PsiElementProcessor<PsiClass>() {
        @Override
        public boolean execute(@NotNull PsiClass selectedClass) {
          AnonymousTargetClassPreselectionUtil.rememberSelection(selectedClass, myTargetClass);
          final List<PsiVariable> array = classes.get(selectedClass);
          myNeedChangeContext = myTargetClass != selectedClass;
          myTargetClass = selectedClass;
          if (array != null) {
            for (PsiVariable variable : array) {
              if (!inputVariables.contains(variable)) {
                inputVariables.addAll(array);
              }
            }
          }
          try {
            return applyChosenClassAndExtract(inputVariables, extractPass);
          }
          catch (PrepareFailedException e) {
            if (myShowErrorDialogs) {
              CommonRefactoringUtil
                .showErrorHint(myProject, myEditor, e.getMessage(), ExtractMethodHandler.REFACTORING_NAME, HelpID.EXTRACT_METHOD);
              ExtractMethodHandler.highlightPrepareError(e, e.getFile(), myEditor, myProject);
            }
            return false;
          }
        }
      };

      classes.put(myTargetClass, null);
      PsiElement target = myTargetClass.getParent();
      PsiElement targetMember = myTargetClass;
      while (true) {
        if (target instanceof PsiFile) break;
        if (target instanceof PsiClass) {
          boolean success = true;
          final List<PsiVariable> array = new ArrayList<>();
          for (PsiElement el : myElements) {
            if (!ControlFlowUtil.collectOuterLocals(array, el, myCodeFragmentMember, targetMember)) {
              success = false;
              break;
            }
          }
          if (success) {
            classes.put((PsiClass)target, array);
            if (shouldAcceptCurrentTarget(extractPass, target)) {
              return processor.execute((PsiClass)target);
            }
          }
        }
        targetMember = target;
        target = target.getParent();
      }

      if (classes.size() > 1) {
        final PsiClass[] psiClasses = classes.keySet().toArray(new PsiClass[classes.size()]);
        final PsiClass preselection = AnonymousTargetClassPreselectionUtil.getPreselection(classes.keySet(), psiClasses[0]);
        NavigationUtil.getPsiElementPopup(psiClasses, new PsiClassListCellRenderer(), "Choose Destination Class", processor, preselection)
          .showInBestPositionFor(myEditor);
        return true;
      }
    }

    return applyChosenClassAndExtract(inputVariables, extractPass);
  }

  private void declareNecessaryVariablesInsideBody(PsiCodeBlock body) throws IncorrectOperationException {
    List<PsiVariable> usedVariables = myControlFlowWrapper.getUsedVariablesInBody(ControlFlowUtil.findCodeFragment(myElements[0]), myOutputVariables);
    for (PsiVariable variable : usedVariables) {
      boolean toDeclare = !isDeclaredInside(variable) && myInputVariables.toDeclareInsideBody(variable);
      if (toDeclare) {
        String name = variable.getName();
        PsiDeclarationStatement statement = myElementFactory.createVariableDeclarationStatement(name, variable.getType(), null);
        body.add(statement);
      }
    }

    if (myArtificialOutputVariable instanceof PsiField && !myIsChainedConstructor) {
      body.add(myElementFactory.createVariableDeclarationStatement(myArtificialOutputVariable.getName(), myArtificialOutputVariable.getType(), null));
    }
  }

  protected void declareNecessaryVariablesAfterCall(PsiVariable outputVariable) throws IncorrectOperationException {
    if (myHasExpressionOutput) return;
    List<PsiVariable> usedVariables = myControlFlowWrapper.getUsedVariables();
    Collection<ControlFlowUtil.VariableInfo> reassigned = myControlFlowWrapper.getInitializedTwice();
    for (PsiVariable variable : usedVariables) {
      boolean toDeclare = isDeclaredInside(variable) && !variable.equals(outputVariable);
      if (toDeclare) {
        String name = variable.getName();
        PsiDeclarationStatement statement = myElementFactory.createVariableDeclarationStatement(name, variable.getType(), null);
        if (reassigned.contains(new ControlFlowUtil.VariableInfo(variable, null))) {
          final PsiElement[] psiElements = statement.getDeclaredElements();
          assert psiElements.length > 0;
          PsiVariable var = (PsiVariable) psiElements [0];
          PsiUtil.setModifierProperty(var, PsiModifier.FINAL, false);
        }
        addToMethodCallLocation(statement);
      }
    }
  }

  public PsiMethodCallExpression getMethodCall() {
    return myMethodCall;
  }

  public void setMethodCall(PsiMethodCallExpression methodCall) {
    myMethodCall = methodCall;
  }

  public boolean isDeclaredInside(PsiVariable variable) {
    if (variable instanceof ImplicitVariable) return false;
    int startOffset;
    int endOffset;
    if (myExpression != null) {
      final TextRange range = myExpression.getTextRange();
      startOffset = range.getStartOffset();
      endOffset = range.getEndOffset();
    } else {
      startOffset = myElements[0].getTextRange().getStartOffset();
      endOffset = myElements[myElements.length - 1].getTextRange().getEndOffset();
    }
    PsiIdentifier nameIdentifier = variable.getNameIdentifier();
    if (nameIdentifier == null) return false;
    final TextRange range = nameIdentifier.getTextRange();
    if (range == null) return false;
    int offset = range.getStartOffset();
    return startOffset <= offset && offset <= endOffset;
  }

  private String getNewVariableName(PsiVariable variable) {
    for (VariableData data : myVariableDatum) {
      if (data.variable.equals(variable)) {
        return data.name;
      }
    }
    return variable.getName();
  }

  private static boolean shouldAcceptCurrentTarget(Pass<ExtractMethodProcessor> extractPass, PsiElement target) {
    return extractPass == null && !(target instanceof PsiAnonymousClass);
  }

  private boolean applyChosenClassAndExtract(List<PsiVariable> inputVariables, @Nullable Pass<ExtractMethodProcessor> extractPass)
    throws PrepareFailedException {
    myStatic = shouldBeStatic();
    final Set<PsiField> fields = new LinkedHashSet<>();
    myCanBeStatic = canBeStatic(myTargetClass, myCodeFragmentMember, myElements, fields);

    myInputVariables = new InputVariables(inputVariables, myProject, new LocalSearchScope(myElements), isFoldingApplicable());
    myInputVariables.setUsedInstanceFields(fields);

    if (!checkExitPoints()){
      return false;
    }

    checkCanBeChainedConstructor();

    if (extractPass != null) {
      extractPass.pass(this);
    }
    return true;
  }

  public static boolean canBeStatic(final PsiClass targetClass, final PsiElement place, final PsiElement[] elements, Set<PsiField> usedFields) {
    if (!PsiUtil.isLocalOrAnonymousClass(targetClass) && (targetClass.getContainingClass() == null || targetClass.hasModifierProperty(PsiModifier.STATIC))) {
      boolean canBeStatic = true;
      if (targetClass.isInterface()) {
        final PsiMethod containingMethod = PsiTreeUtil.getParentOfType(place, PsiMethod.class, false);
        canBeStatic = containingMethod == null || containingMethod.hasModifierProperty(PsiModifier.STATIC);
      }
      if (canBeStatic) {
        ElementNeedsThis needsThis = new ElementNeedsThis(targetClass) {
          @Override
          protected void visitClassMemberReferenceElement(PsiMember classMember, PsiJavaCodeReferenceElement classMemberReference) {
            if (classMember instanceof PsiField && !classMember.hasModifierProperty(PsiModifier.STATIC)) {
              final PsiExpression expression = PsiTreeUtil.getParentOfType(classMemberReference, PsiExpression.class, false);
              if (expression == null || !PsiUtil.isAccessedForWriting(expression)) {
                usedFields.add((PsiField)classMember);
                return;
              }
            }
            super.visitClassMemberReferenceElement(classMember, classMemberReference);
          }
        };
        for (int i = 0; i < elements.length && !needsThis.usesMembers(); i++) {
          PsiElement element = elements[i];
          element.accept(needsThis);
        }
        return !needsThis.usesMembers();
      }
    }
    return false;
  }

  protected boolean isFoldingApplicable() {
    return true;
  }

  protected void chooseAnchor() {
    myAnchor = myCodeFragmentMember;
    while (!myAnchor.getParent().equals(myTargetClass)) {
      myAnchor = myAnchor.getParent();
    }
  }

  private void showMultipleExitPointsMessage() {
    if (myShowErrorDialogs) {
      HighlightManager highlightManager = HighlightManager.getInstance(myProject);
      PsiStatement[] exitStatementsArray = myExitStatements.toArray(new PsiStatement[myExitStatements.size()]);
      EditorColorsManager manager = EditorColorsManager.getInstance();
      TextAttributes attributes = manager.getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
      highlightManager.addOccurrenceHighlights(myEditor, exitStatementsArray, attributes, true, null);
      String message = RefactoringBundle
        .getCannotRefactorMessage(RefactoringBundle.message("there.are.multiple.exit.points.in.the.selected.code.fragment"));
      CommonRefactoringUtil.showErrorHint(myProject, myEditor, message, myRefactoringName, myHelpId);
      WindowManager.getInstance().getStatusBar(myProject).setInfo(RefactoringBundle.message("press.escape.to.remove.the.highlighting"));
    }
  }

  protected void showMultipleOutputMessage(PsiType expressionType) throws PrepareFailedException {
    if (myShowErrorDialogs) {
      String message = buildMultipleOutputMessageError(expressionType) + "\nWould you like to Extract Method Object?";

      if (ApplicationManager.getApplication().isUnitTestMode()) throw new RuntimeException(message);
      RefactoringMessageDialog dialog = new RefactoringMessageDialog(myRefactoringName, message, myHelpId, "OptionPane.errorIcon", true,
                                                                     myProject);
      if (dialog.showAndGet()) {
        new ExtractMethodObjectHandler()
          .invoke(myProject, myEditor, myTargetClass.getContainingFile(), DataManager.getInstance().getDataContext());
      }
    }
  }

  protected String buildMultipleOutputMessageError(PsiType expressionType) {
    StringBuilder buffer = new StringBuilder();
    buffer.append(RefactoringBundle.getCannotRefactorMessage(
      RefactoringBundle.message("there.are.multiple.output.values.for.the.selected.code.fragment")));
    buffer.append("\n");
    if (myHasExpressionOutput) {
      buffer.append("    ").append(RefactoringBundle.message("expression.result")).append(": ");
      buffer.append(PsiFormatUtil.formatType(expressionType, 0, PsiSubstitutor.EMPTY));
      buffer.append(",\n");
    }
    if (myGenerateConditionalExit) {
      buffer.append("    ").append(RefactoringBundle.message("boolean.method.result"));
      buffer.append(",\n");
    }
    for (int i = 0; i < myOutputVariables.length; i++) {
      PsiVariable var = myOutputVariables[i];
      buffer.append("    ");
      buffer.append(var.getName());
      buffer.append(" : ");
      buffer.append(PsiFormatUtil.formatType(var.getType(), 0, PsiSubstitutor.EMPTY));
      if (i < myOutputVariables.length - 1) {
        buffer.append(",\n");
      }
      else {
        buffer.append(".");
      }
    }
    return buffer.toString();
  }

  public PsiMethod getExtractedMethod() {
    return myExtractedMethod;
  }

  public void setMethodName(String methodName) {
    myMethodName = methodName;
  }

  public Boolean hasDuplicates() {
    List<Match> duplicates = getDuplicates();
    if (duplicates != null && !duplicates.isEmpty()) {
      return true;
    }

    if (myExtractedMethod != null) {
      final ExtractMethodSignatureSuggester suggester = new ExtractMethodSignatureSuggester(myProject, myExtractedMethod, myMethodCall, myVariableDatum);
      duplicates = suggester.getDuplicates(myExtractedMethod, myMethodCall, myInputVariables.getFolding());
      if (duplicates != null && !duplicates.isEmpty()) {
        myDuplicates      = duplicates;
        myExtractedMethod = suggester.getExtractedMethod();
        myMethodCall      = suggester.getMethodCall();
        myVariableDatum   = suggester.getVariableData();

        final List<PsiVariable> outputVariables = new ArrayList<>();
        for (PsiReturnStatement statement : PsiUtil.findReturnStatements(myExtractedMethod)) {
          final PsiExpression returnValue = statement.getReturnValue();
          if (returnValue instanceof PsiReferenceExpression) {
            final PsiElement resolve = ((PsiReferenceExpression)returnValue).resolve();
            if (resolve instanceof PsiLocalVariable) {
              outputVariables.add((PsiVariable)resolve);
            }
          }
        }

        if (outputVariables.size() == 1) {
          myOutputVariable = outputVariables.get(0);
        }

        return null;
      }
    }
    return false;
  }

  public boolean hasDuplicates(Set<VirtualFile> files) {
    final DuplicatesFinder finder = initDuplicates();

    final Boolean hasDuplicates = hasDuplicates();
    if (hasDuplicates == null || hasDuplicates) return true;
    if (finder != null) {
      final PsiManager psiManager = PsiManager.getInstance(myProject);
      for (VirtualFile file : files) {
        if (!finder.findDuplicates(psiManager.findFile(file)).isEmpty()) return true;
      }
    }
    return false;
  }

  @Nullable
  public String getConfirmDuplicatePrompt(Match match) {
    final boolean needToBeStatic = RefactoringUtil.isInStaticContext(match.getMatchStart(), myExtractedMethod.getContainingClass());
    final String changedSignature = MatchUtil
      .getChangedSignature(match, myExtractedMethod, needToBeStatic, VisibilityUtil.getVisibilityStringToDisplay(myExtractedMethod));
    if (changedSignature != null) {
      return RefactoringBundle.message("replace.this.code.fragment.and.change.signature", changedSignature);
    }
    if (needToBeStatic && !myExtractedMethod.hasModifierProperty(PsiModifier.STATIC)) {
      return RefactoringBundle.message("replace.this.code.fragment.and.make.method.static");
    }
    return null;
  }

  @Override
  public String getReplaceDuplicatesTitle(int idx, int size) {
    return RefactoringBundle.message("process.duplicates.title", idx, size);
  }

  public InputVariables getInputVariables() {
    return myInputVariables;
  }

  public PsiTypeParameterList getTypeParameterList() {
    return myTypeParameterList;
  }

  public PsiClassType[] getThrownExceptions() {
    return myThrownExceptions;
  }

  public boolean isStatic() {
    return myStatic;
  }

  public boolean isCanBeStatic() {
    return myCanBeStatic;
  }

  public PsiElement[] getElements() {
    return myElements;
  }

  public PsiVariable[] getOutputVariables() {
    return myOutputVariables;
  }

  public void setMethodVisibility(String methodVisibility) {
    myMethodVisibility = methodVisibility;
  }
}
