// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.customization.java.training

import com.intellij.java.ift.JavaLessonsBundle
import com.intellij.java.ift.javaLanguageId
import com.intellij.java.ift.lesson.essential.ideaOnboardingLessonId
import com.intellij.openapi.util.IconLoader
import com.intellij.util.PlatformUtils
import training.ui.welcomeScreen.OnboardingLessonPromoter
import javax.swing.Icon

private class JavaOnboardingTourPromoter : OnboardingLessonPromoter(
  ideaOnboardingLessonId, javaLanguageId, JavaLessonsBundle.message("java.onboarding.lesson.name")
) {
  override val promoImage: Icon
    get() = IconLoader.getIcon("img/idea-onboarding-tour.png", JavaLessonsBundle::class.java.classLoader)

  override fun canCreatePromo(isEmptyState: Boolean): Boolean =
    super.canCreatePromo(isEmptyState) && PlatformUtils.isIntelliJ()
}