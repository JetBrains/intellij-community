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
package com.intellij.codeInspection.reflectiveAccess;

import com.intellij.codeInspection.BaseJavaBatchLocalInspectionTool;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static com.intellij.psi.CommonClassNames.JAVA_UTIL_LIST;
import static com.intellij.psi.impl.source.resolve.reference.impl.JavaReflectionReferenceUtil.*;

/**
 * @author Pavel.Dolgov
 */
public class JavaLangReflectHandleInvocationInspection extends BaseJavaBatchLocalInspectionTool {
  private static final Logger LOG = Logger.getInstance(JavaLangReflectHandleInvocationInspection.class);

  private static final String INVOKE = "invoke";
  private static final String INVOKE_EXACT = "invokeExact";
  private static final String INVOKE_WITH_ARGUMENTS = "invokeWithArguments";
  private static final String JAVA_LANG_INVOKE_METHOD_HANDLE = "java.lang.invoke.MethodHandle";

  private static final Set<String> INVOKE_NAMES = ContainerUtil.set(INVOKE, INVOKE_EXACT, INVOKE_WITH_ARGUMENTS);

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression methodCall) {
        super.visitMethodCallExpression(methodCall);

        final String referenceName = methodCall.getMethodExpression().getReferenceName();
        if (referenceName != null && INVOKE_NAMES.contains(referenceName)) {
          final PsiMethod method = methodCall.resolveMethod();
          if (method != null && isClassWithName(method.getContainingClass(), JAVA_LANG_INVOKE_METHOD_HANDLE)) {
            if (isWithDynamicArguments(methodCall)) {
              return;
            }
            final PsiExpression qualifierDefinition = findDefinition(methodCall.getMethodExpression().getQualifierExpression());
            if (qualifierDefinition instanceof PsiMethodCallExpression) {
              checkMethodHandleInvocation((PsiMethodCallExpression)qualifierDefinition, methodCall, INVOKE_EXACT.equals(referenceName));
            }
          }
        }
      }

      private void checkMethodHandleInvocation(@NotNull PsiMethodCallExpression handleFactoryCall,
                                               @NotNull PsiMethodCallExpression invokeCall,
                                               boolean isExact) {
        final String factoryMethodName = handleFactoryCall.getMethodExpression().getReferenceName();
        if (factoryMethodName != null && JavaLangInvokeHandleSignatureInspection.KNOWN_METHOD_NAMES.contains(factoryMethodName)) {

          final PsiExpression[] handleFactoryArguments = handleFactoryCall.getArgumentList().getExpressions();
          final boolean isFindConstructor = FIND_CONSTRUCTOR.equals(factoryMethodName);
          if (handleFactoryArguments.length == 3 && !isFindConstructor ||
              handleFactoryArguments.length == 2 && isFindConstructor ||
              handleFactoryArguments.length == 4 && FIND_SPECIAL.equals(factoryMethodName)) {

            final PsiMethod factoryMethod = handleFactoryCall.resolveMethod();
            if (factoryMethod != null && isClassWithName(factoryMethod.getContainingClass(), JAVA_LANG_INVOKE_METHOD_HANDLES_LOOKUP)) {
              final PsiClass psiClass = getReflectiveClass(handleFactoryArguments[0]);

              if (isFindConstructor) {
                if (psiClass != null) checkArgumentTypes(invokeCall, handleFactoryArguments[1], isExact, 0, psiClass);
                return;
              }

              final PsiExpression typeExpression = handleFactoryArguments[2];
              switch (factoryMethodName) {
                case FIND_VIRTUAL:
                case FIND_SPECIAL:
                  if (!checkArgumentTypes(invokeCall, typeExpression, isExact, 1, null)) return;
                  checkCallReceiver(invokeCall, psiClass);
                  break;

                case FIND_STATIC:
                  checkArgumentTypes(invokeCall, typeExpression, isExact, 0, null);
                  break;

                case FIND_GETTER:
                  if (!checkGetter(invokeCall, typeExpression, isExact, 1)) return;
                  checkCallReceiver(invokeCall, psiClass);
                  break;

                case FIND_SETTER:
                  if (!checkSetter(invokeCall, typeExpression, isExact, 1)) return;
                  checkCallReceiver(invokeCall, psiClass);
                  break;

                case FIND_STATIC_GETTER:
                  checkGetter(invokeCall, typeExpression, isExact, 0);
                  break;

                case FIND_STATIC_SETTER:
                  checkSetter(invokeCall, typeExpression, isExact, 0);
                  break;

                case FIND_VAR_HANDLE:
                  break;

                case FIND_STATIC_VAR_HANDLE:
                  break;
              }
            }
          }
        }
      }

      private void checkCallReceiver(@NotNull PsiMethodCallExpression invokeCall, @Nullable PsiClass psiClass) {
        final PsiExpressionList argumentList = invokeCall.getArgumentList();
        final PsiExpression[] arguments = argumentList.getExpressions();
        if (arguments.length == 0) {
          holder.registerProblem(argumentList, InspectionsBundle.message("inspection.reflect.handle.invocation.receiver.missing"));
          return;
        }

        final PsiExpression receiver = arguments[0];
        LOG.assertTrue(receiver != null);
        final PsiExpression receiverDefinition = findDefinition(receiver);
        if (ExpressionUtils.isNullLiteral(receiverDefinition)) {
          holder.registerProblem(receiver, InspectionsBundle.message("inspection.reflect.handle.invocation.receiver.null"));
          return;
        }

        if (psiClass != null) {
          final PsiClassType expectedType = JavaPsiFacade.getElementFactory(holder.getProject()).createType(psiClass);
          if (!isCompatible(expectedType, receiver.getType())) {
            holder.registerProblem(receiver,
                                   InspectionsBundle.message("inspection.reflect.handle.invocation.receiver.incompatible",
                                                             psiClass.getQualifiedName()));
          }
          else if (receiver != receiverDefinition && receiverDefinition != null) {
            if (!isCompatible(expectedType, receiverDefinition.getType())) {
              holder.registerProblem(receiver, InspectionsBundle.message("inspection.reflect.handle.invocation.receiver.incompatible",
                                                                         psiClass.getQualifiedName()));
            }
          }
        }
      }

      private boolean checkArgumentTypes(@NotNull PsiMethodCallExpression invokeCall,
                                         @NotNull PsiExpression signatureExpression,
                                         boolean isExact,
                                         int argumentOffset,
                                         @Nullable PsiClass resultClass) {
        final List<Supplier<ReflectiveType>> lazyRequiredTypes = getMethodType(signatureExpression);
        if (lazyRequiredTypes == null) return true;

        final PsiExpressionList argumentList = invokeCall.getArgumentList();
        final PsiExpression[] arguments = argumentList.getExpressions();
        JavaReflectionInvocationInspection.Arguments actualArguments =
          JavaReflectionInvocationInspection.getActualMethodArguments(arguments, argumentOffset, false);
        if (actualArguments == null) return true;

        final int requiredArgumentCount = lazyRequiredTypes.size() - 1; // - 1 stands for return type
        if (!checkArgumentCount(actualArguments.expressions, requiredArgumentCount, argumentOffset, argumentList)) return false;
        final ReflectiveType returnType = resultClass != null ? ReflectiveType.create(resultClass) : lazyRequiredTypes.get(0).get();
        checkReturnType(invokeCall, returnType, isExact);

        LOG.assertTrue(actualArguments.expressions.length == requiredArgumentCount);
        for (int i = 0; i < requiredArgumentCount; i++) {
          final ReflectiveType requiredType = lazyRequiredTypes.get(i + 1).get();
          checkArgumentType(actualArguments.expressions[i], requiredType, argumentList, isExact);
        }
        return true;
      }

      private void checkArgumentType(@NotNull PsiExpression argument,
                                     @Nullable ReflectiveType requiredType,
                                     @NotNull PsiExpressionList argumentList,
                                     boolean isExact) {
        if (requiredType != null) {
          final PsiType actualType = argument.getType();
          if (actualType != null) {
            if (!isCompatible(requiredType, actualType, isExact)) {
              if (PsiTreeUtil.isAncestor(argumentList, argument, false)) {
                holder.registerProblem(argument,
                                       InspectionsBundle.message(isExact
                                                                 ? "inspection.reflect.handle.invocation.argument.not.exact"
                                                                 : "inspection.reflection.invocation.argument.not.assignable",
                                                                 requiredType.getQualifiedName()));
              }
            }
            else if (requiredType.isPrimitive()) {
              final PsiExpression definition = findDefinition(argument);
              if (definition != null && PsiType.NULL.equals(definition.getType())) {
                if (PsiTreeUtil.isAncestor(argumentList, argument, false)) {
                  holder.registerProblem(argument,
                                         InspectionsBundle.message("inspection.reflect.handle.invocation.primitive.argument.null",
                                                                   requiredType.getQualifiedName()));
                }
              }
            }
          }
        }
      }

      private void checkReturnType(@NotNull PsiMethodCallExpression invokeCall,
                                   @Nullable ReflectiveType requiredType,
                                   boolean isExact) {
        if (requiredType == null) return;
        final PsiElement invokeParent = invokeCall.getParent();
        PsiType actualType = null;
        PsiElement problemElement = null;
        if (invokeParent instanceof PsiTypeCastExpression) {
          final PsiTypeElement castTypeElement = ((PsiTypeCastExpression)invokeParent).getCastType();
          if (castTypeElement != null) {
            actualType = castTypeElement.getType();
            problemElement = castTypeElement;
          }
        }
        else if (invokeParent instanceof PsiAssignmentExpression) {
          actualType = ((PsiAssignmentExpression)invokeParent).getLExpression().getType();
        }
        else if (invokeParent instanceof PsiVariable) {
          actualType = ((PsiVariable)invokeParent).getType();
        }

        if (actualType != null && !isCompatible(requiredType, actualType, isExact)) {
          if (problemElement == null) {
            problemElement = invokeCall.getMethodExpression();
          }
          holder.registerProblem(problemElement, InspectionsBundle.message(isExact || requiredType.isPrimitive()
                                                                           ? "inspection.reflect.handle.invocation.result.not.exact"
                                                                           : "inspection.reflect.handle.invocation.result.not.assignable",
                                                                           requiredType.getQualifiedName()));
        }
      }

      @Nullable
      private List<Supplier<ReflectiveType>> getMethodType(@Nullable PsiExpression methodTypeExpression) {
        final PsiExpression typeDefinition = findDefinition(methodTypeExpression);
        if (typeDefinition instanceof PsiMethodCallExpression) {
          final PsiMethodCallExpression typeDefinitionCall = (PsiMethodCallExpression)typeDefinition;

          if (isCallToMethod(typeDefinitionCall, JAVA_LANG_INVOKE_METHOD_TYPE, METHOD_TYPE)) {
            final PsiExpression[] arguments = typeDefinitionCall.getArgumentList().getExpressions();
            if (arguments.length != 0) {
              return ContainerUtil.map(arguments, argument -> (() -> getReflectiveType(argument)));
            }
          }
          else if (isCallToMethod(typeDefinitionCall, JAVA_LANG_INVOKE_METHOD_TYPE, GENERIC_METHOD_TYPE)) {
            final PsiExpression[] arguments = typeDefinitionCall.getArgumentList().getExpressions();
            final Pair.NonNull<Integer, Boolean> signature = JavaLangInvokeHandleSignatureInspection.getGenericSignature(arguments);
            if (signature != null) {
              final int objectArgCount = signature.getFirst();
              final boolean finalArray = signature.getSecond();
              if (objectArgCount == 0 && !finalArray) {
                return Collections.emptyList();
              }
              final JavaPsiFacade facade = JavaPsiFacade.getInstance(holder.getProject());
              final PsiClass objectClass = facade.findClass(CommonClassNames.JAVA_LANG_OBJECT, methodTypeExpression.getResolveScope());
              if (objectClass != null) {
                final List<ReflectiveType> argumentTypes = new ArrayList<>();
                final ReflectiveType objectType = ReflectiveType.create(objectClass);
                argumentTypes.add(objectType); // return type
                for (int i = 0; i < objectArgCount; i++) {
                  argumentTypes.add(objectType);
                }
                if (finalArray) {
                  argumentTypes.add(ReflectiveType.arrayOf(objectType));
                }
                return ContainerUtil.map(argumentTypes, type -> (() -> type));
              }
            }
          }
        }
        return null;
      }


      private boolean checkGetter(@NotNull PsiMethodCallExpression invokeCall,
                                  @NotNull PsiExpression typeExpression,
                                  boolean isExact,
                                  int argumentOffset) {
        final PsiExpressionList argumentList = invokeCall.getArgumentList();
        if (!checkArgumentCount(argumentList.getExpressions(), argumentOffset, 0, argumentList)) return false;

        final ReflectiveType resultType = getReflectiveType(typeExpression);
        if (resultType != null) {
          checkReturnType(invokeCall, resultType, isExact);
        }
        return true;
      }

      private boolean checkSetter(@NotNull PsiMethodCallExpression invokeCall,
                                  @NotNull PsiExpression typeExpression,
                                  boolean isExact,
                                  int argumentOffset) {
        final PsiExpressionList argumentList = invokeCall.getArgumentList();
        final PsiExpression[] arguments = argumentList.getExpressions();
        if (!checkArgumentCount(arguments, argumentOffset + 1, 0, argumentList)) return false;

        LOG.assertTrue(arguments.length == argumentOffset + 1);
        final ReflectiveType requiredType = getReflectiveType(typeExpression);
        checkArgumentType(arguments[argumentOffset], requiredType, argumentList, isExact);

        final PsiElement invokeParent = invokeCall.getParent();
        if (!(invokeParent instanceof PsiStatement)) {
          holder.registerProblem(invokeCall.getMethodExpression(),
                                 InspectionsBundle.message(isExact
                                                           ? "inspection.reflect.handle.invocation.result.void"
                                                           : "inspection.reflect.handle.invocation.result.null"));
        }
        return true;
      }

      private boolean checkArgumentCount(@NotNull PsiExpression[] arguments, int requiredArgumentCount, int argumentOffset,
                                         PsiElement problemElement) {
        if (arguments.length != requiredArgumentCount) {
          holder.registerProblem(problemElement, InspectionsBundle.message(
            "inspection.reflection.invocation.argument.count", requiredArgumentCount + argumentOffset));
          return false;
        }
        return true;
      }

      private boolean isCompatible(@NotNull ReflectiveType requiredType, @NotNull PsiType actualType, boolean isExact) {
        if (isExact) {
          return requiredType.isEqualTo(actualType);
        }
        return requiredType.isAssignableFrom(actualType) || actualType.isAssignableFrom(requiredType.getType());
      }


      private boolean isCompatible(@NotNull PsiClassType expectedType, @Nullable PsiType actualType) {
        return actualType != null && (expectedType.isAssignableFrom(actualType) || actualType.isAssignableFrom(expectedType));
      }

      private boolean isWithDynamicArguments(@NotNull PsiMethodCallExpression invokeCall) {
        if (INVOKE_WITH_ARGUMENTS.equals(invokeCall.getMethodExpression().getReferenceName())) {
          final PsiExpression[] arguments = invokeCall.getArgumentList().getExpressions();
          if (arguments.length == 1) {
            return JavaReflectionInvocationInspection.isVarargAsArray(arguments[0]) ||
                   InheritanceUtil.isInheritor(arguments[0].getType(), JAVA_UTIL_LIST);
          }
        }
        return false;
      }
    };
  }
}
