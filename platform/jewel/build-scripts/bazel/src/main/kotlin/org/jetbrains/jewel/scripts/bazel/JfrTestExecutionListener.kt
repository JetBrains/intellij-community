package org.jetbrains.jewel.scripts.bazel

import java.io.File
import jdk.jfr.Configuration
import jdk.jfr.Recording
import org.junit.platform.engine.TestExecutionResult
import org.junit.platform.launcher.TestExecutionListener
import org.junit.platform.launcher.TestIdentifier

/**
 * Opens one JFR recording per test method as it starts, and closes it as the test finishes -- gives one .jfr
 * file per test method from a single test run, with no test discovery/dry-run step needed at all.
 *
 * Time-sampled event types (`jdk.ExecutionSample`, `jdk.CPULoad`, GC events) are tied to a shared periodic-tick
 * clock rather than to each recording's own lifetime, so a recording only a few tens of milliseconds long (typical
 * for a single test method) can easily start and stop between ticks and end up with none of them.
 * `jdk.CPULoad` specifically has a fixed 1000ms period under the "profile" template, so it will never appear
 * in a recording shorter than that. This is a JFR platform characteristic, not something fixable by recording
 * configuration: expect time/CPU/GC-sampled metrics to read as empty for most test methods in this mode.
 * Threshold-triggered events such as `jdk.ObjectAllocationSample` aren't affected the same way and remain
 * meaningful.
 */
internal class JfrTestExecutionListener(private val outputDir: File, private val fileNameSuffix: String = "") :
    TestExecutionListener {
    private val recordings = mutableMapOf<String, Recording>()
    private val profileConfiguration = Configuration.getConfiguration("profile")

    override fun executionStarted(testIdentifier: TestIdentifier) {
        if (!testIdentifier.isTest) return

        val recording = Recording(profileConfiguration)
        recording.isToDisk = true
        recording.destination = File(outputDir, "${sanitize(testIdentifier.displayName)}$fileNameSuffix.jfr").toPath()
        recording.start()
        recordings[testIdentifier.uniqueId] = recording
    }

    override fun executionFinished(testIdentifier: TestIdentifier, testExecutionResult: TestExecutionResult) {
        if (!testIdentifier.isTest) return

        recordings.remove(testIdentifier.uniqueId)?.let {
            it.stop()
            it.close()
        }
    }
}

internal fun sanitize(name: String): String = name.replace(Regex("[^a-zA-Z0-9_.-]"), "_")
