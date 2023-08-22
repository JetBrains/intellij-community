// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.util

import com.intellij.build.BuildContentManager
import com.intellij.execution.CantRunException
import com.intellij.execution.ExecutionBundle
import com.intellij.execution.runners.ExecutionUtil
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectBundle
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.UnknownSdkFixAction
import com.intellij.openapi.projectRoots.impl.UnknownSdkTracker
import com.intellij.openapi.roots.ui.configuration.SdkLookup
import com.intellij.openapi.roots.ui.configuration.SdkLookup.Companion.newLookupBuilder
import com.intellij.openapi.roots.ui.configuration.SdkLookupDecision
import com.intellij.openapi.roots.ui.configuration.SdkLookupDownloadDecision
import com.intellij.openapi.roots.ui.configuration.SdkLookupParameters
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import java.util.concurrent.atomic.AtomicReference
import javax.swing.event.HyperlinkEvent
import javax.swing.event.HyperlinkListener

@Service //project
class UnknownAlternativeSdkResolver(private val project: Project) {
  companion object {
    @JvmStatic
    fun getInstance(project: Project) = project.service<UnknownAlternativeSdkResolver>()
  }

  @Throws(CantRunException::class)
  fun notifyUserToResolveJreAndFail(jreHome: String) : Nothing {
    val notification = Notification("JDK resolve problems", ProjectBundle.message("failed.to.resolve.sdk.notification.title"), "", NotificationType.INFORMATION)
    notification.addAction(object : AnAction(ProjectBundle.message("try.to.find.sdk.notification.action")) {
      override fun actionPerformed(e: AnActionEvent) {
        try {
          tryResolveJre(jreHome)
        } catch (e: CantRunException) {
          val buildToolWindowId = BuildContentManager.getInstance(project).orCreateToolWindow.id
          ExecutionUtil.handleExecutionError(project, buildToolWindowId, ProjectBundle.message("resolve.sdk.task.name"), e)
        }
      }
    })
    notification.notify(project)
    throw CantRunException.CustomProcessedCantRunException()
  }

  @Throws(CantRunException::class)
  private fun tryResolveJre(jreHome: String) : Sdk? {
    if (!Registry.`is`("jdk.auto.run.configurations")) return null

    val javaSdk = JavaSdk.getInstance()

    //assume it is a JDK name reference
    if (jreHome.contains("/") || jreHome.contains("\\")) return null

    val theDownload = AtomicReference<Sdk?>(null)
    val theSdk = AtomicReference<Sdk?>(null)
    val theFix = AtomicReference<UnknownSdkFixAction>(null)

    object : Task.Modal(project, ProjectBundle.message("progress.title.resolving.sdks"), true) {
      override fun run(indicator: ProgressIndicator) {
        val lookup = newLookupBuilder()
          .withProgressIndicator(indicator)
          .withProject(project)
          .withSdkName(jreHome)
          .withSdkType(javaSdk)
          .onDownloadingSdkDetected { sdk: Sdk ->
            theDownload.set(sdk)
            SdkLookupDownloadDecision.STOP
          }
          .onSdkFixResolved { fix: UnknownSdkFixAction ->
            theFix.set(fix)
            SdkLookupDecision.STOP
          }
          .onSdkResolved { sdk: Sdk? ->
            theSdk.set(sdk)
          }
        SdkLookup.getInstance().lookupBlocking(lookup as SdkLookupParameters)

        val fix = theFix.get()
        if (theSdk.get() == null && fix != null && UnknownSdkTracker.getInstance(project).isAutoFixAction(fix)) {
          theFix.set(null)
          invokeAndWaitIfNeeded {
            if (project.isDisposed) return@invokeAndWaitIfNeeded
            val sdk = UnknownSdkTracker.getInstance(project).applyAutoFixAndNotify(fix, indicator)
            theSdk.set(sdk)
          }
        }
      }
    }.queue()

    val found = theSdk.get()
    if (found != null) return found

    val downloading = theDownload.get()
    if (downloading != null) {
      throw CantRunException(
        ExecutionBundle.message("jre.path.is.not.valid.jre.home.downloading.message", jreHome))
    }

    val fix = theFix.get()
    if (fix != null) {
      val builder = HtmlBuilder()
      val linkTarget = "this-is-an-action-to-fix-jdk"
      builder.append(ExecutionBundle.message("jre.path.is.not.valid.jre.home.error.message", jreHome))
      builder.append(HtmlChunk.br())
      builder.append(HtmlChunk.link(linkTarget, fix.actionDetailedText))

      throw object : CantRunException(builder.toString()), HyperlinkListener {
        override fun hyperlinkUpdate(e: HyperlinkEvent) {
          if (e.eventType != HyperlinkEvent.EventType.ACTIVATED) return
          fix.applySuggestionAsync(project)
        }
      }
    }

    return null
  }
}
