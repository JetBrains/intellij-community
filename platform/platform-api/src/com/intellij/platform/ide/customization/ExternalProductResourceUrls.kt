// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.customization

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.BuildNumber
import com.intellij.util.Url
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus

/**
 * Provides URLs of different external resources associated with an IDE. An IDE should register its implementation of this interface as
 * a service with `overrides="true"` attribute to override the default one (which takes this information from *ApplicationInfo.xml file).
 * 
 * IDEs developed by JetBrains should use [BaseJetBrainsExternalProductResourceUrls][com.intellij.platform.ide.impl.customization.BaseJetBrainsExternalProductResourceUrls] 
 * as a superclass for their implementations.
 * 
 * Members of this interface are supposed to be accessed from platform code only, and should be only overriden in IDEs.  
 */
@ApiStatus.OverrideOnly
interface ExternalProductResourceUrls {
  companion object {
    @JvmStatic
    fun getInstance(): ExternalProductResourceUrls = ApplicationManager.getApplication().getService(ExternalProductResourceUrls::class.java)
  }

  /**
   * Returns URL of an XML file containing meta-information about available IDE versions. It is used by the platform to check if an update
   * is available. Currently, there is no specification of that XML file format; you may use [the file](https://www.jetbrains.com/updates/updates.xml)
   * used by JetBrains IDEs as a reference.
   */
  val updatesMetadataXmlUrl: Url?

  /**
   * Returns URL which can be used to download a patch from build [from] to build [to].
   * This function is called only if [the metadata][updatesMetadataXmlUrl] contains information about a patch for these versions, 
   * and a user initiates update.
   */
  fun computePatchUrl(from: BuildNumber, to: BuildNumber): Url?

  /**
   * Returns a function which computes URL for bug reporting.
   * `description` parameter will contain automatically generated information about the current IDE and the environment, it may be added
   * to the template of the issue report.
   * In order to include custom data in the description, you may use [FeedbackDescriptionProvider][com.intellij.ide.FeedbackDescriptionProvider]
   * extension point.
   *
   * The computed URL will be opened in browser when a user invokes the "Submit a Bug Report" action. If this function returns `null`,
   * the action won't be available.
   */
  val bugReportUrl: ((description: String) -> Url)?
    get() = null
  
  /**
   * Returns a function which computes URL for contacting technical support.
   * `description` parameter will contain automatically generated information about the current IDE and the environment, it may be added
   * to the template of the support form.
   * In order to include custom data in the description, you may use [FeedbackDescriptionProvider][com.intellij.ide.FeedbackDescriptionProvider]
   * extension point.
   *
   * The computed URL will be opened in browser when a user invokes the "Contact Support" action. If this function returns `null`,
   * the action won't be available.
   */
  val technicalSupportUrl: ((description: String) -> Url)?
    get() = null

  /**
   * Returns an instance which will be used to submit feedback about the product via "Submit Feedback" action. 
   * If this function returns `null`, the action won't be available.
   */
  val feedbackReporter: FeedbackReporter?
    get() = null
}

/**
 * Implement this interface and return an instance from [ExternalProductResourceUrls.feedbackReporter] to provide "Submit Feedback" action
 * for the product.
 */
interface FeedbackReporter {
  /**
   * Describes the place where the feedback will be reported to (e.g., address of the site).
   * It's shown in the description of "Submit Feedback" action.
   */
  val destinationDescription: String

  /**
   * Returns a URL which will be opened in the browser when the user invokes "Submit Feedback" action. 
   * @param description contains automatically generated information about the current IDE and the environment, it may be added to the template 
   * of the feedback form.
   * In order to include custom data in the description, you may use [FeedbackDescriptionProvider][com.intellij.ide.FeedbackDescriptionProvider]
   * extension point.
   */
  fun feedbackFormUrl(description: String): Url

  /**
   * Override this function to show a custom form when "Submit Feedback" action is invoked or when the IDE requests a user to provide 
   * feedback during the evaluation period.
   * @param requestedForEvaluation `true` if the form is shown by the IDE during the evaluation period and `false` if user explicitly 
   * invoked "Submit Feedback" action.  
   * @return `true` if the custom form was shown, and `false` otherwise;
   * in the latter case, the default way with opening [feedbackFormUrl] in the browser will be used.
   */
  @RequiresEdt
  fun showFeedbackForm(project: Project?, requestedForEvaluation: Boolean): Boolean = false
}