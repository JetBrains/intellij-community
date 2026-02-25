// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.ide.AboutPopupDescriptionProvider
import com.intellij.ide.gdpr.Consent
import com.intellij.ide.gdpr.ConsentOptions
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.PluginUtil
import com.intellij.ide.util.PropertiesComponent
import com.intellij.idea.AppMode
import com.intellij.internal.statistic.utils.getPluginInfoByDescriptor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.PlatformUtils
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object ExceptionAutoReportUtil {
  private const val EA_AUTO_REPORT_OFFERED_PROPERTY: String = "ea.auto.report.offered"

  val autoReportIsForbiddenForProduct: Boolean
    get() = AppMode.isRemoteDevHost() || PlatformUtils.isJetBrainsClient() || !ApplicationInfoImpl.getShadowInstance().isVendorJetBrains

  @JvmStatic
  val isAutoReportVisible: Boolean
    get() = !autoReportIsForbiddenForProduct && Registry.`is`("ea.auto.report.feature.visible", false)

  @JvmStatic
  val isAutoReportEnabled: Boolean
    get() {
      if (!isAutoReportVisible) return false
      return isAutoReportAllowedByUser()
    }

  private fun isAutoReportAllowedByUser(): Boolean {
    if (ConsentOptions.getInstance().isEAP && ExceptionEAPAutoReportManager.getInstance().enabledInEAP) return true
    val (consent, needsReconfirm) = getConsentAndNeedsReconfirm()
    return consent?.isAccepted == true && !needsReconfirm
  }

  @JvmStatic
  val isAutoReportEnabledOrUndecided: Boolean
    get() {
      if (!isAutoReportVisible) return false
      if (ConsentOptions.getInstance().isEAP && ExceptionEAPAutoReportManager.getInstance().enabledInEAP) return true
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
  fun isAutoReportableException(message: AbstractMessage): Boolean = getRelevantData(message) != null

  fun getRelevantData(message: AbstractMessage): Pair<ITNReporter, IdeaPluginDescriptor?>? {
    val throwable = message.throwable
    if (throwable is JBRCrash) return null
    val plugin = PluginManagerCore.getPlugin(PluginUtil.getInstance().findPluginId(message.throwable))
    if (plugin != null && !getPluginInfoByDescriptor(plugin).isDevelopedByJetBrains()) {
      return null
    }
    val submitter = DefaultIdeaErrorLogger.findSubmitter(throwable, plugin)
    if (submitter !is ITNReporter || !isDefaultSubmitter(submitter)) {
      return null
    }
    return Pair(submitter, plugin)
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
