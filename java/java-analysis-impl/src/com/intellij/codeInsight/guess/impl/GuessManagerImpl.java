/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInsight.guess.impl;

import com.intellij.codeInsight.guess.GuessManager;
import com.intellij.codeInspection.dataFlow.*;
import com.intellij.codeInspection.dataFlow.instructions.*;
import com.intellij.codeInspection.dataFlow.value.DfaInstanceofValue;
import com.intellij.codeInspection.dataFlow.value.DfaRelationValue;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.search.PsiElementProcessorAdapter;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.BitUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class GuessManagerImpl extends GuessManager {

  private final MethodPatternMap myMethodPatternMap = new MethodPatternMap();

  {
    initMethodPatterns();
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
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

  @NotNull
  @Override
  public PsiType[] guessContainerElementType(PsiExpression containerExpr, TextRange rangeToIgnore) {
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
    if (collectionType instanceof PsiClassType) {
      PsiClassType classType = (PsiClassType) collectionType;
      PsiType[] parameters = classType.getParameters();
      if (parameters.length == 1) {
        return parameters[0];
      }
    }
    return null;
  }

  @NotNull
  @Override
  public PsiType[] guessTypeToCast(PsiExpression expr) {
    LinkedHashSet<PsiType> types = new LinkedHashSet<>(getControlFlowExpressionTypeConjuncts(expr));
    addExprTypesWhenContainerElement(types, expr);
    addExprTypesByDerivedClasses(types, expr);
    return types.toArray(PsiType.createArray(types.size()));
  }

  @NotNull
  @Override
  public MultiMap<PsiExpression, PsiType> getControlFlowExpressionTypes(@NotNull PsiExpression forPlace, boolean honorAssignments) {
    MultiMap<PsiExpression, PsiType> typeMap = buildDataflowTypeMap(forPlace, false, honorAssignments);
    return typeMap != null ? typeMap : MultiMap.empty();
  }

  @Nullable
  private static MultiMap<PsiExpression, PsiType> buildDataflowTypeMap(PsiExpression forPlace, boolean onlyForPlace, boolean honorAssignments) {
    PsiType type = forPlace.getType();
    PsiElement scope = DfaPsiUtil.getTopmostBlockInSameClass(forPlace);
    if (scope == null) {
      PsiFile file = forPlace.getContainingFile();
      if (!(file instanceof PsiCodeFragment)) {
        return MultiMap.empty();
      }

      scope = file;
    }

    DataFlowRunner runner = new DataFlowRunner() {
      @NotNull
      @Override
      protected DfaMemoryState createMemoryState() {
        return new ExpressionTypeMemoryState(getFactory(), honorAssignments);
      }
    };

    TypeConstraint initial = type == null ? null : runner.getFactory().createDfaType(type).asConstraint();
    final ExpressionTypeInstructionVisitor visitor = new ExpressionTypeInstructionVisitor(forPlace, onlyForPlace, initial);
    if (runner.analyzeMethodWithInlining(scope, visitor) == RunnerResult.OK) {
      return visitor.getResult();
    }
    return null;
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
    ClassInheritorsSearch.search(refClass).forEach(new PsiElementProcessorAdapter<>(processor));
    if (processor.isOverflow()) return;

    for (PsiClass derivedClass : processor.getCollection()) {
      if (derivedClass instanceof PsiAnonymousClass) continue;
      PsiType derivedType = JavaPsiFacade.getElementFactory(manager.getProject()).createType(derivedClass);
      set.add(derivedType);
    }
  }

  private void addExprTypesWhenContainerElement(LinkedHashSet<? super PsiType> set, PsiExpression expr) {
    if (expr instanceof PsiMethodCallExpression){
      PsiMethodCallExpression callExpr = (PsiMethodCallExpression)expr;
      PsiReferenceExpression methodExpr = callExpr.getMethodExpression();
      String methodName = methodExpr.getReferenceName();
      MethodPattern pattern = myMethodPatternMap.findPattern(methodName, callExpr.getArgumentList().getExpressionCount());
      if (pattern != null && pattern.parameterIndex < 0/* return value */){
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
          if (ref.getParent() instanceof PsiExpressionList && ref.getParent().getParent() instanceof PsiMethodCallExpression) { //TODO : new
            PsiExpressionList list = (PsiExpressionList)ref.getParent();
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
      if (var instanceof PsiParameter && var.getParent() instanceof PsiParameterList && var.getParent().getParent() instanceof PsiMethod){
        PsiParameterList list = (PsiParameterList)var.getParent();
        PsiParameter[] parameters = list.getParameters();
        int argIndex = -1;
        for(int i = 0; i < parameters.length; i++){
          PsiParameter parameter = parameters[i];
          if (parameter.equals(var)){
            argIndex = i;
            break;
          }
        }

        PsiMethod method = (PsiMethod)var.getParent().getParent();
        //System.out.println("analyzing usages of " + method + " in file " + scopeFile);
        for (PsiReference methodRef : ReferencesSearch.search(method, searchScope, false)) {
          PsiElement ref = methodRef.getElement();
          if (ref.getParent() instanceof PsiMethodCallExpression) {
            PsiMethodCallExpression methodCall = (PsiMethodCallExpression)ref.getParent();
            PsiExpression[] args = methodCall.getArgumentList().getExpressions();
            if (args.length <= argIndex) continue;
            PsiExpression arg = args[argIndex];
            if (arg instanceof PsiReferenceExpression) {
              PsiElement refElement = ((PsiReferenceExpression)arg).resolve();
              if (refElement instanceof PsiVariable) {
                addTypesByVariable(typesSet, (PsiVariable)refElement, scopeFile, checkedVariables, flags | CHECK_USAGE, rangeToIgnore);
              }
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
    if (refParent instanceof PsiReferenceExpression){
      PsiReferenceExpression parentExpr = (PsiReferenceExpression)refParent;
      if (ref.equals(parentExpr.getQualifierExpression()) && parentExpr.getParent() instanceof PsiMethodCallExpression){
        String methodName = parentExpr.getReferenceName();
        PsiMethodCallExpression methodCall = (PsiMethodCallExpression)parentExpr.getParent();
        PsiExpression[] args = methodCall.getArgumentList().getExpressions();
        MethodPattern pattern = methodPatternMap.findPattern(methodName, args.length);
        if (pattern != null){
          if (pattern.parameterIndex < 0){ // return value
            if (methodCall.getParent() instanceof PsiTypeCastExpression &&
                (rangeToIgnore == null || !rangeToIgnore.contains(methodCall.getTextRange()))) {
              return ((PsiTypeCastExpression)methodCall.getParent()).getType();
            }
          }
          else{
            return args[pattern.parameterIndex].getType();
          }
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

    List<PsiType> result = null;
    if (!ControlFlowAnalyzer.inlinerMayInferPreciseType(place)) {
      GuessTypeVisitor visitor = tryGuessingTypeWithoutDfa(place, honorAssignments);
      if (!visitor.isDfaNeeded()) {
        result = visitor.mySpecificType == null ?
                 Collections.emptyList() : Collections.singletonList(tryGenerify(expr, visitor.mySpecificType));
      }
    }
    if (result == null) {
      MultiMap<PsiExpression, PsiType> fromDfa = buildDataflowTypeMap(expr, true, honorAssignments);
      result = flattenConjuncts(expr, fromDfa != null ? fromDfa.get(expr) : Collections.emptyList());
    }
    result = ContainerUtil.filter(result, t -> {
      PsiClass typeClass = PsiUtil.resolveClassInType(t);
      return typeClass == null || PsiUtil.isAccessible(typeClass, expr, null);
    });
    if (result.equals(Collections.singletonList(expr.getType()))) {
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

  @NotNull
  private static List<PsiType> flattenConjuncts(@NotNull PsiExpression expr, Collection<PsiType> conjuncts) {
    if (!conjuncts.isEmpty()) {
      Set<PsiType> flatTypes = PsiIntersectionType.flatten(conjuncts.toArray(PsiType.EMPTY_ARRAY), new LinkedHashSet<>());
      return ContainerUtil.mapNotNull(flatTypes, type -> tryGenerify(expr, type));
    }
    return Collections.emptyList();
  }

  private static PsiType tryGenerify(PsiExpression expression, PsiType type) {
    if (!(type instanceof PsiClassType)) {
      return type;
    }
    PsiClassType classType = (PsiClassType)type;
    if (!classType.isRaw()) {
      return classType;
    }
    PsiClass psiClass = classType.resolve();
    if (psiClass == null) return classType;
    PsiType expressionType = expression.getType();
    if (!(expressionType instanceof PsiClassType)) return classType;
    return GenericsUtil.getExpectedGenericType(expression, psiClass, (PsiClassType)expressionType);
  }

  private static class GuessTypeVisitor extends JavaElementVisitor {
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
      if (rawType == null || rawType.equals(PsiType.NULL)) return;
      if (mySpecificType == null) {
        mySpecificType = rawType;
      }
      else if (!mySpecificType.equals(rawType)) {
        myNeedDfa = true;
      }
    }

    @Override
    public void visitAssignmentExpression(PsiAssignmentExpression expression) {
      if (ExpressionTypeMemoryState.EXPRESSION_HASHING_STRATEGY.equals(expression.getLExpression(), myPlace)) {
        handleAssignment(expression.getRExpression());
      }
      super.visitAssignmentExpression(expression);
    }

    @Override
    public void visitLocalVariable(PsiLocalVariable variable) {
      if (ExpressionUtils.isReferenceTo(myPlace, variable)) {
        myDeclared = true;
        handleAssignment(variable.getInitializer());
      }
      super.visitLocalVariable(variable);
    }

    @Override
    public void visitTypeCastExpression(PsiTypeCastExpression expression) {
      PsiExpression operand = expression.getOperand();
      if (operand != null && ExpressionTypeMemoryState.EXPRESSION_HASHING_STRATEGY.equals(operand, myPlace)) {
        myNeedDfa = true;
      }
      super.visitTypeCastExpression(expression);
    }

    @Override
    public void visitInstanceOfExpression(PsiInstanceOfExpression expression) {
      if (ExpressionTypeMemoryState.EXPRESSION_HASHING_STRATEGY.equals(expression.getOperand(), myPlace)) {
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
  private static class ExpressionTypeInstructionVisitor extends StandardInstructionVisitor {
    private final TypeConstraint myInitial;
    private MultiMap<PsiExpression, PsiType> myResult;
    private final PsiElement myForPlace;
    private TypeConstraint myConstraint = null;
    private final boolean myOnlyForPlace;

    private ExpressionTypeInstructionVisitor(@NotNull PsiElement forPlace,
                                             boolean onlyForPlace,
                                             TypeConstraint initial) {
      myOnlyForPlace = onlyForPlace;
      myForPlace = PsiUtil.skipParenthesizedExprUp(forPlace);
      myInitial = initial;
    }

    MultiMap<PsiExpression, PsiType> getResult() {
      if (myConstraint != null && myForPlace instanceof PsiExpression) {
        PsiType type = myConstraint.getPsiType();
        if (type instanceof PsiIntersectionType) {
          myResult.putValues((PsiExpression)myForPlace, Arrays.asList(((PsiIntersectionType)type).getConjuncts()));
        }
        else if (type != null) {
          myResult.putValue((PsiExpression)myForPlace, type);
        }
      }
      return myResult;
    }

    @Contract("null -> false")
    private boolean isInteresting(PsiExpression expression) {
      if (expression == null) return false;
      return !myOnlyForPlace ||
             (myForPlace instanceof PsiExpression &&
              ExpressionTypeMemoryState.EXPRESSION_HASHING_STRATEGY.equals((PsiExpression)myForPlace, expression));
    }

    @Override
    public DfaInstructionState[] visitInstanceof(InstanceofInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
      PsiExpression psiOperand = instruction.getLeft();
      if (!isInteresting(psiOperand) || instruction.isClassObjectCheck()) {
        return super.visitInstanceof(instruction, runner, memState);
      }
      DfaValue type = memState.pop();
      DfaValue operand = memState.pop();
      DfaValue relation = runner.getFactory().createCondition(operand, DfaRelationValue.RelationType.IS, type);
      memState.push(new DfaInstanceofValue(runner.getFactory(), psiOperand, Objects.requireNonNull(instruction.getCastType()), relation, false));
      return new DfaInstructionState[]{new DfaInstructionState(runner.getInstruction(instruction.getIndex() + 1), memState)};
    }

    @Override
    public DfaInstructionState[] visitTypeCast(TypeCastInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
      PsiExpression psiOperand = instruction.getCasted();
      if (isInteresting(psiOperand)) {
        ((ExpressionTypeMemoryState)memState).setExpressionType(psiOperand, instruction.getCastTo());
      }
      return super.visitTypeCast(instruction, runner, memState);
    }

    @Override
    public DfaInstructionState[] visitAssign(AssignInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
      PsiExpression left = instruction.getLExpression();
      PsiExpression right = instruction.getRExpression();
      if (left != null && right != null) {
        ((ExpressionTypeMemoryState)memState).removeExpressionType(left);
      }
      return super.visitAssign(instruction, runner, memState);
    }

    @Override
    public DfaInstructionState[] visitMethodCall(MethodCallInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
      if (myForPlace == instruction.getCallExpression()) {
        addToResult(((ExpressionTypeMemoryState)memState).getStates());
      }
      DfaInstructionState[] states = super.visitMethodCall(instruction, runner, memState);
      if (myForPlace == instruction.getCallExpression()) {
        addConstraints(states);
      }
      return states;
    }

    @Override
    public DfaInstructionState[] visitPush(PushInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
      if (myForPlace == instruction.getExpression()) {
        addToResult(((ExpressionTypeMemoryState)memState).getStates());
      }
      DfaInstructionState[] states = super.visitPush(instruction, runner, memState);
      if (myForPlace == instruction.getExpression()) {
        addConstraints(states);
      }
      return states;
    }

    private void addConstraints(DfaInstructionState[] states) {
      for (DfaInstructionState state : states) {
        DfaMemoryState memoryState = state.getMemoryState();
        if (myConstraint == TypeConstraint.empty()) return;
        TypeConstraint constraint = memoryState.getValueFact(memoryState.peek(), DfaFactType.TYPE_CONSTRAINT);
        if (constraint == null) {
          constraint = myInitial;
        }
        if (constraint != null) {
          myConstraint = myConstraint == null ? constraint : myConstraint.unite(constraint);
          if (myConstraint == null) {
            myConstraint = TypeConstraint.empty();
            return;
          }
        }
      }
    }

    private void addToResult(MultiMap<PsiExpression, PsiType> map) {
      if (myResult == null) {
        myResult = MultiMap.createSet(ExpressionTypeMemoryState.EXPRESSION_HASHING_STRATEGY);
        myResult.putAllValues(map);
      } else {
        final Iterator<PsiExpression> iterator = myResult.keySet().iterator();
        while (iterator.hasNext()) {
          PsiExpression psiExpression = iterator.next();
          if (!myResult.get(psiExpression).equals(map.get(psiExpression))) {
            iterator.remove();
          }
        }
      }
    }
  }
}
