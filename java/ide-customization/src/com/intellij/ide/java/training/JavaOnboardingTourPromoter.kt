// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.java.training

import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.util.IconLoader
import training.ui.welcomeScreen.OnboardingLessonPromoter
import javax.swing.Icon
import javax.swing.JPanel

class JavaOnboardingTourPromoter : OnboardingLessonPromoter("java.onboarding", "Java") {
  override fun promoImage(): Icon = IconLoader.getIcon("img/idea-onboarding-tour.png", JavaOnboardingTourPromoter::class.java.classLoader)

  override fun getPromotionForInitialState(): JPanel? {
    if (ApplicationNamesInfo.getInstance().fullProductNameWithEdition.equals("IDEA Edu")) {
      return null
    }
    return super.getPromotionForInitialState()
  }
}