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
package com.intellij.codeInsight

import com.intellij.psi.*
import com.intellij.psi.search.searches.DeepestSuperMethodsSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtil
import com.intellij.util.containers.MultiMap

private data class MethodArgument(val methodName: String, val paramIndex: Int)

private interface DefaultTypeProvider {
  fun getDefaultType(method: PsiMethod, substitutor: PsiSubstitutor, argument: PsiExpression): PsiType?
}

private val providers = createProviders()

private fun createProviders(): MultiMap<MethodArgument, DefaultTypeProvider> {
  val providers = listOf(
    MethodArgument("log",              0) to object : DefaultTypeProvider {
      override fun getDefaultType(method: PsiMethod, substitutor: PsiSubstitutor, argument: PsiExpression): PsiType? {
        if (isDefinedInClass(method, "org.apache.log4j.Category")) {
          return JavaPsiFacade.getElementFactory(method.project).createTypeFromText("org.apache.log4j.Level", argument)
        }
        return null
      }
    },

    MethodArgument("contains",              0) to takeClassTypeArgument(CommonClassNames.JAVA_UTIL_COLLECTION, 0),
    MethodArgument("remove",                0) to takeClassTypeArgument(CommonClassNames.JAVA_UTIL_COLLECTION, 0),
    MethodArgument("indexOf",               0) to takeClassTypeArgument(CommonClassNames.JAVA_UTIL_LIST, 0),
    MethodArgument("lastIndexOf",           0) to takeClassTypeArgument(CommonClassNames.JAVA_UTIL_LIST, 0),
    MethodArgument("containsKey",           0) to takeClassTypeArgument(CommonClassNames.JAVA_UTIL_MAP, 0),
    MethodArgument("remove",                0) to takeClassTypeArgument(CommonClassNames.JAVA_UTIL_MAP, 0),
    MethodArgument("remove",                1) to takeClassTypeArgument(CommonClassNames.JAVA_UTIL_MAP, 1),
    MethodArgument("get",                   0) to takeClassTypeArgument(CommonClassNames.JAVA_UTIL_MAP, 0),
    MethodArgument("getOrDefault",          0) to takeClassTypeArgument(CommonClassNames.JAVA_UTIL_MAP, 0),
    MethodArgument("containsValue",         0) to takeClassTypeArgument(CommonClassNames.JAVA_UTIL_MAP, 1),
    MethodArgument("removeFirstOccurrence", 0) to takeClassTypeArgument("java.util.Deque", 0),
    MethodArgument("removeLastOccurrence",  0) to takeClassTypeArgument("java.util.Deque", 0),
    MethodArgument("equals",                0) to object: DefaultTypeProvider {
      override fun getDefaultType(method: PsiMethod, substitutor: PsiSubstitutor, argument: PsiExpression): PsiType? {
        if (isDefinedInClass(method, CommonClassNames.JAVA_LANG_OBJECT)) {
          val parent = argument.parent.parent
          if (parent is PsiMethodCallExpression) {
            val qualifierExpression = parent.methodExpression.qualifierExpression
            if (qualifierExpression != null) {
              return qualifierExpression.type
            }
            return PsiTreeUtil.getContextOfType(parent, PsiClass::class.java, true)?.let { JavaPsiFacade.getElementFactory(it.project).createType(it) }
          }
        }
        return null
      }
    }
  )
  val result = MultiMap.create<MethodArgument, DefaultTypeProvider>()
  for ((key, value) in providers) {
    result.putValue(key, value)
  }
  return result
}

private fun takeClassTypeArgument(className: String, typeParamIndex: Int) = object : DefaultTypeProvider {
  override fun getDefaultType(method: PsiMethod, substitutor: PsiSubstitutor, argument: PsiExpression): PsiType? {
    if (!isDefinedInClass(method, className)) return null
    val containingClass = method.containingClass ?: return null
    return PsiUtil.substituteTypeParameter(JavaPsiFacade.getElementFactory(method.project).createType(containingClass, substitutor),
                                           className, typeParamIndex, false)
  }
}

private fun isDefinedInClass(method: PsiMethod, className: String): Boolean =
  method.containingClass?.qualifiedName == className ||
  DeepestSuperMethodsSearch.search(method).findAll().any { it.containingClass?.qualifiedName == className }

public fun getDefaultType(method: PsiMethod, substitutor: PsiSubstitutor, argIndex: Int, argument: PsiExpression): PsiType? {
  for (provider in providers[MethodArgument(method.name, argIndex)]) {
    return provider.getDefaultType(method, substitutor, argument) ?: continue
  }
  return null
}