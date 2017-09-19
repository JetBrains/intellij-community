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
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInsight.daemon.impl.analysis.JavaGenericsUtil;
import com.intellij.codeInspection.dataFlow.instructions.Instruction;
import com.intellij.codeInspection.dataFlow.instructions.MethodCallInstruction;
import com.intellij.codeInspection.dataFlow.instructions.ReturnInstruction;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.NullableFunction;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class DfaPsiUtil {

  public static boolean isFinalField(PsiVariable var) {
    return var.hasModifierProperty(PsiModifier.FINAL) && !var.hasModifierProperty(PsiModifier.TRANSIENT) && var instanceof PsiField;
  }

  static PsiElement getEnclosingCodeBlock(final PsiVariable variable, final PsiElement context) {
    PsiElement codeBlock;
    if (variable instanceof PsiParameter) {
      codeBlock = ((PsiParameter)variable).getDeclarationScope();
      if (codeBlock instanceof PsiMethod) {
        codeBlock = ((PsiMethod)codeBlock).getBody();
      }
    }
    else if (variable instanceof PsiLocalVariable) {
      codeBlock = PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class);
    }
    else {
      codeBlock = getTopmostBlockInSameClass(context);
    }
    while (codeBlock != null) {
      PsiAnonymousClass anon = PsiTreeUtil.getParentOfType(codeBlock, PsiAnonymousClass.class);
      if (anon == null) break;
      codeBlock = PsiTreeUtil.getParentOfType(anon, PsiCodeBlock.class);
    }
    return codeBlock;
  }

  @NotNull
  public static Nullness getElementNullability(@Nullable PsiType resultType, @Nullable PsiModifierListOwner owner) {
    return getElementNullability(resultType, owner, false);
  }

  @NotNull
  public static Nullness getElementNullabilityIgnoringParameterInference(@Nullable PsiType resultType,
                                                                         @Nullable PsiModifierListOwner owner) {
    return getElementNullability(resultType, owner, true);
  }

  @NotNull
  private static Nullness getElementNullability(@Nullable PsiType resultType,
                                        @Nullable PsiModifierListOwner owner,
                                        boolean ignoreParameterNullabilityInference) {
    if (owner == null) return getTypeNullability(resultType);

    if (resultType instanceof PsiPrimitiveType) {
      return Nullness.UNKNOWN;
    }

    if (owner instanceof PsiEnumConstant || PsiUtil.isAnnotationMethod(owner)) {
      return Nullness.NOT_NULL;
    }
    if (owner instanceof PsiMethod && isEnumPredefinedMethod((PsiMethod)owner)) {
      return Nullness.NOT_NULL;
    }

    if (NullableNotNullManager.isNullable(owner)) {
      return Nullness.NULLABLE;
    }
    if (isNotNullLocally(owner, ignoreParameterNullabilityInference)) {
      return Nullness.NOT_NULL;
    }

    Nullness fromType = getTypeNullability(resultType);
    if (fromType != Nullness.UNKNOWN) return fromType;

    if (owner instanceof PsiParameter) {
      return inferParameterNullability((PsiParameter)owner);
    }

    return Nullness.UNKNOWN;
  }

  @NotNull
  public static Nullness inferParameterNullability(@NotNull PsiParameter parameter) {
    PsiElement parent = parameter.getParent();
    if (parent instanceof PsiParameterList && parent.getParent() instanceof PsiLambdaExpression) {
      return getFunctionalParameterNullability((PsiLambdaExpression)parent.getParent(), ((PsiParameterList)parent).getParameterIndex(parameter));
    }
    if (parent instanceof PsiForeachStatement) {
      PsiExpression iteratedValue = ((PsiForeachStatement)parent).getIteratedValue();
      if (iteratedValue != null) {
        return getTypeNullability(JavaGenericsUtil.getCollectionItemType(iteratedValue));
      }
    }
    return Nullness.UNKNOWN;
  }

  @NotNull
  public static Nullness getTypeNullability(@Nullable PsiType type) {
    if (type == null || type instanceof PsiPrimitiveType) return Nullness.UNKNOWN;

    Ref<Nullness> result = Ref.create(Nullness.UNKNOWN);
    InheritanceUtil.processSuperTypes(type, true, eachType -> {
      result.set(getTypeOwnNullability(eachType));
      return result.get() == Nullness.UNKNOWN;
    });
    return result.get();
  }

  @NotNull
  private static Nullness getTypeOwnNullability(PsiType eachType) {
    for (PsiAnnotation annotation : eachType.getAnnotations()) {
      String qualifiedName = annotation.getQualifiedName();
      NullableNotNullManager nnn = NullableNotNullManager.getInstance(annotation.getProject());
      if (nnn.getNullables().contains(qualifiedName)) {
        return Nullness.NULLABLE;
      }
      if (nnn.getNotNulls().contains(qualifiedName)) {
        return Nullness.NOT_NULL;
      }
    }
    return Nullness.UNKNOWN;
  }

  /**
   * Returns the nullness of functional expression parameter
   *
   * @param function functional expression
   * @param index parameter index
   * @return nullness, defined by SAM parameter annotations or known otherwise
   */
  @NotNull
  public static Nullness getFunctionalParameterNullability(PsiFunctionalExpression function, int index) {
    Nullness nullness = inferLambdaParameterNullness(function, index);
    if(nullness != Nullness.UNKNOWN) {
      return nullness;
    }
    PsiClassType type = ObjectUtils.tryCast(LambdaUtil.getFunctionalInterfaceType(function, true), PsiClassType.class);
    PsiMethod sam = LambdaUtil.getFunctionalInterfaceMethod(type);
    if (sam != null && index < sam.getParameterList().getParametersCount()) {
      PsiParameter parameter = sam.getParameterList().getParameters()[index];
      nullness = getElementNullability(null, parameter);
      if(nullness != Nullness.UNKNOWN) {
        return nullness;
      }
      PsiType parameterType = type.resolveGenerics().getSubstitutor().substitute(parameter.getType());
      return getTypeNullability(GenericsUtil.eliminateWildcards(parameterType, false, true));
    }
    return Nullness.UNKNOWN;
  }

  @NotNull
  private static Nullness inferLambdaParameterNullness(PsiFunctionalExpression lambda, int parameterIndex) {
    PsiElement expression = lambda;
    PsiElement expressionParent = lambda.getParent();
    while(expressionParent instanceof PsiConditionalExpression || expressionParent instanceof PsiParenthesizedExpression) {
      expression = expressionParent;
      expressionParent = expressionParent.getParent();
    }
    if(expressionParent instanceof PsiExpressionList) {
      PsiExpressionList list = (PsiExpressionList)expressionParent;
      PsiElement listParent = list.getParent();
      if(listParent instanceof PsiMethodCallExpression) {
        PsiMethod method = ((PsiMethodCallExpression)listParent).resolveMethod();
        if(method != null) {
          int expressionIndex = ArrayUtil.find(list.getExpressions(), expression);
          return getLambdaParameterNullness(method, expressionIndex, parameterIndex);
        }
      }
    }
    return Nullness.UNKNOWN;
  }

  @NotNull
  private static Nullness getLambdaParameterNullness(@NotNull PsiMethod method, int parameterIndex, int lambdaParameterIndex) {
    PsiClass type = method.getContainingClass();
    if(type != null) {
      if(CommonClassNames.JAVA_UTIL_OPTIONAL.equals(type.getQualifiedName())) {
        String methodName = method.getName();
        if((methodName.equals("map") || methodName.equals("filter") || methodName.equals("ifPresent") || methodName.equals("flatMap"))
          && parameterIndex == 0 && lambdaParameterIndex == 0) {
          return Nullness.NOT_NULL;
        }
      }
    }
    return Nullness.UNKNOWN;
  }

  private static boolean isNotNullLocally(@NotNull PsiModifierListOwner owner, boolean ignoreParameterNullabilityInference) {
    NullableNotNullManager nnnm = NullableNotNullManager.getInstance(owner.getProject());
    PsiAnnotation notNullAnno = nnnm.getNotNullAnnotation(owner, true);
    if (notNullAnno == null ||
        ignoreParameterNullabilityInference && owner instanceof PsiParameter && AnnotationUtil.isInferredAnnotation(notNullAnno)) {
      return false;
    }

    if (!(owner instanceof PsiParameter)) return true; // notnull on a super method requires all inheritors to return notnull as well

    // @NotNull on a super parameter doesn't prohibit calling the inheritors with null args, if they're ready for that.
    // so treat parameters as @NotNull only if they're annotated explicitly, or if they're in a scope of some nullity default annotation.
    return isOwnAnnotation(owner, notNullAnno) || nnnm.isContainerAnnotation(notNullAnno);
  }

  private static boolean isOwnAnnotation(@NotNull PsiModifierListOwner owner, @NotNull PsiAnnotation anno) {
    return AnnotationUtil.findAnnotation(owner, anno.getQualifiedName()) == anno;
  }

  private static boolean isEnumPredefinedMethod(PsiMethod method) {
    String methodName = method.getName();
    if (("valueOf".equals(methodName) || "values".equals(methodName)) && method.hasModifierProperty(PsiModifier.STATIC)) {
      PsiClass containingClass = method.getContainingClass();
      if (containingClass != null && containingClass.isEnum()) {
        PsiParameter[] parameters = method.getParameterList().getParameters();
        if ("values".equals(methodName)) return parameters.length == 0;
        return parameters.length == 1 && parameters[0].getType().equalsToText(CommonClassNames.JAVA_LANG_STRING);
      }
    }
    return false;
  }

  public static boolean isInitializedNotNull(PsiField field) {
    PsiClass containingClass = field.getContainingClass();
    if (containingClass == null) return false;

    PsiMethod[] constructors = containingClass.getConstructors();
    if (constructors.length == 0) return false;
    
    for (PsiMethod method : constructors) {
      if (!getNotNullInitializedFields(method, containingClass).contains(field)) {
        return false;
      }
    }
    return true;
  }

  private static Set<PsiField> getNotNullInitializedFields(final PsiMethod constructor, final PsiClass containingClass) {
    if (!constructor.getLanguage().isKindOf(JavaLanguage.INSTANCE)) return Collections.emptySet();
    
    final PsiCodeBlock body = constructor.getBody();
    if (body == null) return Collections.emptySet();
    
    return CachedValuesManager.getCachedValue(constructor, new CachedValueProvider<Set<PsiField>>() {
      @NotNull
      @Override
      public Result<Set<PsiField>> compute() {
        final PsiCodeBlock body = constructor.getBody();
        final Map<PsiField, Boolean> map = ContainerUtil.newHashMap();
        final StandardDataFlowRunner dfaRunner = new StandardDataFlowRunner(false, false) {

          private boolean isCallExposingNonInitializedFields(Instruction instruction) {
            if (!(instruction instanceof MethodCallInstruction) ||
                ((MethodCallInstruction)instruction).getMethodType() != MethodCallInstruction.MethodType.REGULAR_METHOD_CALL) {
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

          @NotNull
          @Override
          protected DfaInstructionState[] acceptInstruction(@NotNull InstructionVisitor visitor, @NotNull DfaInstructionState instructionState) {
            Instruction instruction = instructionState.getInstruction();
            if (isCallExposingNonInitializedFields(instruction) ||
                instruction instanceof ReturnInstruction && !((ReturnInstruction)instruction).isViaException()) {
              for (PsiField field : containingClass.getFields()) {
                if (!instructionState.getMemoryState().isNotNull(getFactory().getVarFactory().createVariableValue(field, false))) {
                  map.put(field, false);
                } else if (!map.containsKey(field)) {
                  map.put(field, true);
                }
              }
              return DfaInstructionState.EMPTY_ARRAY;
            }
            return super.acceptInstruction(visitor, instructionState);
          }
        };
        final RunnerResult rc = dfaRunner.analyzeMethod(body, new StandardInstructionVisitor());
        Set<PsiField> notNullFields = ContainerUtil.newHashSet();
        if (rc == RunnerResult.OK) {
          for (PsiField field : map.keySet()) {
            if (map.get(field)) {
              notNullFields.add(field);
            }
          }
        }
        return Result.create(notNullFields, constructor, PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT);
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
      //noinspection unchecked
      return MultiMap.EMPTY;
    }

    return CachedValuesManager.getCachedValue(psiClass, new CachedValueProvider<MultiMap<PsiField, PsiExpression>>() {
      @NotNull
      @Override
      public Result<MultiMap<PsiField, PsiExpression>> compute() {
        final Set<String> fieldNames = ContainerUtil.newHashSet();
        for (PsiField field : psiClass.getFields()) {
          ContainerUtil.addIfNotNull(fieldNames, field.getName());
        }

        final MultiMap<PsiField, PsiExpression> result = new MultiMap<>();
        JavaRecursiveElementWalkingVisitor visitor = new JavaRecursiveElementWalkingVisitor() {
          @Override
          public void visitAssignmentExpression(PsiAssignmentExpression assignment) {
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
        if (parent instanceof PsiAssignmentExpression) {
          final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)parent;
          final IElementType operation = assignmentExpression.getOperationTokenType();
          if (assignmentExpression.getLExpression() == psiReference) {
            if (JavaTokenType.EQ.equals(operation)) {
              final PsiExpression rValue = assignmentExpression.getRExpression();
              if (!literalsOnly || allOperandsAreLiterals(rValue)) {
                // if there's a codeBlock omit the values assigned later
                if (codeBlock != null && PsiTreeUtil.isAncestor(codeBlock, parent, true)
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
        if (psiExpression instanceof PsiPolyadicExpression) {
          PsiPolyadicExpression binaryExpression = (PsiPolyadicExpression)psiExpression;
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
}
