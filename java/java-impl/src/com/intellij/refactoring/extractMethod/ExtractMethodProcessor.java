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
package com.intellij.refactoring.extractMethod;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.ChangeContextUtil;
import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.codeInsight.intention.impl.AddNullableNotNullAnnotationFix;
import com.intellij.codeInspection.dataFlow.RunnerResult;
import com.intellij.codeInspection.dataFlow.StandardDataFlowRunner;
import com.intellij.codeInspection.dataFlow.StandardInstructionVisitor;
import com.intellij.codeInspection.dataFlow.instructions.BranchingInstruction;
import com.intellij.codeInspection.dataFlow.instructions.Instruction;
import com.intellij.ide.DataManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.controlFlow.ControlFlowUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.extractMethodObject.ExtractMethodObjectHandler;
import com.intellij.refactoring.introduceField.ElementToWorkOn;
import com.intellij.refactoring.introduceVariable.IntroduceVariableBase;
import com.intellij.refactoring.util.*;
import com.intellij.refactoring.util.classMembers.ElementNeedsThis;
import com.intellij.refactoring.util.duplicates.DuplicatesFinder;
import com.intellij.refactoring.util.duplicates.Match;
import com.intellij.refactoring.util.duplicates.MatchProvider;
import com.intellij.refactoring.util.duplicates.VariableReturnValue;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import com.intellij.util.VisibilityUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

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
  private ParameterTablePanel.VariableData[] myVariableDatum; // parameter data for extracted method
  protected PsiClassType[] myThrownExceptions; // exception to declare as thrown by extracted method
  protected boolean myStatic; // whether to declare extracted method static

  protected PsiClass myTargetClass; // class to create the extracted method in
  private PsiElement myAnchor; // anchor to insert extracted method after it

  protected ControlFlowWrapper myControlFlowWrapper;
  protected InputVariables myInputVariables; // input variables
  protected PsiVariable[] myOutputVariables; // output variables
  protected PsiVariable myOutputVariable; // the only output variable
  private Collection<PsiStatement> myExitStatements;

  private boolean myHasReturnStatement; // there is a return statement
  private boolean myHasReturnStatementOutput; // there is a return statement and its type is not void
  protected boolean myHasExpressionOutput; // extracted code is an expression with non-void type
  private boolean myNeedChangeContext; // target class is not immediate container of the code to be extracted

  private boolean myShowErrorDialogs = true;
  protected boolean myCanBeStatic;
  protected boolean myCanBeChainedConstructor;
  protected boolean myIsChainedConstructor;
  private DuplicatesFinder myDuplicatesFinder;
  private List<Match> myDuplicates;
  @Modifier private String myMethodVisibility = PsiModifier.PRIVATE;
  protected boolean myGenerateConditionalExit;
  private PsiStatement myFirstExitStatementCopy;
  private PsiMethod myExtractedMethod;
  private PsiMethodCallExpression myMethodCall;
  private boolean myNullConditionalCheck = false;

  public ExtractMethodProcessor(Project project,
                                Editor editor,
                                PsiElement[] elements,
                                PsiType forcedReturnType,
                                String refactoringName,
                                String initialMethodName,
                                String helpId) {
    myProject = project;
    myEditor = editor;
    if (elements.length != 1 || elements.length == 1 && !(elements[0] instanceof PsiBlockStatement)) {
      myElements = elements;
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
    if (first instanceof PsiJavaToken && ((PsiJavaToken)first).getTokenType() == JavaTokenType.LBRACE) {
      resultStart++;
    }
    final PsiElement last = codeBlockChildren[codeBlockChildren.length - 1];
    if (last instanceof PsiJavaToken && ((PsiJavaToken)last).getTokenType() == JavaTokenType.RBRACE) {
      resultLast--;
    }
    final ArrayList<PsiElement> result = new ArrayList<PsiElement>();
    for (int i = resultStart; i < resultLast; i++) {
      PsiElement element = codeBlockChildren[i];
      if (!(element instanceof PsiWhiteSpace)) {
        result.add(element);
      }
    }

    return result.toArray(new PsiElement[result.size()]);
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



  /**
   * Invoked in atomic action
   */
  public boolean prepare() throws PrepareFailedException {
    myExpression = null;
    if (myElements.length == 1 && myElements[0] instanceof PsiExpression) {
      final PsiExpression expression = (PsiExpression)myElements[0];
      if (expression.getParent() instanceof PsiExpressionStatement) {
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
      myExitStatements = myControlFlowWrapper.prepareExitStatements(myElements);
      if (myControlFlowWrapper.isGenerateConditionalExit()) {
        myGenerateConditionalExit = true;
      } else {
        myHasReturnStatement = myExpression == null && myControlFlowWrapper.isReturnPresentBetween();
      }
      myFirstExitStatementCopy = myControlFlowWrapper.getFirstExitStatementCopy();
    }
    catch (ControlFlowWrapper.ExitStatementsNotSameException e) {
      myExitStatements = myControlFlowWrapper.getExitStatements();
      showMultipleExitPointsMessage();
      return false;
    }

    myOutputVariables = myControlFlowWrapper.getOutputVariables();
    final List<PsiVariable> inputVariables = myControlFlowWrapper.getInputVariables(codeFragment);
    chooseTargetClass(inputVariables);

    myInputVariables = new InputVariables(inputVariables, myProject, new LocalSearchScope(myElements), true);

    PsiType expressionType = null;
    if (myExpression != null) {
      if (myForcedReturnType != null) {
        expressionType = myForcedReturnType;
      }
      else {
        expressionType = RefactoringUtil.getTypeByExpressionWithExpectedType(myExpression);
      }
    }
    if (expressionType == null) {
      expressionType = PsiType.VOID;
    }
    myHasExpressionOutput = expressionType != PsiType.VOID;

    PsiType returnStatementType = null;
    if (myHasReturnStatement) {
      returnStatementType = myCodeFragmentMember instanceof PsiMethod ? ((PsiMethod)myCodeFragmentMember).getReturnType() : null;
    }
    myHasReturnStatementOutput = returnStatementType != null && returnStatementType != PsiType.VOID;

    if (myGenerateConditionalExit && myOutputVariables.length == 1) {
      if (!(myOutputVariables[0].getType() instanceof PsiPrimitiveType)) {
        myNullConditionalCheck = true;
        for (PsiStatement exitStatement : myExitStatements) {
          if (exitStatement instanceof PsiReturnStatement) {
            final PsiExpression returnValue = ((PsiReturnStatement)exitStatement).getReturnValue();
            myNullConditionalCheck &= returnValue == null ||
                                      returnValue instanceof PsiLiteralExpression && PsiType.NULL.equals(returnValue.getType());
          }
        }
        myNullConditionalCheck &= isNotNull(myOutputVariables[0]);
      }
    }

    if (!myHasReturnStatementOutput && checkOutputVariablesCount() && !myNullConditionalCheck) {
      showMultipleOutputMessage(expressionType);
      return false;
    }

    myOutputVariable = myOutputVariables.length > 0 ? myOutputVariables[0] : null;
    if (myHasReturnStatementOutput) {
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
    if (container instanceof PsiMethod) {
      PsiElement[] elements = myElements;
      if (myExpression == null) {
        if (myOutputVariable != null) {
          elements = ArrayUtil.append(myElements, myOutputVariable, PsiElement.class);
        }
        if (myCodeFragmentMember != null) {
          elements = ArrayUtil.append(myElements, ((PsiMethod)myCodeFragmentMember).getReturnTypeElement(), PsiElement.class);
        }
      }
      myTypeParameterList = RefactoringUtil.createTypeParameterListWithUsedTypeParameters(((PsiMethod)container).getTypeParameterList(), elements);
    }
    List<PsiClassType> exceptions = ExceptionUtil.getThrownCheckedExceptions(myElements);
    myThrownExceptions = exceptions.toArray(new PsiClassType[exceptions.size()]);
    myStatic = shouldBeStatic();

    if (myTargetClass.getContainingClass() == null || myTargetClass.hasModifierProperty(PsiModifier.STATIC)) {
      ElementNeedsThis needsThis = new ElementNeedsThis(myTargetClass);
      for (int i = 0; i < myElements.length && !needsThis.usesMembers(); i++) {
        PsiElement element = myElements[i];
        element.accept(needsThis);
      }
      myCanBeStatic = !needsThis.usesMembers();
    }
    else {
      myCanBeStatic = false;
    }

    if (container instanceof PsiMethod) {
      checkLocalClasses((PsiMethod) container);
    }

    checkCanBeChainedConstructor();





    return true;
  }

  private boolean isNotNull(PsiVariable outputVariable) {
    final StandardDataFlowRunner dfaRunner = new StandardDataFlowRunner(false);
    final PsiCodeBlock block = myElementFactory.createCodeBlock();
    for (PsiElement element : myElements) {
      block.add(element);
    }
    final PsiIfStatement statementFromText = (PsiIfStatement)myElementFactory.createStatementFromText("if (" + outputVariable.getName() + " == null);", null);
    block.add(statementFromText);

    final StandardInstructionVisitor visitor = new StandardInstructionVisitor();
    final RunnerResult rc = dfaRunner.analyzeMethod(block, visitor);
    if (rc == RunnerResult.OK) {
      if (dfaRunner.problemsDetected(visitor)) {
        final Pair<Set<Instruction>,Set<Instruction>>
          conditionalExpressions = dfaRunner.getConstConditionalExpressions();
        final Set<Instruction> falseSet = conditionalExpressions.getSecond();
        for (Instruction instruction : falseSet) {
          if (instruction instanceof BranchingInstruction) {
            if (((BranchingInstruction)instruction).getPsiAnchor().getText().equals(statementFromText.getCondition().getText())) {
              return true;
            }
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
    if (!method.isConstructor() || myReturnType != PsiType.VOID) {
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
    final List<PsiClass> localClasses = new ArrayList<PsiClass>();
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
      final List<PsiElement> extractedReferences = new ArrayList<PsiElement>();
      final List<PsiElement> remainingReferences = new ArrayList<PsiElement>();
      ReferencesSearch.search(localClass).forEach(new Processor<PsiReference>() {
        public boolean process(final PsiReference psiReference) {
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
        }
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
      if (statement != null && RefactoringUtil.isSuperOrThisCall(statement, true, true)) {
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
    myStatic |= dialog.isMakeStatic();
    myIsChainedConstructor = dialog.isChainedConstructor();
    myMethodVisibility = dialog.getVisibility();
  }

  protected AbstractExtractDialog createExtractMethodDialog(final boolean direct) {
    return new ExtractMethodDialog(myProject, myTargetClass, myInputVariables, myReturnType, myTypeParameterList,
                                                         myThrownExceptions, myStatic, myCanBeStatic, myCanBeChainedConstructor, myInitialMethodName,
                                                         myRefactoringName, myHelpId, myElements) {
      {
        init();
      }
      protected boolean areTypesDirected() {
        return direct;
      }

      @Override
      protected boolean isOutputVariable(PsiVariable var) {
        return ExtractMethodProcessor.this.isOutputVariable(var);
      }
    };
  }

  public boolean isOutputVariable(PsiVariable var) {
    return ArrayUtil.find(myOutputVariables, var) != -1;
  }

  public boolean showDialog() {
    return showDialog(true);
  }

  public void testRun() throws IncorrectOperationException {
    myInputVariables.setFoldingAvailable(myInputVariables.isFoldingSelectedByDefault());
    myMethodName = myInitialMethodName;
    myVariableDatum = new ParameterTablePanel.VariableData[myInputVariables.getInputVariables().size()];
    for (int i = 0; i < myInputVariables.getInputVariables().size(); i++) {
      myVariableDatum[i] = myInputVariables.getInputVariables().get(i);
    }

    doRefactoring();
  }

  /**
   * Invoked in command and in atomic action
   */
  public void doRefactoring() throws IncorrectOperationException {
    List<PsiElement> elements = new ArrayList<PsiElement>();
    for (PsiElement element : myElements) {
      if (!(element instanceof PsiWhiteSpace || element instanceof PsiComment)) {
        elements.add(element);
      }
    }
    if (myExpression != null) {
      myDuplicatesFinder = new DuplicatesFinder(elements.toArray(new PsiElement[elements.size()]), myInputVariables.copy(), new ArrayList<PsiVariable>());
      myDuplicates = myDuplicatesFinder.findDuplicates(myTargetClass);
    }
    else if (elements.size() > 0){
      myDuplicatesFinder = new DuplicatesFinder(elements.toArray(new PsiElement[elements.size()]), myInputVariables.copy(), myOutputVariable != null ? new VariableReturnValue(myOutputVariable) : null, Arrays.asList(myOutputVariables));
      myDuplicates = myDuplicatesFinder.findDuplicates(myTargetClass);
    } else {
      myDuplicates = new ArrayList<Match>();
    }

    chooseAnchor();

    int col = myEditor.getCaretModel().getLogicalPosition().column;
    int line = myEditor.getCaretModel().getLogicalPosition().line;
    LogicalPosition pos = new LogicalPosition(0, 0);
    myEditor.getCaretModel().moveToLogicalPosition(pos);

    SearchScope processConflictsScope = myMethodVisibility.equals(PsiModifier.PRIVATE) ?
                                        new LocalSearchScope(myTargetClass) :
                                        GlobalSearchScope.projectScope(myProject);

    final Map<PsiMethodCallExpression, PsiMethod> overloadsResolveMap =
      ExtractMethodUtil.encodeOverloadTargets(myTargetClass, processConflictsScope, myMethodName, myCodeFragmentMember);

    doExtract();
    ExtractMethodUtil.decodeOverloadTargets(overloadsResolveMap, myExtractedMethod, myCodeFragmentMember);

    LogicalPosition pos1 = new LogicalPosition(line, col);
    myEditor.getCaretModel().moveToLogicalPosition(pos1);
    int offset = myMethodCall.getMethodExpression().getTextRange().getStartOffset();
    myEditor.getCaretModel().moveToOffset(offset);
    myEditor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    myEditor.getSelectionModel().removeSelection();
    myEditor.getSelectionModel().removeSelection();
  }

  private void doExtract() throws IncorrectOperationException {

    PsiMethod newMethod = generateEmptyMethod(myThrownExceptions, myStatic);

    myExpression = myInputVariables.replaceWrappedReferences(myElements, myExpression);
    renameInputVariables();

    LOG.assertTrue(myElements[0].isValid());

    PsiCodeBlock body = newMethod.getBody();
    myMethodCall = generateMethodCall(null, true);

    LOG.assertTrue(myElements[0].isValid());

    if (myExpression == null) {
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

      boolean hasNormalExit = false;
      PsiElement lastElement = myElements[myElements.length - 1];
      if (!(lastElement instanceof PsiReturnStatement || lastElement instanceof PsiBreakStatement ||
            lastElement instanceof PsiContinueStatement)) {
        hasNormalExit = true;
      }

      PsiStatement exitStatementCopy = myControlFlowWrapper.getExitStatementCopy(returnStatement, myElements);


      declareNecessaryVariablesInsideBody(body);



      if (myNeedChangeContext) {
        for (PsiElement element : myElements) {
          ChangeContextUtil.encodeContextInfo(element, false);
        }
      }

      body.addRange(myElements[0], myElements[myElements.length - 1]);
      if (myNullConditionalCheck) {
        body.add(myElementFactory.createStatementFromText("return " + myOutputVariable.getName() + ";", null));
      } else if (myGenerateConditionalExit) {
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
                  InlineUtil.inlineVariable((PsiVariable)resolved, ((PsiVariable)resolved).getInitializer(), (PsiReferenceExpression)returnValue);
                  resolved.delete();
                }
              }
            }
          }
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
        PsiIfStatement ifStatement =
          (PsiIfStatement)myElementFactory.createStatementFromText(myHasReturnStatementOutput || (myGenerateConditionalExit && myFirstExitStatementCopy instanceof PsiReturnStatement &&
                                                                                                  ((PsiReturnStatement)myFirstExitStatementCopy).getReturnValue() != null)
                                                                   ? "if (" + varName + "==null) return null;"
                                                                   : "if (" + varName + "==null) return;", null);
        ifStatement = (PsiIfStatement)addToMethodCallLocation(ifStatement);
        CodeStyleManager.getInstance(myProject).reformat(ifStatement);
      }
      else if (myGenerateConditionalExit) {
        PsiIfStatement ifStatement = (PsiIfStatement)myElementFactory.createStatementFromText("if (a) b;", null);
        ifStatement = (PsiIfStatement)addToMethodCallLocation(ifStatement);
        myMethodCall = (PsiMethodCallExpression)ifStatement.getCondition().replace(myMethodCall);
        ifStatement.getThenBranch().replace(myFirstExitStatementCopy);
        CodeStyleManager.getInstance(myProject).reformat(ifStatement);
      }
      else if (myOutputVariable != null) {
        String name = myOutputVariable.getName();
        boolean toDeclare = isDeclaredInside(myOutputVariable);
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
      if (myHasReturnStatement && !myHasReturnStatementOutput && !hasNormalExit) {
        PsiStatement statement = myElementFactory.createStatementFromText("return;", null);
        addToMethodCallLocation(statement);
      }
      else if (!myGenerateConditionalExit && exitStatementCopy != null) {
        addToMethodCallLocation(exitStatementCopy);
      }

      if (!myNullConditionalCheck) {
        declareNecessaryVariablesAfterCall(myOutputVariable);
      }

      deleteExtracted();
    }
    else {
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
      final PsiElement replacement = IntroduceVariableBase.replace(myExpression, myMethodCall, myProject);
      myMethodCall = PsiTreeUtil.getParentOfType(replacement.findElementAt(replacement.getText().indexOf(myMethodCall.getText())), PsiMethodCallExpression.class);
    }

    if (myAnchor instanceof PsiField) {
      ((PsiField)myAnchor).normalizeDeclaration();
    }

    adjustFinalParameters(newMethod);

    myExtractedMethod = (PsiMethod)myTargetClass.addAfter(newMethod, myAnchor);
    if (myNeedChangeContext) {
      ChangeContextUtil.decodeContextInfo(myExtractedMethod, myTargetClass, RefactoringUtil.createThisExpression(myManager, null));
    }

  }

  private void declareVariableAtMethodCallLocation(String name) {
    PsiDeclarationStatement statement =
      myElementFactory.createVariableDeclarationStatement(name, myOutputVariable.getType(), myMethodCall);
    statement = (PsiDeclarationStatement)addToMethodCallLocation(statement);
    PsiVariable var = (PsiVariable)statement.getDeclaredElements()[0];
    myMethodCall = (PsiMethodCallExpression)var.getInitializer();
    var.getModifierList().replace(myOutputVariable.getModifierList());
  }

  private void adjustFinalParameters(final PsiMethod method) throws IncorrectOperationException {
    final IncorrectOperationException[] exc = new IncorrectOperationException[1];
    exc[0] = null;
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    if (parameters.length > 0) {
      if (CodeStyleSettingsManager.getSettings(myProject).GENERATE_FINAL_PARAMETERS) {
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
      else {
        method.accept(new JavaRecursiveElementVisitor() {
          @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
            final PsiElement resolved = expression.resolve();
            final int index = ArrayUtil.find(parameters, resolved);
            if (index >= 0) {
              final PsiParameter param = parameters[index];
              if (!param.hasModifierProperty(PsiModifier.FINAL) && RefactoringUtil.isInsideAnonymous(expression, method)) {
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
    List<Match> result = new ArrayList<Match>();
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

  public PsiElement processMatch(Match match) throws IncorrectOperationException {
    match.changeSignature(myExtractedMethod);
    if (RefactoringUtil.isInStaticContext(match.getMatchStart(), myExtractedMethod.getContainingClass())) {
      PsiUtil.setModifierProperty(myExtractedMethod, PsiModifier.STATIC, true);
    }
    final PsiMethodCallExpression methodCallExpression = generateMethodCall(match.getInstanceExpression(), false);

    ArrayList<ParameterTablePanel.VariableData> datas = new ArrayList<ParameterTablePanel.VariableData>();
    for (final ParameterTablePanel.VariableData variableData : myVariableDatum) {
      if (variableData.passAsParameter) {
        datas.add(variableData);
      }
    }
    for (ParameterTablePanel.VariableData data : datas) {
      final List<PsiElement> parameterValue = match.getParameterValues(data.variable);
      for (PsiElement val : parameterValue) {
        methodCallExpression.getArgumentList().add(val);
      }
    }
    return match.replace(myExtractedMethod, methodCallExpression, myOutputVariable);
  }

  private void deleteExtracted() throws IncorrectOperationException {
    if (myEnclosingBlockStatement == null) {
      myElements[0].getParent().deleteChildRange(myElements[0], myElements[myElements.length - 1]);
    }
    else {
      myEnclosingBlockStatement.delete();
    }
  }

  protected PsiElement addToMethodCallLocation(PsiElement statement) throws IncorrectOperationException {
    if (myEnclosingBlockStatement == null) {
      return myElements[0].getParent().addBefore(statement, myElements[0]);
    }
    else {
      return myEnclosingBlockStatement.getParent().addBefore(statement, myEnclosingBlockStatement);
    }
  }

  private void renameInputVariables() throws IncorrectOperationException {
    for (ParameterTablePanel.VariableData data : myVariableDatum) {
      PsiVariable variable = data.variable;
      if (!data.name.equals(variable.getName())) {
        for (PsiElement element : myElements) {
          RefactoringUtil.renameVariableReferences(variable, data.name, new LocalSearchScope(element));
        }
      }
    }
  }

  public PsiClass getTargetClass() {
    return myTargetClass;
  }

  private PsiMethod generateEmptyMethod(PsiClassType[] exceptions, boolean isStatic) throws IncorrectOperationException {
    PsiMethod newMethod;
    if (myIsChainedConstructor) {
      newMethod = myElementFactory.createConstructor();      
    }
    else {
      newMethod = myElementFactory.createMethod(myMethodName, myReturnType);
      PsiUtil.setModifierProperty(newMethod, PsiModifier.STATIC, isStatic);
    }
    PsiUtil.setModifierProperty(newMethod, myMethodVisibility, true);
    if (myTypeParameterList != null) {
      newMethod.getTypeParameterList().replace(myTypeParameterList);
    }
    PsiCodeBlock body = newMethod.getBody();
    LOG.assertTrue(body != null);

    boolean isFinal = CodeStyleSettingsManager.getSettings(myProject).GENERATE_FINAL_PARAMETERS;
    PsiParameterList list = newMethod.getParameterList();
    for (ParameterTablePanel.VariableData data : myVariableDatum) {
      if (data.passAsParameter) {
        PsiParameter parm = myElementFactory.createParameter(data.name, data.type);
        if (isFinal) {
          PsiUtil.setModifierProperty(parm, PsiModifier.FINAL, true);
        }
        list.add(parm);
      }
      else {
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
    for (PsiClassType exception : exceptions) {
      throwsList.add(JavaPsiFacade.getInstance(myManager.getProject()).getElementFactory().createReferenceElementByType(exception));
    }

    if (myNullConditionalCheck) {
      final boolean isNullCheckReturnNull = (myHasExpressionOutput ? 1 : 0) + (myGenerateConditionalExit ? 1 : 0) + myOutputVariables.length <= 1;
      if (isNullCheckReturnNull && PsiUtil.isLanguageLevel5OrHigher(myElements[0])) {
        final PsiClass nullableAnnotationClass =
          JavaPsiFacade.getInstance(myProject).findClass(AnnotationUtil.NULLABLE, GlobalSearchScope.allScope(myProject));
        if (nullableAnnotationClass != null) {
          new AddNullableNotNullAnnotationFix(AnnotationUtil.NULLABLE, newMethod, AnnotationUtil.NOT_NULL)
            .invoke(myProject, myEditor, myTargetClass.getContainingFile());
        }
      }
    }

    return (PsiMethod)myStyleManager.reformat(newMethod);
  }

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
        if (myNeedChangeContext) {
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
      for (ParameterTablePanel.VariableData data : myVariableDatum) {
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
    return expr;
  }

  private void declareNecessaryVariablesInsideBody(PsiCodeBlock body) throws IncorrectOperationException {
    List<PsiVariable> usedVariables = myControlFlowWrapper.getUsedVariablesInBody();
    for (PsiVariable variable : usedVariables) {
      boolean toDeclare = !isDeclaredInside(variable) && myInputVariables.toDeclareInsideBody(variable);
      if (toDeclare) {
        String name = variable.getName();
        PsiDeclarationStatement statement = myElementFactory.createVariableDeclarationStatement(name, variable.getType(), null);
        body.add(statement);
      }
    }
  }

  protected void declareNecessaryVariablesAfterCall(PsiVariable outputVariable) throws IncorrectOperationException {
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

  public boolean isDeclaredInside(PsiVariable variable) {
    if (variable instanceof ImplicitVariable) return false;
    int startOffset = myElements[0].getTextRange().getStartOffset();
    int endOffset = myElements[myElements.length - 1].getTextRange().getEndOffset();
    PsiIdentifier nameIdentifier = variable.getNameIdentifier();
    if (nameIdentifier == null) return false;
    final TextRange range = nameIdentifier.getTextRange();
    if (range == null) return false;
    int offset = range.getStartOffset();
    return startOffset <= offset && offset <= endOffset;
  }

  private String getNewVariableName(PsiVariable variable) {
    for (ParameterTablePanel.VariableData data : myVariableDatum) {
      if (data.variable.equals(variable)) {
        return data.name;
      }
    }
    return variable.getName();
  }

  private void chooseTargetClass(List<PsiVariable> inputVariables) {
    myNeedChangeContext = false;
    myTargetClass = myCodeFragmentMember instanceof PsiMember
                    ? ((PsiMember)myCodeFragmentMember).getContainingClass()
                    : PsiTreeUtil.getParentOfType(myCodeFragmentMember, PsiClass.class);
    if (myTargetClass instanceof PsiAnonymousClass) {
      PsiElement target = myTargetClass.getParent();
      PsiElement targetMember = myTargetClass;
      while (true) {
        if (target instanceof PsiFile) break;
        if (target instanceof PsiClass && !(target instanceof PsiAnonymousClass)) break;
        targetMember = target;
        target = target.getParent();
      }
      if (target instanceof PsiClass) {
        PsiClass newTargetClass = (PsiClass)target;
        List<PsiVariable> array = new ArrayList<PsiVariable>();
        boolean success = true;
        for (PsiElement element : myElements) {
          if (!ControlFlowUtil.collectOuterLocals(array, element, myCodeFragmentMember, targetMember)) {
            success = false;
            break;
          }
        }
        if (success) {
          myTargetClass = newTargetClass;
          inputVariables.addAll(array);
          myNeedChangeContext = true;
        }
      }
    }
  }

  private void chooseAnchor() {
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

  private void showMultipleOutputMessage(PsiType expressionType) {
    if (myShowErrorDialogs) {
      StringBuilder buffer = new StringBuilder();
      buffer.append(RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("there.are.multiple.output.values.for.the.selected.code.fragment")));
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
      buffer.append("\nWould you like to Extract Method Object?");

      String message = buffer.toString();

      if (ApplicationManager.getApplication().isUnitTestMode()) throw new RuntimeException(message);
      RefactoringMessageDialog dialog = new RefactoringMessageDialog(myRefactoringName, message, myHelpId, "OptionPane.errorIcon", true,
                                                                     myProject);
      dialog.show();
      if (dialog.isOK()) {
        new ExtractMethodObjectHandler().invoke(myProject, myEditor, myTargetClass.getContainingFile(), DataManager.getInstance().getDataContext());
      }
    }
  }

  public PsiMethod getExtractedMethod() {
    return myExtractedMethod;
  }

  public boolean hasDuplicates() {
    final List<Match> duplicates = getDuplicates();
    return duplicates != null && !duplicates.isEmpty();
  }

  public boolean hasDuplicates(Set<VirtualFile> files) {
    if (hasDuplicates()) return true;
    final PsiManager psiManager = PsiManager.getInstance(myProject);
    for (VirtualFile file : files) {
      if (myDuplicatesFinder != null && !myDuplicatesFinder.findDuplicates(psiManager.findFile(file)).isEmpty()) return true;
    }
    return false;
  }

  @NotNull
  public String getConfirmDuplicatePrompt(Match match) {
    final boolean needToBeStatic = RefactoringUtil.isInStaticContext(match.getMatchStart(), myExtractedMethod.getContainingClass());
    final String changedSignature = match.getChangedSignature(myExtractedMethod, needToBeStatic, VisibilityUtil.getVisibilityStringToDisplay(myExtractedMethod));
    if (changedSignature != null) {
      return RefactoringBundle.message("replace.this.code.fragment.and.change.signature", changedSignature);
    }
    if (needToBeStatic && !myExtractedMethod.hasModifierProperty(PsiModifier.STATIC)) {
      return RefactoringBundle.message("replace.this.code.fragment.and.make.method.static");
    }
    return RefactoringBundle.message("replace.this.code.fragment");
  }

  public InputVariables getInputVariables() {
    return myInputVariables;
  }
}
