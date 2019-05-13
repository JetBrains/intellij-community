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
package com.intellij.codeInsight.hints.filtering

import com.intellij.openapi.util.Couple


interface ParamMatcher {
  fun isMatching(paramNames: List<String>): Boolean
}

interface MethodMatcher {
  fun isMatching(fullyQualifiedMethodName: String, paramNames: List<String>): Boolean
}

object AnyParamMatcher: ParamMatcher {
  override fun isMatching(paramNames: List<String>): Boolean = true
}

class StringParamMatcher(private val paramMatchers: List<StringMatcher>): ParamMatcher {
  override fun isMatching(paramNames: List<String>): Boolean {
    if (paramNames.size != paramMatchers.size) {
      return false
    }

    return paramMatchers
        .zip(paramNames)
        .find { !it.first.isMatching(it.second) } == null
  }
}

class Matcher(private val methodNameMatcher: StringMatcher, 
              private val paramMatchers: ParamMatcher): MethodMatcher 
{
  override fun isMatching(fullyQualifiedMethodName: String, paramNames: List<String>): Boolean {
    return methodNameMatcher.isMatching(fullyQualifiedMethodName) && paramMatchers.isMatching(paramNames)
  }
}

object MatcherConstructor {

  fun extract(matcher: String): Couple<String>? {
    val trimmedMatcher = matcher.trim()
    if (trimmedMatcher.isEmpty()) return null

    val openParenthIndex = trimmedMatcher.indexOf('(')
    if (openParenthIndex < 0) {
      return Couple(trimmedMatcher, "")
    }
    else if (openParenthIndex == 0) {
      val paramsMatcher = getParamsMatcher(trimmedMatcher) ?: return null
      return Couple("", paramsMatcher)
    }

    val methodMatcher = trimmedMatcher.substring(0, openParenthIndex)
    val paramsMatcher = getParamsMatcher(trimmedMatcher) ?: return null

    return Couple(methodMatcher.trim(), paramsMatcher.trim())
  }
  
  private fun getParamsMatcher(matcher: String): String? {
    val openBraceIndex = matcher.indexOf("(")
    val closeBraceIndex = matcher.indexOf(")")

    if (openBraceIndex >= 0 && closeBraceIndex > 0) {
      return matcher.substring(openBraceIndex, closeBraceIndex + 1).trim()
    }
    
    return null
  }

  private fun createParametersMatcher(paramsMatcher: String): ParamMatcher? {
    if (paramsMatcher.length <= 2) return null

    val paramsString = paramsMatcher.substring(1, paramsMatcher.length - 1)
    val params = paramsString.split(',').map(String::trim)
    if (params.find(String::isEmpty) != null) return null

    val matchers = params.mapNotNull { StringMatcherBuilder.create(it) }
    return if (matchers.size == params.size) StringParamMatcher(matchers) else null
  }

  fun createMatcher(matcher: String): Matcher? {
    val pair = extract(matcher) ?: return null

    val methodNameMatcher = StringMatcherBuilder.create(pair.first) ?: return null
    val paramMatcher = if (pair.second.isEmpty()) AnyParamMatcher else createParametersMatcher(pair.second)
    
    return if (paramMatcher != null) Matcher(methodNameMatcher, paramMatcher) else null
  }


}


