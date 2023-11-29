// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.ift

import com.intellij.openapi.project.ProjectBundle
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.ui.configuration.JdkComboBox
import com.intellij.openapi.roots.ui.configuration.SdkListItem
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.components.panels.NonOpaquePanel
import training.dsl.LearningBalloonConfig
import training.dsl.LessonContext
import training.dsl.LessonUtil
import training.learn.course.KLesson
import training.project.ReadMeCreator
import training.ui.LearningUiHighlightingManager
import training.ui.LearningUiHighlightingManager.HighlightingOptions
import training.ui.LearningUiUtil
import training.util.getFeedbackLink
import javax.swing.JList

/** should be the same as in the [JavaLangSupport] bean declaration in the plugin XML */
const val javaLanguageId: String = "JAVA"

internal class JavaLangSupport : JavaBasedLangSupport() {
  override val contentRootDirectoryName: String = "IdeaLearningProject"
  override val projectResourcePath: String = "learnProjects/java/LearnProject"

  override val primaryLanguage: String = javaLanguageId

  override val defaultProductName: String = "IDEA"

  override val scratchFileName: String = "Learning.java"

  override val sampleFilePath: String = "$sourcesDirectoryPath/Sample.java"

  override val langCourseFeedback: String?
    get() = getFeedbackLink(this, false)

  override val readMeCreator: ReadMeCreator by lazy { ReadMeCreator() }

  override val sdkConfigurationTasks: LessonContext.(lesson: KLesson) -> Unit = {
    val setupSdkText = ProjectBundle.message("project.sdk.setup")

    task {
      if (isSdkConfigured(project)) return@task
      triggerAndBorderHighlight().component { ui: HyperlinkLabel ->
        ui.text == setupSdkText
      }
    }

    task {
      if (isSdkConfigured(project)) return@task

      rehighlightPreviousUi = true
      text(JavaLessonsBundle.message("java.missed.sdk.click.setup", strong(setupSdkText)))
      text(JavaLessonsBundle.message("java.missed.sdk.show.options"), LearningBalloonConfig(Balloon.Position.below, 0))
      text(JavaLessonsBundle.message("java.missed.sdk.read.more.tip", LessonUtil.getHelpLink("sdk.html#jdk")))
      triggerAndBorderHighlight().component { list: JList<*> ->
        val model = list.model
        (0 until model.size).any {
          model.getElementAt(it).let { item -> item is SdkListItem || item is JdkComboBox.JdkComboBoxItem }
        }
      }
    }

    task {
      if (isSdkConfigured(project)) return@task

      rehighlightPreviousUi = true
      text(JavaLessonsBundle.message("java.missed.sdk.configure"))
      var progressHighlightingStarted = false
      timerCheck {
        val jdk = JavaProjectUtil.getEffectiveJdk(project)

        if (jdk != null && !progressHighlightingStarted) {
          progressHighlightingStarted = true
          invokeInBackground {
            LearningUiUtil.findComponentOrNull(project, NonOpaquePanel::class.java) { panel ->
              panel.javaClass.name.contains("InlineProgressPanel")
            }?.let { panel ->
              taskInvokeLater {
                if (!disposed) {
                  LearningUiHighlightingManager.highlightComponent(panel, HighlightingOptions(highlightInside = false))
                  this@task.text(JavaLessonsBundle.message("java.missed.sdk.wait.installation"),
                                 LearningBalloonConfig(Balloon.Position.above, width = 0, highlightingComponent = panel))
                }
              }
            }
          }
        }

        jdk != null && jdk.rootProvider.getFiles(OrderRootType.CLASSES).isNotEmpty()
      }
    }
  }
}
