// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.newUiOnboarding

import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Tag

/** Key of [NewUiOnboardingStep] should be used to reference the step as [stepId] */
internal sealed class NewUiOnboardingCustomization {
  @Attribute
  lateinit var stepId: String

  abstract fun customize(stepIds: MutableList<String>)
}

/**
 * Possible values for [order]:
 * 1. first
 * 2. last
 * 3. before `<stepId>`
 * 4. after `<stepId>`
 *
 * [com.intellij.openapi.extensions.LoadingOrder] in intentionally not used here, because it is too complicated
 * and its sorting algorithm provides unexpected results. Our case is much simpler.
 */
@Tag("add")
internal class AddCustomization : NewUiOnboardingCustomization() {
  @Attribute
  lateinit var order: String

  override fun customize(stepIds: MutableList<String>) {
    when (order) {
      "first" -> stepIds.add(0, stepId)
      "last" -> stepIds.add(stepId)
      else -> {
        val anchor = order.substringBefore(' ', "").takeIf { it.isNotEmpty() }
        val relativeId = order.substringAfterLast(' ', "").takeIf { it.isNotEmpty() }
        if (anchor == null || relativeId == null) error("Incorrect order specified: $order")
        val isAfter = when (anchor) {
          "after" -> true
          "before" -> false
          else -> error("Incorrect order specified: $order. Unknown anchor: $anchor")
        }
        val index = stepIds.indexOf(relativeId)
        if (index == -1) error("Incorrect order specified: $order. Unknown relativeId: $relativeId")
        stepIds.add(if (isAfter) index + 1 else index, stepId)
      }
    }
  }
}

@Tag("remove")
internal class RemoveCustomization : NewUiOnboardingCustomization() {
  override fun customize(stepIds: MutableList<String>) {
    stepIds.remove(stepId)
  }
}

@Tag("replace")
internal class ReplaceCustomization : NewUiOnboardingCustomization() {
  @Attribute
  lateinit var newStepId: String

  override fun customize(stepIds: MutableList<String>) {
    val index = stepIds.indexOf(stepId)
    if (index == -1) error("Not found step with ID: $stepId")
    stepIds.removeAt(index)
    stepIds.add(index, newStepId)
  }
}