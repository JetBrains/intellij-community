// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.ift

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.progress.util.AbstractProgressIndicatorExBase
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectBundle
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.ui.configuration.JdkComboBox
import com.intellij.openapi.roots.ui.configuration.SdkListItem
import com.intellij.openapi.roots.ui.configuration.projectRoot.SdkDownloadTracker
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

internal class JavaLangSupport : JavaBasedLangSupport() {
  override val contentRootDirectoryName = "IdeaLearningProject"
  override val projectResourcePath = "learnProjects/java/LearnProject"

  override val primaryLanguage: String = "JAVA"

  override val defaultProductName: String = "IDEA"

  override val scratchFileName: String = "Learning.java"

  override val sampleFilePath: String = "src/Sample.java"

  override val langCourseFeedback
    get() = getFeedbackLink(this, false)

  override val readMeCreator by lazy { ReadMeCreator() }

  override val sdkConfigurationTasks: LessonContext.(lesson: KLesson) -> Unit = {
    val setupSdkText = ProjectBundle.message("project.sdk.setup")

    fun isJdkInstalled(project: Project) = JavaProjectUtil.getProjectJdk(project) != null

    task {
      if (isJdkInstalled(project)) return@task
      triggerAndFullHighlight { usePulsation = true }.component { ui: HyperlinkLabel ->
        ui.text == setupSdkText
      }
    }

    task {
      if (isJdkInstalled(project)) return@task

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
      if (isJdkInstalled(project)) return@task

      rehighlightPreviousUi = true
      text(JavaLessonsBundle.message("java.missed.sdk.configure"))
      var progressHighlightingStarted = false
      var downloadingListenerAdded = false
      timerCheck {
        val jdk = JavaProjectUtil.getProjectJdk(project)

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

        if (jdk != null && !downloadingListenerAdded) {
          downloadingListenerAdded = SdkDownloadTracker.getInstance().tryRegisterDownloadingListener(jdk, taskDisposable,
                                                                                                     AbstractProgressIndicatorExBase()) {
            // todo: For some reason after the downloading JDK is not fully applied, imports are still red.
            //  So we need to trigger the indexing by setting the same JDK that already set just to call listeners of JDK update.
            //  Remove this hack when IDEA-244649 will be fixed.
            runWriteAction {
              ProjectRootManager.getInstance(project).projectSdk = JavaProjectUtil.getProjectJdk(project)
            }
          }
        }

        jdk != null && jdk.rootProvider.getFiles(OrderRootType.CLASSES).isNotEmpty()
      }
    }
  }
}
