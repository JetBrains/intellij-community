// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options

/**
 * Defining options that by default should be available in all products
 */
class GlobalOptionsApplicabilityFilter : OptionsApplicabilityFilter() {
  override fun isOptionApplicable(optionId: OptionId?): Boolean = optionId == OptionId.INSERT_PARENTHESES_AUTOMATICALLY
}