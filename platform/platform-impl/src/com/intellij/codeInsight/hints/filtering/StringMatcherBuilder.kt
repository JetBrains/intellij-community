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

interface StringMatcher {
  fun isMatching(text: String): Boolean
}

class StringMatcherImpl(private val matcher: (String) -> Boolean) : StringMatcher {
  override fun isMatching(text: String) = matcher(text)
}

object StringMatcherBuilder {

  fun create(matcher: String): StringMatcher? {
    if (matcher.isEmpty()) return StringMatcherImpl { true }

    val asterisksCount = matcher.count { it == '*' }
    if (asterisksCount > 1) return null
    if (asterisksCount == 1) return createAsterisksMatcher(matcher)

    return StringMatcherImpl { it == matcher }
  }

  private fun createAsterisksMatcher(matcher: String): StringMatcher? {
    if (matcher == "*") {
      return StringMatcherImpl { true }
    }

    if (matcher.startsWith('*')) {
      val target = matcher.substring(1)
      return StringMatcherImpl { it.endsWith(target) }
    }

    if (matcher.endsWith('*')) {
      val target = matcher.substring(0, matcher.length - 1)
      return StringMatcherImpl { it.startsWith(target) }
    }

    return null
  }

}