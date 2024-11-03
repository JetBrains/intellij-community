// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.Nullability;
import com.intellij.codeInsight.NullabilityAnnotationInfo;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.codeInsight.daemon.impl.analysis.JavaGenericsUtil;
import com.intellij.codeInspection.dataFlow.interpreter.RunnerResult;
import com.intellij.codeInspection.dataFlow.interpreter.StandardDataFlowInterpreter;
import com.intellij.codeInspection.dataFlow.java.ControlFlowAnalyzer;
import com.intellij.codeInspection.dataFlow.java.JavaDfaListener;
import com.intellij.codeInspection.dataFlow.java.inst.MethodCallInstruction;
import com.intellij.codeInspection.dataFlow.jvm.descriptors.PlainDescriptor;
import com.intellij.codeInspection.dataFlow.lang.DfaListener;
import com.intellij.codeInspection.dataFlow.lang.ir.*;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryStateImpl;
import com.intellij.codeInspection.dataFlow.types.DfPrimitiveType;
import com.intellij.codeInspection.dataFlow.types.DfReferenceType;
import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.codeInspection.dataFlow.types.DfTypes;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.codeInspection.dataFlow.value.RelationType;
import com.intellij.codeInspection.util.OptionalUtil;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.DeepestSuperMethodsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.containers.Stack;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.psi.CommonClassNames.*;
import static com.intellij.util.ObjectUtils.tryCast;
import static com.siyeh.ig.callMatcher.CallMatcher.staticCall;

public final class DfaPsiUtil {
  private static final Logger LOG = Logger.getInstance(DfaPsiUtil.class);

  private static final CallMatcher NON_NULL_VAR_ARG = CallMatcher.anyOf(
    staticCall(JAVA_UTIL_LIST, "of"),
    staticCall(JAVA_UTIL_SET, "of"),
    staticCall(JAVA_UTIL_MAP, "ofEntries"));

  public static boolean isFinalField(PsiVariable var) {
    return var.hasModifierProperty(PsiModifier.FINAL) && !var.hasModifierProperty(PsiModifier.TRANSIENT) && var instanceof PsiField;
  }

  @NotNull
  public static Nullability getElementNullability(@Nullable PsiType resultType, @Nullable PsiModifierListOwner owner) {
    return getElementNullability(resultType, owner, false);
  }

  @NotNull
  public static Nullability getElementNullabilityIgnoringParameterInference(@Nullable PsiType resultType,
                                                                         @Nullable PsiModifierListOwner owner) {
    return getElementNullability(resultType, owner, true);
  }

  @NotNull
  private static Nullability getElementNullability(@Nullable PsiType resultType,
                                                   @Nullable PsiModifierListOwner owner,
                                                   boolean ignoreParameterNullabilityInference) {
    if (owner == null) return getTypeNullability(resultType);

    if (resultType instanceof PsiPrimitiveType) {
      return Nullability.UNKNOWN;
    }

    if (owner instanceof PsiEnumConstant) {
      return Nullability.NOT_NULL;
    }

    // Annotation manager requires index
    if (DumbService.isDumb(owner.getProject())) return Nullability.UNKNOWN;
    NullabilityAnnotationInfo fromAnnotation = getNullabilityFromAnnotation(owner, ignoreParameterNullabilityInference);
    if (fromAnnotation != null) {
      if (fromAnnotation.getNullability() != Nullability.NOT_NULL &&
          owner instanceof PsiMethod method) {
        PsiType type = method.getReturnType();
        PsiAnnotationOwner annotationOwner = fromAnnotation.getAnnotation().getOwner();
        if (PsiUtil.resolveClassInClassTypeOnly(type) instanceof PsiTypeParameter &&
            annotationOwner instanceof PsiType && annotationOwner != type) {
          // Nullable/Unknown from type hierarchy: should check the instantiation, as it could be more concrete
          Nullability fromType = getNullabilityFromType(resultType, owner);
          if (fromType != null) return fromType;
        }
      }
      return fromAnnotation.getNullability();
    }

    if (owner instanceof PsiMethod method) {
      if (isEnumPredefinedMethod(method)) {
        return Nullability.NOT_NULL;
      }
      if (isMapMethodWithUnknownNullity(method)) {
        return getTypeNullability(resultType) == Nullability.NULLABLE ? Nullability.NULLABLE : Nullability.UNKNOWN;
      }
    }

    if (owner instanceof PsiParameter parameter) {
      Nullability nullability = inferParameterNullability(parameter);
      if (nullability != Nullability.UNKNOWN) {
        return nullability;
      }
    }

    Nullability fromType = getNullabilityFromType(resultType, owner);
    if (fromType != null) return fromType;

    if (owner instanceof PsiMethod method && method.getParameterList().isEmpty()) {
      PsiField field = PropertyUtil.getFieldOfGetter(method);
      if (field != null && getElementNullability(resultType, field) == Nullability.NULLABLE) {
        return Nullability.NULLABLE;
      }
    }

    return Nullability.UNKNOWN;
  }

  @Nullable
  private static Nullability getNullabilityFromType(@Nullable PsiType resultType, @NotNull PsiModifierListOwner owner) {
    Nullability fromType = getTypeNullability(resultType);
    if (fromType != Nullability.UNKNOWN) {
      if (fromType == Nullability.NOT_NULL && hasNullContract(owner)) {
        return Nullability.UNKNOWN;
      }
      return fromType;
    }
    return null;
  }

  private static boolean hasNullContract(@NotNull PsiModifierListOwner owner) {
    if (owner instanceof PsiParameter parameter && parameter.getDeclarationScope() instanceof PsiMethod method) {
      int index = method.getParameterList().getParameterIndex(parameter);
      List<StandardMethodContract> contracts = JavaMethodContractUtil.getMethodContracts(method);
      return ContainerUtil.exists(contracts,
                               c -> c.getParameterConstraint(index) == StandardMethodContract.ValueConstraint.NULL_VALUE);
    }
    return false;
  }

  private static @Nullable NullabilityAnnotationInfo getNullabilityFromAnnotation(@NotNull PsiModifierListOwner owner,
                                                                                  boolean ignoreParameterNullabilityInference) {
    NullableNotNullManager manager = NullableNotNullManager.getInstance(owner.getProject());
    NullabilityAnnotationInfo info = manager.findEffectiveNullabilityInfo(owner);
    if (info == null || shouldIgnoreAnnotation(info.getAnnotation())) {
      return null;
    }
    if (ignoreParameterNullabilityInference && owner instanceof PsiParameter && info.isInferred()) {
      List<PsiParameter> supers = AnnotationUtil.getSuperAnnotationOwners((PsiParameter)owner);
      return StreamEx.of(supers).map(param -> manager.findEffectiveNullabilityInfo(param))
        .findFirst(i -> i != null && i.getInheritedFrom() == null && i.getNullability() == Nullability.NULLABLE)
        .orElse(null);
    }
    return info;
  }

  private static boolean isMapMethodWithUnknownNullity(@NotNull PsiMethod method) {
    String name = method.getName();
    if (!"get".equals(name) && !"remove".equals(name)) return false;
    PsiMethod superMethod = DeepestSuperMethodsSearch.search(method).findFirst();
    return ("java.util.Map." + name).equals(PsiUtil.getMemberQualifiedName(superMethod != null ? superMethod : method));
  }

  @NotNull
  public static Nullability inferParameterNullability(@NotNull PsiParameter parameter) {
    PsiElement parent = parameter.getParent();
    if (parent instanceof PsiParameterList) {
      PsiElement gParent = parent.getParent();
      if (gParent instanceof PsiLambdaExpression) {
        return getFunctionalParameterNullability((PsiLambdaExpression)gParent, ((PsiParameterList)parent).getParameterIndex(parameter));
      } else if (gParent instanceof PsiMethod && OptionalUtil.OPTIONAL_OF_NULLABLE.methodMatches((PsiMethod)gParent)) {
        return Nullability.NULLABLE;
      }
    }
    if (parent instanceof PsiForeachStatement) {
      return getTypeNullability(inferLoopParameterTypeWithNullability((PsiForeachStatement)parent));
    }
    return Nullability.UNKNOWN;
  }

  @Nullable
  private static PsiType inferLoopParameterTypeWithNullability(PsiForeachStatement loop) {
    PsiExpression iteratedValue = PsiUtil.skipParenthesizedExprDown(loop.getIteratedValue());
    if (iteratedValue == null) return null;

    PsiType iteratedType = iteratedValue.getType();
    if (iteratedValue instanceof PsiReferenceExpression refExpr &&
        refExpr.resolve() instanceof PsiParameter parameter &&
        parameter.getParent() instanceof PsiForeachStatement targetLoop &&
        PsiTreeUtil.isAncestor(targetLoop, loop, true) &&
        !HighlightControlFlowUtil.isReassigned(parameter, new HashMap<>())) {
      iteratedType = inferLoopParameterTypeWithNullability(targetLoop);
    }
    return JavaGenericsUtil.getCollectionItemType(iteratedType, iteratedValue.getResolveScope());
  }


  @NotNull
  public static Nullability getTypeNullability(@Nullable PsiType type) {
    NullabilityAnnotationInfo info = getTypeNullabilityInfo(type);
    return info == null ? Nullability.UNKNOWN : info.getNullability();
  }

  @Nullable
  public static NullabilityAnnotationInfo getTypeNullabilityInfo(@Nullable PsiType type) {
    if (type == null || type instanceof PsiPrimitiveType) return null;

    Ref<NullabilityAnnotationInfo> result = Ref.create(null);
    InheritanceUtil.processSuperTypes(type, true, eachType -> {
      result.set(getTypeOwnNullability(eachType));
      return result.get() == null &&
             (!(type instanceof PsiClassType) || PsiUtil.resolveClassInClassTypeOnly(type) instanceof PsiTypeParameter);
    });
    return result.get();
  }

  @Nullable
  private static NullabilityAnnotationInfo getTypeOwnNullability(PsiType eachType) {
    for (PsiAnnotation annotation : eachType.getAnnotations()) {
      String qualifiedName = annotation.getQualifiedName();
      NullableNotNullManager nnn = NullableNotNullManager.getInstance(annotation.getProject());
      Optional<Nullability> optionalNullability = nnn.getAnnotationNullability(qualifiedName);
      if (optionalNullability.isPresent()) {
        Nullability nullability = optionalNullability.get();
        if (nullability == Nullability.NULLABLE && shouldIgnoreAnnotation(annotation)) continue;
        return new NullabilityAnnotationInfo(annotation, nullability, false);
      }
    }
    if (eachType instanceof PsiClassType) {
      PsiElement context = ((PsiClassType)eachType).getPsiContext();
      if (context != null) {
        return NullableNotNullManager.getInstance(context.getProject()).findDefaultTypeUseNullability(context);
      }
    }
    return null;
  }

  private static boolean shouldIgnoreAnnotation(PsiAnnotation annotation) {
    PsiClass containingClass = PsiUtil.getContainingClass(annotation);
    if (containingClass == null || !containingClass.isValid()) return false;
    String qualifiedName = containingClass.getQualifiedName();
    // We deliberately ignore nullability annotations on Guava functional interfaces to avoid noise warnings
    // See IDEA-170548 for details
    return "com.google.common.base.Predicate".equals(qualifiedName) || "com.google.common.base.Function".equals(qualifiedName);
  }

  /**
   * Returns the nullability of functional expression parameter
   *
   * @param function functional expression
   * @param index parameter index
   * @return nullability, defined by SAM parameter annotations or known otherwise
   */
  @NotNull
  public static Nullability getFunctionalParameterNullability(PsiFunctionalExpression function, int index) {
    PsiClassType type = tryCast(LambdaUtil.getFunctionalInterfaceType(function, true), PsiClassType.class);
    PsiMethod sam = LambdaUtil.getFunctionalInterfaceMethod(type);
    if (sam != null) {
      PsiParameter parameter = sam.getParameterList().getParameter(index);
      if (parameter != null) {
        Nullability nullability = getElementNullability(null, parameter);
        if (nullability != Nullability.UNKNOWN) {
          return nullability;
        }
        PsiType parameterType = type.resolveGenerics().getSubstitutor().substitute(parameter.getType());
        return getTypeNullability(GenericsUtil.eliminateWildcards(parameterType, false, true));
      }
    }
    return Nullability.UNKNOWN;
  }

  private static boolean isEnumPredefinedMethod(PsiMethod method) {
    return CallMatcher.enumValueOf().methodMatches(method) || CallMatcher.enumValues().methodMatches(method);
  }

  public static boolean isInitializedNotNull(PsiField field) {
    PsiClass containingClass = field.getContainingClass();
    if (containingClass == null) return false;

    boolean staticField = field.hasModifierProperty(PsiModifier.STATIC);
    for (PsiClassInitializer initializer : containingClass.getInitializers()) {
      if (initializer.getLanguage().isKindOf(JavaLanguage.INSTANCE) &&
          initializer.hasModifierProperty(PsiModifier.STATIC) == staticField &&
          getBlockNotNullFields(initializer.getBody()).contains(field)) {
        return true;
      }
    }
    if (staticField) return false;

    PsiMethod[] constructors = containingClass.getConstructors();
    if (constructors.length == 0) return false;

    for (PsiMethod method : constructors) {
      if (!method.getLanguage().isKindOf(JavaLanguage.INSTANCE) ||
          !getBlockNotNullFields(method.getBody()).contains(field)) {
        return false;
      }
    }
    return true;
  }

  private static Set<PsiField> getBlockNotNullFields(PsiCodeBlock body) {
    if (body == null) return Collections.emptySet();

    return CachedValuesManager.getCachedValue(body, new CachedValueProvider<>() {
      @NotNull
      @Override
      public Result<Set<PsiField>> compute() {
        PsiClass containingClass = PsiTreeUtil.getContextOfType(body, PsiClass.class);
        LOG.assertTrue(containingClass != null);
        DfaValueFactory factory = new DfaValueFactory(body.getProject());
        ControlFlow flow = ControlFlowAnalyzer.buildFlow(body, factory, true);
        if (flow == null) {
          return Result.create(Set.of(), body, PsiModificationTracker.MODIFICATION_COUNT);
        }
        var interpreter = new StandardDataFlowInterpreter(flow, DfaListener.EMPTY) {
          final Map<PsiField, Boolean> map = new HashMap<>();
          private boolean isCallExposingNonInitializedFields(Instruction instruction) {
            if (!(instruction instanceof MethodCallInstruction)) {
              return false;
            }

            PsiCall call = ((MethodCallInstruction)instruction).getCallExpression();
            if (call == null) return false;

            if (call instanceof PsiNewExpression && canAccessFields((PsiExpression)call)) {
              return true;
            }

            if (call instanceof PsiMethodCallExpression) {
              PsiExpression qualifier = ((PsiMethodCallExpression)call).getMethodExpression().getQualifierExpression();
              if (qualifier == null || canAccessFields(qualifier)) {
                return true;
              }
            }

            PsiExpressionList argumentList = call.getArgumentList();
            if (argumentList != null) {
              for (PsiExpression expression : argumentList.getExpressions()) {
                if (canAccessFields(expression)) return true;
              }
            }

            return false;
          }

          private boolean canAccessFields(PsiExpression expression) {
            PsiClass type = PsiUtil.resolveClassInClassTypeOnly(expression.getType());
            JBIterable<PsiClass> typeContainers =
              JBIterable.generate(type, PsiClass::getContainingClass).takeWhile(c -> !c.hasModifierProperty(PsiModifier.STATIC));
            return typeContainers.contains(containingClass);
          }

          @Override
          protected DfaInstructionState @NotNull [] acceptInstruction(@NotNull DfaInstructionState instructionState) {
            Instruction instruction = instructionState.getInstruction();
            if (instruction instanceof FinishElementInstruction finishInstruction) {
              finishInstruction.removeFromFlushList(desc ->
                desc instanceof PlainDescriptor plainDescriptor && plainDescriptor.getPsiElement() instanceof PsiField field &&
                field.getContainingClass() == containingClass);
            }
            if ((isCallExposingNonInitializedFields(instruction) || instruction instanceof ReturnInstruction)) {
              for (PsiField field : containingClass.getFields()) {
                DfaVariableValue value = PlainDescriptor.createVariableValue(getFactory(), field);
                DfType dfType = instructionState.getMemoryState().getDfType(value);
                if (dfType.isSuperType(DfTypes.NULL)) {
                  map.put(field, false);
                }
                else if (!map.containsKey(field)) {
                  map.put(field, true);
                }
              }
              return DfaInstructionState.EMPTY_ARRAY;
            }
            return super.acceptInstruction(instructionState);
          }
        };
        DfaMemoryState state = DfaUtil.createStateWithEnabledAssertions(factory);
        final RunnerResult rc = interpreter.interpret(state);
        Set<PsiField> notNullFields = new HashSet<>();
        if (rc == RunnerResult.OK) {
          interpreter.map.forEach((key, value) -> {
            if (value) {
              notNullFields.add(key);
            }
          });
        }
        return Result.create(notNullFields, body, PsiModificationTracker.MODIFICATION_COUNT);
      }
    });
  }

  public static List<PsiExpression> findAllConstructorInitializers(PsiField field) {
    final List<PsiExpression> result = ContainerUtil.createLockFreeCopyOnWriteList();
    ContainerUtil.addIfNotNull(result, field.getInitializer());

    final PsiClass containingClass = field.getContainingClass();
    if (containingClass != null && !(containingClass instanceof PsiCompiledElement)) {
      result.addAll(getAllConstructorFieldInitializers(containingClass).get(field));
    }
    return result;
  }

  private static MultiMap<PsiField, PsiExpression> getAllConstructorFieldInitializers(final PsiClass psiClass) {
    if (psiClass instanceof PsiCompiledElement) {
      return MultiMap.empty();
    }

    return CachedValuesManager.getCachedValue(psiClass, new CachedValueProvider<>() {
      @NotNull
      @Override
      public Result<MultiMap<PsiField, PsiExpression>> compute() {
        final Set<String> fieldNames = new HashSet<>();
        for (PsiField field : psiClass.getFields()) {
          ContainerUtil.addIfNotNull(fieldNames, field.getName());
        }

        final MultiMap<PsiField, PsiExpression> result = new MultiMap<>();
        JavaRecursiveElementWalkingVisitor visitor = new JavaRecursiveElementWalkingVisitor() {
          @Override
          public void visitAssignmentExpression(@NotNull PsiAssignmentExpression assignment) {
            super.visitAssignmentExpression(assignment);
            PsiExpression lExpression = assignment.getLExpression();
            PsiExpression rExpression = assignment.getRExpression();
            if (rExpression != null &&
                lExpression instanceof PsiReferenceExpression &&
                fieldNames.contains(((PsiReferenceExpression)lExpression).getReferenceName())) {
              PsiElement target = ((PsiReferenceExpression)lExpression).resolve();
              if (target instanceof PsiField && ((PsiField)target).getContainingClass() == psiClass) {
                result.putValue((PsiField)target, rExpression);
              }
            }
          }
        };

        for (PsiMethod constructor : psiClass.getConstructors()) {
          if (constructor.getLanguage().isKindOf(JavaLanguage.INSTANCE)) {
            constructor.accept(visitor);
          }
        }

        for (PsiClassInitializer initializer : psiClass.getInitializers()) {
          if (initializer.getLanguage().isKindOf(JavaLanguage.INSTANCE)) {
            initializer.accept(visitor);
          }
        }

        return Result.create(result, psiClass);
      }
    });
  }

  @Nullable
  public static PsiElement getTopmostBlockInSameClass(@NotNull PsiElement position) {
    return JBIterable.
      generate(position, PsiElement::getParent).
      takeWhile(e -> !(e instanceof PsiMember || e instanceof PsiFile || e instanceof PsiLambdaExpression)).
      filter(e -> e instanceof PsiCodeBlock || e instanceof PsiExpression && e.getParent() instanceof PsiLambdaExpression).
      last();
  }

  @NotNull
  public static Collection<PsiExpression> getVariableAssignmentsInFile(@NotNull PsiVariable psiVariable,
                                                                       final boolean literalsOnly,
                                                                       final PsiElement place) {
    Ref<Boolean> modificationRef = Ref.create(Boolean.FALSE);
    PsiElement codeBlock = place == null? null : getTopmostBlockInSameClass(place);
    int placeOffset = codeBlock != null? place.getTextRange().getStartOffset() : 0;
    PsiFile containingFile = psiVariable.getContainingFile();
    LocalSearchScope scope = new LocalSearchScope(new PsiElement[]{containingFile}, null, true);
    Collection<PsiReference> references = ReferencesSearch.search(psiVariable, scope).findAll();
    List<PsiExpression> list = ContainerUtil.mapNotNull(
      references,
      (NullableFunction<PsiReference, PsiExpression>)psiReference -> {
        if (modificationRef.get()) return null;
        final PsiElement parent = psiReference.getElement().getParent();
        if (parent instanceof PsiAssignmentExpression assignmentExpression) {
          final IElementType operation = assignmentExpression.getOperationTokenType();
          if (assignmentExpression.getLExpression() == psiReference) {
            if (JavaTokenType.EQ.equals(operation)) {
              final PsiExpression rValue = assignmentExpression.getRExpression();
              if (!literalsOnly || allOperandsAreLiterals(rValue)) {
                // if there's a codeBlock omit the values assigned later
                if (PsiTreeUtil.isAncestor(codeBlock, parent, true)
                    && placeOffset < parent.getTextRange().getStartOffset()) {
                  return null;
                }
                return rValue;
              }
              else {
                modificationRef.set(Boolean.TRUE);
              }
            }
            else if (JavaTokenType.PLUSEQ.equals(operation)) {
              modificationRef.set(Boolean.TRUE);
            }
          }
        }
        return null;
      });
    if (modificationRef.get()) return Collections.emptyList();
    PsiExpression initializer = psiVariable.getInitializer();
    if (initializer != null && (!literalsOnly || allOperandsAreLiterals(initializer))) {
      list = ContainerUtil.concat(list, Collections.singletonList(initializer));
    }
    return list;
  }

  private static boolean allOperandsAreLiterals(@Nullable final PsiExpression expression) {
    if (expression == null) return false;
    if (expression instanceof PsiLiteralExpression) return true;
    if (expression instanceof PsiPolyadicExpression) {
      Stack<PsiExpression> stack = new Stack<>();
      stack.add(expression);
      while (!stack.isEmpty()) {
        PsiExpression psiExpression = stack.pop();
        if (psiExpression instanceof PsiPolyadicExpression binaryExpression) {
          for (PsiExpression op : binaryExpression.getOperands()) {
            stack.push(op);
          }
        }
        else if (!(psiExpression instanceof PsiLiteralExpression)) {
          return false;
        }
      }
      return true;
    }
    return false;
  }

  /**
   * @param method method to check
   * @return nullability of vararg parameter component; {@link Nullability#UNKNOWN} if not specified or method is not vararg method.
   */
  @NotNull
  static Nullability getVarArgComponentNullability(PsiMethod method) {
    if (method != null) {
      if (NON_NULL_VAR_ARG.methodMatches(method)) {
        return Nullability.NOT_NULL;
      }
      PsiParameter varArg = ArrayUtil.getLastElement(method.getParameterList().getParameters());
      if (varArg != null) {
        PsiType type = varArg.getType();
        if (type instanceof PsiEllipsisType) {
          PsiType componentType = ((PsiEllipsisType)type).getComponentType();
          return getTypeNullability(componentType);
        }
      }
    }
    return Nullability.UNKNOWN;
  }

  /**
   * Try to restore type parameters based on the expression type
   *
   * @param expression expression which type is a supertype of the type to generify
   * @param type a type to generify
   * @return a generified type, or original type if generification is not possible
   */
  public static PsiType tryGenerify(PsiExpression expression, PsiType type) {
    if (!(type instanceof PsiClassType classType)) {
      return type;
    }
    if (!classType.isRaw()) {
      return classType;
    }
    PsiClass psiClass = classType.resolve();
    if (psiClass == null) return classType;
    if (!(expression.getType() instanceof PsiClassType expressionType)) return classType;
    PsiClassType result = GenericsUtil.getExpectedGenericType(expression, psiClass, expressionType);
    if (result.isRaw()) {
      PsiClass aClass = result.resolve();
      if (aClass != null) {
        int length = aClass.getTypeParameters().length;
        PsiWildcardType wildcard = PsiWildcardType.createUnbounded(aClass.getManager());
        PsiType[] arguments = new PsiType[length];
        Arrays.fill(arguments, wildcard);
        return JavaPsiFacade.getElementFactory(aClass.getProject()).createType(aClass, arguments);
      }
    }
    return result;
  }

  /**
   * @param expr literal to create a constant type from
   * @return a DfType that describes given literal
   */
  @NotNull
  public static DfType fromLiteral(@NotNull PsiLiteralExpression expr) {
    PsiType type = expr.getType();
    if (type == null) return DfType.TOP;
    if (PsiTypes.nullType().equals(type)) return DfTypes.NULL;
    Object value = expr.getValue();
    if (value == null) return DfTypes.typedObject(type, Nullability.NOT_NULL);
    return DfTypes.constant(value, type);
  }

  /**
   * Create RelationType from Java token
   * @param type Java token
   * @return RelationType
   */
  @Nullable
  public static RelationType getRelationByToken(IElementType type) {
    if(JavaTokenType.EQEQ.equals(type)) {
      return RelationType.EQ;
    }
    if(JavaTokenType.NE.equals(type)) {
      return RelationType.NE;
    }
    if(JavaTokenType.LT.equals(type)) {
      return RelationType.LT;
    }
    if(JavaTokenType.GT.equals(type)) {
      return RelationType.GT;
    }
    if(JavaTokenType.LE.equals(type)) {
      return RelationType.LE;
    }
    if(JavaTokenType.GE.equals(type)) {
      return RelationType.GE;
    }
    if(JavaTokenType.INSTANCEOF_KEYWORD.equals(type)) {
      return RelationType.IS;
    }
    return null;
  }

  /**
   * Tries to convert {@link DfType} to {@link PsiType}.
   *
   * @param project project
   * @param dfType DfType to convert
   * @return converted DfType; null if no corresponding PsiType found.
   */
  public static @Nullable PsiType dfTypeToPsiType(@NotNull Project project, @Nullable DfType dfType) {
    if (dfType instanceof DfPrimitiveType) {
      return ((DfPrimitiveType)dfType).getPsiType();
    }
    if (dfType instanceof DfReferenceType) {
      return ((DfReferenceType)dfType).getConstraint().getPsiType(project);
    }
    return null;
  }

  /**
   * @param value constant value
   * @return human readable representation of the value
   */
  public static @NlsSafe String renderValue(Object value) {
    if (value == null) return "null";
    if (value instanceof String) return '"' + StringUtil.escapeStringCharacters((String)value) + '"';
    if (value instanceof Float) return value + "f";
    if (value instanceof Long) return value + "L";
    if (value instanceof PsiField field) {
      PsiClass containingClass = field.getContainingClass();
      return containingClass == null ? field.getName() : containingClass.getName() + "." + field.getName();
    }
    if (value instanceof PsiType type) {
      return type.getPresentableText();
    }
    return value.toString();
  }

  /**
   * @param anchor boolean expression
   * @param expectedValue the expected result of boolean expression
   * @return true if this boolean expression is effectively an assertion (code throws if its value is not equal to expectedValue)
   */
  public static boolean isAssertionEffectively(@NotNull PsiExpression anchor, boolean expectedValue) {
    PsiElement parent;
    while (true) {
      parent = anchor.getParent();
      if (parent instanceof PsiExpression parentExpr && BoolUtils.isNegation(parentExpr)) {
        expectedValue = !expectedValue;
        anchor = parentExpr;
        continue;
      }
      if (parent instanceof PsiParenthesizedExpression parenthesized) {
        anchor = parenthesized;
        continue;
      }
      if (parent instanceof PsiPolyadicExpression polyadic) {
        IElementType tokenType = polyadic.getOperationTokenType();
        if (tokenType.equals(JavaTokenType.ANDAND) || tokenType.equals(JavaTokenType.OROR)) {
          // always true operand makes always true OR-chain and does not affect the result of AND-chain
          // Note that in `assert unknownExpression && trueExpression;` the trueExpression should not be reported
          // because this assertion is essentially the shortened `assert unknownExpression; assert trueExpression;`
          // which is not reported.
          boolean causesShortCircuit = (tokenType.equals(JavaTokenType.OROR) == expectedValue) &&
                                       ArrayUtil.getLastElement(polyadic.getOperands()) != anchor;
          if (!causesShortCircuit) {
            // We still report `assert trueExpression || unknownExpression`, because here `unknownExpression` is never checked
            // which is probably not intended.
            anchor = polyadic;
            continue;
          }
        }
      }
      break;
    }
    if (parent instanceof PsiAssertStatement) {
      return expectedValue;
    }
    if (parent instanceof PsiIfStatement ifStatement && anchor == ifStatement.getCondition()) {
      PsiStatement thenBranch = ControlFlowUtils.stripBraces(ifStatement.getThenBranch());
      if (thenBranch instanceof PsiThrowStatement) {
        return !expectedValue;
      }
    }
    return isAssertCallArgument(anchor, ContractValue.booleanValue(expectedValue));
  }

  /**
   * @param anchor expression
   * @param wantedConstraint expected contract value
   * @return true if this expression is an argument for an assertion call that ensures that the expression is equal to the expected value
   */
  public static boolean isAssertCallArgument(@NotNull PsiElement anchor, @NotNull ContractValue wantedConstraint) {
    PsiElement parent = PsiUtil.skipParenthesizedExprUp(anchor.getParent());
    if (parent instanceof PsiExpressionList) {
      int index = ArrayUtil.indexOf(((PsiExpressionList)parent).getExpressions(), anchor);
      if (index >= 0) {
        PsiMethodCallExpression call = tryCast(parent.getParent(), PsiMethodCallExpression.class);
        if (call != null) {
          MethodContract contract = ContainerUtil.getOnlyItem(JavaMethodContractUtil.getMethodCallContracts(call));
          if (contract != null && contract.getReturnValue().isFail()) {
            ContractValue condition = ContainerUtil.getOnlyItem(contract.getConditions());
            if (condition != null) {
              return condition.getArgumentComparedTo(wantedConstraint, false).orElse(-1) == index;
            }
          }
        }
      }
    }
    return false;
  }

  /**
   * @param cond1 first condition
   * @param cond2 second condition
   * @return true if conditions cannot be true at the same time.
   */
  public static boolean mutuallyExclusive(@NotNull PsiExpression cond1, @NotNull PsiExpression cond2) {
    Project project = cond1.getProject();
    PsiPolyadicExpression expr =
      (PsiPolyadicExpression)JavaPsiFacade.getElementFactory(project).createExpressionFromText("a && b", cond1);
    PsiExpression[] operands = expr.getOperands();
    operands[0].replace(cond1);
    operands[1].replace(cond2);
    DfaValueFactory factory = new DfaValueFactory(project);
    ControlFlow flow = ControlFlowAnalyzer.buildFlow(expr, factory, true);
    if (flow == null) return false;
    var listener = new JavaDfaListener() {
      final Set<DfType> results = new HashSet<>();

      @Override
      public void beforeExpressionPush(@NotNull DfaValue value, @NotNull PsiExpression expression, @NotNull DfaMemoryState state) {
        if (expression == expr) {
          results.add(value.getDfType());
        }
      }
    };
    StandardDataFlowInterpreter interpreter = new StandardDataFlowInterpreter(flow, listener);
    if (interpreter.interpret(new DfaMemoryStateImpl(factory)) != RunnerResult.OK) return false;
    return DfTypes.FALSE.equals(ContainerUtil.getOnlyItem(listener.results));
  }
}
