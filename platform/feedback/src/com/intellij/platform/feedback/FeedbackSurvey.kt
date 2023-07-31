// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginAware
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.RequiredElement
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.annotations.Attribute

/**
 * Represents a feedback survey.
 *
 * Specific feedback surveys must be registered with a unique `survey_id` and implementation class.
 *
 * For an example, see
 * @see com.intellij.platform.feedback.demo.DemoInIdeFeedbackSurvey
 */
abstract class FeedbackSurvey : PluginAware {

  companion object {
    private val IDLE_FEEDBACK_SURVEY = ExtensionPointName<FeedbackSurvey>("com.intellij.feedback.idleFeedbackSurvey")

    fun getJBExtensionList(): List<FeedbackSurvey> {
      return IDLE_FEEDBACK_SURVEY.extensionList.filter {
        it.getPluginDescriptor()?.vendor?.equals("JetBrains") ?: true
      }
    }
  }

  @RequiredElement
  @Attribute("survey_id")
  protected var surveyId: String = ""

  private var pluginDescriptor: PluginDescriptor? = null

  protected abstract val feedbackSurveyType: FeedbackSurveyType<*>

  override fun setPluginDescriptor(pluginDescriptor: PluginDescriptor) {
    this.pluginDescriptor = pluginDescriptor
  }

  fun getPluginDescriptor(): PluginDescriptor? {
    return pluginDescriptor
  }

  fun isSuitableToShow(project: Project): Boolean {
    return feedbackSurveyType.isSuitableToShow(project)
  }

  fun showNotification(project: Project, forTest: Boolean = false) {
    feedbackSurveyType.showNotification(project, forTest)
  }
}