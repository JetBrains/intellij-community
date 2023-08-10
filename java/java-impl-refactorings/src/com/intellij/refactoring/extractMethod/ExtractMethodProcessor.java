// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.extractMethod;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.*;
import com.intellij.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import com.intellij.codeInsight.daemon.impl.quickfix.AnonymousTargetClassPreselectionUtil;
import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.codeInsight.intention.AddAnnotationPsiFix;
import com.intellij.codeInsight.navigation.PsiTargetNavigator;
import com.intellij.codeInspection.dataFlow.DfaNullability;
import com.intellij.codeInspection.dataFlow.DfaUtil;
import com.intellij.codeInspection.dataFlow.StandardDataFlowRunner;
import com.intellij.codeInspection.dataFlow.interpreter.RunnerResult;
import com.intellij.codeInspection.dataFlow.java.JavaDfaListener;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.redundantCast.RemoveRedundantCastUtil;
import com.intellij.ide.DataManager;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.*;
import com.intellij.psi.controlFlow.*;
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
import com.intellij.refactoring.IntroduceVariableUtil;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.extractMethodObject.ExtractMethodObjectHandler;
import com.intellij.refactoring.introduceField.ElementToWorkOn;
import com.intellij.refactoring.util.*;
import com.intellij.refactoring.util.duplicates.*;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.text.UniqueNameGenerator;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.function.Consumer;

import static com.intellij.codeInsight.AnnotationUtil.CHECK_TYPE;
import static com.intellij.refactoring.util.duplicates.DuplicatesFinder.MatchType;

public class ExtractMethodProcessor implements MatchProvider {
  private static final Logger LOG = Logger.getInstance(ExtractMethodProcessor.class);
  @TestOnly
  public static final Key<Boolean> SIGNATURE_CHANGE_ALLOWED = Key.create("SignatureChangeAllowed");

  protected final Project myProject;
  private final Editor myEditor;
  protected final PsiElement[] myElements;
  private final PsiBlockStatement myEnclosingBlockStatement;
  private final PsiType myForcedReturnType;
  protected final @NlsContexts.DialogTitle String myRefactoringName;
  protected final String myInitialMethodName;
  private final String myHelpId;

  private final PsiManager myManager;
  private final PsiElementFactory myElementFactory;
  private final CodeStyleManager myStyleManager;

  private PsiExpression myExpression;

  private PsiElement myCodeFragmentMember; // parent of myCodeFragment

  protected @NlsSafe String myMethodName; // name for extracted method
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
  private boolean myIsPreviewSupported;
  private boolean myPreviewDuplicates;
  protected boolean myCanBeStatic;
  protected boolean myCanBeChainedConstructor;
  protected boolean myIsChainedConstructor;
  protected List<Match> myDuplicates;
  private ParametrizedDuplicates myParametrizedDuplicates;
  private ParametrizedDuplicates myExactDuplicates;
  @PsiModifier.ModifierConstant protected String myMethodVisibility = PsiModifier.PRIVATE;
  protected boolean myGenerateConditionalExit;
  protected PsiStatement myFirstExitStatementCopy;
  protected PsiMethod myExtractedMethod;
  private PsiMethodCallExpression myMethodCall;
  private PsiBlockStatement myAddedToMethodCallLocation;
  protected boolean myNullConditionalCheck;
  protected boolean myNotNullConditionalCheck;
  protected Nullability myNullability;

  private final CodeStyleSettings myStyleSettings;

  public ExtractMethodProcessor(Project project,
                                Editor editor,
                                PsiElement[] elements,
                                PsiType forcedReturnType,
                                @NlsContexts.DialogTitle String refactoringName,
                                String initialMethodName,
                                String helpId) {
    myProject = project;
    myEditor = editor;
    if (elements.length != 1 || !(elements[0] instanceof PsiBlockStatement)) {
      if (elements.length == 1 && elements[0] instanceof PsiParenthesizedExpression) {
        PsiExpression expression = PsiUtil.skipParenthesizedExprDown((PsiExpression)elements[0]);
        myElements = expression != null ? new PsiElement[]{expression} : elements;
      }
      else {
        myElements = elements;
      }
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
    myElementFactory = JavaPsiFacade.getElementFactory(myManager.getProject());
    myStyleManager = CodeStyleManager.getInstance(myProject);
    myStyleSettings = editor != null ? CodeStyle.getSettings(editor) :
                      CodeStyle.getSettings(elements[0].getContainingFile());
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

  public void setPreviewSupported(boolean previewSupported) {
    myIsPreviewSupported = previewSupported;
  }

  public boolean isPreviewDuplicates() {
    return myPreviewDuplicates;
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
  public boolean prepare(@Nullable Consumer<? super ExtractMethodProcessor> pass) throws PrepareFailedException {
    if (myElements.length == 0) return false;
    myExpression = null;
    if (myElements.length == 1 && myElements[0] instanceof PsiExpression expression) {
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
      PsiElement context = codeFragment.getContext();
      LOG.assertTrue(context != null, "code fragment context is null: " + codeFragment.getClass());
      myCodeFragmentMember = ControlFlowUtil.findCodeFragment(context).getParent();
    }

    if (PsiTreeUtil.getParentOfType(myElements[0].isPhysical() ? myElements[0] : myCodeFragmentMember, PsiAnnotation.class) != null) {
      throw new PrepareFailedException(JavaRefactoringBundle.message("extract.method.error.annotation.value"), myElements[0]);
    }

    myControlFlowWrapper = new ControlFlowWrapper(codeFragment, myElements);

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

    PsiClass defaultTargetClass = Optional.ofNullable(myElements[0].getContainingFile())
                                          .map(ExtractMethodSnapshot.SNAPSHOT_KEY::get)
                                          .map(snapshot -> snapshot.getTargetClass())
                                          .orElse(null);
    return chooseTargetClass(codeFragment, pass, defaultTargetClass);
  }

  private boolean checkExitPoints() throws PrepareFailedException {
    PsiType expressionType = null;
    if (myExpression != null) {
      if (myForcedReturnType != null) {
        expressionType = myForcedReturnType;
      }
      else {
        expressionType = CommonJavaRefactoringUtil.getTypeByExpressionWithExpectedType(myExpression);
        if (expressionType == null && !(myExpression.getParent() instanceof PsiExpressionStatement)) {
          expressionType = PsiType.getJavaLangObject(myExpression.getManager(), GlobalSearchScope.allScope(myProject));
        }
      }
    }
    if (expressionType == null) {
      expressionType = PsiTypes.voidType();
    }
    myHasExpressionOutput = !PsiTypes.voidType().equals(expressionType);

    final PsiType returnStatementType = getExpectedReturnType();
    myHasReturnStatementOutput = myHasReturnStatement && returnStatementType != null && !PsiTypes.voidType().equals(returnStatementType);

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
      myReturnType = PsiTypes.booleanType();
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
      myTypeParameterList = CommonJavaRefactoringUtil.createTypeParameterListWithUsedTypeParameters(((PsiMethod)container).getTypeParameterList(),
                                                                                                    elements);
    }
    List<PsiClassType> exceptions = ExceptionUtil.getThrownCheckedExceptions(myElements);
    myThrownExceptions = exceptions.toArray(PsiClassType.EMPTY_ARRAY);

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
            public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
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
      if (returnStatementType != null && !PsiTypes.voidType().equals(returnStatementType)) {
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
    if (myCodeFragmentMember instanceof PsiParameterListOwner) {
      body = ((PsiParameterListOwner)myCodeFragmentMember).getBody();
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
        if (type == PsiTypes.nullType()) {
          it.remove(); // don't need to check
        }
        else if (type instanceof PsiPrimitiveType) {
          return false;
        }
      }
      else {
        if (type == PsiTypes.nullType()) {
          return false;
        }
        else if (type instanceof PsiPrimitiveType) {
          it.remove(); // don't need to check
        }
      }
    }
    if (returnedExpressions.isEmpty()) return true;

    final var dfaRunner = new StandardDataFlowRunner(myProject);
    final var returnChecker = new JavaDfaListener() {
      @Override
      public void beforeValueReturn(@NotNull DfaValue value,
                                    @Nullable PsiExpression expression,
                                    @NotNull PsiElement context,
                                    @NotNull DfaMemoryState state) {
        if (context == myCodeFragmentMember && expression != null &&
            returnedExpressions.stream().anyMatch(ret -> PsiTreeUtil.isAncestor(ret, expression, false))) {
          DfaNullability nullability = DfaNullability.fromDfType(state.getDfType(value));
          boolean result = nullability == (nullsExpected ? DfaNullability.NULL : DfaNullability.NOT_NULL);
          if (!result) {
            dfaRunner.cancel();
          }
        }
      }
    };
    return dfaRunner.analyzeMethod(body, returnChecker) == RunnerResult.OK;
  }

  protected boolean insertNotNullCheckIfPossible() {
    return true;
  }

  private boolean isNullInferred(String exprText) {
    final PsiCodeBlock block = myElementFactory.createCodeBlockFromText("{}", myElements[0]);
    for (PsiElement element : myElements) {
      block.add(element);
    }
    PsiReturnStatement statementFromText = (PsiReturnStatement)myElementFactory.createStatementFromText("return " + exprText + ";", null);
    statementFromText = (PsiReturnStatement)block.add(statementFromText);

    return inferNullability(block, Objects.requireNonNull(statementFromText.getReturnValue())) == Nullability.NOT_NULL;
  }

  private static Nullability inferNullability(@NotNull PsiCodeBlock block, @NotNull PsiExpression expr) {
    final var dfaRunner = new StandardDataFlowRunner(block.getProject());

    var interceptor = new JavaDfaListener() {
      DfaNullability myNullability = DfaNullability.NOT_NULL;
      boolean myVisited = false;

      @Override
      public void beforeExpressionPush(@NotNull DfaValue value,
                                       @NotNull PsiExpression expression,
                                       @NotNull DfaMemoryState state) {
        if (expression == expr) {
          myVisited = true;
          myNullability = myNullability.unite(DfaNullability.fromDfType(state.getDfType(value)));
        }
      }
    };
    final RunnerResult rc = dfaRunner.analyzeMethod(block, interceptor);
    return rc == RunnerResult.OK && interceptor.myVisited ? DfaNullability.toNullability(interceptor.myNullability) : Nullability.UNKNOWN;
  }

  protected boolean checkOutputVariablesCount() {
    int outputCount = (myHasExpressionOutput ? 1 : 0) + (myGenerateConditionalExit ? 1 : 0) + myOutputVariables.length;
    return outputCount > 1;
  }

  private void checkCanBeChainedConstructor() {
    if (!(myCodeFragmentMember instanceof PsiMethod method)) {
      return;
    }
    if (!method.isConstructor() || !PsiTypes.voidType().equals(myReturnType)) {
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
      @Override public void visitClass(final @NotNull PsiClass aClass) {
        localClasses.add(aClass);
      }

      @Override public void visitAnonymousClass(final @NotNull PsiAnonymousClass aClass) {
        visitElement(aClass);
      }

      @Override public void visitTypeParameter(final @NotNull PsiTypeParameter classParameter) {
        visitElement(classParameter);
      }
    });
    for(PsiClass localClass: localClasses) {
      final boolean classExtracted = isExtractedElement(localClass);
      final List<PsiElement> extractedReferences = Collections.synchronizedList(new ArrayList<>());
      final List<PsiElement> remainingReferences = Collections.synchronizedList(new ArrayList<>());
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
        throw new PrepareFailedException(JavaRefactoringBundle.message("extract.method.error.local.class.defined.outside"), extractedReferences.get(0));
      }
      if (!remainingReferences.isEmpty()) {
        throw new PrepareFailedException(JavaRefactoringBundle.message("extract.method.error.local.class.used.outside"), remainingReferences.get(0));
      }
      if (classExtracted) {
        for (PsiVariable variable : myControlFlowWrapper.getUsedVariables()) {
          if (isDeclaredInside(variable) && !variable.equals(myOutputVariable) && PsiUtil.resolveClassInType(variable.getType()) == localClass) {
            throw new PrepareFailedException(JavaRefactoringBundle.message("extract.method.error.local.class.variable.used.outside"), variable);
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
    if (dialog.isOK()) {
      apply(dialog);
      return true;
    }
    return false;
  }

  protected void apply(final AbstractExtractDialog dialog) {
    myMethodName = dialog.getChosenMethodName();
    myVariableDatum = dialog.getChosenParameters();
    myStatic = isStatic() || dialog.isMakeStatic();
    myIsChainedConstructor = dialog.isChainedConstructor();
    myMethodVisibility = dialog.getVisibility();

    final PsiType returnType = dialog.getReturnType();
    if (returnType != null) {
      myReturnType = returnType;
    }
    myPreviewDuplicates = dialog.isPreviewUsages();
  }

  protected AbstractExtractDialog createExtractMethodDialog(final boolean direct) {
    setDataFromInputVariables();
    myNullability = initNullability();
    myArtificialOutputVariable = PsiTypes.voidType().equals(myReturnType) ? getArtificialOutputVariable() : null;
    final PsiType returnType = myArtificialOutputVariable != null ? myArtificialOutputVariable.getType() : myReturnType;
    return new ExtractMethodDialog(myProject, myTargetClass, myInputVariables, returnType, getTypeParameterList(),
                                   getThrownExceptions(), isStatic(), isCanBeStatic(), myCanBeChainedConstructor,
                                   myRefactoringName, myHelpId, myNullability, myElements,
                                   this::estimateDuplicatesCount) {
      @Override
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

      @Override
      protected boolean isVoidReturn() {
        return myArtificialOutputVariable != null && !(myArtificialOutputVariable instanceof PsiField);
      }

      @Override
      protected void checkMethodConflicts(MultiMap<PsiElement, @NlsContexts.DialogMessage String> conflicts) {
        super.checkMethodConflicts(conflicts);
        final VariableData[] parameters = getChosenParameters();
        final Map<String, PsiLocalVariable> vars = new HashMap<>();
        for (PsiElement element : myElements) {
          element.accept(new JavaRecursiveElementWalkingVisitor() {
            @Override
            public void visitLocalVariable(@NotNull PsiLocalVariable variable) {
              super.visitLocalVariable(variable);
              vars.put(variable.getName(), variable);
            }

            @Override
            public void visitClass(@NotNull PsiClass aClass) {}
          });
        }
        for (VariableData parameter : parameters) {
          final String paramName = parameter.name;
          final PsiLocalVariable variable = vars.get(paramName);
          if (variable != null) {
            conflicts.putValue(variable, JavaRefactoringBundle.message("extract.method.conflict.variable", paramName));
          }
        }
      }

      @Override
      protected boolean hasPreviewButton() {
        return myIsPreviewSupported;
      }
    };
  }

  public void setDataFromInputVariables() {
    final List<VariableData> variables = myInputVariables.getInputVariables();
    myVariableDatum = variables.toArray(new VariableData[0]);
  }

  public PsiExpression[] findOccurrences() {
    if (myExpression != null) {
      return new PsiExpression[] {myExpression};
    }
    if (myOutputVariable != null) {
      final PsiElement scope = myOutputVariable instanceof PsiLocalVariable
                               ? CommonJavaRefactoringUtil.getVariableScope((PsiLocalVariable)myOutputVariable)
                               : PsiTreeUtil.findCommonParent(myElements);
      return CodeInsightUtil.findReferenceExpressions(scope, myOutputVariable);
    }
    final List<PsiStatement> filter = ContainerUtil.filter(myExitStatements, statement -> statement instanceof PsiReturnStatement && ((PsiReturnStatement)statement).getReturnValue() != null);
    final List<PsiExpression> map = ContainerUtil.map(filter, statement -> ((PsiReturnStatement)statement).getReturnValue());
    return map.toArray(PsiExpression.EMPTY_ARRAY);
  }

  private Nullability initNullability() {
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
        return Nullability.NULLABLE;
      }
      return DfaUtil.inferMethodNullability(emptyMethod);
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
      return ArrayUtilRt.toStringArray(getters);
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
    prepareNullability();
    ExtractMethodHandler.extractMethod(myProject, this);
  }

  public void prepareNullability() {
    myNullability = initNullability();
  }

  @TestOnly
  public void testPrepare() {
    prepareVariablesAndName();
  }

  public void prepareVariablesAndName(){
    myInputVariables.setFoldingAvailable(myInputVariables.isFoldingSelectedByDefault());
    myMethodName = myInitialMethodName;
    myVariableDatum = new VariableData[myInputVariables.getInputVariables().size()];
    for (int i = 0; i < myInputVariables.getInputVariables().size(); i++) {
      myVariableDatum[i] = myInputVariables.getInputVariables().get(i);
    }
  }

  public void setTargetClass(@Nullable PsiClass targetClass) {
    if (targetClass != null) {
      myTargetClass = targetClass;
      myNeedChangeContext = true;
    }
  }

  @TestOnly
  public void testPrepare(PsiType returnType, boolean makeStatic) throws PrepareFailedException{
    if (makeStatic) {
      if (!isCanBeStatic()) {
        throw new PrepareFailedException(JavaRefactoringBundle.message("extract.method.error.make.static"), myElements[0]);
      }
      myInputVariables.setPassFields(true);
      myStatic = true;
    }
    if (PsiTypes.voidType().equals(myReturnType)) {
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
    initDuplicates(null);

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
      if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(collectOverloads,
                                                                             JavaRefactoringBundle.message("collect.overloads"), true, myProject)) return;
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

  public void previewRefactoring(@Nullable Set<? extends TextRange> textRanges) {
    initDuplicates(textRanges);
    chooseAnchor();
  }

  protected void initDuplicates(@Nullable Set<? extends TextRange> textRanges) {
    myParametrizedDuplicates = ParametrizedDuplicates.findDuplicates(this, MatchType.PARAMETRIZED, textRanges);
    if (myParametrizedDuplicates != null && !myParametrizedDuplicates.isEmpty()) {
      myExactDuplicates = ParametrizedDuplicates.findDuplicates(this, MatchType.EXACT, textRanges);
    }
    myDuplicates = new ArrayList<>();
  }

  protected int estimateDuplicatesCount() {
    PsiElement[] elements = getFilteredElements();

    ReturnValue value;
    List<PsiVariable> parameters;
    if (myExpression != null) {
      value = null;
      parameters = Collections.emptyList();
    }
    else if (elements.length != 0) {
      value = myOutputVariable != null ? new VariableReturnValue(myOutputVariable) : null;
      parameters = Arrays.asList(myOutputVariables);
    }
    else {
      return 0;
    }
    DuplicatesFinder finder = new DuplicatesFinder(elements, myInputVariables.copy(), value, parameters);
    List<Match> myDuplicates = finder.findDuplicates(myTargetClass);
    if (!ContainerUtil.isEmpty(myDuplicates)) return myDuplicates.size();
    ParametrizedDuplicates parametrizedDuplicates = ParametrizedDuplicates.findDuplicates(this, MatchType.PARAMETRIZED, null);
    if (parametrizedDuplicates != null) {
      List<Match> duplicates = parametrizedDuplicates.getDuplicates();
      return duplicates != null ? duplicates.size() : 0;
    }
    return 0;
  }

  private PsiElement @NotNull [] getFilteredElements() {
    return StreamEx.of(myElements)
                   .filter(e -> !(e instanceof PsiWhiteSpace || e instanceof PsiComment || e instanceof PsiEmptyStatement))
                   .toArray(PsiElement.EMPTY_ARRAY);
  }

  public void doExtract() throws IncorrectOperationException {

    PsiMethod newMethod = generateEmptyMethod();

    myExpression = myInputVariables.replaceWrappedReferences(myElements, myExpression);
    renameInputVariables();

    LOG.assertTrue(myElements[0].isValid());

    PsiCodeBlock body = newMethod.getBody();
    myMethodCall = generateMethodCall(null, true, myExpression);

    LOG.assertTrue(myElements[0].isValid());

    final PsiStatement exitStatementCopy = prepareMethodBody(newMethod, true);

    if (myExpression == null) {
      if (isNeedToChangeCallContext()) {
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
          ifStatement = generateConditionalExitStatement(varName);
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
        addToMethodCallLocation(generateNotNullConditionalStatement(varName));
        declareVariableReusedAfterCall(myOutputVariable);
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
      myExpression = (PsiExpression)IntroduceVariableUtil.replace(expression2Replace, myMethodCall, myProject);
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
        final PsiElement parent = element.getParent();
        if (parent instanceof PsiTypeCastExpression typeCastExpression) {
          final PsiTypeElement castType = typeCastExpression.getCastType();
          if (castType != null && Comparing.equal(castType.getType(), paramType)) {
            RemoveRedundantCastUtil.removeCast(typeCastExpression);
          }
        }
      }
    }

    if (myNullability != null &&
        PsiUtil.resolveClassInType(newMethod.getReturnType()) != null &&
        PropertiesComponent.getInstance(myProject).getBoolean(ExtractMethodDialog.EXTRACT_METHOD_GENERATE_ANNOTATIONS, true)) {
      NullableNotNullManager nullManager = NullableNotNullManager.getInstance(myProject);
      switch (myNullability) {
        case NOT_NULL ->
          updateAnnotations(newMethod, nullManager.getNullables(), nullManager.getDefaultNotNull(), nullManager.getNotNulls());
        case NULLABLE ->
          updateAnnotations(newMethod, nullManager.getNotNulls(), nullManager.getDefaultNullable(), nullManager.getNullables());
        default -> { }
      }
    }

    myExtractedMethod = addExtractedMethod(newMethod);
    if (isNeedToChangeCallContext()) {
      ChangeContextUtil.decodeContextInfo(myExtractedMethod, myTargetClass, RefactoringChangeUtil.createThisExpression(myManager, null));
      if (myMethodCall.resolveMethod() != myExtractedMethod) {
        final PsiReferenceExpression methodExpression = myMethodCall.getMethodExpression();
        RefactoringChangeUtil.qualifyReference(methodExpression, myExtractedMethod, PsiUtil.getEnclosingStaticElement(methodExpression, myTargetClass) != null ? myTargetClass : null);
      }
    }
  }

  @NotNull
  private PsiIfStatement generateConditionalExitStatement(String varName) {
    if (myFirstExitStatementCopy instanceof PsiReturnStatement && ((PsiReturnStatement)myFirstExitStatementCopy).getReturnValue() != null) {
      return (PsiIfStatement)myElementFactory.createStatementFromText("if (" + varName + "==null) return null;", null);
    }
    return (PsiIfStatement)myElementFactory.createStatementFromText("if (" + varName + "==null) " + myFirstExitStatementCopy.getText(), null);
  }

  @NotNull
  private PsiStatement generateNotNullConditionalStatement(String varName) {
    return myElementFactory.createStatementFromText("if (" + varName + " != null) return " + varName + ";", null);
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
        final PsiExpression returnValue = CommonJavaRefactoringUtil.convertInitializerToNormalExpression(myExpression, myForcedReturnType);
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
          final PsiVariable variable = ObjectUtils.tryCast(((PsiReferenceExpression)returnValue).resolve(), PsiVariable.class);
          if (variable != null && Comparing.strEqual(variable.getName(), outVariableName)) {
            final PsiStatement statement = PsiTreeUtil.getPrevSiblingOfType(insertedReturnStatement, PsiStatement.class);
            if (statement instanceof PsiDeclarationStatement) {
              final PsiElement[] declaredElements = ((PsiDeclarationStatement)statement).getDeclaredElements();
              if (ArrayUtil.find(declaredElements, variable) != -1) {
                InlineUtil.inlineVariable(variable, PsiUtil.skipParenthesizedExprDown(variable.getInitializer()),
                                          (PsiReferenceExpression)returnValue);
                variable.delete();
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
    return myArtificialOutputVariable != null && !PsiTypes.voidType().equals(myReturnType) && !myIsChainedConstructor;
  }

  private boolean hasNormalExit() {
    try {
      PsiCodeBlock block = JavaPsiFacade.getElementFactory(myProject).createCodeBlock();
      block.addRange(myElements[0], myElements[myElements.length - 1]);
      ControlFlow flow = ControlFlowFactory.getControlFlow(block, new LocalsControlFlowPolicy(block),
                                                           ControlFlowOptions.NO_CONST_EVALUATE);
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
    return myNeedChangeContext;
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
      if (myStyleSettings.getCustomSettings(JavaCodeStyleSettings.class).GENERATE_FINAL_PARAMETERS) {
        method.accept(new JavaRecursiveElementVisitor() {

          @Override public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
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
          @Override public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
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

  @Override
  public List<Match> getDuplicates() {
    if (myIsChainedConstructor) {
      return filterChainedConstructorDuplicates(myDuplicates);
    }
    return myDuplicates;
  }

  @Nullable
  public ParametrizedDuplicates getParametrizedDuplicates() {
    return myParametrizedDuplicates;
  }

  @Nullable
  public List<Match> getAnyDuplicates() {
    return Optional.ofNullable(getParametrizedDuplicates())
      .map(ParametrizedDuplicates::getDuplicates)
      .orElse(getDuplicates());
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

  @Override
  public PsiElement processMatch(Match match) throws IncorrectOperationException {
    if (PsiTreeUtil.isContextAncestor(myExtractedMethod.getContainingClass(), match.getMatchStart(), false) &&
        CommonJavaRefactoringUtil.isInStaticContext(match.getMatchStart(), myExtractedMethod.getContainingClass())) {
      PsiUtil.setModifierProperty(myExtractedMethod, PsiModifier.STATIC, true);
    }
    final PsiMethodCallExpression methodCallExpression = generateMethodCall(match.getInstanceExpression(), false, match.getMatchStart());

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
          if (val instanceof PsiExpression && isCastRequired(data, (PsiExpression)val)) {
            final PsiTypeCastExpression cast = (PsiTypeCastExpression)elementFactory.createExpressionFromText("(A)a", val);
            cast.getCastType().replace(elementFactory.createTypeElement(data.type));
            cast.getOperand().replace(val.copy());
            val = cast;
          }
          methodCallExpression.getArgumentList().add(val);
        }
      } else {
        methodCallExpression.getArgumentList().add(myElementFactory.createExpressionFromText(data.variable.getName(), methodCallExpression));
      }
    }
    List<String> reusedVariables = findReusedVariables(match, myInputVariables, myOutputVariable);
    PsiElement replacedMatch = match.replace(myExtractedMethod, methodCallExpression, myOutputVariable, myReturnType);

    PsiElement appendLocation = addNotNullConditionalCheck(match, replacedMatch);
    declareReusedVariables(appendLocation, reusedVariables);
    return replacedMatch;
  }

  private static boolean isCastRequired(@NotNull VariableData data, @NotNull PsiExpression val) {
    final PsiType exprType = val.getType();
    if (exprType == null || TypeConversionUtil.isAssignable(data.type, exprType)) {
      return false;
    }
    final PsiClass psiClass = PsiUtil.resolveClassInClassTypeOnly(data.type);
    return !(psiClass instanceof PsiTypeParameter);
  }

  @NotNull
  private static List<String> findReusedVariables(@NotNull Match match,
                                                  @NotNull InputVariables inputVariables,
                                                  @Nullable PsiVariable outputVariable) {
    Set<PsiLocalVariable> ignoreVariables = Collections.emptySet();
    ReturnValue returnValue = match.getOutputVariableValue(outputVariable);
    if (returnValue instanceof VariableReturnValue) {
      PsiVariable returnedVariable = ((VariableReturnValue)returnValue).getVariable();
      if (returnedVariable instanceof PsiLocalVariable) {
        ignoreVariables = Collections.singleton((PsiLocalVariable)returnedVariable);
      }
    }
    List<ReusedLocalVariable> reusedLocalVariables =
      ReusedLocalVariablesFinder.findReusedLocalVariables(match.getMatchStart(), match.getMatchEnd(), ignoreVariables, inputVariables);
    if (!reusedLocalVariables.isEmpty()) {
      List<String> result = new ArrayList<>();
      for (ReusedLocalVariable variable : reusedLocalVariables) {
        if (!variable.reuseValue()) {
          result.add(variable.getDeclarationText());
        }
      }
      return result;
    }

    return Collections.emptyList();
  }

  private static void declareReusedVariables(@NotNull PsiElement appendLocation, @NotNull List<String> reusedVariables) {
    if (reusedVariables.isEmpty()) {
      return;
    }
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(appendLocation.getProject());

    for (String variable : reusedVariables) {
      PsiStatement declaration = factory.createStatementFromText(variable, appendLocation);
      appendLocation = appendLocation.getParent().addAfter(declaration, appendLocation);
    }
  }

  private PsiElement addNotNullConditionalCheck(Match match, PsiElement replacedMatch) {
    if ((myNotNullConditionalCheck || myGenerateConditionalExit) && myOutputVariable != null) {
      ReturnValue returnValue = match.getOutputVariableValue(myOutputVariable);
      if (returnValue instanceof VariableReturnValue) {
        String varName = ((VariableReturnValue)returnValue).getVariable().getName();
        LOG.assertTrue(varName != null, "returned variable name is null");
        PsiStatement statement = PsiTreeUtil.getParentOfType(replacedMatch, PsiStatement.class, false);
        if (statement != null) {
          PsiStatement conditionalExit = myNotNullConditionalCheck ?
                                         generateNotNullConditionalStatement(varName) : generateConditionalExitStatement(varName);
          return statement.getParent().addAfter(conditionalExit, statement);
        }
      }
    }
    return replacedMatch;
  }

  @Nullable
  protected PsiMethodCallExpression getMatchMethodCallExpression(PsiElement element) {
    PsiMethodCallExpression methodCallExpression = null;
    if (element instanceof PsiMethodCallExpression) {
      methodCallExpression = (PsiMethodCallExpression)element;
    }
    else if (element instanceof PsiExpressionStatement) {
      final PsiExpression expression = ((PsiExpressionStatement)element).getExpression();
      if (expression instanceof PsiMethodCallExpression) {
        methodCallExpression = (PsiMethodCallExpression)expression;
      }
      else if (expression instanceof PsiAssignmentExpression) {
        final PsiExpression psiExpression = ((PsiAssignmentExpression)expression).getRExpression();
        if (psiExpression instanceof PsiMethodCallExpression) {
          methodCallExpression = (PsiMethodCallExpression)psiExpression;
        }
      }
    }
    else if (element instanceof PsiDeclarationStatement) {
      final PsiElement[] declaredElements = ((PsiDeclarationStatement)element).getDeclaredElements();
      for (PsiElement declaredElement : declaredElements) {
        if (declaredElement instanceof PsiLocalVariable) {
          final PsiExpression initializer = ((PsiLocalVariable)declaredElement).getInitializer();
          if (initializer instanceof PsiMethodCallExpression) {
            methodCallExpression = (PsiMethodCallExpression)initializer;
            break;
          }
        }
      }
    }
    else if (element instanceof PsiIfStatement) {
      PsiExpression condition = ((PsiIfStatement)element).getCondition();
      if (condition instanceof PsiMethodCallExpression) {
        methodCallExpression = (PsiMethodCallExpression)condition;
      }
    }
    return methodCallExpression;
  }

  protected void deleteExtracted() throws IncorrectOperationException {
    if (myEnclosingBlockStatement == null) {
      myElements[0].getParent().deleteChildRange(myElements[0], myElements[myElements.length - 1]);
    }
    else {
      myEnclosingBlockStatement.delete();
    }
  }

  protected PsiElement addToMethodCallLocation(PsiStatement newStatement) throws IncorrectOperationException {
    if (myAddedToMethodCallLocation != null) {
      PsiCodeBlock block = myAddedToMethodCallLocation.getCodeBlock();
      return block.addBefore(newStatement, block.getRBrace());
    }

    PsiElement location;
    PsiStatement oldStatement;
    if (myEnclosingBlockStatement != null) {
      location = oldStatement = myEnclosingBlockStatement;
    }
    else {
      PsiElement element = myExpression != null ? myExpression : myElements[0];
      PsiComment comment = PsiTreeUtil.getParentOfType(element, PsiComment.class, false);

      if (comment == null) {
        location = oldStatement = PsiTreeUtil.getParentOfType(element, PsiStatement.class, false);
      }
      else {
        location = comment;
        oldStatement = StreamEx.of(myElements)
          .filter(e -> !(e instanceof PsiComment) && !(e instanceof PsiWhiteSpace))
          .map(e -> PsiTreeUtil.getParentOfType(e, PsiStatement.class, false))
          .nonNull()
          .findFirst()
          .orElse(null);
      }
    }

    LOG.assertTrue(location != null, "Can't find statement/comment at the extracted location");

    PsiElement parent = location.getParent();
    if (isBranchOrBody(parent, oldStatement)) {
      // The parent statement will be inconsistent until deleting the extracted part:
      // the block statement is being added just before a then/else branch or loop body,
      // but the original then/else branch or loop body will be deleted in the end so it's fine.
      // Example: "if(..) oldStatement" -> "if(..) { newStatement } oldStatement" -> "if(..) { newStatement }"
      myAddedToMethodCallLocation = (PsiBlockStatement)myElementFactory.createStatementFromText("{}", oldStatement);
      myAddedToMethodCallLocation = (PsiBlockStatement)parent.addBefore(myAddedToMethodCallLocation, location);
      PsiCodeBlock block = myAddedToMethodCallLocation.getCodeBlock();
      return block.addBefore(newStatement, block.getRBrace());
    }
    return parent.addBefore(newStatement, location);
  }

  @Contract("_,null -> false; null,_ -> false")
  private static boolean isBranchOrBody(PsiElement parent, PsiElement element) {
    if (element == null) {
      return false;
    }
    if (parent instanceof PsiIfStatement) {
      return (((PsiIfStatement)parent).getThenBranch() == element || ((PsiIfStatement)parent).getElseBranch() == element);
    }
    if (parent instanceof PsiLoopStatement) {
      return ((PsiLoopStatement)parent).getBody() == element;
    }
    return false;
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
          if (element instanceof PsiReferenceExpression referenceExpression) {
            final PsiExpression qualifierExpression = referenceExpression.getQualifierExpression();
            if (qualifierExpression instanceof PsiQualifiedExpression) {
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

    boolean isFinal = myStyleSettings.getCustomSettings(JavaCodeStyleSettings.class).GENERATE_FINAL_PARAMETERS;
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
      throwsList.add(JavaPsiFacade.getElementFactory(myManager.getProject()).createReferenceElementByType(exception));
    }

    if (myTargetClass.isInterface()) {
      updateModifiersInInterface(newMethod);
    }
    return (PsiMethod)myStyleManager.reformat(newMethod);
  }

  private void updateModifiersInInterface(PsiMethod newMethod) {
    LanguageLevel languageLevel = PsiUtil.getLanguageLevel(myTargetClass);
    if (languageLevel.isAtLeast(LanguageLevel.JDK_1_8)) {
      if (!isStatic()) {
        final PsiMethod containingMethod = PsiTreeUtil.getParentOfType(myCodeFragmentMember, PsiMethod.class, false);
        if (containingMethod != null && containingMethod.hasModifierProperty(PsiModifier.DEFAULT)) {
          if (languageLevel.isAtLeast(LanguageLevel.JDK_1_9)) {
            PsiUtil.setModifierProperty(newMethod, PsiModifier.PRIVATE, true); // don't increase the API surface
          }
          else {
            PsiUtil.setModifierProperty(newMethod, PsiModifier.DEFAULT, true);
          }
        }
      }
      PsiUtil.setModifierProperty(newMethod, PsiModifier.PUBLIC, false);
      PsiUtil.setModifierProperty(newMethod, PsiModifier.PROTECTED, false);
      if (isStatic() || !languageLevel.isAtLeast(LanguageLevel.JDK_1_9)) {
        PsiUtil.setModifierProperty(newMethod, PsiModifier.PRIVATE, false);
      }
    }
  }

  protected boolean defineVariablesForUnselectedParameters() {
    return true;
  }

  private void copyParamAnnotations(PsiParameter parm) {
    final PsiVariable variable = PsiResolveHelper.getInstance(myProject).resolveReferencedVariable(parm.getName(), myElements[0]);
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

    if (AnnotationUtil.isAnnotated(variable, nullableAnnotations, CHECK_TYPE) ||
        AnnotationUtil.isAnnotated(variable, notNullAnnotations, CHECK_TYPE) ||
        PropertiesComponent.getInstance(myProject).getBoolean(ExtractMethodDialog.EXTRACT_METHOD_GENERATE_ANNOTATIONS, false)) {
      final Nullability nullability = inferNullability(variable, myElements[0]);
      if (nullability != Nullability.UNKNOWN) {
        boolean isNotNull = nullability == Nullability.NOT_NULL;
        final List<String> toKeep = isNotNull ? notNullAnnotations : nullableAnnotations;
        final List<String> toRemove = isNotNull ? nullableAnnotations : notNullAnnotations;
        final String toAdd = isNotNull ? nullabilityManager.getDefaultNotNull() : nullabilityManager.getDefaultNullable();
        updateAnnotations(parm, toRemove, toAdd, toKeep);
      }
    }
  }

  private void updateAnnotations(PsiModifierListOwner owner, List<String> toRemove, String toAdd, List<String> toKeep) {
    AddAnnotationPsiFix.removePhysicalAnnotations(owner, ArrayUtilRt.toStringArray(toRemove));
    PsiModifierList modifierList = owner.getModifierList();
    if (modifierList != null && !AnnotationUtil.isAnnotated(owner, toKeep, CHECK_TYPE)) {
      PsiAnnotation annotation = AddAnnotationPsiFix.addPhysicalAnnotationIfAbsent(toAdd, PsiNameValuePair.EMPTY_ARRAY, modifierList);
      if (annotation != null) {
        JavaCodeStyleManager.getInstance(myProject).shortenClassReferences(annotation);
      }
    }
  }

  @NotNull
  private static Nullability inferNullability(@NotNull PsiVariable variable, PsiElement startElement) {
    String variableName = variable.getName();
    if (variableName == null) return Nullability.UNKNOWN;

    PsiElement methodOrLambdaBody = null;
    if (variable instanceof PsiPatternVariable) {
      return Nullability.NOT_NULL;
    }
    if (variable instanceof PsiLocalVariable || variable instanceof PsiParameter) {
      final PsiParameterListOwner methodOrLambda = PsiTreeUtil.getParentOfType(variable, PsiMethod.class, PsiLambdaExpression.class);
      if (methodOrLambda != null) {
        methodOrLambdaBody = methodOrLambda.getBody();
      }
    }
    if (methodOrLambdaBody instanceof PsiCodeBlock) {
      PsiElement topmostLambdaOrAnonymousClass = null;
      for (PsiElement element = startElement; element != null && element != methodOrLambdaBody; element = element.getParent()) {
        if (element instanceof PsiLambdaExpression || element instanceof PsiAnonymousClass) {
          topmostLambdaOrAnonymousClass = element;
        }
      }
      if (topmostLambdaOrAnonymousClass != null) {
        startElement = topmostLambdaOrAnonymousClass;
      }

      Project project = methodOrLambdaBody.getProject();
      PsiFile file = methodOrLambdaBody.getContainingFile();
      final PsiFile copy = PsiFileFactory.getInstance(project)
        .createFileFromText(file.getName(), file.getFileType(), file.getText(), file.getModificationStamp(), false);

      PsiCodeBlock bodyCopy = findCopy(copy, methodOrLambdaBody, PsiCodeBlock.class);
      if (startElement instanceof PsiExpression) {
        startElement = PsiTreeUtil.getParentOfType(startElement, PsiStatement.class);
      }
      if (startElement instanceof PsiStatement) {
        PsiStatement startStatementCopy = findCopy(copy, startElement, PsiStatement.class);

        try {
          PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
          startStatementCopy = wrapWithBlockStatementIfNeeded(startStatementCopy, factory);
          String dummyName = JavaCodeStyleManager.getInstance(project).suggestUniqueVariableName("_Dummy_", startElement.getParent(), true);
          PsiDeclarationStatement declarationStatement = (PsiDeclarationStatement)factory.createStatementFromText(
            CommonClassNames.JAVA_LANG_OBJECT + " " + dummyName + " = " + variableName + ";", startStatementCopy);

          PsiElement parent = startStatementCopy.getParent();
          declarationStatement = (PsiDeclarationStatement)parent.addBefore(declarationStatement, startStatementCopy);
          PsiElement[] declaredElements = declarationStatement.getDeclaredElements();
          PsiExpression initializer = Objects.requireNonNull(((PsiVariable)declaredElements[0]).getInitializer());

          return inferNullability(bodyCopy, initializer);
        }
        catch (IncorrectOperationException ignore) {
          return Nullability.UNKNOWN;
        }
      }
    }
    return Nullability.UNKNOWN;
  }

  private static <T extends PsiElement> T findCopy(@NotNull PsiFile copy, @NotNull PsiElement element, @NotNull Class<T> clazz) {
    TextRange range = element.getTextRange();
    return CodeInsightUtil.findElementInRange(copy, range.getStartOffset(), range.getEndOffset(), clazz);
  }

  private static PsiStatement wrapWithBlockStatementIfNeeded(@NotNull PsiStatement statement, @NotNull PsiElementFactory factory) {
    PsiElement parent = statement.getParent();
    if (parent instanceof PsiLoopStatement && ((PsiLoopStatement)parent).getBody() == statement ||
        parent instanceof PsiIfStatement &&
        (((PsiIfStatement)parent).getThenBranch() == statement || ((PsiIfStatement)parent).getElseBranch() == statement)) {
      PsiBlockStatement blockStatement = (PsiBlockStatement)factory.createStatementFromText("{}", statement);
      blockStatement.getCodeBlock().add(statement);
      blockStatement = (PsiBlockStatement)statement.replace(blockStatement);
      return blockStatement.getCodeBlock().getStatements()[0];
    }
    if (parent instanceof PsiForStatement) {
      if (((PsiForStatement)parent).getInitialization() == statement || ((PsiForStatement)parent).getUpdate() == statement) {
        return wrapWithBlockStatementIfNeeded((PsiForStatement)parent, factory);
      }
    }
    return statement;
  }

  @NotNull
  protected PsiMethodCallExpression generateMethodCall(PsiExpression instanceQualifier, final boolean generateArgs, PsiElement context) {
    @NonNls StringBuilder buffer = new StringBuilder();

    final boolean skipInstanceQualifier;
    if (myIsChainedConstructor) {
      skipInstanceQualifier = true;
      buffer.append(PsiKeyword.THIS);
    }
    else {
      skipInstanceQualifier = instanceQualifier == null || instanceQualifier instanceof PsiThisExpression;
      if (skipInstanceQualifier) {
        if (isNeedToChangeCallContext()) {
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

    PsiMethodCallExpression expr = (PsiMethodCallExpression)myElementFactory.createExpressionFromText(text, context);
    expr = (PsiMethodCallExpression)myStyleManager.reformat(expr);
    if (!skipInstanceQualifier) {
      PsiExpression qualifierExpression = expr.getMethodExpression().getQualifierExpression();
      LOG.assertTrue(qualifierExpression != null);
      qualifierExpression.replace(instanceQualifier);
    }
    return (PsiMethodCallExpression)JavaCodeStyleManager.getInstance(myProject).shortenClassReferences(expr);
  }

  private boolean chooseTargetClass(PsiElement codeFragment,
                                    final Consumer<? super ExtractMethodProcessor> extractPass,
                                    @Nullable PsiClass defaultTargetClass) throws PrepareFailedException {
    final List<PsiVariable> inputVariables = myControlFlowWrapper.getInputVariables(codeFragment, myElements, myOutputVariables);

    myNeedChangeContext = false;
    myTargetClass = myCodeFragmentMember instanceof PsiMember
                    ? ((PsiMember)myCodeFragmentMember).getContainingClass()
                    : PsiTreeUtil.getParentOfType(myCodeFragmentMember, PsiClass.class);
    if (myTargetClass == null) {
      LOG.error("Unable to find target class. Parents are: "+
                StreamEx.iterate(myCodeFragmentMember, PsiElement::getParent)
                  .takeWhileInclusive(e -> e != null && !(e instanceof PsiFileSystemItem))
                  .map(e -> e.getClass().getSimpleName()).joining(","));
    }
    if (!shouldAcceptCurrentTarget(extractPass, myTargetClass)) {

      final LinkedHashMap<PsiClass, List<PsiVariable>> classes = new LinkedHashMap<>();
      final PsiElementProcessor<PsiClass> processor = new PsiElementProcessor<>() {
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
                .showErrorHint(myProject, myEditor, e.getMessage(), ExtractMethodHandler.getRefactoringName(), HelpID.EXTRACT_METHOD);
              ExtractMethodHandler.highlightPrepareError(e, e.getFile(), myEditor, myProject);
            }
            return false;
          }
        }
      };

      classes.put(myTargetClass, null);
      PsiElement target = myTargetClass.getParent();
      PsiElement targetMember = myTargetClass;
      while (!(target instanceof PsiFile)) {
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
        if (defaultTargetClass != null) {
          return processor.execute(defaultTargetClass);
        }
        final PsiClass[] psiClasses = classes.keySet().toArray(PsiClass.EMPTY_ARRAY);
        if (myEditor == null) {
          return processor.execute(psiClasses[0]);
        }
        final PsiClass preselection = AnonymousTargetClassPreselectionUtil.getPreselection(classes.keySet(), psiClasses[0]);
        new PsiTargetNavigator<>(psiClasses).selection(preselection).createPopup(myProject, RefactoringBundle.message("choose.destination.class"), processor)
          .showInBestPositionFor(myEditor);
        return true;
      }
    }

    return applyChosenClassAndExtract(inputVariables, extractPass);
  }

  @NotNull
  protected Set<PsiVariable> getEffectivelyLocalVariables() {
    Set<PsiVariable> effectivelyLocal = new LinkedHashSet<>();
    List<PsiVariable> usedVariables = myControlFlowWrapper.getUsedVariablesInBody(ControlFlowUtil.findCodeFragment(myElements[0]), myOutputVariables);
    for (PsiVariable variable : usedVariables) {
      boolean toDeclare = !isDeclaredInside(variable) && myInputVariables.toDeclareInsideBody(variable);
      if (toDeclare) {
        effectivelyLocal.add(variable);
      }
    }

    if (myArtificialOutputVariable instanceof PsiField && !myIsChainedConstructor) {
      effectivelyLocal.add(myArtificialOutputVariable);
    }
    return effectivelyLocal;
  }

  private void declareNecessaryVariablesInsideBody(PsiCodeBlock body) throws IncorrectOperationException {
    Set<PsiVariable> effectivelyLocal = getEffectivelyLocalVariables();
    for (PsiVariable variable : effectivelyLocal) {
      String name = variable.getName();
      LOG.assertTrue(name != null, "variable name is null");
      PsiDeclarationStatement statement = myElementFactory.createVariableDeclarationStatement(name, variable.getType(), null);
      body.add(statement);
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

  private static boolean shouldAcceptCurrentTarget(java.util.function.Consumer<? super ExtractMethodProcessor> extractPass, PsiElement target) {
    return extractPass == null && !(target instanceof PsiAnonymousClass);
  }

  private boolean applyChosenClassAndExtract(List<? extends PsiVariable> inputVariables, @Nullable Consumer<? super ExtractMethodProcessor> extractPass)
    throws PrepareFailedException {
    myStatic = shouldBeStatic();
    final Set<PsiField> fields = new LinkedHashSet<>();
    myCanBeStatic = CommonJavaRefactoringUtil.canBeStatic(myTargetClass, myCodeFragmentMember, myElements, fields);

    myInputVariables = new InputVariables(inputVariables, myProject, new LocalSearchScope(myElements), isFoldingApplicable(), fields);

    if (!checkExitPoints()){
      return false;
    }

    checkCanBeChainedConstructor();

    if (extractPass != null) {
      extractPass.accept(this);
    }
    return true;
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

  private void declareVariableReusedAfterCall(PsiVariable variable) {
    if (variable != null &&
        variable.getName() != null &&
        isDeclaredInside(variable) &&
        myControlFlowWrapper.getUsedVariables().contains(variable) &&
        !myControlFlowWrapper.needVariableValueAfterEnd(variable)) {

      PsiDeclarationStatement declaration =
        myElementFactory.createVariableDeclarationStatement(variable.getName(), variable.getType(), null);
      addToMethodCallLocation(declaration);
    }
  }

  private void showMultipleExitPointsMessage() {
    if (myShowErrorDialogs) {
      HighlightManager highlightManager = HighlightManager.getInstance(myProject);
      PsiStatement[] exitStatementsArray = myExitStatements.toArray(PsiStatement.EMPTY_ARRAY);
      highlightManager.addOccurrenceHighlights(myEditor, exitStatementsArray, EditorColors.SEARCH_RESULT_ATTRIBUTES, true, null);
      String message = RefactoringBundle
        .getCannotRefactorMessage(JavaRefactoringBundle.message("there.are.multiple.exit.points.in.the.selected.code.fragment"));
      CommonRefactoringUtil.showErrorHint(myProject, myEditor, message, myRefactoringName, myHelpId);
      WindowManager.getInstance().getStatusBar(myProject).setInfo(RefactoringBundle.message("press.escape.to.remove.the.highlighting"));
    }
  }

  protected void showMultipleOutputMessage(PsiType expressionType) throws PrepareFailedException {
    if (myShowErrorDialogs) {
      String message = buildMultipleOutputMessageError(expressionType) + "\n"
                       + JavaRefactoringBundle.message("extract.method.object.suggestion");

      if (ApplicationManager.getApplication().isUnitTestMode()) throw new RuntimeException(message);
      RefactoringMessageDialog dialog = new RefactoringMessageDialog(myRefactoringName, message, myHelpId, "OptionPane.errorIcon", true,
                                                                     myProject);
      if (dialog.showAndGet()) {
        new ExtractMethodObjectHandler()
          .invoke(myProject, myEditor, myTargetClass.getContainingFile(), DataManager.getInstance().getDataContext());
      }
    }
  }

  @NotNull
  protected @NlsContexts.DialogMessage String buildMultipleOutputMessageError(@NotNull PsiType expressionType) {
    @Nls
    StringBuilder buffer = new StringBuilder();
    buffer.append(RefactoringBundle.getCannotRefactorMessage(
      JavaRefactoringBundle.message("there.are.multiple.output.values.for.the.selected.code.fragment")));
    buffer.append("\n");
    if (myHasExpressionOutput) {
      buffer.append("    ").append(JavaRefactoringBundle.message("expression.result")).append(": ");
      buffer.append(PsiFormatUtil.formatType(expressionType, 0, PsiSubstitutor.EMPTY));
      buffer.append(",\n");
    }
    if (myGenerateConditionalExit) {
      buffer.append("    ").append(JavaRefactoringBundle.message("boolean.method.result"));
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

  public void setExtractedMethod(PsiMethod method){
    myExtractedMethod = method;
  }

  public void setMethodName(String methodName) {
    myMethodName = methodName;
  }

  public PsiElement getAnchor() {
    return myAnchor;
  }

  @Override
  public Boolean hasDuplicates() {
    List<Match> duplicates = getDuplicates();
    if (duplicates != null && !duplicates.isEmpty()) {
      return true;
    }

    if (initParametrizedDuplicates(true)) return null;
    return false;
  }

  public boolean initParametrizedDuplicates(boolean showDialog) {
    if (myExtractedMethod != null && myParametrizedDuplicates != null) {
      myDuplicates = myParametrizedDuplicates.getDuplicates();
      if (myDuplicates.isEmpty()) {
        return false;
      }
      boolean isSignatureUnchanged = myDuplicates.stream()
                                                 .map(Match::getExtractedParameters)
                                                 .allMatch(List::isEmpty);
      boolean isFoldable = myInputVariables.isFoldable();
      if (!showDialog || isSignatureUnchanged || isFoldable ||
          shouldChangeSignature()) {

        myDuplicates = myParametrizedDuplicates.getDuplicates();
        if (myExtractedMethod.isPhysical()) {
          WriteCommandAction.runWriteCommandAction(myProject, () -> {
            PsiDocumentManager.getInstance(myProject).commitAllDocuments();
            replaceParametrizedMethod();
          });
        }
        else {
          replaceParametrizedMethod();
        }
        myVariableDatum = myParametrizedDuplicates.getVariableDatum();
        return true;
      }
    }
    myDuplicates = myExactDuplicates != null ? myExactDuplicates.getDuplicates() : new ArrayList<>();
    return !myDuplicates.isEmpty();
  }

  private boolean shouldChangeSignature() {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      //noinspection TestOnlyProblems
      return Optional.of(myExtractedMethod)
                     .map(PsiElement::getContainingFile)
                     .map(psiFile -> psiFile.getUserData(SIGNATURE_CHANGE_ALLOWED))
                     .orElse(true);
    }
    return new SignatureSuggesterPreviewDialog(myExtractedMethod, myParametrizedDuplicates.getParametrizedMethod(),
                                               myMethodCall, myParametrizedDuplicates.getParametrizedCall(), 0,
                                               myParametrizedDuplicates.getSize()).showAndGet();
  }

  private void replaceParametrizedMethod() {
    LOG.assertTrue(myParametrizedDuplicates != null);
    myExtractedMethod = myParametrizedDuplicates.replaceMethod(myExtractedMethod);
    myMethodCall = myParametrizedDuplicates.replaceCall(myMethodCall);
  }

  public boolean hasDuplicates(Set<? extends VirtualFile> files) {
    initDuplicates(null);
    final DuplicatesFinder finder = getExactDuplicatesFinder();

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
  protected DuplicatesFinder getExactDuplicatesFinder() {
    DuplicatesFinder finder = null;
    PsiElement[] elements = getFilteredElements();
    if (myExpression != null) {
      finder = new DuplicatesFinder(elements, myInputVariables.copy(), Collections.emptyList());
    }
    else if (elements.length != 0) {
      finder = new DuplicatesFinder(elements, myInputVariables.copy(),
                                    myOutputVariable != null ? new VariableReturnValue(myOutputVariable) : null,
                                    Arrays.asList(myOutputVariables));
    }
    return finder;
  }

  @Override
  @Nullable
  public String getConfirmDuplicatePrompt(Match match) {
    final boolean needToBeStatic = CommonJavaRefactoringUtil.isInStaticContext(match.getMatchStart(), myExtractedMethod.getContainingClass());
    final String changedSignature = MatchUtil
      .getChangedSignature(match, myExtractedMethod, needToBeStatic, VisibilityUtil.getVisibilityStringToDisplay(myExtractedMethod));
    if (changedSignature != null) {
      return JavaRefactoringBundle.message("replace.this.code.fragment.and.change.signature", changedSignature);
    }
    if (needToBeStatic && !myExtractedMethod.hasModifierProperty(PsiModifier.STATIC)) {
      return JavaRefactoringBundle.message("replace.this.code.fragment.and.make.method.static");
    }
    return null;
  }

  @NotNull
  public UniqueNameGenerator getParameterNameGenerator(PsiElement scopeElement) {
    UniqueNameGenerator uniqueNameGenerator = new UniqueNameGenerator();
    for (VariableData data : myInputVariables.getInputVariables()) {
      if (data.variable != null) {
        String name = data.variable.getName();
        if (name != null) uniqueNameGenerator.addExistingName(name);
      }
    }
    for (PsiVariable variable : myOutputVariables) {
      String name = variable.getName();
      if (name != null) uniqueNameGenerator.addExistingName(name);
    }
    PsiElement superParent = PsiTreeUtil.getParentOfType(scopeElement, PsiMember.class, PsiLambdaExpression.class);
    if (superParent != null) {
      SyntaxTraverser.psiTraverser().withRoot(superParent)
        .filter(element -> element instanceof PsiVariable)
        .forEach(element -> {
          String name = ((PsiVariable)element).getName();
          if (name != null) uniqueNameGenerator.addExistingName(name);
        });
    }
    return uniqueNameGenerator;
  }

  @Override
  public String getReplaceDuplicatesTitle(int idx, int size) {
    return JavaRefactoringBundle.message("process.duplicates.title", idx, size);
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

  public void setStatic(boolean shouldBeStatic){
    myStatic = shouldBeStatic;
  }

  public boolean isCanBeStatic() {
    return myCanBeStatic;
  }

  public PsiElement[] getElements() {
    return myElements;
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  @NlsSafe
  public String getMethodName() {
    return myMethodName;
  }

  public PsiVariable[] getOutputVariables() {
    return myOutputVariables;
  }

  public void setMethodVisibility(String methodVisibility) {
    myMethodVisibility = methodVisibility;
  }
}
