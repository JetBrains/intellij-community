// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.ift.lesson.essential

import com.intellij.codeInsight.daemon.impl.analysis.HighlightingFeature
import com.intellij.java.ift.JavaLessonsBundle
import com.intellij.openapi.module.LanguageLevelUtil
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.Balloon
import com.siyeh.InspectionGadgetsBundle
import com.siyeh.IntentionPowerPackBundle
import org.jetbrains.annotations.Nls
import training.dsl.*
import training.dsl.LessonUtil.restoreIfModifiedOrMoved
import training.util.*

class JavaOnboardingTourLesson : OnboardingTourLessonBase("java.onboarding") {
  override val demoFileExtension: String = "java"
  override val learningProjectName: String = "IdeaLearningProject"
  override val sample: LessonSample = parseLessonSample("""
    import java.util.Arrays;
    import java.util.List;
    
    class Welcome {
        public static void main(String[] args) {
            int[] array = {5, 6, 7, 8};
            System.out.println("AVERAGE of array " + Arrays.toString(array) + " is " + findAverage(array));
        }
    
        private static double findAverage(int[] values) {
            double result = 0;
            <caret id=3/>for (int i = 0; i < values.length; i++) {
                result += values[i];
            }
            <caret>return result<caret id=2/>;
        }
    }
    """.trimIndent())

  override val completionStepExpectedCompletion: String = "length"

  override fun LessonContext.contextActions() {
    val quickFixMessage = InspectionGadgetsBundle.message("foreach.replace.quickfix")
    caret(sample.getPosition(3))

    task {
      triggerOnEditorText("for", highlightBorder = true)
    }

    task("ShowIntentionActions") {
      text(JavaLessonsBundle.message("java.onboarding.invoke.intention.for.warning.1"))
      text(JavaLessonsBundle.message("java.onboarding.invoke.intention.for.warning.2", action(it)))
      text(JavaLessonsBundle.message("java.onboarding.invoke.intention.for.warning.balloon", action(it)),
           LearningBalloonConfig(Balloon.Position.above, width = 0, cornerToPointerDistance = 80))
      triggerAndBorderHighlight().listItem { item ->
        item.isToStringContains(quickFixMessage)
      }
      restoreIfModifiedOrMoved()
    }

    task {
      text(JavaLessonsBundle.message("java.onboarding.select.fix", strong(quickFixMessage)))
      stateCheck {
        editor.document.text.contains("for (int value : values)")
      }
      restoreByUi(delayMillis = defaultRestoreDelay)
    }

    fun getIntentionMessage(project: Project): @Nls String {
      val module = ModuleManager.getInstance(project).modules.firstOrNull() ?: error("Not found modules in project '${project.name}'")
      val langLevel = LanguageLevelUtil.getEffectiveLanguageLevel(module)
      val messageKey = if (langLevel.isAtLeast(HighlightingFeature.TEXT_BLOCKS.level)) {
        "replace.concatenation.with.format.string.intention.name.formatted"
      }
      else "replace.concatenation.with.format.string.intention.name"
      return IntentionPowerPackBundle.message(messageKey)
    }

    caret("RAGE")

    task {
      triggerOnEditorText("AVERAGE")
    }

    task("ShowIntentionActions") {
      text(JavaLessonsBundle.message("java.onboarding.invoke.intention.for.code", action(it)))
      text(JavaLessonsBundle.message("java.onboarding.invoke.intention.for.code.balloon", action(it)),
           LearningBalloonConfig(Balloon.Position.below, width = 0))
      val intentionMessage = getIntentionMessage(project)
      triggerAndBorderHighlight().listItem { item ->
        item.isToStringContains(intentionMessage)
      }
      restoreIfModifiedOrMoved()
    }

    task {
      text(JavaLessonsBundle.message("java.onboarding.apply.intention", strong(getIntentionMessage(project)), LessonUtil.rawEnter()))
      stateCheck {
        val text = editor.document.text
        text.contains("System.out.printf") || text.contains("MessageFormat.format")
      }
      restoreByUi(delayMillis = defaultRestoreDelay)
    }
  }
}