// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl

import com.intellij.internal.statistic.FUCollectorTestCase
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.service.fus.collectors.CounterUsageCollectorEP
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.internal.statistic.service.fus.collectors.UsageCollectors.COUNTER_EP_NAME
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.platform.ml.Environment
import com.intellij.platform.ml.Feature
import com.intellij.platform.ml.ObsoleteTierDescriptor
import com.intellij.platform.ml.impl.MLTaskApproach.Companion.startMLSession
import com.intellij.platform.ml.impl.apiPlatform.CodeLikePrinter
import com.intellij.platform.ml.impl.apiPlatform.ReplaceableIJPlatform
import com.intellij.platform.ml.impl.logs.EntireSessionLoggingStrategy
import com.intellij.platform.ml.impl.logs.MLEventLoggerProvider.Companion.ML_RECORDER_ID
import com.intellij.platform.ml.impl.logs.registerMLTaskLogging
import com.intellij.platform.ml.impl.monitoring.MLTaskGroupListener
import com.intellij.platform.ml.with
import com.intellij.util.application
import com.jetbrains.fus.reporting.model.lion3.LogEvent
import kotlinx.coroutines.runBlocking
import java.util.function.Consumer

object MockDumbTask : DumbMLTask(
  name = "mock",
  levels = listOf(
    setOf(TierCompletionSession),
    setOf(TierLookup),
    setOf(TierItem)
  ),
  callParameters = listOf(
    setOf(),
    setOf(),
    setOf()
  )
)


private object DumbModeApiPlatform : TestApiPlatform() {
  override val tierDescriptors = listOf(
    CompletionSessionFeatures(),
    ItemFeatures1(),
    GitFeatures1(),
  )

  override val environmentExtenders = listOf(
    GitInformant(),
  )

  override val taskApproaches: List<MLTaskApproachBuilder<*>> = listOf(
    DumbApproachBuilder(MockDumbTask)
  )

  override val initialTaskListeners: List<MLTaskGroupListener> = listOf(
    SomeListener("Nika"),
    SomeListener("Alex"),
  )


  override fun manageNonDeclaredFeatures(descriptor: ObsoleteTierDescriptor, nonDeclaredFeatures: Set<Feature>) {
    val printer = CodeLikePrinter()
    println("$descriptor is missing the following declaration: ${printer.printCodeLikeString(nonDeclaredFeatures.map { it.declaration })}")
  }
}

private class DumbTaskFusLogger : CounterUsagesCollector() {
  companion object {
    val GROUP = EventLogGroup("dumb-task", 1, ML_RECORDER_ID).also {
      it.registerMLTaskLogging<DumbMLModel, Unit>("finished", MockDumbTask, EntireSessionLoggingStrategy.UNIT)
    }
  }

  override fun getGroup() = GROUP
}

class DumbTask : MLApiLogsTestCase() {
  fun `test dumb ml task`() {
    // After the session is finished, it will be logged to community/platform/ml-impl/testResources/dumb_ml_logs.js

    val logs: MutableList<Pair<String, Map<String, Any>>> = mutableListOf()
    val collectLogs = Consumer { fusLog: LogEvent ->
      logs.add(fusLog.event.id to fusLog.event.data)
    }

    ReplaceableIJPlatform.replacingWith(DumbModeApiPlatform) {
      registerEventLogger(DumbTaskFusLogger())

      val loggerEP = CounterUsageCollectorEP()
      application.extensionArea.getExtensionPoint(COUNTER_EP_NAME).registerExtension(loggerEP, this.testRootDisposable)

      FUCollectorTestCase.listenForEvents(ML_RECORDER_ID, this.testRootDisposable, collectLogs) {

        repeat(3) { sessionIndex ->

          println("Demo session #$sessionIndex has started")

          runBlocking {

            val startOutcome = MockDumbTask.startMLSession(
              callParameters = Environment.of(),
              permanentSessionEnvironment = Environment.of(
                TierCompletionSession with CompletionSession(
                  language = PlainTextLanguage.INSTANCE,
                  callOrder = 1,
                  completionType = CompletionType.SMART
                )
              )
            )

            val completionSession = startOutcome.session ?: return@runBlocking
            completionSession.withNestedDumbSessions { lookupSessionCreator ->

              lookupSessionCreator.nestConsidering(Environment.of(), Environment.of(TierLookup with LookupImpl(true, 1)))
                .withConsiderations {
                  it.consider(Environment.of(), Environment.of(TierItem with LookupItem("hello", emptyMap())))
                  it.consider(Environment.of(), Environment.of(TierItem with LookupItem("world", emptyMap())))
                }

              lookupSessionCreator.nestConsidering(Environment.of(), Environment.of(TierLookup with LookupImpl(false, 2)))
                .withConsiderations {
                  it.consider(Environment.of(), Environment.of(TierItem with LookupItem("hello!!!", mapOf("bold" to true))))
                  it.consider(Environment.of(), Environment.of(TierItem with LookupItem("AAAAA!!", mapOf("strikethrough" to true))))
                  it.consider(Environment.of(), Environment.of(TierItem with LookupItem("AAAAAAAAAAAAAAAAA", mapOf("cursive" to true))))
                }
            }
          }

          Thread.sleep(3 * 1000)

          println("Demo session #$sessionIndex has finished")
        }
      }
    }

    //val jsonSaver = MLLogsToJsonSaver(Path.of(".") / "testResources")
    //jsonSaver.save(logs, "ml_logs_dumb")
  }
}
