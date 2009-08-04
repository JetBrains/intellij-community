
package com.intellij.codeInsight.guess.impl;

import com.intellij.codeInsight.guess.GuessManager;
import com.intellij.codeInspection.dataFlow.*;
import com.intellij.codeInspection.dataFlow.instructions.PushInstruction;
import com.intellij.codeInspection.dataFlow.instructions.TypeCastInstruction;
import com.intellij.codeInspection.dataFlow.instructions.InstanceofInstruction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.search.PsiElementProcessorAdapter;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiUtil;
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

  public PsiType[] guessContainerElementType(PsiExpression containerExpr, TextRange rangeToIgnore) {
    HashSet<PsiType> typesSet = new HashSet<PsiType>();

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
        HashSet<PsiVariable> checkedVariables = new HashSet<PsiVariable>();
        addTypesByVariable(typesSet, (PsiVariable)refElement, file, checkedVariables, CHECK_USAGE | CHECK_DOWN, rangeToIgnore);
        checkedVariables.clear();
        addTypesByVariable(typesSet, (PsiVariable)refElement, file, checkedVariables, CHECK_UP, rangeToIgnore);
      }
    }

    return typesSet.toArray(new PsiType[typesSet.size()]);
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

  public PsiType[] guessTypeToCast(PsiExpression expr) { //TODO : make better guess based on control flow
    LinkedHashSet<PsiType> types = new LinkedHashSet<PsiType>();

    ContainerUtil.addIfNotNull(_getDataFlowExpressionTypes(expr).get(expr), types);
    addExprTypesWhenContainerElement(types, expr);
    addExprTypesByDerivedClasses(types, expr);

    return types.toArray(new PsiType[types.size()]);
  }

  @NotNull
  @Override
  public LinkedHashMap<PsiExpression, PsiType> getDataFlowExpressionTypes(@NotNull final PsiExpression forPlace) {
    final Map<PsiExpression, PsiType> map = _getDataFlowExpressionTypes(forPlace);
    List<PsiExpression> exprs = new ArrayList<PsiExpression>(map.keySet());
    Collections.sort(exprs, new Comparator<PsiExpression>() {
      public int compare(PsiExpression o1, PsiExpression o2) {
        return o1.getTextRange().getStartOffset() - o2.getTextRange().getStartOffset();
      }
    });
    final LinkedHashMap<PsiExpression, PsiType> result = new LinkedHashMap<PsiExpression, PsiType>();
    for (final PsiExpression expression : exprs) {
      result.put(expression, map.get(expression));
    }
    return result;
  }

  @NotNull
  private static Map<PsiExpression, PsiType> _getDataFlowExpressionTypes(final PsiExpression forPlace) {
    PsiElement scope = forPlace.getParent();
    PsiElement lastScope = scope;
    while(true){
      if (scope == null || scope instanceof PsiFile) break;
      scope = scope.getParent();
      if (scope instanceof PsiCodeBlock ||
          scope instanceof PsiFile && PsiUtil.isInJspFile(scope)) {
        lastScope = scope;
      }
    }

    DataFlowRunner runner = new DataFlowRunner(new InstructionFactory()) {
      protected DfaMemoryState createMemoryState() {
        return new ExpressionTypeMemoryState(getFactory());
      }
    };

    final ExpressionTypeInstructionVisitor visitor = new ExpressionTypeInstructionVisitor(forPlace);
    if (runner.analyzeMethod(lastScope, visitor) == RunnerResult.OK) {
      final Map<PsiExpression, PsiType> map = visitor.getResult();
      if (map != null) {
        return map;
      }
    }
    return Collections.emptyMap();
  }

  private void addExprTypesByDerivedClasses(LinkedHashSet<PsiType> set, PsiExpression expr) {
    PsiType type = expr.getType();
    if (!(type instanceof PsiClassType)) return;
    PsiClass refClass = PsiUtil.resolveClassInType(type);
    if (refClass == null) return;

    PsiManager manager = PsiManager.getInstance(myProject);
    PsiElementProcessor.CollectElementsWithLimit<PsiClass> processor = new PsiElementProcessor.CollectElementsWithLimit<PsiClass>(5);
    ClassInheritorsSearch.search(refClass, refClass.getUseScope(), true).forEach(new PsiElementProcessorAdapter<PsiClass>(processor));
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

    if ((flags & (CHECK_USAGE | CHECK_DOWN)) != 0){
      for (PsiReference varRef : ReferencesSearch.search(var, searchScope, false)) {
        PsiElement ref = varRef.getElement();

        if ((flags & CHECK_USAGE) != 0) {
          PsiType type = guessElementTypeFromReference(myMethodPatternMap, ref, rangeToIgnore);
          if (type != null && !(type instanceof PsiPrimitiveType)) {
            typesSet.add(type);
          }
        }

        if ((flags & CHECK_DOWN) != 0) {
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

    if ((flags & CHECK_UP) != 0){
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
              final PsiTypeElement castType = ((PsiTypeCastExpression)methodCall.getParent()).getCastType();
              return castType == null ? null : castType.getType();
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

  private static class ExpressionTypeInstructionVisitor extends InstructionVisitor {
    private Map<PsiExpression, PsiType> myResult;
    private PsiElement myForPlace;

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
    public DfaInstructionState[] visitPush(PushInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
      if (myForPlace == instruction.getPlace()) {
        final Map<PsiExpression, PsiType> map = ((ExpressionTypeMemoryState)memState).getStates();
        if (myResult == null) {
          myResult = new THashMap<PsiExpression, PsiType>(map, ExpressionTypeMemoryState.EXPRESSION_HASHING_STRATEGY);
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
      return super.visitPush(instruction, runner, memState);
    }
  }
}
