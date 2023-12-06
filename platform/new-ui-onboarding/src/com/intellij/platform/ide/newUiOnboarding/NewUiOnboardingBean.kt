// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.newUiOnboarding

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.RequiredElement
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Property
import com.intellij.util.xmlb.annotations.XCollection

/**
 * Should be defined in each IDE where onboarding should be present.
 *
 * [ideHelpName] should be equal the IDE name that used in the Web Help page link.
 * For example, for IDEA it should be `idea`, because the pattern of the link is `https://www.jetbrains.com/help/idea/<topicName>`.
 *
 * Allows overriding the default steps order to add/remove/replace some steps.
 * See [NewUiOnboardingService.getDefaultStepsOrder] to get the default steps order.
 *
 * Example of declaration with steps order customizations inside plugin.xml:
 * ```xml
 * <newUiOnboarding ideHelpName="idea">
 *   <add stepId="myStep1" order="first"/>
 *   <add stepId="myStep2" order="before runWidget"/>
 *   <remove stepId="gitWidget"/>
 *   <replace stepId="projectWidget" newStepId="myStep3"/>
 * </newUiOnboarding>
 * ```
 * @see [NewUiOnboardingCustomization]
 * @see [AddCustomization]
 * @see [RemoveCustomization]
 * @see [ReplaceCustomization]
 */
internal class NewUiOnboardingBean {
  @Attribute
  @RequiredElement
  lateinit var ideHelpName: String

  @Property(surroundWithTag = false)
  @XCollection(elementTypes = [AddCustomization::class, RemoveCustomization::class, ReplaceCustomization::class])
  val customizations: List<NewUiOnboardingCustomization> = emptyList()

  companion object {
    private val EP_NAME: ExtensionPointName<NewUiOnboardingBean> = ExtensionPointName("com.intellij.ide.newUiOnboarding")

    val isPresent: Boolean
      get() = EP_NAME.findFirstSafe { true } != null

    fun getInstance(): NewUiOnboardingBean {
      return EP_NAME.findFirstSafe { true } ?: error("NewUiOnboarding bean must be defined")
    }
  }
}