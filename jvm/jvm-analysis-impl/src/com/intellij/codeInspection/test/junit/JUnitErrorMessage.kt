// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.test.junit

import com.intellij.util.SmartList

fun getPublicStaticErrorMessage(
  isStatic: Boolean,
  isPublic: Boolean,
  shouldBeStatic: Boolean
): String {
  val errors = SmartList<String>()
  if (!isPublic) errors.add("'public'")
  when {
    isStatic && !shouldBeStatic -> errors.add("non-static")
    !isStatic && shouldBeStatic -> errors.add("'static'")
  }
  return errors.joinToString(separator = " and ") { it }
}