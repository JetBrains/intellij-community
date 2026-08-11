// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.unscramble

import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull


@TestApplication
internal class IntellijDiagnosticCoroutineDumpImportTest : AbstractThreadDumpImportTest() {

  @Test
  fun `parse-diagnostic-intellij-coroutines-dump-1`() {
    val dumpText = loadThreadDump("intellijCoroutineDump/threadDump-2.txt")

    val parsedThreadDump = requireNotNull(parseIntelliJThreadDump(dumpText))
    val coroutines = parsedThreadDump.threadStates.filter { it.type == IntelliJThreadDumpMetadata.COROUTINE_TYPE }
    assert(coroutines.size == 8)
    assertEquals(
      "-[x1 of] Dumped Coroutines\n" +
      "  -[x1 of] \"com.intellij.platform.rpc.topics.frontend.FrontendRemoteTopicListenersRegistry\":StandaloneCoroutine{Active}\n" +
      "  -[x9 of] \"com.intellij.platform.ide.CoreUiCoroutineScopeHolder\":StandaloneCoroutine{Active}\n" +
      "    -[x1 of] \"com.intellij.platform.ide.CoreUiCoroutineScopeHolder\":ProducerCoroutine{Active}\n" +
      "      -[x1 of] \"com.intellij.platform.ide.CoreUiCoroutineScopeHolder\":ProducerCoroutine{Active}\n" +
      "        -[x1 of] \"com.intellij.platform.ide.CoreUiCoroutineScopeHolder\":StandaloneCoroutine{Active}\n" +
      "          -[x7 of] \"com.intellij.platform.ide.CoreUiCoroutineScopeHolder\":ProducerCoroutine{Active}\n" +
      "          -[x7 of] \"com.intellij.platform.ide.CoreUiCoroutineScopeHolder\":ProducerCoroutine{Active}\n" +
      "    -[x7 of] \"com.intellij.execution.impl.InvisibleHyperlinkHintManager\":supervisor:ChildScope{Active}\n",
      printThreadStateTree(parsedThreadDump.threadStates)
    )
    val context = "Kernel@h8q0ivt7115gkj7eqdv0, Rete(abortOnError=false, commands=capacity=2147483647,data=[onReceive], reteState=kotlinx.coroutines.flow.StateFlowImpl@7ea37dbf, dbSource=ReteDbSource(reteState=kotlinx.coroutines.flow.StateFlowImpl@7ea37dbf)), DbSourceContextElement(kernel Kernel@h8q0ivt7115gkj7eqdv0), ComponentManager(ProjectImpl@884232594), com.intellij.codeWithMe.ClientIdContextElementPrecursor, Dispatchers.Default"
    assertEquals(6, coroutines.count { it.metadata["dispatcher"] == context })
    assertEquals("- \"com.intellij.platform.rpc.topics.frontend.FrontendRemoteTopicListenersRegistry\":StandaloneCoroutine{Active}, state: SUSPENDED [Kernel@h8q0ivt7115gkj7eqdv0, Rete(abortOnError=false, commands=capacity=2147483647,data=[onReceive], reteState=kotlinx.coroutines.flow.StateFlowImpl@7ea37dbf, dbSource=ReteDbSource(reteState=kotlinx.coroutines.flow.StateFlowImpl@7ea37dbf)), DbSourceContextElement(kernel Kernel@h8q0ivt7115gkj7eqdv0), ComponentManager(ApplicationImpl@2013013843), com.intellij.codeWithMe.ClientIdContextElementPrecursor, Dispatchers.Default]\n" +
                 "\tat kotlinx.coroutines.channels.BufferedChannel.emitAllInternal\$kotlinx_coroutines_core(BufferedChannel.kt:1580)", coroutines[0].stackTrace)
    assertEquals("-[x9 of] \"com.intellij.platform.ide.CoreUiCoroutineScopeHolder\":StandaloneCoroutine{Active}, state: SUSPENDED [Kernel@h8q0ivt7115gkj7eqdv0, Rete(abortOnError=false, commands=capacity=2147483647,data=[onReceive], reteState=kotlinx.coroutines.flow.StateFlowImpl@7ea37dbf, dbSource=ReteDbSource(reteState=kotlinx.coroutines.flow.StateFlowImpl@7ea37dbf)), DbSourceContextElement(kernel Kernel@h8q0ivt7115gkj7eqdv0), ComponentManager(ProjectImpl@884232594), com.intellij.codeWithMe.ClientIdContextElementPrecursor, Dispatchers.Default]\n" +
                 "\tat kotlinx.coroutines.channels.BufferedChannel.emitAllInternal\$kotlinx_coroutines_core(BufferedChannel.kt:1580)\n" +
                 "\tat kotlinx.coroutines.flow.FlowKt__ChannelsKt.emitAllInternal(Channels.kt:44)\n" +
                 "\tat kotlinx.coroutines.flow.FlowKt__ChannelsKt.emitAllInternal(Channels.kt:47)\n" +
                 "\tat kotlinx.coroutines.flow.FlowKt__ChannelsKt.emitAllImpl\$FlowKt__ChannelsKt(Channels.kt:32)\n" +
                 "\tat kotlinx.coroutines.flow.internal.ChannelFlow\$collect\$2.invokeSuspend(ChannelFlow.kt:119)\n" +
                 "\tat com.intellij.openapi.editor.impl.EditorMarkupModelImpl\$7.invokeSuspend(EditorMarkupModelImpl.kt:343)", coroutines[1].stackTrace)
    assertEquals("- \"com.intellij.platform.ide.CoreUiCoroutineScopeHolder\":ProducerCoroutine{Active}, state: SUSPENDED [Kernel@h8q0ivt7115gkj7eqdv0, Rete(abortOnError=false, commands=capacity=2147483647,data=[onReceive], reteState=kotlinx.coroutines.flow.StateFlowImpl@7ea37dbf, dbSource=ReteDbSource(reteState=kotlinx.coroutines.flow.StateFlowImpl@7ea37dbf)), DbSourceContextElement(kernel Kernel@h8q0ivt7115gkj7eqdv0), ComponentManager(ProjectImpl@884232594), com.intellij.codeWithMe.ClientIdContextElementPrecursor, Dispatchers.Default]\n" +
                 "\tat kotlinx.coroutines.channels.BufferedChannel.emitAllInternal\$kotlinx_coroutines_core(BufferedChannel.kt:1580)\n" +
                 "\tat kotlinx.coroutines.flow.FlowKt__ChannelsKt.emitAllInternal(Channels.kt:44)\n" +
                 "\tat kotlinx.coroutines.flow.internal.ChannelFlow\$collectToFun\$1.invokeSuspend(ChannelFlow.kt:56)", coroutines[2].stackTrace)
    assertEquals("- \"com.intellij.platform.ide.CoreUiCoroutineScopeHolder\":ProducerCoroutine{Active}, state: SUSPENDED [Kernel@h8q0ivt7115gkj7eqdv0, Rete(abortOnError=false, commands=capacity=2147483647,data=[onReceive], reteState=kotlinx.coroutines.flow.StateFlowImpl@7ea37dbf, dbSource=ReteDbSource(reteState=kotlinx.coroutines.flow.StateFlowImpl@7ea37dbf)), DbSourceContextElement(kernel Kernel@h8q0ivt7115gkj7eqdv0), ComponentManager(ProjectImpl@884232594), com.intellij.codeWithMe.ClientIdContextElementPrecursor, Dispatchers.Default]\n" +
                 "\tat kotlinx.coroutines.channels.BufferedChannel.emitAllInternal\$kotlinx_coroutines_core(BufferedChannel.kt:1580)\n" +
                 "\tat kotlinx.coroutines.flow.FlowKt__ChannelsKt.emitAllInternal(Channels.kt:44)\n" +
                 "\tat kotlinx.coroutines.flow.internal.ChannelFlow\$collectToFun\$1.invokeSuspend(ChannelFlow.kt:56)", coroutines[3].stackTrace)
  }

  @Test
  fun `parse-diagnostic-intellij-threadDump-with-coroutines`() {
    val dumpText = loadThreadDump("intellijCoroutineDump/threadDump-1.txt")

    val parsedThreadDump = requireNotNull(parseIntelliJThreadDump(dumpText))
    val coroutines = parsedThreadDump.threadStates.filter { it.type == IntelliJThreadDumpMetadata.COROUTINE_TYPE }
    assert(coroutines.size == 112)
    val dumpedThreadStates = printThreadStateTree(parsedThreadDump.threadStates)
    assertEquals("-[x1 of] DebuggerManagerThread\n" +
                 "-[x1 of] HttpClient-19-SelectorManager\n" +
                 "-[x1 of] HttpClient-2-SelectorManager\n" +
                 "-[x1 of] HttpClient-360-SelectorManager\n" +
                 "-[x1 of] HttpClient-377-SelectorManager\n" +
                 "-[x1 of] HttpClient-398-SelectorManager\n" +
                 "-[x1 of] HttpClient-6-SelectorManager\n" +
                 "-[x1 of] HttpClient-7-SelectorManager\n" +
                 "-[x1 of] ForkJoinPool.commonPool-delayScheduler\n" +
                 "-[x1 of] Reference Handler\n" +
                 "-[x1 of] AWT-AppKit\n" +
                 "-[x1 of] Notification Thread\n" +
                 "-[x1 of] Signal Dispatcher\n" +
                 "-[x1 of] Dumped Coroutines\n" +
                 "  -[x1 of] BlockingCoroutine{Active}@2c9a4996\n" +
                 "    -[x1 of] StandaloneCoroutine{Active}\n" +
                 "      -[x1 of] \"Transactor loop Kernel@h8q0ivt7115gkj7eqdv0\":StandaloneCoroutine{Active}\n" +
                 "      -[x1 of] \"transactor log collector\":StandaloneCoroutine{Active}\n" +
                 "      -[x1 of] \"rete event loop\":StandaloneCoroutine{Active}\n" +
                 "        -[x1 of] \"rete event loop\":ProducerCoroutine{Active}\n" +
                 "          -[x1 of] \"rete event loop\":ProducerCoroutine{Active}\n" +
                 "    -[x1 of] \"Application\":supervisor:ChildScope{Active}\n" +
                 "      -[x1 of] \"ApplicationImpl@2013013843 container\":supervisor:ChildScope{Active}\n" +
                 "        -[x2 of] \"(ApplicationImpl@2013013843 x org.jetbrains.plugins.yaml)\":supervisor:ChildScope{Active}\n" +
                 "        -[x1 of] \"(ApplicationImpl@2013013843 x org.jetbrains.plugins.textmate)\":supervisor:ChildScope{Active}\n" +
                 "          -[x1 of] \"org.jetbrains.plugins.textmate.TextMateServiceImpl\":supervisor:ChildScope{Active}\n" +
                 "        -[x1 of] \"(ApplicationImpl@2013013843 x org.jetbrains.plugins.terminal)\":supervisor:ChildScope{Active}\n" +
                 "          -[x1 of] \"(ProjectImpl@165022960 x (ApplicationImpl@2013013843 x org.jetbrains.plugins.terminal))\":supervisor:ChildScope{Active}\n" +
                 "          -[x1 of] \"org.jetbrains.plugins.terminal.TerminalOptionsProvider\":supervisor:ChildScope{Active}\n" +
                 "          -[x1 of] \"(ProjectImpl@884232594 x (ApplicationImpl@2013013843 x org.jetbrains.plugins.terminal))\":supervisor:ChildScope{Active}\n" +
                 "          -[x1 of] \"org.jetbrains.plugins.terminal.TerminalFontSettingsService\":supervisor:ChildScope{Active}\n" +
                 "          -[x1 of] \"org.jetbrains.plugins.terminal.block.completion.ShellCommandSpecsManagerImpl\":supervisor:ChildScope{Active}\n" +
                 "          -[x1 of] \"(ProjectImpl@1342778193 x (ApplicationImpl@2013013843 x org.jetbrains.plugins.terminal))\":supervisor:ChildScope{Active}\n" +
                 "        -[x2 of] \"(ApplicationImpl@2013013843 x org.jetbrains.plugins.terminal)\":supervisor:ChildScope{Active}\n" +
                 "        -[x2 of] \"(ApplicationImpl@2013013843 x com.intellij.properties)\":supervisor:ChildScope{Active}\n" +
                 "        -[x1 of] \"(ApplicationImpl@2013013843 x com.intellij.platform.images)\":supervisor:ChildScope{Active}\n" +
                 "        -[x1 of] \"(ApplicationImpl@2013013843 x com.intellij.platform.acp)\":supervisor:ChildScope{Active}\n" +
                 "        -[x2 of] \"(ApplicationImpl@2013013843 x com.intellij.modules.json)\":supervisor:ChildScope{Active}\n" +
                 "        -[x7 of] \"(ApplicationImpl@2013013843 x JavaScript)\":supervisor:ChildScope{Active}\n" +
                 "        -[x10 of] \"(ApplicationImpl@2013013843 x com.intellij.java)\":supervisor:ChildScope{Active}\n" +
                 "        -[x2 of] \"(ApplicationImpl@2013013843 x com.intellij.dfa.analysis)\":supervisor:ChildScope{Active}\n" +
                 "        -[x1 of] \"(ApplicationImpl@2013013843 x com.intellij.copyright)\":supervisor:ChildScope{Active}\n" +
                 "        -[x25 of] \"(ApplicationImpl@2013013843 x com.intellij)\":supervisor:ChildScope{Active}\n" +
                 "        -[x2 of] \"(ApplicationImpl@2013013843 x org.jetbrains.idea.reposearch)\":supervisor:ChildScope{Active}\n" +
                 "        -[x2 of] \"(ApplicationImpl@2013013843 x com.intellij.moduleSet.grid.core)\":supervisor:ChildScope{Active}\n" +
                 "        -[x1 of] \"(ApplicationImpl@2013013843 x com.intellij.database)\":supervisor:ChildScope{Active}\n" +
                 "          -[x1 of] \"com.intellij.database.dataSource.DataSourceModelStorageImpl\$App\":supervisor:ChildScope{Active}\n" +
                 "          -[x1 of] \"com.intellij.database.settings.QueryFileAppSettings\":supervisor:ChildScope{Active}\n" +
                 "          -[x1 of] \"(ProjectImpl@165022960 x (ApplicationImpl@2013013843 x com.intellij.database))\":supervisor:ChildScope{Active}\n" +
                 "            -[x1 of] \"com.intellij.database.dataSource.history.DataSourcesHistory\$HistoryInitialization\":ChildScope{Active}\n" +
                 "          -[x1 of] \"com.intellij.database.dataSource.DatabaseConnectionManager\":supervisor:ChildScope{Active}\n" +
                 "          -[x1 of] \"(ProjectImpl@884232594 x (ApplicationImpl@2013013843 x com.intellij.database))\":supervisor:ChildScope{Active}\n" +
                 "            -[x1 of] \"com.intellij.database.dataSource.history.DataSourcesHistory\$HistoryInitialization\":ChildScope{Active}\n" +
                 "          -[x1 of] \"(ProjectImpl@1342778193 x (ApplicationImpl@2013013843 x com.intellij.database))\":supervisor:ChildScope{Active}\n" +
                 "            -[x1 of] \"com.intellij.database.dataSource.history.DataSourcesHistory\$HistoryInitialization\":ChildScope{Active}\n" +
                 "        -[x3 of] \"(ApplicationImpl@2013013843 x com.intellij.database)\":supervisor:ChildScope{Active}\n" +
                 "        -[x2 of] \"(ApplicationImpl@2013013843 x com.jetbrains.gateway)\":supervisor:ChildScope{Active}\n" +
                 "        -[x1 of] \"(ApplicationImpl@2013013843 x com.intellij)\":supervisor:ChildScope{Active}\n" +
                 "          -[x1 of] \"com.intellij.platform.rpc.topics.frontend.FrontendRemoteTopicListenersRegistry\":supervisor:ChildScope{Active}\n" +
                 "            -[x1 of] \"com.intellij.platform.rpc.topics.frontend.FrontendRemoteTopicListenersRegistry\":StandaloneCoroutine{Active}\n" +
                 "              -[x1 of] \"com.intellij.platform.rpc.topics.frontend.FrontendRemoteTopicListenersRegistry\":ProducerCoroutine{Active}\n" +
                 "        -[x2 of] \"(ApplicationImpl@2013013843 x com.jetbrains.sh)\":supervisor:ChildScope{Active}\n" +
                 "        -[x1 of] \"(ApplicationImpl@2013013843 x com.intellij)\":supervisor:ChildScope{Active}\n" +
                 "          -[x1 of] \"com.intellij.ide.RecentProjectsManagerBase\":supervisor:ChildScope{Active}\n" +
                 "            -[x1 of] \"com.intellij.ide.RecentProjectsManagerBase\":StandaloneCoroutine{Active}\n" +
                 "              -[x1 of] \"com.intellij.ide.RecentProjectsManagerBase\":ProducerCoroutine{Active}\n" +
                 "                -[x1 of] \"com.intellij.ide.RecentProjectsManagerBase\":ProducerCoroutine{Active}\n" +
                 "            -[x1 of] \"com.intellij.ide.RecentProjectsManagerBase\":StandaloneCoroutine{Active}\n" +
                 "              -[x1 of] \"com.intellij.ide.RecentProjectsManagerBase\":ProducerCoroutine{Active}\n" +
                 "                -[x1 of] \"com.intellij.ide.RecentProjectsManagerBase\":ProducerCoroutine{Active}\n" +
                 "        -[x1 of] \"(ApplicationImpl@2013013843 x com.intellij)\":supervisor:ChildScope{Active}\n" +
                 "          -[x1 of] \"com.intellij.platform.rpc.backend.impl.RemoteApiRegistry\":supervisor:ChildScope{Active}\n" +
                 "        -[x1 of] \"(ApplicationImpl@2013013843 x com.intellij)\":supervisor:ChildScope{Active}\n" +
                 "          -[x1 of] \"com.intellij.platform.kernel.backend.BackendKernelService\":supervisor:ChildScope{Active}\n" +
                 "            -[x1 of] \"com.intellij.platform.kernel.backend.BackendKernelService\":StandaloneCoroutine{Completing}\n" +
                 "              -[x1 of] \"KernelCoroutineScope\":supervisor:ChildScope{Active}\n" +
                 "                -[x1 of] \"RemoteKernelScope\":supervisor:ChildScope{Active}\n" +
                 "                -[x3 of] \"IdeFrameImpl.restoreBoundsRequests\":ProducerCoroutine{Active}\n" +
                 "                -[x1 of] \"static nav bar vm\":ProducerCoroutine{Active}\n" +
                 "                  -[x1 of] \"static nav bar vm\":StandaloneCoroutine{Active}\n" +
                 "                    -[x1 of] \"static nav bar vm\":StandaloneCoroutine{Active}\n" +
                 "                      -[x1 of] \"static nav bar vm\":ProducerCoroutine{Active}\n" +
                 "                        -[x1 of] \"static nav bar vm\":StandaloneCoroutine{Active}\n" +
                 "                          -[x7 of] \"static nav bar vm\":StandaloneCoroutine{Active}\n" +
                 "                          -[x1 of] \"static nav bar vm\":ProducerCoroutine{Active}\n" +
                 "                -[x3 of] \"static nav bar window\":StandaloneCoroutine{Active}\n" +
                 "                  -[x1 of] \"static nav bar window\":ProducerCoroutine{Active}\n" +
                 "                    -[x1 of] \"static nav bar window\":ProducerCoroutine{Active}\n" +
                 "                -[x3 of] \"static nav bar window\":StandaloneCoroutine{Active}\n" +
                 "                  -[x1 of] \"static nav bar window\":ProducerCoroutine{Active}\n" +
                 "                    -[x1 of] \"static nav bar window\":StandaloneCoroutine{Active}\n" +
                 "                -[x3 of] \"static nav bar window\":StandaloneCoroutine{Active}\n" +
                 "                  -[x1 of] \"static nav bar window\":ProducerCoroutine{Active}\n" +
                 "                -[x3 of] \"static nav bar window\":StandaloneCoroutine{Active}\n" +
                 "                -[x1 of] \"static nav bar vm\":ProducerCoroutine{Active}\n" +
                 "                  -[x1 of] \"static nav bar vm\":StandaloneCoroutine{Active}\n" +
                 "                    -[x1 of] \"static nav bar vm\":StandaloneCoroutine{Active}\n" +
                 "                      -[x1 of] \"static nav bar vm\":ProducerCoroutine{Active}\n" +
                 "                        -[x1 of] \"static nav bar vm\":StandaloneCoroutine{Active}\n" +
                 "                          -[x11 of] \"static nav bar vm\":StandaloneCoroutine{Active}\n" +
                 "                          -[x1 of] \"static nav bar vm\":ProducerCoroutine{Active}\n" +
                 "                -[x1 of] \"static nav bar vm\":ProducerCoroutine{Active}\n" +
                 "                  -[x1 of] \"static nav bar vm\":StandaloneCoroutine{Active}\n" +
                 "                    -[x1 of] \"static nav bar vm\":StandaloneCoroutine{Active}\n" +
                 "                      -[x1 of] \"static nav bar vm\":ProducerCoroutine{Active}\n" +
                 "                        -[x1 of] \"static nav bar vm\":StandaloneCoroutine{Active}\n" +
                 "                          -[x15 of] \"static nav bar vm\":StandaloneCoroutine{Active}\n" +
                 "  -[x1 of] SupervisorJobImpl{Active}\n" +
                 "    -[x1 of] StandaloneCoroutine{Active}\n" +
                 "      -[x2 of] StandaloneCoroutine{Active}\n" +
                 "    -[x1 of] StandaloneCoroutine{Active}\n" +
                 "    -[x1 of] StandaloneCoroutine{Active}\n" +
                 "      -[x1 of] ProducerCoroutine{Active}\n" +
                 "        -[x1 of] StandaloneCoroutine{Active}\n" +
                 "    -[x1 of] StandaloneCoroutine{Active}\n" +
                 "      -[x1 of] ProducerCoroutine{Active}\n" +
                 "    -[x1 of] StandaloneCoroutine{Active}\n" +
                 "  -[x1 of] \"Terminal Session start\":StandaloneCoroutine{Active}\n" +
                 "    -[x1 of] \"Terminal Session start\":ProducerCoroutine{Active}\n" +
                 "  -[x1 of] SupervisorJobImpl{Active}\n" +
                 "    -[x1 of] \"PackageChecker.VulnerableApiService.intellij.database.core.impl\":StandaloneCoroutine{Active}\n" +
                 "      -[x1 of] \"PackageChecker.VulnerableApiService.intellij.database.core.impl\":ProducerCoroutine{Active}\n" +
                 "    -[x1 of] \"PackageChecker.VulnerableApiService.intellij.database.core.impl\":StandaloneCoroutine{Active}\n" +
                 "      -[x1 of] \"PackageChecker.VulnerableApiService.intellij.database.core.impl\":StandaloneCoroutine{Active}\n" +
                 "      -[x1 of] \"PackageChecker.VulnerableApiService.intellij.database.core.impl\":StandaloneCoroutine{Active}\n" +
                 "  -[x1 of] \"indicator watcher\":StandaloneCoroutine{Active}\n" +
                 "-[x1 of] Other diagnostics\n", dumpedThreadStates)
    val coroutine1 = coroutines.find {it.name == "\"com.intellij.platform.rpc.topics.frontend.FrontendRemoteTopicListenersRegistry\":StandaloneCoroutine{Active}"}
    assertNotNull(coroutine1)
    assertEquals("- \"com.intellij.platform.rpc.topics.frontend.FrontendRemoteTopicListenersRegistry\":StandaloneCoroutine{Active}, state: SUSPENDED [Kernel@h8q0ivt7115gkj7eqdv0, Rete(abortOnError=false, commands=capacity=2147483647,data=[onReceive], reteState=kotlinx.coroutines.flow.StateFlowImpl@7ea37dbf, dbSource=ReteDbSource(reteState=kotlinx.coroutines.flow.StateFlowImpl@7ea37dbf)), DbSourceContextElement(kernel Kernel@h8q0ivt7115gkj7eqdv0), ComponentManager(ApplicationImpl@2013013843), com.intellij.codeWithMe.ClientIdContextElementPrecursor, Dispatchers.Default]\n" +
                 "\tat kotlinx.coroutines.channels.BufferedChannel.emitAllInternal\$kotlinx_coroutines_core(BufferedChannel.kt:1580)\n" +
                 "\tat kotlinx.coroutines.flow.FlowKt__ChannelsKt.emitAllInternal(Channels.kt:44)\n" +
                 "\tat kotlinx.coroutines.flow.FlowKt__ChannelsKt.emitAllInternal(Channels.kt:47)\n" +
                 "\tat kotlinx.coroutines.flow.FlowKt__ChannelsKt.emitAllImpl\$FlowKt__ChannelsKt(Channels.kt:32)\n" +
                 "\tat kotlinx.coroutines.flow.internal.ChannelFlow\$collect\$2.invokeSuspend(ChannelFlow.kt:119)\n" +
                 "\tat com.intellij.platform.rpc.topics.frontend.FrontendRemoteTopicListenersRegistry\$1\$1.invokeSuspend(FrontendRemoteTopicListenersRegistry.kt:36)\n" +
                 "\tat fleet.rpc.client.DurableKt\$durable\$2.invokeSuspend(Durable.kt:36)\n" +
                 "\tat fleet.rpc.client.DurableKt.durable(Durable.kt:36)\n" +
                 "\tat com.intellij.platform.rpc.topics.frontend.FrontendRemoteTopicListenersRegistry\$1.invokeSuspend(FrontendRemoteTopicListenersRegistry.kt:35)", coroutine1.stackTrace)
    val otherDiagnostic = parsedThreadDump.threadStates.find { it.name == "Other diagnostics" }
    assertEquals("---------- ProgressIndicator dump ----------\n" +
                 "Indicator JobDependentIndicator(JobImpl{Active}@4bb2aaed) (canceled: false, running:true) corresponds to the following threads:\n" +
                 " - Thread[#5333,ApplicationImpl pooled thread 617,4,main];\n" +
                 "Indicator com.intellij.openapi.progress.EmptyProgressIndicator@752c9d61 (canceled: false, running:true) corresponds to the following threads:\n" +
                 " - Thread[#5333,ApplicationImpl pooled thread 617,4,main];\n" +
                 "Indicator com.intellij.openapi.progress.EmptyProgressIndicator@3e1a4a78 (canceled: false, running:true) corresponds to the following threads:\n" +
                 " - Thread[#3856,DebuggerManagerThread,4,main];\n", otherDiagnostic?.stackTrace)
  }

  @Test
  fun `parse-intellij-failed-dump`() {
    val dumpText = listOf(
      "---------- Coroutine dump ----------",
      "Coroutine dump has failed: boom",
      "Please report this issue to the developers.",
    ).joinToString("\n")

    val dumpItems = requireNotNull(parseIntelliJThreadDump(dumpText)).dumpItems()

    assertThat(dumpItems.map { it.name }).containsExactly("Coroutine dump")
  }
}
