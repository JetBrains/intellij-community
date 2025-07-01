// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.dialog

import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.platform.feedback.dialog.uiBlocks.FeedbackBlock
import com.intellij.platform.feedback.dialog.uiBlocks.JsonDataProvider
import com.intellij.platform.feedback.dialog.uiBlocks.NoEmailAgreementBlock
import com.intellij.platform.feedback.impl.FEEDBACK_REPORT_ID_KEY
import com.intellij.platform.feedback.impl.FeedbackRequestData
import com.intellij.platform.feedback.impl.FeedbackRequestType
import com.intellij.platform.feedback.impl.bundle.CommonFeedbackBundle
import com.intellij.platform.feedback.impl.submitFeedback
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.EDT
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.launchOnShow
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.concurrent.atomic.AtomicReference
import javax.swing.Action
import javax.swing.JComponent

/** This number should be increased when [BlockBasedFeedbackDialog] fields changing */
const val BLOCK_BASED_FEEDBACK_VERSION = 1

/**
 * The base class for building feedback dialogs.
 *
 * If your dialog doesn't need to provide any system data in addition to [CommonFeedbackSystemData],
 * consider using [CommonBlockBasedFeedbackDialog] instead.
 */
abstract class BlockBasedFeedbackDialog<T : SystemDataJsonSerializable>(
  protected val myProject: Project?,
  protected val myForTest: Boolean
) : DialogWrapper(myProject) {

  private val myFeedbackJsonVersionKey: String = "format_version"

  /** Increase the additional number when feedback format is changed */
  protected open val myFeedbackJsonVersion: Int = COMMON_FEEDBACK_SYSTEM_INFO_VERSION + BLOCK_BASED_FEEDBACK_VERSION
  protected abstract val myFeedbackReportId: String

  protected abstract val myTitle: String
  protected abstract val myBlocks: List<FeedbackBlock>

  internal val mySystemInfoDataComputation = SystemInfoDataComputation { computeSystemInfoData() }

  /**
   * Returns the computed system data, awaiting the result if necessary.
   *
   * If accessed on the EDT, awaits the computation result under a modal progress.
   * Otherwise, blocks the current thread to await.
   *
   * Guaranteed to always return the same instance,
   * so can be used to update the already computed data later,
   * in exotic cases.
   *
   * @see computeSystemInfoData
   */
  protected val myComputedSystemData: T
    get() = mySystemInfoDataComputation.getComputationResult()

  private val myJsonConverter = Json { prettyPrint = true }

  private val mySystemInfoJsonName: String = "system_info"

  private val myNoEmailAgreementBlock: NoEmailAgreementBlock = NoEmailAgreementBlock(myProject) {
    showFeedbackSystemInfoDialog(mySystemInfoDataComputation.getComputationResult())
  }

  init {
    isResizable = false
  }

  /**
   * Computes the system data to send along with the feedback.
   *
   * There are no guarantees about the context it's invoked in,
   * so the implementations must take care to switch to the correct context if necessary.
   *
   * Is guaranteed to be invoked only once, when the dialog is shown.
   * The computation result may be accessed with [myComputedSystemData],
   * though normally it isn't necessary.
   */
  protected abstract suspend fun computeSystemInfoData(): T

  /**
   * Shows the dialog displaying the computed system data.
   *
   * The system data to show is passed as the [systemInfoData] parameter.
   */
  protected abstract fun showFeedbackSystemInfoDialog(systemInfoData: T)

  fun setTitle() {
    title = myTitle
  }

  override fun createCenterPanel(): JComponent {
    setTitle()

    val mainPanel = panel {
      for (block in myBlocks) {
        block.addToPanel(this)
      }

      addFooterToPanel(this)
    }.also { dialog ->
      dialog.border = JBEmptyBorder(0, 10, 0, 10)
    }

    val scrollablePane = JBScrollPane(
      mainPanel,
      JBScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
      JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER
    ).apply {
      border = JBUI.Borders.empty()
    }

    return panel {
      row {
        cell(scrollablePane)
          .align(Align.FILL)
      }.resizableRow()
    }.apply {
      registerIntegratedPanel(mainPanel)
      val owner = this
      launchOnShow("${javaClass.name}.mySystemInfoDataComputation") {
        mySystemInfoDataComputation.compute(coroutineScope = this, owner = owner)
      }
    }
  }

  protected open fun addFooterToPanel(panel: Panel) {
    myNoEmailAgreementBlock.addToPanel(panel)
  }

  override fun doOKAction() {
    // Because the computation is tied to the dialog's lifetime, make sure it's finished before we continue.
    // Among other things, it allows async feedback submission, after the dialog is already closed.
    mySystemInfoDataComputation.awaitComputationOnEDT()
    super.doOKAction()
    sendFeedbackData()
  }

  protected open fun sendFeedbackData() {
    val feedbackData = FeedbackRequestData(myFeedbackReportId, collectDataToJsonObject())
    submitFeedback(feedbackData,
                   { showThanksNotification() },
                   { },
                   if (myForTest) FeedbackRequestType.TEST_REQUEST else FeedbackRequestType.PRODUCTION_REQUEST)
  }

  protected open fun showThanksNotification() {

  }

  protected fun collectDataToJsonObject(): JsonObject {
    return buildJsonObject {
      put(FEEDBACK_REPORT_ID_KEY, myFeedbackReportId)
      put(myFeedbackJsonVersionKey, myFeedbackJsonVersion)

      for (block in myBlocks) {
        if (block is JsonDataProvider) {
          block.collectBlockDataToJson(this)
        }
      }

      put(mySystemInfoJsonName, mySystemInfoDataComputation.getComputationResult().serializeToJson(myJsonConverter))
    }
  }

  override fun createActions(): Array<Action> {
    return arrayOf(cancelAction, okAction)
  }

  override fun getOKAction(): Action {
    return object : OkAction() {
      init {
        putValue(NAME, CommonFeedbackBundle.message("dialog.feedback.ok.label"))
      }
    }
  }

  override fun getCancelAction(): Action {
    val cancelAction = super.getCancelAction()
    cancelAction.putValue(Action.NAME, CommonFeedbackBundle.message("dialog.feedback.cancel.label"))
    return cancelAction
  }
}

internal class SystemInfoDataComputation<T : SystemDataJsonSerializable>(
  private val computeSystemInfoData: suspend () -> T,
) {

  private data class ComputationJob<T : SystemDataJsonSerializable>(val job: Deferred<T>, val owner: JComponent)
  private var computationJob = AtomicReference<ComputationJob<T>?>()
  private var computedResult = AtomicReference<T?>()

  fun compute(coroutineScope: CoroutineScope, owner: JComponent) {
    computationJob.set(
      ComputationJob(
        job = coroutineScope.async {
          withContext(Dispatchers.Default) {
            val result = computeSystemInfoData()
            computedResult.set(result)
            result
          }
        },
        owner = owner,
      )
    )
  }

  fun getComputationResult(): T {
    val computedResult = computedResult.get()
    if (computedResult != null) return computedResult
    val computationJob = computationJob.get()
    if (computationJob == null) {
      throw IllegalStateException(
        "Should be called when the dialog is showing, but computationJob == null, which likely means that it was never shown"
      )
    }
    return if (EDT.isCurrentThreadEdt()) {
      runWithModalProgressBlocking(ModalTaskOwner.component(computationJob.owner), CommonFeedbackBundle.message("dialog.feedback.computing.system.info")) {
        computationJob.job.await()
      }
    }
    else {
      runBlockingMaybeCancellable {
        computationJob.job.await()
      }
    }
  }

  fun awaitComputationOnEDT() {
    getComputationResult()
  }
}
