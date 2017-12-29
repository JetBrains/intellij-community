/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.codeInspection.miscGenerics;

import com.intellij.codeInsight.daemon.impl.analysis.JavaGenericsUtil;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.graphInference.PsiPolyExpressionUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.IntArrayList;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class SuspiciousMethodCallUtil {

  // List.of/Set.of are unnecessary here as they don't accept nulls
  private static final CallMatcher.Simple SINGLETON_COLLECTION =
    CallMatcher.staticCall(CommonClassNames.JAVA_UTIL_COLLECTIONS, "singletonList", "singleton").parameterCount(1);

  static void setupPatternMethods(PsiManager manager,
                                  GlobalSearchScope searchScope,
                                  List<PsiMethod> patternMethods,
                                  IntArrayList indices) {
    final JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(manager.getProject());
    final PsiClass
      collectionClass = javaPsiFacade.findClass(CommonClassNames.JAVA_UTIL_COLLECTION, searchScope);
    PsiType[] javaLangObject = {PsiType.getJavaLangObject(manager, searchScope)};
    MethodSignature removeSignature = MethodSignatureUtil
      .createMethodSignature("remove", javaLangObject, PsiTypeParameter.EMPTY_ARRAY, PsiSubstitutor.EMPTY);
    if (collectionClass != null) {
      PsiMethod remove = MethodSignatureUtil.findMethodBySignature(collectionClass, removeSignature, false);
      addMethod(remove, 0, patternMethods, indices);

      MethodSignature containsSignature = MethodSignatureUtil.createMethodSignature("contains", javaLangObject, PsiTypeParameter.EMPTY_ARRAY, PsiSubstitutor.EMPTY);
      PsiMethod contains = MethodSignatureUtil.findMethodBySignature(collectionClass, containsSignature, false);
      addMethod(contains, 0, patternMethods, indices);

      if (PsiUtil.isLanguageLevel5OrHigher(collectionClass)) {
        PsiClassType wildcardCollection = javaPsiFacade.getElementFactory().createType(collectionClass, PsiWildcardType.createUnbounded(manager));
        MethodSignature removeAllSignature = MethodSignatureUtil.createMethodSignature("removeAll", new PsiType[] {wildcardCollection}, PsiTypeParameter.EMPTY_ARRAY, PsiSubstitutor.EMPTY);
        PsiMethod removeAll = MethodSignatureUtil.findMethodBySignature(collectionClass, removeAllSignature, false);
        addMethod(removeAll, 0, patternMethods, indices);
      }
    }

    final PsiClass listClass = javaPsiFacade.findClass(CommonClassNames.JAVA_UTIL_LIST, searchScope);
    if (listClass != null) {
      MethodSignature indexofSignature = MethodSignatureUtil.createMethodSignature("indexOf", javaLangObject, PsiTypeParameter.EMPTY_ARRAY, PsiSubstitutor.EMPTY);
      PsiMethod indexof = MethodSignatureUtil.findMethodBySignature(listClass, indexofSignature, false);
      addMethod(indexof, 0, patternMethods, indices);
      MethodSignature lastindexofSignature = MethodSignatureUtil.createMethodSignature("lastIndexOf", javaLangObject, PsiTypeParameter.EMPTY_ARRAY, PsiSubstitutor.EMPTY);
      PsiMethod lastindexof = MethodSignatureUtil.findMethodBySignature(listClass, lastindexofSignature, false);
      addMethod(lastindexof, 0, patternMethods, indices);
    }

    final PsiClass mapClass = javaPsiFacade.findClass(CommonClassNames.JAVA_UTIL_MAP, searchScope);
    if (mapClass != null) {
      PsiMethod remove = MethodSignatureUtil.findMethodBySignature(mapClass, removeSignature, false);
      addMethod(remove, 0, patternMethods, indices);

      MethodSignature getSignature = MethodSignatureUtil.createMethodSignature("get", javaLangObject, PsiTypeParameter.EMPTY_ARRAY, PsiSubstitutor.EMPTY);
      PsiMethod get = MethodSignatureUtil.findMethodBySignature(mapClass, getSignature, false);
      addMethod(get, 0, patternMethods, indices);

      PsiTypeParameter[] typeParameters = mapClass.getTypeParameters();
      if (typeParameters.length > 0) {
        MethodSignature getOrDefaultSignature = MethodSignatureUtil.createMethodSignature("getOrDefault",
                                                                                          new PsiType[] {PsiType.getJavaLangObject(manager, searchScope),
                                                                                            PsiSubstitutor.EMPTY.substitute(typeParameters[1])}, PsiTypeParameter.EMPTY_ARRAY, PsiSubstitutor.EMPTY);
        PsiMethod getOrDefault = MethodSignatureUtil.findMethodBySignature(mapClass, getOrDefaultSignature, false);
        addMethod(getOrDefault, 0, patternMethods, indices);
      }

      MethodSignature removeWithDefaultSignature = MethodSignatureUtil.createMethodSignature("remove",
                                                                                             new PsiType[] {PsiType.getJavaLangObject(manager, searchScope), PsiType.getJavaLangObject(manager, searchScope)},
                                                                                             PsiTypeParameter.EMPTY_ARRAY, PsiSubstitutor.EMPTY);
      PsiMethod removeWithDefault = MethodSignatureUtil.findMethodBySignature(mapClass, removeWithDefaultSignature, false);
      addMethod(removeWithDefault, 0, patternMethods, indices);

      MethodSignature containsKeySignature = MethodSignatureUtil.createMethodSignature("containsKey", javaLangObject, PsiTypeParameter.EMPTY_ARRAY, PsiSubstitutor.EMPTY);
      PsiMethod containsKey = MethodSignatureUtil.findMethodBySignature(mapClass, containsKeySignature, false);
      addMethod(containsKey, 0, patternMethods, indices);

      MethodSignature containsValueSignature = MethodSignatureUtil.createMethodSignature("containsValue", javaLangObject, PsiTypeParameter.EMPTY_ARRAY, PsiSubstitutor.EMPTY);
      PsiMethod containsValue = MethodSignatureUtil.findMethodBySignature(mapClass, containsValueSignature, false);
      addMethod(containsValue, 1, patternMethods, indices);
    }

    final PsiClass concurrentMapClass = javaPsiFacade.findClass(CommonClassNames.JAVA_UTIL_CONCURRENT_HASH_MAP, searchScope);
    if (concurrentMapClass != null) {
      MethodSignature containsSignature = MethodSignatureUtil.createMethodSignature("contains", javaLangObject, PsiTypeParameter.EMPTY_ARRAY, PsiSubstitutor.EMPTY);
      PsiMethod contains = MethodSignatureUtil.findMethodBySignature(concurrentMapClass, containsSignature, false);
      addMethod(contains, 1, patternMethods, indices);
    }
  }

  private static void addMethod(final PsiMethod patternMethod, int typeParamIndex, List<PsiMethod> patternMethods, IntArrayList indices) {
    if (patternMethod != null) {
      patternMethods.add(patternMethod);
      indices.add(typeParamIndex);
    }
  }

  static boolean isInheritorOrSelf(PsiMethod inheritorCandidate, PsiMethod base) {
    PsiClass aClass = inheritorCandidate.getContainingClass();
    PsiClass bClass = base.getContainingClass();
    if (aClass == null || bClass == null) return false;
    PsiSubstitutor substitutor = TypeConversionUtil.getClassSubstitutor(bClass, aClass, PsiSubstitutor.EMPTY);
    return substitutor != null &&
           MethodSignatureUtil.findMethodBySignature(bClass, inheritorCandidate.getSignature(substitutor), false) == base;
  }

  @Nullable
  public static String getSuspiciousMethodCallMessage(@NotNull PsiMethodCallExpression methodCall,
                                                      PsiExpression arg,
                                                      PsiType argType,
                                                      boolean reportConvertibleMethodCalls,
                                                      @NotNull List<PsiMethod> patternMethods,
                                                      @NotNull IntArrayList indices) {
    final PsiReferenceExpression methodExpression = methodCall.getMethodExpression();

    if (arg instanceof PsiConditionalExpression &&
        argType != null &&
        argType.equalsToText(CommonClassNames.JAVA_LANG_OBJECT) &&
        PsiPolyExpressionUtil.isPolyExpression(arg)) {
      return null;
    }
    return getSuspiciousMethodCallMessage(methodExpression, argType, reportConvertibleMethodCalls, patternMethods, indices);
  }

  @Nullable
  public static String getSuspiciousMethodCallMessage(PsiReferenceExpression methodExpression,
                                                      PsiType argType,
                                                      boolean reportConvertibleMethodCalls,
                                                      @NotNull List<PsiMethod> patternMethods,
                                                      @NotNull IntArrayList indices) {
    final PsiExpression qualifier = methodExpression.getQualifierExpression();
    if (qualifier == null || qualifier instanceof PsiThisExpression || qualifier instanceof PsiSuperExpression) return null;
    if (argType instanceof PsiPrimitiveType) {
      argType = ((PsiPrimitiveType)argType).getBoxedType(methodExpression);
    }

    if (argType == null) return null;

    final JavaResolveResult resolveResult = methodExpression.advancedResolve(false);
    PsiElement element = resolveResult.getElement();
    if (!(element instanceof PsiMethod)) return null;
    PsiMethod calleeMethod = (PsiMethod)element;
    PsiMethod contextMethod = PsiTreeUtil.getParentOfType(methodExpression, PsiMethod.class);

    //noinspection SynchronizationOnLocalVariableOrMethodParameter
    synchronized (patternMethods) {
      if (patternMethods.isEmpty()) {
        setupPatternMethods(methodExpression.getManager(), methodExpression.getResolveScope(), patternMethods, indices);
      }
    }

    for (int i = 0; i < patternMethods.size(); i++) {
      PsiMethod patternMethod = patternMethods.get(i);
      if (!patternMethod.getName().equals(methodExpression.getReferenceName())) continue;
      int index = indices.get(i);

      //we are in collections method implementation
      if (contextMethod != null && isInheritorOrSelf(contextMethod, patternMethod)) return null;

      final PsiClass calleeClass = calleeMethod.getContainingClass();
      PsiSubstitutor substitutor = resolveResult.getSubstitutor();
      final PsiClass patternClass = patternMethod.getContainingClass();
      assert patternClass != null;
      assert calleeClass != null;
      substitutor = TypeConversionUtil.getClassSubstitutor(patternClass, calleeClass, substitutor);
      if (substitutor == null) continue;

      if (!patternMethod.getSignature(substitutor).equals(calleeMethod.getSignature(PsiSubstitutor.EMPTY))) continue;

      PsiTypeParameter[] typeParameters = patternClass.getTypeParameters();
      if (typeParameters.length <= index) return null;
      final PsiTypeParameter typeParameter = typeParameters[index];
      PsiType typeParamMapping = substitutor.substitute(typeParameter);
      if (typeParamMapping == null) return null;

      PsiParameter[] parameters = patternMethod.getParameterList().getParameters();
      if (parameters.length == 1 && "removeAll".equals(patternMethod.getName())) {
        PsiType paramType = parameters[0].getType();
        if (InheritanceUtil.isInheritor(paramType, CommonClassNames.JAVA_UTIL_COLLECTION)) {
          PsiType qualifierType = qualifier.getType();
          if (qualifierType != null) {
            final PsiType itemType = JavaGenericsUtil.getCollectionItemType(argType, calleeMethod.getResolveScope());
            final PsiType qualifierItemType = JavaGenericsUtil.getCollectionItemType(qualifierType, calleeMethod.getResolveScope());
            if (qualifierItemType != null && itemType != null && !qualifierItemType.isAssignableFrom(itemType)) {
              if (TypeUtils.isJavaLangObject(itemType) && hasNullCollectionArg(methodExpression)) {
                // removeAll(Collections.singleton(null)) is a valid way to remove all nulls from collection
                return null;
              }
              if (qualifierItemType.isConvertibleFrom(itemType) && !reportConvertibleMethodCalls) {
                return null;
              }
              return InspectionsBundle.message("inspection.suspicious.collections.method.calls.problem.descriptor",
                                               PsiFormatUtil.formatType(qualifierType, 0, PsiSubstitutor.EMPTY),
                                               PsiFormatUtil.formatType(itemType, 0, PsiSubstitutor.EMPTY));
            }
          }
          return null;
        }
      }

      String message = null;
      if (typeParamMapping instanceof PsiCapturedWildcardType) {
        typeParamMapping = ((PsiCapturedWildcardType)typeParamMapping).getWildcard();
      }
      if (!typeParamMapping.isAssignableFrom(argType)) {
        if (typeParamMapping.isConvertibleFrom(argType)) {
          if (reportConvertibleMethodCalls) {
            message = InspectionsBundle.message("inspection.suspicious.collections.method.calls.problem.descriptor1",
                                                PsiFormatUtil.formatMethod(calleeMethod, substitutor,
                                                                           PsiFormatUtilBase.SHOW_NAME |
                                                                           PsiFormatUtilBase.SHOW_CONTAINING_CLASS,
                                                                           PsiFormatUtilBase.SHOW_TYPE));
          }
        }
        else {
          PsiType qualifierType = qualifier.getType();
          if (qualifierType != null) {
            message = InspectionsBundle.message("inspection.suspicious.collections.method.calls.problem.descriptor",
                                                PsiFormatUtil.formatType(qualifierType, 0, PsiSubstitutor.EMPTY),
                                                PsiFormatUtil.formatType(argType, 0, PsiSubstitutor.EMPTY));
          }
        }
      }
      return message;
    }
    return null;
  }

  private static boolean hasNullCollectionArg(PsiReferenceExpression methodExpression) {
    PsiMethodCallExpression call = ObjectUtils.tryCast(methodExpression.getParent(), PsiMethodCallExpression.class);
    if (call != null) {
      PsiExpression arg =
        ExpressionUtils.resolveExpression(ArrayUtil.getFirstElement(call.getArgumentList().getExpressions()));
      PsiMethodCallExpression argCall =
        ObjectUtils.tryCast(PsiUtil.skipParenthesizedExprDown(arg), PsiMethodCallExpression.class);
      if (SINGLETON_COLLECTION.test(argCall) && ExpressionUtils.isNullLiteral(argCall.getArgumentList().getExpressions()[0])) {
        return true;
      }
    }
    return false;
  }
}
