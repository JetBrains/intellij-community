// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.java.training

import com.intellij.java.ift.JavaLessonsBundle
import com.intellij.openapi.util.IconLoader
import com.intellij.util.PlatformUtils
import training.ui.welcomeScreen.OnboardingLessonPromoter
import javax.swing.Icon

class JavaOnboardingTourPromoter : OnboardingLessonPromoter(
  "java.onboarding", "JAVA", JavaLessonsBundle.message("java.onboarding.lesson.name")
) {
  override val promoImage: Icon
    get() = IconLoader.getIcon("img/idea-onboarding-tour.png", JavaOnboardingTourPromoter::class.java.classLoader)

  override fun canCreatePromo(isEmptyState: Boolean): Boolean =
    super.canCreatePromo(isEmptyState) && PlatformUtils.isIntelliJ()
}