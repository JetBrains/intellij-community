// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.guess.impl;

import com.intellij.codeInsight.guess.GuessManager;
import com.intellij.codeInspection.dataFlow.DfaPsiUtil;
import com.intellij.codeInspection.dataFlow.StandardDataFlowRunner;
import com.intellij.codeInspection.dataFlow.TypeConstraint;
import com.intellij.codeInspection.dataFlow.TypeConstraints;
import com.intellij.codeInspection.dataFlow.interpreter.RunnerResult;
import com.intellij.codeInspection.dataFlow.interpreter.StandardDataFlowInterpreter;
import com.intellij.codeInspection.dataFlow.java.ControlFlowAnalyzer;
import com.intellij.codeInspection.dataFlow.java.JavaDfaListener;
import com.intellij.codeInspection.dataFlow.java.anchor.JavaExpressionAnchor;
import com.intellij.codeInspection.dataFlow.java.inst.InstanceofInstruction;
import com.intellij.codeInspection.dataFlow.java.inst.TypeCastInstruction;
import com.intellij.codeInspection.dataFlow.lang.DfaListener;
import com.intellij.codeInspection.dataFlow.lang.ir.ControlFlow;
import com.intellij.codeInspection.dataFlow.lang.ir.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.lang.ir.Instruction;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryStateImpl;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.search.PsiElementProcessorAdapter;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.BitUtil;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.indexing.DumbModeAccessType;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class GuessManagerImpl extends GuessManager {
  private final MethodPatternMap myMethodPatternMap = new MethodPatternMap();

  {
    initMethodPatterns();
  }

  private void initMethodPatterns() {
    // Collection
    myMethodPatternMap.addPattern(new MethodPattern("add", 1, 0));
    myMethodPatternMap.addPattern(new MethodPattern("contains", 1, 0));
    myMethodPatternMap.addPattern(new MethodPattern("remove", 1, 0));

    // Vector
    myMethodPatternMap.addPattern(new MethodPattern("add", 2, 1));
    myMethodPatternMap.addPattern(new MethodPattern("addElement", 1, 0));
    myMethodPatternMap.addPattern(new MethodPattern("elementAt", 1, -1));
    myMethodPatternMap.addPattern(new MethodPattern("firstElement", 0, -1));
    myMethodPatternMap.addPattern(new MethodPattern("lastElement", 0, -1));
    myMethodPatternMap.addPattern(new MethodPattern("get", 1, -1));
    myMethodPatternMap.addPattern(new MethodPattern("indexOf", 1, 0));
    myMethodPatternMap.addPattern(new MethodPattern("indexOf", 2, 0));
    myMethodPatternMap.addPattern(new MethodPattern("lastIndexOf", 1, 0));
    myMethodPatternMap.addPattern(new MethodPattern("lastIndexOf", 2, 0));
    myMethodPatternMap.addPattern(new MethodPattern("insertElementAt", 2, 0));
    myMethodPatternMap.addPattern(new MethodPattern("removeElement", 1, 0));
    myMethodPatternMap.addPattern(new MethodPattern("set", 2, 1));
    myMethodPatternMap.addPattern(new MethodPattern("setElementAt", 2, 0));
  }

  private final Project myProject;

  public GuessManagerImpl(Project project) {
    myProject = project;
  }

  @Override
  public PsiType @NotNull [] guessContainerElementType(PsiExpression containerExpr, TextRange rangeToIgnore) {
    HashSet<PsiType> typesSet = new HashSet<>();

    PsiType type = containerExpr.getType();
    PsiType elemType;
    if ((elemType = getGenericElementType(type)) != null) return new PsiType[]{elemType};

    if (containerExpr instanceof PsiReferenceExpression){
      PsiElement refElement = ((PsiReferenceExpression)containerExpr).resolve();
      if (refElement instanceof PsiVariable){

        PsiFile file = refElement.getContainingFile();
        if (file == null){
          file = containerExpr.getContainingFile(); // implicit variable in jsp
        }
        HashSet<PsiVariable> checkedVariables = new HashSet<>();
        addTypesByVariable(typesSet, (PsiVariable)refElement, file, checkedVariables, CHECK_USAGE | CHECK_DOWN, rangeToIgnore);
        checkedVariables.clear();
        addTypesByVariable(typesSet, (PsiVariable)refElement, file, checkedVariables, CHECK_UP, rangeToIgnore);
      }
    }

    return typesSet.toArray(PsiType.createArray(typesSet.size()));
  }

  @Nullable
  private static PsiType getGenericElementType(PsiType collectionType) {
    if (collectionType instanceof PsiClassType classType) {
      PsiType[] parameters = classType.getParameters();
      if (parameters.length == 1) {
        return parameters[0];
      }
    }
    return null;
  }

  @Override
  public PsiType @NotNull [] guessTypeToCast(PsiExpression expr) {
    LinkedHashSet<PsiType> types = new LinkedHashSet<>(getControlFlowExpressionTypeConjuncts(expr));
    addExprTypesWhenContainerElement(types, expr);
    addExprTypesByDerivedClasses(types, expr);
    return types.toArray(PsiType.createArray(types.size()));
  }

  @NotNull
  @Override
  public MultiMap<PsiExpression, PsiType> getControlFlowExpressionTypes(@NotNull PsiExpression forPlace, boolean honorAssignments) {
    PsiElement scope = DfaPsiUtil.getTopmostBlockInSameClass(forPlace);
    if (scope == null) {
      PsiFile file = forPlace.getContainingFile();
      if (!(file instanceof PsiCodeFragment)) {
        return MultiMap.empty();
      }
      scope = file;
    }

    GuessManagerRunner runner = new GuessManagerRunner(scope.getProject(), honorAssignments, null);

    var interceptor = new ExpressionTypeListener(runner, forPlace);
    RunnerResult result = runner.analyzeMethodWithInlining(scope, interceptor);
    if (result == RunnerResult.OK || result == RunnerResult.CANCELLED) {
      return interceptor.getResult();
    }
    return MultiMap.empty();
  }

  @Nullable
  private static PsiType getTypeFromDataflow(PsiExpression forPlace, boolean honorAssignments) {
    PsiType type = forPlace.getType();
    TypeConstraint initial = type == null ? TypeConstraints.TOP : TypeConstraints.instanceOf(type);
    PsiElement scope = DfaPsiUtil.getTopmostBlockInSameClass(forPlace);
    if (scope == null) {
      PsiFile file = forPlace.getContainingFile();
      if (!(file instanceof PsiCodeFragment)) {
        return null;
      }
      scope = file;
    }

    GuessManagerRunner runner = new GuessManagerRunner(scope.getProject(), honorAssignments, forPlace);

    class MyListener implements JavaDfaListener {
      TypeConstraint constraint = TypeConstraints.BOTTOM;

      @Override
      public void beforeExpressionPush(@NotNull DfaValue value,
                                       @NotNull PsiExpression expression,
                                       @NotNull DfaMemoryState state) {
        if (expression == forPlace) {
          if (!(value instanceof DfaVariableValue) || ((DfaVariableValue)value).isFlushableByCalls()) {
            value = value.getFactory().getVarFactory().createVariableValue(new ExpressionVariableDescriptor(expression));
          }
          constraint = constraint.join(TypeConstraint.fromDfType(state.getDfType(value)));
          runner.placeVisited();
        }
      }
    }
    var interceptor = new MyListener();
    RunnerResult result = runner.analyzeMethodWithInlining(scope, interceptor);
    if (result == RunnerResult.OK || result == RunnerResult.CANCELLED) {
      return interceptor.constraint.meet(initial).getPsiType(scope.getProject());
    }
    return null;
  }

  private static class GuessManagerRunner extends StandardDataFlowRunner {
    private final boolean myAssignments;
    private final PsiExpression myPlace;
    private boolean myPlaceVisited;

    GuessManagerRunner(@NotNull Project project, boolean honorAssignments, @Nullable PsiExpression place) {
      super(project);
      myAssignments = honorAssignments;
      myPlace = place;
    }

    @Override
    protected @NotNull StandardDataFlowInterpreter createInterpreter(@NotNull DfaListener listener, @NotNull ControlFlow flow) {
      return new StandardDataFlowInterpreter(flow, listener) {
        @Override
        public int getComplexityLimit() {
          // Limit analysis complexity for completion as it could be relaunched many times
          return DEFAULT_MAX_STATES_PER_BRANCH / 3;
        }

        @Override
        protected void afterInstruction(Instruction instruction) {
          super.afterInstruction(instruction);
          if (myPlaceVisited && flow.getLoopNumbers()[instruction.getIndex()] == 0) {
            // We cancel the analysis first time we exit all the loops
            // after the target expression is visited (in this case,
            // we can be sure we'll not reach it again)
            cancel();
          }
        }

        @Override
        protected DfaInstructionState @NotNull [] acceptInstruction(@NotNull DfaInstructionState instructionState) {
          Instruction instruction = instructionState.getInstruction();
          DfaMemoryState memState = instructionState.getMemoryState();
          if (instruction instanceof TypeCastInstruction typeCast) {
            DfaValue value = memState.pop();
            memState.push(adjustValue(value, typeCast.getCasted()));
          }
          else if (instruction instanceof InstanceofInstruction instanceOf) {
            DfaValue dfaRight = memState.pop();
            DfaValue dfaLeft = memState.pop();
            memState.push(adjustValue(dfaLeft, getInstanceOfOperand(instanceOf)));
            memState.push(dfaRight);
          }
          return super.acceptInstruction(instructionState);
        }

        @Nullable
        private static PsiExpression getInstanceOfOperand(InstanceofInstruction instruction) {
          if (instruction.getDfaAnchor() instanceof JavaExpressionAnchor anchor &&
              anchor.getExpression() instanceof PsiInstanceOfExpression instanceOf) {
            return instanceOf.getOperand();
          }
          return null;
        }

        private DfaValue adjustValue(@NotNull DfaValue value, @Nullable PsiExpression expression) {
          if (expression != null && isInteresting(value, expression)) {
            return getFactory().getVarFactory().createVariableValue(new ExpressionVariableDescriptor(expression));
          }
          return value;
        }

        private boolean isInteresting(@NotNull DfaValue value, @NotNull PsiExpression expression) {
          if (myPlace == null) return true;
          return (!(value instanceof DfaVariableValue var) || var.isFlushableByCalls()) &&
                 ExpressionVariableDescriptor.EXPRESSION_HASHING_STRATEGY.equals(expression, myPlace);
        }
      };
    }

    void placeVisited() {
      myPlaceVisited = true;
    }

    @NotNull
    @Override
    protected DfaMemoryState createMemoryState() {
      return myAssignments ? super.createMemoryState() : new AssignmentFilteringMemoryState(getFactory());
    }
  }

  private static PsiElement getTopmostBlock(PsiElement scope) {
    assert scope.isValid();
    PsiElement lastScope = scope;
    while (true) {
      final PsiCodeBlock lastCodeBlock = PsiTreeUtil.getParentOfType(lastScope, PsiCodeBlock.class, true);
      if (lastCodeBlock == null) {
        break;
      }
      lastScope = lastCodeBlock;
    }
    if (lastScope == scope) {
      PsiFile file = scope.getContainingFile();
      if (file instanceof PsiCodeFragment) {
        return file;
      }
    }
    return lastScope;
  }

  private void addExprTypesByDerivedClasses(LinkedHashSet<? super PsiType> set, PsiExpression expr) {
    PsiType type = expr.getType();
    if (!(type instanceof PsiClassType)) return;
    PsiClass refClass = PsiUtil.resolveClassInType(type);
    if (refClass == null) return;

    PsiManager manager = PsiManager.getInstance(myProject);
    PsiElementProcessor.CollectElementsWithLimit<PsiClass> processor = new PsiElementProcessor.CollectElementsWithLimit<>(5);
    DumbModeAccessType.RELIABLE_DATA_ONLY.ignoreDumbMode(
      () -> ClassInheritorsSearch.search(refClass).forEach(new PsiElementProcessorAdapter<>(processor)));
    if (processor.isOverflow()) return;

    for (PsiClass derivedClass : processor.getCollection()) {
      if (derivedClass instanceof PsiAnonymousClass) continue;
      PsiType derivedType = JavaPsiFacade.getElementFactory(manager.getProject()).createType(derivedClass);
      set.add(derivedType);
    }
  }

  private void addExprTypesWhenContainerElement(LinkedHashSet<? super PsiType> set, PsiExpression expr) {
    if (expr instanceof PsiMethodCallExpression callExpr){
      PsiReferenceExpression methodExpr = callExpr.getMethodExpression();
      String methodName = methodExpr.getReferenceName();
      MethodPattern pattern = myMethodPatternMap.findPattern(methodName, callExpr.getArgumentList().getExpressionCount());
      if (pattern != null && pattern.parameterIndex() < 0/* return value */){
        PsiExpression qualifier = methodExpr.getQualifierExpression();
        if (qualifier != null) {
          PsiType[] types = guessContainerElementType(qualifier, null);
          for (PsiType type : types) {
            if (type instanceof PsiClassType) {
              if (((PsiClassType)type).resolve() instanceof PsiAnonymousClass) continue;
            }
            set.add(type);
          }
        }
      }
    }
  }

  private static final int CHECK_USAGE = 0x01;
  private static final int CHECK_UP = 0x02;
  private static final int CHECK_DOWN = 0x04;

  private void addTypesByVariable(HashSet<? super PsiType> typesSet,
                                  PsiVariable var,
                                  PsiFile scopeFile,
                                  HashSet<? super PsiVariable> checkedVariables,
                                  int flags,
                                  TextRange rangeToIgnore) {
    if (!checkedVariables.add(var)) return;
    //System.out.println("analyzing usages of " + var + " in file " + scopeFile);
    SearchScope searchScope = new LocalSearchScope(scopeFile);

    if (BitUtil.isSet(flags, CHECK_USAGE) || BitUtil.isSet(flags, CHECK_DOWN)) {
      for (PsiReference varRef : ReferencesSearch.search(var, searchScope, false)) {
        PsiElement ref = varRef.getElement();

        if (BitUtil.isSet(flags, CHECK_USAGE)) {
          PsiType type = guessElementTypeFromReference(myMethodPatternMap, ref, rangeToIgnore);
          if (type != null && !(type instanceof PsiPrimitiveType)) {
            typesSet.add(type);
          }
        }

        if (BitUtil.isSet(flags, CHECK_DOWN)) {
          if (ref.getParent() instanceof PsiExpressionList list && ref.getParent().getParent() instanceof PsiMethodCallExpression) { //TODO : new
            int argIndex = ArrayUtil.indexOf(list.getExpressions(), ref);

            PsiMethodCallExpression methodCall = (PsiMethodCallExpression)list.getParent();
            PsiMethod method = (PsiMethod)methodCall.getMethodExpression().resolve();
            if (method != null) {
              PsiParameter[] parameters = method.getParameterList().getParameters();
              if (argIndex < parameters.length) {
                addTypesByVariable(typesSet, parameters[argIndex], method.getContainingFile(), checkedVariables, flags | CHECK_USAGE,
                                   rangeToIgnore);
              }
            }
          }
        }
      }
    }

    if (BitUtil.isSet(flags, CHECK_UP)){
      if (var instanceof PsiParameter &&
          var.getParent() instanceof PsiParameterList list &&
          var.getParent().getParent() instanceof PsiMethod method){
        PsiParameter[] parameters = list.getParameters();
        int argIndex = ArrayUtil.indexOf(parameters, var);

        for (PsiReference methodRef : ReferencesSearch.search(method, searchScope, false)) {
          if (methodRef.getElement().getParent() instanceof PsiMethodCallExpression methodCall) {
            PsiExpression[] args = methodCall.getArgumentList().getExpressions();
            if (args.length <= argIndex) continue;
            PsiExpression arg = args[argIndex];
            if (arg instanceof PsiReferenceExpression ref && ref.resolve() instanceof PsiVariable variable) {
              addTypesByVariable(typesSet, variable, scopeFile, checkedVariables, flags | CHECK_USAGE, rangeToIgnore);
            }
            //TODO : constructor
          }
        }
      }
    }
  }

  @Nullable
  private static PsiType guessElementTypeFromReference(MethodPatternMap methodPatternMap,
                                                       PsiElement ref,
                                                       TextRange rangeToIgnore) {
    PsiElement refParent = ref.getParent();
    if (refParent instanceof PsiReferenceExpression parentExpr &&
        ref.equals(parentExpr.getQualifierExpression()) &&
        parentExpr.getParent() instanceof PsiMethodCallExpression methodCall) {
      String methodName = parentExpr.getReferenceName();
      PsiExpression[] args = methodCall.getArgumentList().getExpressions();
      MethodPattern pattern = methodPatternMap.findPattern(methodName, args.length);
      if (pattern != null) {
        if (pattern.parameterIndex() < 0) { // return value
          if (methodCall.getParent() instanceof PsiTypeCastExpression cast &&
              (rangeToIgnore == null || !rangeToIgnore.contains(methodCall.getTextRange()))) {
            return cast.getType();
          }
        }
        else {
          return args[pattern.parameterIndex()].getType();
        }
      }
    }
    return null;
  }

  @NotNull
  @Override
  public List<PsiType> getControlFlowExpressionTypeConjuncts(@NotNull PsiExpression expr, boolean honorAssignments) {
    if (expr.getType() instanceof PsiPrimitiveType) {
      return Collections.emptyList();
    }
    PsiExpression place = PsiUtil.skipParenthesizedExprDown(expr);
    if (place == null) return Collections.emptyList();

    DumbService dumbService = DumbService.getInstance(myProject);
    PsiType type = dumbService.computeWithAlternativeResolveEnabled(() -> {
      if (!ControlFlowAnalyzer.inlinerMayInferPreciseType(place)) {
        GuessTypeVisitor visitor = tryGuessingTypeWithoutDfa(place, honorAssignments);
        if (!visitor.isDfaNeeded()) return visitor.mySpecificType;
      }
      return getTypeFromDataflow(expr, honorAssignments);
    });
    return dumbService.computeWithAlternativeResolveEnabled(() -> postFilter(expr, type));
  }

  @NotNull
  private static List<PsiType> flattenAndGenerify(@NotNull PsiExpression expr, PsiType psiType) {
    if (psiType instanceof PsiIntersectionType intersection) {
      return ContainerUtil.mapNotNull(intersection.getConjuncts(), type -> DfaPsiUtil.tryGenerify(expr, type));
    }
    else if (psiType != null) {
      return Collections.singletonList(DfaPsiUtil.tryGenerify(expr, psiType));
    }
    else {
      return Collections.emptyList();
    }
  }

  @NotNull
  private static List<PsiType> postFilter(@NotNull PsiExpression expr, PsiType type) {
    List<PsiType> result = ContainerUtil.filter(flattenAndGenerify(expr, type), t -> {
      PsiClass typeClass = PsiUtil.resolveClassInType(t);
      return typeClass == null || PsiUtil.isAccessible(typeClass, expr, null);
    });
    if (result.equals(Collections.singletonList(TypeConversionUtil.erasure(expr.getType())))) {
      return Collections.emptyList();
    }
    return result;
  }

  @NotNull
  private static GuessTypeVisitor tryGuessingTypeWithoutDfa(PsiExpression place, boolean honorAssignments) {
    List<PsiElement> exprsAndVars = getPotentiallyAffectingElements(place);
    GuessTypeVisitor visitor = new GuessTypeVisitor(place, honorAssignments);
    for (PsiElement e : exprsAndVars) {
      e.accept(visitor);
      if (e == place || visitor.isDfaNeeded()) {
        break;
      }
    }
    return visitor;
  }

  private static List<PsiElement> getPotentiallyAffectingElements(PsiExpression place) {
    PsiElement topmostBlock = getTopmostBlock(place);
    return CachedValuesManager.getCachedValue(topmostBlock, () -> {
      List<PsiElement> list = SyntaxTraverser.psiTraverser(topmostBlock).filter(e -> e instanceof PsiExpression || e instanceof PsiLocalVariable).toList();
      return new CachedValueProvider.Result<>(list, topmostBlock);
    });
  }

  private static class GuessTypeVisitor extends JavaElementVisitor {
    private static final CallMatcher OBJECT_GET_CLASS =
      CallMatcher.exactInstanceCall(CommonClassNames.JAVA_LANG_OBJECT, "getClass").parameterCount(0);
    private final @NotNull PsiExpression myPlace;
    PsiType mySpecificType;
    private boolean myNeedDfa;
    private boolean myDeclared;
    private final boolean myHonorAssignments;

    GuessTypeVisitor(@NotNull PsiExpression place, boolean honorAssignments) {
      myPlace = place;
      myHonorAssignments = honorAssignments;
    }

    protected void handleAssignment(@Nullable PsiExpression expression) {
      if (!myHonorAssignments || expression == null) return;
      PsiType type = expression.getType();
      if (type instanceof PsiPrimitiveType) {
        type = ((PsiPrimitiveType)type).getBoxedType(expression);
      }
      PsiType rawType = type instanceof PsiClassType ? ((PsiClassType)type).rawType() : type;
      if (rawType == null || rawType.equals(PsiTypes.nullType())) return;
      if (mySpecificType == null) {
        mySpecificType = rawType;
      }
      else if (!mySpecificType.equals(rawType)) {
        myNeedDfa = true;
      }
    }

    @Override
    public void visitAssignmentExpression(@NotNull PsiAssignmentExpression expression) {
      if (ExpressionVariableDescriptor.EXPRESSION_HASHING_STRATEGY.equals(expression.getLExpression(), myPlace)) {
        handleAssignment(expression.getRExpression());
      }
      super.visitAssignmentExpression(expression);
    }

    @Override
    public void visitLocalVariable(@NotNull PsiLocalVariable variable) {
      if (ExpressionUtils.isReferenceTo(myPlace, variable)) {
        myDeclared = true;
        handleAssignment(variable.getInitializer());
      }
      super.visitLocalVariable(variable);
    }

    @Override
    public void visitTypeCastExpression(@NotNull PsiTypeCastExpression expression) {
      PsiExpression operand = expression.getOperand();
      if (operand != null && ExpressionVariableDescriptor.EXPRESSION_HASHING_STRATEGY.equals(operand, myPlace)) {
        myNeedDfa = true;
      }
      super.visitTypeCastExpression(expression);
    }

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
      if (OBJECT_GET_CLASS.test(call)) {
        PsiExpression qualifier = ExpressionUtils.getEffectiveQualifier(call.getMethodExpression());
        if (qualifier != null && ExpressionVariableDescriptor.EXPRESSION_HASHING_STRATEGY.equals(qualifier, myPlace)) {
          myNeedDfa = true;
        }
      }
      super.visitMethodCallExpression(call);
    }

    @Override
    public void visitInstanceOfExpression(@NotNull PsiInstanceOfExpression expression) {
      if (ExpressionVariableDescriptor.EXPRESSION_HASHING_STRATEGY.equals(expression.getOperand(), myPlace)) {
        myNeedDfa = true;
      }
      super.visitInstanceOfExpression(expression);
    }

    public boolean isDfaNeeded() {
      if (myNeedDfa) return true;
      if (myDeclared || mySpecificType == null) return false;
      PsiType type = myPlace.getType();
      PsiType rawType = type instanceof PsiClassType ? ((PsiClassType)type).rawType() : type;
      return !mySpecificType.equals(rawType);
    }
  }

  private static final class ExpressionTypeListener implements JavaDfaListener {
    private final Map<DfaVariableValue, TypeConstraint> myResult = new HashMap<>();
    private final GuessManagerRunner myRunner;
    private final PsiElement myForPlace;

    private ExpressionTypeListener(GuessManagerRunner runner,
                                   @NotNull PsiElement forPlace) {
      myRunner = runner;
      myForPlace = PsiUtil.skipParenthesizedExprUp(forPlace);
    }

    MultiMap<PsiExpression, PsiType> getResult() {
      MultiMap<PsiExpression, PsiType> result = MultiMap.createSet(
        CollectionFactory.createCustomHashingStrategyMap(ExpressionVariableDescriptor.EXPRESSION_HASHING_STRATEGY));
      Project project = myForPlace.getProject();
      myResult.forEach((value, constraint) -> {
        if (value.getDescriptor() instanceof ExpressionVariableDescriptor) {
          PsiExpression expression = ((ExpressionVariableDescriptor)value.getDescriptor()).getExpression();
          PsiType type = constraint.getPsiType(project);
          if (type instanceof PsiIntersectionType) {
            result.putValues(expression, Arrays.asList(((PsiIntersectionType)type).getConjuncts()));
          }
          else if (type != null) {
            result.putValue(expression, type);
          }
        }
      });
      return result;
    }

    @Override
    public void beforeExpressionPush(@NotNull DfaValue value,
                                     @NotNull PsiExpression expression,
                                     @NotNull DfaMemoryState state) {
      if (myForPlace == expression) {
        ((DfaMemoryStateImpl)state).forRecordedVariableTypes((var, dfType) -> {
          myResult.merge(var, TypeConstraint.fromDfType(dfType), TypeConstraint::join);
        });
        myRunner.placeVisited();
      }
    }
  }
}
