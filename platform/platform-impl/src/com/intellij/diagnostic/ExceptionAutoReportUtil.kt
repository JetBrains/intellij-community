// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.ide.AboutPopupDescriptionProvider
import com.intellij.ide.gdpr.Consent
import com.intellij.ide.gdpr.ConsentOptions
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.PluginUtil
import com.intellij.ide.util.PropertiesComponent
import com.intellij.idea.AppMode
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.diagnostic.ProblematicPluginInfo
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.ide.impl.diagnostic.errorsDialog.ErrorMessageClustering
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object ExceptionAutoReportUtil {
  private const val EA_AUTO_REPORT_OFFERED_PROPERTY: String = "ea.auto.report.offered"
  private const val ENABLED_FOR_DEVELOPMENT = false

  // may be queried before Application started
  val autoReportIsForbiddenForProduct: Boolean
    get() = !ApplicationInfoImpl.getShadowInstance().isVendorJetBrains
            || AppMode.isRemoteDevHost() // we handle everything on client
            || AppMode.isHeadless()

  @JvmStatic
  val isAutoReportVisible: Boolean
    get() = !autoReportIsForbiddenForProduct && Registry.`is`("ea.auto.report.feature.visible", false)

  @JvmStatic
  val isAutoReportEnabled: Boolean
    get() {
      if (!isAutoReportVisible) return false
      return isAutoReportAllowedByUser()
    }

  private val isDevelopmentEnvironment: Boolean
    get() = ApplicationManagerEx.isInIntegrationTest()
            || AppMode.isRunningFromDevBuild()
            || PluginManagerCore.isRunningFromSources()

  private fun isAutoReportAllowedByUser(): Boolean {
    if (isDevelopmentEnvironment) return ENABLED_FOR_DEVELOPMENT
    if (ConsentOptions.getInstance().isEAP) {
      return ExceptionEAPAutoReportManager.getInstance().enabledInEAP
    }

    val (consent, needsReconfirm) = getConsentAndNeedsReconfirm()
    return consent?.isAccepted == true && !needsReconfirm
  }

  @JvmStatic
  val isAutoReportEnabledOrUndecided: Boolean
    get() {
      if (!isAutoReportVisible) return false

      if (isDevelopmentEnvironment) return ENABLED_FOR_DEVELOPMENT
      if (ConsentOptions.getInstance().isEAP) {
        return ExceptionEAPAutoReportManager.getInstance().enabledInEAP
      }

      val (consent, needsReconfirm) = getConsentAndNeedsReconfirm()
      return consent?.isAccepted == true || needsReconfirm
    }

  private fun getConsentAndNeedsReconfirm(): Pair<Consent?, Boolean> {
    val (consents, needsReconfirm) = ConsentOptions.getInstance().getConsents(ConsentOptions.condEAAutoReportConsent())
    thisLogger().assertTrue(consents.size <= 1) {
      "Consent is expected to be bundled; multiple consents: ${consents.joinToString(",")}"
    }
    return Pair(consents.firstOrNull(), needsReconfirm)
  }

  fun shouldOfferEnablingAutoReport(): Boolean {
    if (!isAutoReportVisible || ConsentOptions.getInstance().isEAP) return false
    if (isDevelopmentEnvironment) {
      return false
    }

    val (consent, needsReconfirm) = getConsentAndNeedsReconfirm()
    if (consent == null) return false
    // the feature is already enabled
    if (consent.isAccepted && !needsReconfirm) return false
    // the feature was never proposed
    if (!PropertiesComponent.getInstance().getBoolean(EA_AUTO_REPORT_OFFERED_PROPERTY, false)) return true
    // ask once for each consent version
    return !PropertiesComponent.getInstance().getBoolean("$EA_AUTO_REPORT_OFFERED_PROPERTY.${consent.version}", false)
  }

  fun enablingAutoReportOffered(autoReportEnabled: Boolean) {
    if (!isAutoReportVisible) return
    ConsentOptions.getInstance().setEAAutoReportAllowed(autoReportEnabled)
    PropertiesComponent.getInstance().setValue(EA_AUTO_REPORT_OFFERED_PROPERTY, true)

    val consent = getConsentAndNeedsReconfirm().first
    if (consent != null) {
      PropertiesComponent.getInstance().setValue("$EA_AUTO_REPORT_OFFERED_PROPERTY.${consent.version}", true)
    }
  }

  /**
   * Checks only [message], not the state of functionality
   */
  suspend fun isAutoReportableException(message: AbstractMessage): Boolean {
    return getRelevantData(message) != null
  }

  suspend fun getRelevantData(message: AbstractMessage): Pair<ITNReporter, ProblematicPluginInfo?>? {
    val throwable = message.throwable
    if (throwable is JBRCrash) return null

    val pluginId = PluginUtil.getInstance().findPluginId(message.throwable)
    val pluginInfo = ErrorMessageClustering.getInstance().createPluginInfo(pluginId)
    val submitter = DefaultIdeaErrorLogger.findSubmitterByPluginInfo(message.throwable, pluginInfo)
    val itnReporter = submitter as? ITNReporter ?: return null

    val isErrorSendable = if (pluginInfo == null || PluginManagerCore.isDevelopedByJetBrains(pluginInfo.vendor)) {
      isDefaultSubmitter(submitter)
    }
    else {
      submitter.javaClass == JetBrainsMarketplaceErrorReportSubmitter::class.java
    }

    if (isErrorSendable) {
      return Pair(itnReporter, pluginInfo)
    }
    else {
      return null
    }
  }

  private fun isDefaultSubmitter(submitter: ITNReporter): Boolean {
    val cls = submitter.javaClass
    return cls == ITNReporter::class.java
           || cls.name == $$"com.intellij.rustrover.RustRoverMessagePoolAutoReporter$MyITNReporter"
           && ApplicationManager.getApplication().isEAP
  }
}

internal class ReporterIdForEAAutoReporters : AboutPopupDescriptionProvider {
  override fun getDescription(): @NlsContexts.DetailedDescription String? = null
  override fun getExtendedDescription(): @NlsContexts.DetailedDescription String = DiagnosticBundle.message("about.dialog.text.ea.reporting.id", ITNProxy.DEVICE_ID)
}

internal class ReporterIdLoggerActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    thisLogger().info(DiagnosticBundle.message("about.dialog.text.ea.reporting.id", ITNProxy.DEVICE_ID))
  }
}
