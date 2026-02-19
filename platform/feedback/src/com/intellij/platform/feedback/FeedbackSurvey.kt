// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback

import com.intellij.openapi.extensions.PluginAware
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread

/**
 * Represents a feedback survey.
 *
 * Specific feedback surveys must be registered with a unique `survey_id` and implementation class.
 *
 * For an example, see
 * @see com.intellij.platform.feedback.demo.DemoInIdeFeedbackSurvey
 */
abstract class FeedbackSurvey : PluginAware {

  private var pluginDescriptor: PluginDescriptor? = null

  protected abstract val feedbackSurveyType: FeedbackSurveyType<*>

  override fun setPluginDescriptor(pluginDescriptor: PluginDescriptor) {
    this.pluginDescriptor = pluginDescriptor
  }

  internal fun getFeedbackSurveyId(): String {
    return feedbackSurveyType.getFeedbackSurveyId()
  }

  internal fun getPluginDescriptor(): PluginDescriptor? {
    return pluginDescriptor
  }

  @RequiresBackgroundThread
  internal fun isSuitableToShow(project: Project): Boolean {
    ThreadingAssertions.assertBackgroundThread()
    return feedbackSurveyType.isSuitableToShow(project)
  }

  fun showNotification(project: Project, forTest: Boolean = false) {
    feedbackSurveyType.showNotification(project, forTest)
  }
}