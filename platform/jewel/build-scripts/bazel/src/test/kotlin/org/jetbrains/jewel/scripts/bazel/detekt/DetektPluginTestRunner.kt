package org.jetbrains.jewel.scripts.bazel.detekt

import java.io.PrintWriter
import kotlin.system.exitProcess
import org.junit.platform.engine.discovery.DiscoverySelectors.selectClass
import org.junit.platform.engine.discovery.DiscoverySelectors.selectPackage
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder
import org.junit.platform.launcher.core.LauncherFactory
import org.junit.platform.launcher.listeners.SummaryGeneratingListener

// Run it like: bazel test //platform/jewel/build-scripts/bazel:detektRulesTest
fun main(args: Array<String>) {
    val selectors =
        if (args.isEmpty()) {
            listOf(selectPackage("org.jetbrains.jewel.scripts.bazel.detekt.rules"))
        } else {
            args.map { selectClass(it) }
        }

    val request = LauncherDiscoveryRequestBuilder.request().selectors(*selectors.toTypedArray()).build()

    val summary = SummaryGeneratingListener()
    LauncherFactory.create().apply {
        registerTestExecutionListeners(summary)
        execute(request)
    }

    summary.summary.printTo(PrintWriter(System.out))
    summary.summary.printFailuresTo(PrintWriter(System.out))

    if (summary.summary.totalFailureCount > 0) exitProcess(1)
}
