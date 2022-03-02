// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.miscGenerics;

import com.intellij.codeInsight.daemon.impl.analysis.JavaGenericsUtil;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.graphInference.PsiPolyExpressionUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.intellij.openapi.util.NullableLazyValue.lazyNullable;

public final class SuspiciousMethodCallUtil {

  // List.of/Set.of are unnecessary here as they don't accept nulls
  private static final CallMatcher.Simple SINGLETON_COLLECTION =
    CallMatcher.staticCall(CommonClassNames.JAVA_UTIL_COLLECTIONS, "singletonList", "singleton").parameterCount(1);

  private static void setupPatternMethods(PsiManager manager,
                                          GlobalSearchScope searchScope,
                                          List<? super PatternMethod> patternMethods) {
    final JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(manager.getProject());
    final PsiClass collectionClass = javaPsiFacade.findClass(CommonClassNames.JAVA_UTIL_COLLECTION, searchScope);
    PsiClassType object = PsiType.getJavaLangObject(manager, searchScope);
    PsiType[] javaLangObject = {object};
    PsiType[] twoObjects = {object, object};
    MethodSignature removeSignature =
      MethodSignatureUtil.createMethodSignature("remove", javaLangObject, PsiTypeParameter.EMPTY_ARRAY, PsiSubstitutor.EMPTY);
    if (collectionClass != null) {
      PsiMethod remove = MethodSignatureUtil.findMethodBySignature(collectionClass, removeSignature, false);
      addMethod(remove, 0, patternMethods, 0);

      addSingleParameterMethod(patternMethods, collectionClass, "contains", object);

      if (PsiUtil.isLanguageLevel5OrHigher(collectionClass)) {
        PsiClassType wildcardCollection = javaPsiFacade.getElementFactory().createType(collectionClass, PsiWildcardType.createUnbounded(manager));
        addSingleParameterMethod(patternMethods, collectionClass, "removeAll", wildcardCollection);
        addSingleParameterMethod(patternMethods, collectionClass, "retainAll", wildcardCollection);
        addSingleParameterMethod(patternMethods, collectionClass, "containsAll", wildcardCollection);
      }
    }

    final PsiClass listClass = javaPsiFacade.findClass(CommonClassNames.JAVA_UTIL_LIST, searchScope);
    if (listClass != null) {
      addSingleParameterMethod(patternMethods, listClass, "indexOf", object);
      addSingleParameterMethod(patternMethods, listClass, "lastIndexOf", object);
    }

    final PsiClass mapClass = javaPsiFacade.findClass(CommonClassNames.JAVA_UTIL_MAP, searchScope);
    if (mapClass != null) {
      PsiMethod remove = MethodSignatureUtil.findMethodBySignature(mapClass, removeSignature, false);
      addMethod(remove, 0, patternMethods, 0);

      addSingleParameterMethod(patternMethods, mapClass, "get", object);

      PsiTypeParameter[] typeParameters = mapClass.getTypeParameters();
      if (typeParameters.length > 0) {
        MethodSignature getOrDefaultSignature = MethodSignatureUtil.createMethodSignature("getOrDefault",
                                                                                          new PsiType[]{object,
                                                                                            PsiSubstitutor.EMPTY.substitute(typeParameters[1])}, PsiTypeParameter.EMPTY_ARRAY, PsiSubstitutor.EMPTY);
        PsiMethod getOrDefault = MethodSignatureUtil.findMethodBySignature(mapClass, getOrDefaultSignature, false);
        addMethod(getOrDefault, 0, patternMethods, 0);
      }

      MethodSignature removeWithDefaultSignature = MethodSignatureUtil.createMethodSignature("remove",
                                                                                             twoObjects,
                                                                                             PsiTypeParameter.EMPTY_ARRAY, PsiSubstitutor.EMPTY);
      PsiMethod removeWithDefault = MethodSignatureUtil.findMethodBySignature(mapClass, removeWithDefaultSignature, false);
      addMethod(removeWithDefault, 0, patternMethods, 0);
      addMethod(removeWithDefault, 1, patternMethods, 1);

      addSingleParameterMethod(patternMethods, mapClass, "containsKey", object);

      MethodSignature containsValueSignature = MethodSignatureUtil.createMethodSignature("containsValue", javaLangObject, PsiTypeParameter.EMPTY_ARRAY, PsiSubstitutor.EMPTY);
      PsiMethod containsValue = MethodSignatureUtil.findMethodBySignature(mapClass, containsValueSignature, false);
      addMethod(containsValue, 1, patternMethods, 0);
    }

    final PsiClass concurrentMapClass = javaPsiFacade.findClass(CommonClassNames.JAVA_UTIL_CONCURRENT_HASH_MAP, searchScope);
    if (concurrentMapClass != null) {
      MethodSignature containsSignature = MethodSignatureUtil.createMethodSignature("contains", javaLangObject, PsiTypeParameter.EMPTY_ARRAY, PsiSubstitutor.EMPTY);
      PsiMethod contains = MethodSignatureUtil.findMethodBySignature(concurrentMapClass, containsSignature, false);
      addMethod(contains, 1, patternMethods, 0);
    }
    PsiClass guavaTable = javaPsiFacade.findClass("com.google.common.collect.Table", searchScope);
    if (guavaTable != null) {
      MethodSignature getSignature =
        MethodSignatureUtil.createMethodSignature("get", twoObjects, PsiTypeParameter.EMPTY_ARRAY, PsiSubstitutor.EMPTY);
      PsiMethod get = MethodSignatureUtil.findMethodBySignature(guavaTable, getSignature, false);
      addMethod(get, 0, patternMethods, 0);
      addMethod(get, 1, patternMethods, 1);

      MethodSignature containsSignature =
        MethodSignatureUtil.createMethodSignature("contains", twoObjects, PsiTypeParameter.EMPTY_ARRAY, PsiSubstitutor.EMPTY);
      PsiMethod contains = MethodSignatureUtil.findMethodBySignature(guavaTable, containsSignature, false);
      addMethod(contains, 0, patternMethods, 0);
      addMethod(contains, 1, patternMethods, 1);

      MethodSignature containsRowSignature =
        MethodSignatureUtil.createMethodSignature("containsRow", javaLangObject, PsiTypeParameter.EMPTY_ARRAY, PsiSubstitutor.EMPTY);
      PsiMethod containsRow = MethodSignatureUtil.findMethodBySignature(guavaTable, containsRowSignature, false);
      addMethod(containsRow, 0, patternMethods, 0);

      MethodSignature containsColumnSignature =
        MethodSignatureUtil.createMethodSignature("containsColumn", javaLangObject, PsiTypeParameter.EMPTY_ARRAY, PsiSubstitutor.EMPTY);
      PsiMethod containsColumn = MethodSignatureUtil.findMethodBySignature(guavaTable, containsColumnSignature, false);
      addMethod(containsColumn, 1, patternMethods, 0);

      MethodSignature containsValueSignature =
        MethodSignatureUtil.createMethodSignature("containsValue", javaLangObject, PsiTypeParameter.EMPTY_ARRAY, PsiSubstitutor.EMPTY);
      PsiMethod containsValue = MethodSignatureUtil.findMethodBySignature(guavaTable, containsValueSignature, false);
      addMethod(containsValue, 2, patternMethods, 0);

      MethodSignature removeByRowAndColumnSignature =
        MethodSignatureUtil.createMethodSignature("remove", twoObjects, PsiTypeParameter.EMPTY_ARRAY, PsiSubstitutor.EMPTY);
      PsiMethod removeByRowAndColumn = MethodSignatureUtil.findMethodBySignature(guavaTable, removeByRowAndColumnSignature, false);
      addMethod(removeByRowAndColumn, 0, patternMethods, 0);
      addMethod(removeByRowAndColumn, 1, patternMethods, 1);
    }

    PsiClass guavaMultimap = javaPsiFacade.findClass("com.google.common.collect.Multimap", searchScope);
    if (guavaMultimap != null) {
      MethodSignature containsKeySignature =
        MethodSignatureUtil.createMethodSignature("containsKey", javaLangObject, PsiTypeParameter.EMPTY_ARRAY, PsiSubstitutor.EMPTY);
      PsiMethod containsKey = MethodSignatureUtil.findMethodBySignature(guavaMultimap, containsKeySignature, false);
      addMethod(containsKey, 0, patternMethods, 0);

      MethodSignature containsValueSignature =
        MethodSignatureUtil.createMethodSignature("containsValue", javaLangObject, PsiTypeParameter.EMPTY_ARRAY, PsiSubstitutor.EMPTY);
      PsiMethod containsValue = MethodSignatureUtil.findMethodBySignature(guavaMultimap, containsValueSignature, false);
      addMethod(containsValue, 1, patternMethods, 0);

      MethodSignature containsEntrySignature =
        MethodSignatureUtil.createMethodSignature("containsEntry", twoObjects, PsiTypeParameter.EMPTY_ARRAY, PsiSubstitutor.EMPTY);
      PsiMethod containsEntry = MethodSignatureUtil.findMethodBySignature(guavaMultimap, containsEntrySignature, false);
      addMethod(containsEntry, 0, patternMethods, 0);
      addMethod(containsEntry, 1, patternMethods, 1);

      MethodSignature removeByKeyAndValueSignature =
        MethodSignatureUtil.createMethodSignature("remove", twoObjects, PsiTypeParameter.EMPTY_ARRAY, PsiSubstitutor.EMPTY);
      PsiMethod removeByKeyAndValue = MethodSignatureUtil.findMethodBySignature(guavaMultimap, removeByKeyAndValueSignature, false);
      addMethod(removeByKeyAndValue, 0, patternMethods, 0);
      addMethod(removeByKeyAndValue, 1, patternMethods, 1);

      MethodSignature removeAllSignature =
        MethodSignatureUtil.createMethodSignature("removeAll", javaLangObject, PsiTypeParameter.EMPTY_ARRAY, PsiSubstitutor.EMPTY);
      PsiMethod removeAll = MethodSignatureUtil.findMethodBySignature(guavaMultimap, removeAllSignature, false);
      addMethod(removeAll, 0, patternMethods, 0);
    }

    PsiClass guavaMultiset = javaPsiFacade.findClass("com.google.common.collect.Multiset", searchScope);
    if (guavaMultiset != null) {
      MethodSignature countSignature =
        MethodSignatureUtil.createMethodSignature("count", javaLangObject, PsiTypeParameter.EMPTY_ARRAY, PsiSubstitutor.EMPTY);
      PsiMethod count = MethodSignatureUtil.findMethodBySignature(guavaMultiset, countSignature, false);
      addMethod(count, 0, patternMethods, 0);
    }

    PsiClass guavaCache = javaPsiFacade.findClass("com.google.common.cache.Cache", searchScope);
    if (guavaCache != null) {
      MethodSignature getIfPresentSignature =
        MethodSignatureUtil.createMethodSignature("getIfPresent", javaLangObject, PsiTypeParameter.EMPTY_ARRAY, PsiSubstitutor.EMPTY);
      PsiMethod getIfPresent = MethodSignatureUtil.findMethodBySignature(guavaCache, getIfPresentSignature, false);
      addMethod(getIfPresent, 0, patternMethods, 0);
      MethodSignature invalidateSignature =
        MethodSignatureUtil.createMethodSignature("invalidate", javaLangObject, PsiTypeParameter.EMPTY_ARRAY, PsiSubstitutor.EMPTY);
      PsiMethod invalidate = MethodSignatureUtil.findMethodBySignature(guavaCache, invalidateSignature, false);
      addMethod(invalidate, 0, patternMethods, 0);
    }
  }

  private static void addSingleParameterMethod(List<? super PatternMethod> patternMethods,
                                               PsiClass methodClass, String methodName, PsiClassType parameterType) {
    MethodSignature signature = MethodSignatureUtil
      .createMethodSignature(methodName, new PsiType[]{parameterType}, PsiTypeParameter.EMPTY_ARRAY, PsiSubstitutor.EMPTY);
    PsiMethod method = MethodSignatureUtil.findMethodBySignature(methodClass, signature, false);
    addMethod(method, 0, patternMethods, 0);
  }

  private static void addMethod(final PsiMethod patternMethod,
                                int typeParamIndex,
                                List<? super PatternMethod> patternMethods,
                                int argIdx) {
    if (patternMethod != null) {
      patternMethods.add(new PatternMethod(patternMethod, typeParamIndex, argIdx));
    }
  }

  private static boolean isInheritorOrSelf(PsiMethod inheritorCandidate, PsiMethod base) {
    PsiClass aClass = inheritorCandidate.getContainingClass();
    PsiClass bClass = base.getContainingClass();
    if (aClass == null || bClass == null) return false;
    PsiSubstitutor substitutor = TypeConversionUtil.getClassSubstitutor(bClass, aClass, PsiSubstitutor.EMPTY);
    return substitutor != null &&
           MethodSignatureUtil.findMethodBySignature(bClass, inheritorCandidate.getSignature(substitutor), false) == base;
  }

  @Nullable
  public static @InspectionMessage String getSuspiciousMethodCallMessage(@NotNull PsiMethodCallExpression methodCall,
                                                                         PsiExpression arg,
                                                                         PsiType argType,
                                                                         boolean reportConvertibleMethodCalls,
                                                                         @NotNull List<PatternMethod> patternMethods,
                                                                         int idx) {
    final PsiReferenceExpression methodExpression = methodCall.getMethodExpression();

    if (arg instanceof PsiConditionalExpression &&
        argType != null &&
        argType.equalsToText(CommonClassNames.JAVA_LANG_OBJECT) &&
        PsiPolyExpressionUtil.isPolyExpression(arg)) {
      return null;
    }
    return getSuspiciousMethodCallMessage(methodExpression, argType, reportConvertibleMethodCalls, patternMethods, idx);
  }

  @Nullable
  static @InspectionMessage String getSuspiciousMethodCallMessage(PsiReferenceExpression methodExpression,
                                                                  PsiType argType,
                                                                  boolean reportConvertibleMethodCalls,
                                                                  @NotNull List<PatternMethod> patternMethods,
                                                                  int argIdx) {
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
    NullableLazyValue<PsiMethod> lazyContextMethod = lazyNullable(() -> PsiTreeUtil.getParentOfType(methodExpression, PsiMethod.class));

    //noinspection SynchronizationOnLocalVariableOrMethodParameter
    synchronized (patternMethods) {
      if (patternMethods.isEmpty()) {
        setupPatternMethods(methodExpression.getManager(), methodExpression.getResolveScope(), patternMethods);
      }
    }

    for (PatternMethod patternMethod: patternMethods) {
      PsiMethod method = patternMethod.patternMethod;
      if (!method.getName().equals(methodExpression.getReferenceName())) continue;
      if (patternMethod.argIdx != argIdx) continue;

      //we are in collections method implementation
      PsiMethod contextMethod = lazyContextMethod.getValue();
      if (contextMethod != null && isInheritorOrSelf(contextMethod, method)) return null;

      final PsiClass calleeClass = calleeMethod.getContainingClass();
      PsiSubstitutor substitutor = resolveResult.getSubstitutor();
      final PsiClass patternClass = method.getContainingClass();
      assert patternClass != null;
      assert calleeClass != null;
      substitutor = TypeConversionUtil.getClassSubstitutor(patternClass, calleeClass, substitutor);
      if (substitutor == null) continue;

      if (!method.getSignature(substitutor).equals(calleeMethod.getSignature(resolveResult.getSubstitutor()))) continue;

      PsiTypeParameter[] typeParameters = patternClass.getTypeParameters();
      if (typeParameters.length <= patternMethod.typeParameterIdx) return null;
      final PsiTypeParameter typeParameter = typeParameters[patternMethod.typeParameterIdx];
      PsiType typeParamMapping = substitutor.substitute(typeParameter);
      if (typeParamMapping == null) return null;

      PsiParameter[] parameters = method.getParameterList().getParameters();
      if (parameters.length == 1 && isCollectionAcceptingMethod(method.getName())) {
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
              return JavaAnalysisBundle.message("inspection.suspicious.collections.method.calls.problem.descriptor",
                                               PsiFormatUtil.formatType(qualifierType, 0, PsiSubstitutor.EMPTY),
                                               PsiFormatUtil.formatType(itemType, 0, PsiSubstitutor.EMPTY),
                                               "objects");
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
            message = JavaAnalysisBundle.message("inspection.suspicious.collections.method.calls.problem.descriptor1",
                                                PsiFormatUtil.formatMethod(calleeMethod, substitutor,
                                                                           PsiFormatUtilBase.SHOW_NAME |
                                                                           PsiFormatUtilBase.SHOW_CONTAINING_CLASS,
                                                                           PsiFormatUtilBase.SHOW_TYPE));
          }
        }
        else {
          PsiType qualifierType = qualifier.getType();
          if (qualifierType != null) {
            message = JavaAnalysisBundle.message("inspection.suspicious.collections.method.calls.problem.descriptor",
                                                PsiFormatUtil.formatType(qualifierType, 0, PsiSubstitutor.EMPTY),
                                                PsiFormatUtil.formatType(argType, 0, PsiSubstitutor.EMPTY),
                                                getPreciseObjectTitle(patternClass, patternMethod.typeParameterIdx));
          }
        }
      }
      return message;
    }
    return null;
  }

  @Contract(value = "null -> false", pure = true)
  static boolean isCollectionAcceptingMethod(@Nullable String name) {
    return "removeAll".equals(name) || "retainAll".equals(name) || "containsAll".equals(name);
  }

  private static @Nls String getPreciseObjectTitle(PsiClass patternClass, int index) {
    if (InheritanceUtil.isInheritor(patternClass, CommonClassNames.JAVA_UTIL_MAP)) {
      return index == 0 ? JavaAnalysisBundle.message("element.kind.keys") : JavaAnalysisBundle.message("element.kind.values");
    }

    return JavaAnalysisBundle.message("element.kind.objects");
  }

  private static boolean hasNullCollectionArg(PsiReferenceExpression methodExpression) {
    PsiMethodCallExpression call = ObjectUtils.tryCast(methodExpression.getParent(), PsiMethodCallExpression.class);
    if (call != null) {
      PsiExpression arg =
        ExpressionUtils.resolveExpression(ArrayUtil.getFirstElement(call.getArgumentList().getExpressions()));
      PsiMethodCallExpression argCall =
        ObjectUtils.tryCast(PsiUtil.skipParenthesizedExprDown(arg), PsiMethodCallExpression.class);
      return SINGLETON_COLLECTION.test(argCall) && ExpressionUtils.isNullLiteral(argCall.getArgumentList().getExpressions()[0]);
    }
    return false;
  }

  static class PatternMethod {
    PsiMethod patternMethod;
    int typeParameterIdx;
    int argIdx;

    PatternMethod(PsiMethod patternMethod, int typeParameterIdx, int argIdx) {
      this.patternMethod = patternMethod;
      this.typeParameterIdx = typeParameterIdx;
      this.argIdx = argIdx;
    }
  }
}
