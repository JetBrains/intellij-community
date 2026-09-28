// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.ide.AboutPopupDescriptionProvider
import com.intellij.ide.gdpr.ConsentOptions
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.PluginUtil
import com.intellij.ide.util.PropertiesComponent
import com.intellij.idea.AppMode
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.diagnostic.ProblematicPluginInfo
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.util.application
import com.intellij.util.text.nullize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

@ApiStatus.Internal
object ExceptionAutoReportUtil {
  private const val EA_AUTO_REPORT_NOTIFIED_PROPERTY: String = "ea.auto.report.notified"
  private const val EA_AUTO_REPORT_CONFIGURED_PROPERTY: String = "ea.auto.report.configured"

  private const val ENABLED_FOR_DEVELOPMENT = false
  private const val BACKEND_THROWABLE_HEADER_PREFIX = "backend"
  private const val BACKEND_EXCEPTION_CLASS_NAME = "com.jetbrains.rd.platform.diagnostics.BackendException"

  // may be queried before Application started
  val autoReportIsForbiddenForProduct: Boolean
    get() = !ApplicationInfoImpl.getShadowInstance().isVendorJetBrains || AppMode.isHeadless()

  suspend fun isAutoReportVisible(): Boolean {
    return !autoReportIsForbiddenForProduct && RegistryManager.getInstanceAsync().`is`("ea.auto.report.allowed")
  }

  fun isAutoReportVisibleBlocking(): Boolean {
    // may be called extremely early before IDE started!
    return !autoReportIsForbiddenForProduct && Registry.`is`("ea.auto.report.allowed", false)
  }

  @JvmStatic
  val isAutoReportForced: Boolean
    get() = getForcedAutoReportLevel() != ForcedReportLevel.NONE

  suspend fun isAutoReportEnabled(): Boolean {
    if (!isAutoReportVisible()) return false
    if (isDevelopmentEnvironment) return ENABLED_FOR_DEVELOPMENT

    return isAutoReportAllowedByUser()
  }

  @JvmStatic // may be called extremely early before IDE started!
  val isConsentAllowedToBeVisible: Boolean
    get() = isAutoReportVisibleBlocking() && !isAutoReportForced // do not show consents UI if level is forced

  fun getAutoReportTag(): String? {
    return Registry.stringValue("ea.auto.report.forced.tag", "").nullize()
  }

  private fun getForcedAutoReportLevel(): ForcedReportLevel {
    return try {
      ForcedReportLevel.valueOf(Registry.stringValue("ea.auto.report.forced", ForcedReportLevel.NONE.name).uppercase())
    }
    catch (_: IllegalArgumentException) {
      return ForcedReportLevel.NONE
    }
  }

  private val isDevelopmentEnvironment: Boolean
    get() = ApplicationManagerEx.isInIntegrationTest()
            || AppMode.isRunningFromDevBuild()
            || PluginManagerCore.isRunningFromSources()

  suspend fun isAutoReportAllowedByUser(): Boolean {
    if (isAutoReportForced) return true // set by provisioning

    val (consents, needsReconfirm) = withContext(Dispatchers.IO) {
      ConsentOptions.getInstance().getConsents(ConsentOptions.condEAAutoReportConsent())
    }
    thisLogger().assertTrue(consents.size <= 1) {
      "Consent is expected to be bundled; multiple consents: ${consents.joinToString(",")}"
    }
    return consents.firstOrNull()?.isAccepted == true && !needsReconfirm
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

    // a marker of stopped error monitoring, not an actual error; it is handled by the message pool clients themselves
    if (throwable.isInstance<MessagePool.TooManyErrorsException>()) {
      thisLogger().debug("Ignoring it as an error monitoring marker: ${getThrowableFqn(throwable)}")
      return null
    }

    // if level is ALL or NONE, then we report exceptions based on regular rules
    val level = getForcedAutoReportLevel()
    if (level == ForcedReportLevel.FREEZES && !isFreeze(throwable)) {
      thisLogger().debug("Ignoring it as not a freeze: ${getThrowableFqn(throwable)}. Only freezes are allowed to be auto-reported.")
      return null
    }

    val pluginId = PluginUtil.getInstance().findPluginId(throwable)
    val pluginInfo = ErrorMessageClustering.getInstance().createPluginInfo(pluginId)
    val submitter = DefaultIdeaErrorLogger.findSubmitterByPluginInfo(throwable, pluginInfo)
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
  }

  fun isFreeze(throwable: Throwable): Boolean {
    return throwable.isInstance<Freeze>()
  }

  private fun getThrowableFqn(throwable: Throwable): String? {
    if (throwable is RemoteSerializedThrowable) return throwable.classFqn
    return throwable::class.qualifiedName
  }

  fun getAutoReportSource(throwable: Throwable): String {
    return if (isBackendThrowable(throwable)) "backend" else "frontend"
  }

  private fun isBackendThrowable(throwable: Throwable): Boolean {
    return throwable is RemoteSerializedThrowable && throwable.headerPrefix == BACKEND_THROWABLE_HEADER_PREFIX
           || throwable.javaClass.name == BACKEND_EXCEPTION_CLASS_NAME
  }

  @TestOnly
  fun createFreezeLogMessage(): LogMessage = LogMessage(Freeze(null, null, emptyList()), null, emptyList())

  fun isUserNotifiedOfDataCollection(): Boolean {
    return PropertiesComponent.getInstance().getInt(EA_AUTO_REPORT_NOTIFIED_PROPERTY, 0) > 0
  }

  fun recordUserNotifiedOfDataCollection() {
    val propertiesComponent = PropertiesComponent.getInstance()
    val counter = propertiesComponent.getInt(EA_AUTO_REPORT_NOTIFIED_PROPERTY, 0) + 1
    propertiesComponent.setValue(EA_AUTO_REPORT_NOTIFIED_PROPERTY, counter, 0)
  }

  fun needNotificationOfDataCollection() : Boolean {
    return PropertiesComponent.getInstance().getInt(EA_AUTO_REPORT_NOTIFIED_PROPERTY, 0) < 3
  }

  fun recordUserVisitedConfigure() {
    PropertiesComponent.getInstance().setValue(EA_AUTO_REPORT_CONFIGURED_PROPERTY, true)
  }

  /**
   * @return true if user visited Configure... page from notification about data collection
   */
  fun isUserVisitedConfigure() : Boolean {
    return PropertiesComponent.getInstance().getBoolean(EA_AUTO_REPORT_CONFIGURED_PROPERTY, false)
  }
}

internal class ReporterIdForEAAutoReporters : AboutPopupDescriptionProvider {
  override fun getDescription(): @NlsContexts.DetailedDescription String? = null
  override fun getExtendedDescription(): String = DiagnosticBundle.message("about.dialog.text.ea.reporting.id", ITNProxy.DEVICE_ID)
}

internal class ReporterIdLoggerActivity : ProjectActivity {
  init {
    if (application.isHeadlessEnvironment) throw ExtensionNotApplicableException.create()
  }

  override suspend fun execute(project: Project) {
    thisLogger().info(DiagnosticBundle.message("about.dialog.text.ea.reporting.id", ITNProxy.DEVICE_ID))
  }
}

internal enum class ForcedReportLevel {
  ALL, FREEZES, NONE
}

@ApiStatus.Internal
interface ExceptionAutoReportService {
  fun getResendAttempts(): Int
}
