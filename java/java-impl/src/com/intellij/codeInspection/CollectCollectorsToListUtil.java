/*
 * Copyright 2003-2021 Dave Griffith, Bas Leijdekkers
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
package com.intellij.codeInspection;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.bugs.MismatchedCollectionQueryUpdateInspection;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.ui.ExternalizableStringSet;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NonNls;

import java.util.Arrays;
import java.util.Set;

public class CollectCollectorsToListUtil {

  private static final CallMatcher COLLECTION_SAFE_ARGUMENT_METHODS =
    CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_COLLECTION, "addAll", "removeAll", "containsAll", "remove");
  private static final @NonNls Set<String> COLLECTIONS_QUERIES =
    ContainerUtil.set("binarySearch", "disjoint", "indexOfSubList", "lastIndexOfSubList", "max", "min");
  private static final @NonNls Set<String> COLLECTIONS_UPDATES = ContainerUtil.set("addAll", "fill", "copy", "replaceAll", "sort");
  private static final Set<String> COLLECTIONS_ALL = StreamEx.of(COLLECTIONS_QUERIES).append(COLLECTIONS_UPDATES).toImmutableSet();
  private static final ExternalizableStringSet queryNames =
    new ExternalizableStringSet(
      "contains", "copyInto", "equals", "forEach", "get", "hashCode", "iterator", "parallelStream", "peek", "propertyNames",
      "save", "size", "store", "stream", "toArray", "toString", "write");
  private static final ExternalizableStringSet updateNames =
    new ExternalizableStringSet("add", "clear", "insert", "load", "merge", "offer", "poll", "pop", "push", "put", "remove", "replace",
                                "retain", "set", "take");

  public static boolean isUnmodified(PsiMethodCallExpression methodCall) {
    final PsiExpression effectiveReference =  MismatchedCollectionQueryUpdateInspection.findEffectiveReference(methodCall);
    if (!process(effectiveReference)) return true;
    final PsiElement parent = effectiveReference.getParent();
    if (parent instanceof PsiLocalVariable || parent instanceof PsiField) {
      final PsiElement context =
        parent instanceof PsiLocalVariable ? PsiTreeUtil.getParentOfType(parent, PsiCodeBlock.class) : PsiUtil.getTopLevelClass(parent);
      if (context == null) return false;
      MismatchedCollectionQueryUpdateInspection.QueryUpdateInfo info =
        MismatchedCollectionQueryUpdateInspection.getCollectionQueryUpdateInfo((PsiVariable)parent, context, queryNames, updateNames);
      return !info.updated;
    }
    return false;
  }

  private static boolean process(PsiExpression reference) {
    PsiMethodCallExpression qualifiedCall = ExpressionUtils.getCallForQualifier(reference);
    if (qualifiedCall != null) {
      return isUpdateMethodName(qualifiedCall.getMethodExpression().getReferenceName());
    }
    PsiElement parent = reference.getParent();
    if (parent instanceof PsiExpressionList) {
      PsiExpressionList args = (PsiExpressionList)parent;
      PsiCallExpression surroundingCall = ObjectUtils.tryCast(args.getParent(), PsiCallExpression.class);
      if (surroundingCall != null) {
        if (surroundingCall instanceof PsiMethodCallExpression) {
          PsiMethodCallExpression call = (PsiMethodCallExpression)surroundingCall;
          PsiExpressionList expressionList = call.getArgumentList();
          String name = call.getMethodExpression().getReferenceName();
          if (COLLECTIONS_ALL.contains(name) && MismatchedCollectionQueryUpdateInspection.isCollectionsClassMethod(call)) {
            if (COLLECTIONS_QUERIES.contains(name) && !(call.getParent() instanceof PsiExpressionStatement)) {
              return false;
            }
            if (COLLECTIONS_UPDATES.contains(name)) {
              return ArrayUtil.indexOf(expressionList.getExpressions(), reference) == 0;
            }
          }
        }
        return !MismatchedCollectionQueryUpdateInspection.isQueryMethod(surroundingCall) &&
               !COLLECTION_SAFE_ARGUMENT_METHODS.matches(surroundingCall);
      }
    }
    if (parent instanceof PsiMethodReferenceExpression) {
      final String methodName = ((PsiMethodReferenceExpression)parent).getReferenceName();
      return (isUpdateMethodName(methodName));
    }
    if (parent instanceof PsiForeachStatement && ((PsiForeachStatement)parent).getIteratedValue() == reference) {
      return false;
    }
    if (parent instanceof PsiPolyadicExpression) {
      IElementType tokenType = ((PsiPolyadicExpression)parent).getOperationTokenType();
      if (Arrays.asList(JavaTokenType.PLUS, JavaTokenType.EQEQ, JavaTokenType.NE).contains(tokenType)) {
        return false;
      }
    }
    if (parent instanceof PsiAssertStatement && ((PsiAssertStatement)parent).getAssertDescription() == reference) return false;
    if (parent instanceof PsiInstanceOfExpression || parent instanceof PsiSynchronizedStatement) return false;
    return true;
  }

  private static boolean isUpdateMethodName(String methodName) {
    return MismatchedCollectionQueryUpdateInspection.isQueryUpdateMethodName(methodName, updateNames);
  }
}
