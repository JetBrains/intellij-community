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
import com.intellij.codeInspection.dataFlow.instructions.InstanceofInstruction;
import com.intellij.codeInspection.dataFlow.instructions.MethodCallInstruction;
import com.intellij.codeInspection.dataFlow.instructions.PushInstruction;
import com.intellij.codeInspection.dataFlow.instructions.TypeCastInstruction;
import com.intellij.codeInspection.dataFlow.value.DfaInstanceofValue;
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
import gnu.trove.THashMap;
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

  @Override
  public PsiType[] guessTypeToCast(PsiExpression expr) { //TODO : make better guess based on control flow
    LinkedHashSet<PsiType> types = new LinkedHashSet<>();

    ContainerUtil.addIfNotNull(types, getControlFlowExpressionType(expr));
    addExprTypesWhenContainerElement(types, expr);
    addExprTypesByDerivedClasses(types, expr);

    return types.toArray(PsiType.createArray(types.size()));
  }

  @NotNull
  @Override
  public Map<PsiExpression, PsiType> getControlFlowExpressionTypes(@NotNull final PsiExpression forPlace) {
    final Map<PsiExpression, PsiType> typeMap = buildDataflowTypeMap(forPlace);
    if (typeMap != null) {
      return typeMap;
    }

    return Collections.emptyMap();
  }

  @Nullable
  private static Map<PsiExpression, PsiType> buildDataflowTypeMap(PsiExpression forPlace) {
    PsiElement scope = DfaPsiUtil.getTopmostBlockInSameClass(forPlace);
    if (scope == null) {
      PsiFile file = forPlace.getContainingFile();
      if (!(file instanceof PsiCodeFragment)) {
        return Collections.emptyMap();
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

    final ExpressionTypeInstructionVisitor visitor = new ExpressionTypeInstructionVisitor(forPlace);
    if (runner.analyzeMethod(scope, visitor) == RunnerResult.OK) {
      return visitor.getResult();
    }
    return null;
  }

  private static Map<PsiExpression, PsiType> getAllTypeCasts(PsiExpression forPlace) {
    assert forPlace.isValid();
    final int start = forPlace.getTextRange().getStartOffset();
    final Map<PsiExpression, PsiType> allCasts = new THashMap<>(ExpressionTypeMemoryState.EXPRESSION_HASHING_STRATEGY);
    getTopmostBlock(forPlace).accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitTypeCastExpression(PsiTypeCastExpression expression) {
        final PsiType castType = expression.getType();
        final PsiExpression operand = expression.getOperand();
        if (operand != null && castType != null) {
          allCasts.put(operand, castType);
        }
        super.visitTypeCastExpression(expression);
      }

      @Override
      public void visitInstanceOfExpression(PsiInstanceOfExpression expression) {
        final PsiTypeElement castType = expression.getCheckType();
        final PsiExpression operand = expression.getOperand();
        if (castType != null) {
          allCasts.put(operand, castType.getType());
        }
        super.visitInstanceOfExpression(expression);
      }

      @Override
      public void visitElement(PsiElement element) {
        if (element.getTextRange().getStartOffset() > start) {
          return;
        }

        super.visitElement(element);
      }
    });
    return allCasts;
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

  @Override
  @Nullable
  public PsiType getControlFlowExpressionType(@NotNull PsiExpression expr) {
    final Map<PsiExpression, PsiType> allCasts = getAllTypeCasts(expr);
    if (!allCasts.containsKey(expr)) {
      return null; //optimization
    }

    final Map<PsiExpression, PsiType> fromDfa = buildDataflowTypeMap(expr);
    if (fromDfa != null) {
      return fromDfa.get(expr);
    }

    return null;
  }

  private static class ExpressionTypeInstructionVisitor extends InstructionVisitor {
    private Map<PsiExpression, PsiType> myResult;
    private final PsiElement myForPlace;

    private ExpressionTypeInstructionVisitor(PsiElement forPlace) {
      myForPlace = forPlace;
    }

    public Map<PsiExpression, PsiType> getResult() {
      return myResult;
    }

    @Override
    public DfaInstructionState[] visitInstanceof(InstanceofInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
      memState.pop();
      memState.pop();
      memState.push(new DfaInstanceofValue(runner.getFactory(), instruction.getLeft(), instruction.getCastType()));
      return new DfaInstructionState[]{new DfaInstructionState(runner.getInstruction(instruction.getIndex() + 1), memState)};
    }

    @Override
    public DfaInstructionState[] visitTypeCast(TypeCastInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
      ((ExpressionTypeMemoryState) memState).setExpressionType(instruction.getCasted(), instruction.getCastTo());
      return super.visitTypeCast(instruction, runner, memState);
    }

    @Override
    public DfaInstructionState[] visitMethodCall(MethodCallInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
      if (myForPlace == instruction.getCallExpression()) {
        addToResult(((ExpressionTypeMemoryState)memState).getStates());
      }
      return super.visitMethodCall(instruction, runner, memState);
    }

    @Override
    public DfaInstructionState[] visitPush(PushInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
      if (myForPlace == instruction.getPlace()) {
        addToResult(((ExpressionTypeMemoryState)memState).getStates());
      }
      return super.visitPush(instruction, runner, memState);
    }

    private void addToResult(Map<PsiExpression, PsiType> map) {
      if (myResult == null) {
        myResult = new THashMap<>(map, ExpressionTypeMemoryState.EXPRESSION_HASHING_STRATEGY);
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
