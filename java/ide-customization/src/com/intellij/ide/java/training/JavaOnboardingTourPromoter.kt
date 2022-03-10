// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.java.training

import com.intellij.openapi.application.ApplicationNamesInfo
import training.FeaturesTrainerIcons
import training.ui.welcomeScreen.OnboardingLessonPromoter
import javax.swing.Icon
import javax.swing.JPanel

class JavaOnboardingTourPromoter : OnboardingLessonPromoter("java.onboarding", "Java") {
  override fun promoImage(): Icon = FeaturesTrainerIcons.Img.PluginIcon  // todo: Replace with Java-specific icon

  override fun getPromotionForInitialState(): JPanel? {
    if (ApplicationNamesInfo.getInstance().fullProductNameWithEdition.equals("IDEA Edu")) {
      return null
    }
    return super.getPromotionForInitialState()
  }
}