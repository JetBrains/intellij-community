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
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
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
  public MultiMap<PsiExpression, PsiType> getControlFlowExpressionTypes(@NotNull final PsiExpression forPlace) {
    MultiMap<PsiExpression, PsiType> typeMap = buildDataflowTypeMap(forPlace, false);
    return typeMap != null ? typeMap : MultiMap.empty();
  }

  @Nullable
  private static MultiMap<PsiExpression, PsiType> buildDataflowTypeMap(PsiExpression forPlace, boolean onlyForPlace) {
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
        return new ExpressionTypeMemoryState(getFactory());
      }
    };

    final ExpressionTypeInstructionVisitor visitor = new ExpressionTypeInstructionVisitor(forPlace, onlyForPlace);
    if (runner.analyzeMethodWithInlining(scope, visitor) == RunnerResult.OK) {
      return visitor.getResult();
    }
    return null;
  }

  private static boolean mayHaveMorePreciseType(PsiExpression expr) {
    PsiExpression place = PsiUtil.skipParenthesizedExprDown(expr);
    if (place instanceof PsiReferenceExpression) {
      PsiElement target = ((PsiReferenceExpression)place).resolve();
      if (target instanceof PsiParameter) {
        PsiElement parent = target.getParent();
        if (parent instanceof PsiParameterList && parent.getParent() instanceof PsiLambdaExpression) {
          return true;
        }
      }
    }
    if (place == null) return false;
    final int start = place.getTextRange().getStartOffset();
    class Visitor extends JavaRecursiveElementWalkingVisitor {
      public boolean hasInteresting;

      @Override
      public void visitAssignmentExpression(PsiAssignmentExpression expression) {
        if (ExpressionTypeMemoryState.EXPRESSION_HASHING_STRATEGY.equals(expression.getLExpression(), place)) {
          hasInteresting = true;
          stopWalking();
        }
        super.visitAssignmentExpression(expression);
      }

      @Override
      public void visitLocalVariable(PsiLocalVariable variable) {
        if (variable.getInitializer() != null && ExpressionUtils.isReferenceTo(place, variable)) {
          hasInteresting = true;
          stopWalking();
        }
        super.visitLocalVariable(variable);
      }

      @Override
      public void visitTypeCastExpression(PsiTypeCastExpression expression) {
        if (ExpressionTypeMemoryState.EXPRESSION_HASHING_STRATEGY.equals(expression.getOperand(), place)) {
          hasInteresting = true;
          stopWalking();
        }
        super.visitTypeCastExpression(expression);
      }

      @Override
      public void visitInstanceOfExpression(PsiInstanceOfExpression expression) {
        if (ExpressionTypeMemoryState.EXPRESSION_HASHING_STRATEGY.equals(expression.getOperand(), place)) {
          hasInteresting = true;
          stopWalking();
        }
        super.visitInstanceOfExpression(expression);
      }

      @Override
      public void visitElement(PsiElement element) {
        if (element.getTextRange().getStartOffset() > start) {
          stopWalking();
        }
        super.visitElement(element);
      }
    }
    Visitor visitor = new Visitor();
    getTopmostBlock(place).accept(visitor);
    return visitor.hasInteresting;
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

  private void addExprTypesByDerivedClasses(LinkedHashSet<PsiType> set, PsiExpression expr) {
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
      PsiType derivedType = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory().createType(derivedClass);
      set.add(derivedType);
    }
  }

  private void addExprTypesWhenContainerElement(LinkedHashSet<PsiType> set, PsiExpression expr) {
    if (expr instanceof PsiMethodCallExpression){
      PsiMethodCallExpression callExpr = (PsiMethodCallExpression)expr;
      PsiReferenceExpression methodExpr = callExpr.getMethodExpression();
      String methodName = methodExpr.getReferenceName();
      MethodPattern pattern = myMethodPatternMap.findPattern(methodName, callExpr.getArgumentList().getExpressions().length);
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

  private void addTypesByVariable(HashSet<PsiType> typesSet,
                                  PsiVariable var,
                                  PsiFile scopeFile,
                                  HashSet<PsiVariable> checkedVariables,
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
            PsiExpression[] args = list.getExpressions();
            int argIndex = -1;
            for (int j = 0; j < args.length; j++) {
              PsiExpression arg = args[j];
              if (arg.equals(ref)) {
                argIndex = j;
                break;
              }
            }

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
  public List<PsiType> getControlFlowExpressionTypeConjuncts(@NotNull PsiExpression expr) {
    if (!mayHaveMorePreciseType(expr)) {
      return Collections.emptyList(); //optimization
    }

    MultiMap<PsiExpression, PsiType> fromDfa = buildDataflowTypeMap(expr, true);
    if (fromDfa != null) {
      Collection<PsiType> conjuncts = fromDfa.get(expr);
      if (!conjuncts.isEmpty()) {
        Set<PsiType> flatTypes = PsiIntersectionType.flatten(conjuncts.toArray(PsiType.EMPTY_ARRAY), new LinkedHashSet<>());
        return ContainerUtil.mapNotNull(flatTypes, type -> tryGenerify(expr, type));
      }
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

  private static class ExpressionTypeInstructionVisitor extends StandardInstructionVisitor {
    private MultiMap<PsiExpression, PsiType> myResult;
    private final PsiElement myForPlace;
    private TypeConstraint myConstraint = null;
    private final boolean myOnlyForPlace;

    private ExpressionTypeInstructionVisitor(@NotNull PsiElement forPlace, boolean onlyForPlace) {
      myOnlyForPlace = onlyForPlace;
      myForPlace = PsiUtil.skipParenthesizedExprUp(forPlace);
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
      if (!isInteresting(psiOperand)) {
        return super.visitInstanceof(instruction, runner, memState);
      }
      DfaValue type = memState.pop();
      DfaValue operand = memState.pop();
      DfaValue relation = runner.getFactory().createCondition(operand, DfaRelationValue.RelationType.IS, type);
      memState.push(new DfaInstanceofValue(runner.getFactory(), psiOperand, instruction.getCastType(), relation, false));
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
      if (myForPlace == instruction.getPlace()) {
        addToResult(((ExpressionTypeMemoryState)memState).getStates());
      }
      DfaInstructionState[] states = super.visitPush(instruction, runner, memState);
      if (myForPlace == instruction.getPlace()) {
        addConstraints(states);
      }
      return states;
    }

    private void addConstraints(DfaInstructionState[] states) {
      for (DfaInstructionState state : states) {
        DfaMemoryState memoryState = state.getMemoryState();
        if (myConstraint == TypeConstraint.EMPTY) return;
        TypeConstraint constraint = memoryState.getValueFact(memoryState.peek(), DfaFactType.TYPE_CONSTRAINT);
        if (constraint != null) {
          myConstraint = myConstraint == null ? constraint : myConstraint.union(constraint);
          if (myConstraint == null) {
            myConstraint = TypeConstraint.EMPTY;
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
