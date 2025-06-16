// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.jcef.test.cases

import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import org.cef.CefApp
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Stream
import javax.swing.JFrame
import javax.swing.SwingUtilities
import javax.swing.Timer
import kotlin.jvm.optionals.getOrDefault
import kotlin.jvm.optionals.getOrNull

private class CefProcess() {
  data class ProcessInfo(
    val processType: ProcessType,
    val pid: Long,
    val commandLine: String,
    val cpuUsage: Double?, // null if unknown yet
    val lastTimeMs: Long,
    val lastCpuTimeMs: Long,
  )

  enum class ProcessType {
    MAIN, RENDERER, GPU, NETWORK, STORAGE, UTILITY_OTHER, ZYGOTE, UNKNOWN
  }

  private val knowProcess = mutableMapOf<Long, ProcessInfo>() // PID to ProcessInfo

  fun getProcesses(): List<ProcessInfo> {
    val allCefProcesses = getAllSubprocessesRecursively(ProcessHandle.current()).toList()
    val cpuTime = getProcessCpuTime(allCefProcesses)
    val processes =
      allCefProcesses
        .filter { isCefProcess(it.info().command().getOrDefault("")) }
        .map { process ->
          val pid = process.pid()
          val knownProcess = knowProcess[pid]
          val commandLine = knownProcess?.commandLine ?: getCommandLine(process)
          val type = knownProcess?.processType ?: when {
            !commandLine.contains("--type=") -> ProcessType.MAIN
            commandLine.contains("--type=renderer") -> ProcessType.RENDERER
            commandLine.contains("--type=gpu-process") -> ProcessType.GPU
            commandLine.contains("--utility-sub-type=network.mojom.NetworkService") -> ProcessType.NETWORK
            commandLine.contains("--utility-sub-type=storage.mojom.StorageService") -> ProcessType.STORAGE
            commandLine.contains("--type=utility") -> ProcessType.UTILITY_OTHER
            commandLine.contains("--type=zygote") -> ProcessType.ZYGOTE
            else -> ProcessType.UNKNOWN
          }

          val cpuTimeNowMs = cpuTime[pid] ?: return@map null
          val timeNowMs = System.currentTimeMillis()

          val cpuUsage = knownProcess?.let {
            (cpuTimeNowMs - it.lastCpuTimeMs) * 100.0 / (timeNowMs - it.lastTimeMs)
          }
          ProcessInfo(
            type,
            pid,
            commandLine,
            cpuUsage,
            timeNowMs,
            cpuTimeNowMs)
            .also { knowProcess[pid] = it }
        }
        .filterNotNull()
        .toList()

    return processes
  }

  companion object {
    private fun isCefProcess(commandLine: String): Boolean {
      return when {
        CefApp.isRemoteEnabled() -> commandLine.contains("cef_server")
        SystemInfo.isMac -> commandLine.contains("jcef Helper")
        SystemInfo.isWindows -> commandLine.contains("jcef_helper.exe")
        SystemInfo.isLinux -> commandLine.contains("jcef_helper")
        else -> false
      }
    }

    fun getAllSubprocessesRecursively(process: ProcessHandle): Stream<ProcessHandle> {
      return Stream.concat(
        process.children(),
        process.children().flatMap { getAllSubprocessesRecursively(it) } // Recursive traversal
      )
    }

    fun getProcessCpuTime(processes: List<ProcessHandle>): Map<Long, Long> {
      return when {
        SystemInfo.isMac -> {
          ProcessBuilder("ps", "-p", processes.map { it.pid() }.joinToString(","), "-o", "pid=,time=").start()
            .inputStream.bufferedReader().use { it.readText().trim() }
            .trim()
            .lines()
            .map(String::trim)
            .map { it.split("\\s+".toRegex()).filter { !it.isEmpty() } }
            .associate { it[0].toLong() to parseTime(it[1]) }
        }
        SystemInfo.isWindows -> {
          processes.associate { Pair(it.pid(), it.info().totalCpuDuration().getOrNull()?.toMillis() ?: 0L) }
        }
        SystemInfo.isLinux -> {
          processes.associate { Pair(it.pid(), it.info().totalCpuDuration().getOrNull()?.toMillis() ?: 0L) }
        }
        else -> mapOf()
      }
    }

    private fun getCommandLine(processHandle: ProcessHandle): String {
      if (SystemInfo.isMac) {
        return processHandle.info().commandLine().getOrDefault("") +
               processHandle.info().arguments().getOrDefault(emptyArray())?.joinToString(" ")
      }
      else if (SystemInfo.isWindows) {
        try {
          ProcessBuilder(
            "powershell",
            "-Command",
            // Retrieves the CommandLine for the given ProcessId in a compact format
            "Get-CimInstance Win32_Process -Filter \"ProcessId=${processHandle.pid()}\" | Select-Object -ExpandProperty CommandLine"
          ).start().inputStream.bufferedReader().use { reader ->
            return reader.readText().trim()
          }
        }
        catch (_: Exception) {
          return ""
        }
      }
      else if (SystemInfo.isLinux) {
        try {
          return Files.readAllLines(Path.of("/proc", processHandle.pid().toString(), "cmdline"))
            .joinToString("\n")
            .split('\u0000')
            .filter { it.isNotEmpty() }
            .joinToString(" ")
        }
        catch (_: Exception) {
          return ""
        }
      }



      return ""
    }

    fun parseTime(time: String): Long {
      if (time.contains("-")) {
        val (days, rest) = time.split("-")
        return days.toLong() * 24 * 3600 * 1000 + parseTime(rest)
      }

      val values = time.split(":")
      var result = 0L
      for (value in values) {
        result *= 60
        result += (value.toDouble() * 1000).toLong()
      }
      return result
    }
  }
}

internal fun showCpuUsage() {
  SwingUtilities.invokeLater {
    val frame = JFrame("JCEF CPU Usage")
    val chart = PlotlyChart()
    frame.add(chart.getComponent())
    frame.defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
    frame.isVisible = true
    frame.setSize(800, 600)
    frame.addWindowListener(object : java.awt.event.WindowAdapter() {
      override fun windowClosing(e: java.awt.event.WindowEvent) {
        Disposer.dispose(chart)
      }
    })

    val cefProcesses = CefProcess()
    val traceIndexes = mutableMapOf<Long, Int>()
    var nextTraceIndex = 1

    val startTime = System.currentTimeMillis()

    chart.newPlot(
      data =
        """
        [
          {
            x: [],
            y: [],
            mode: 'lines',
            name: 'Total',
            line: { color: 'rgba(255, 99, 132, 1)' },
          }
        ]
        """.trimIndent(),
      layout = """
        {
          title: 'CPU Usage',
          xaxis: { title: 'Time, s' },
          yaxis: { title: 'CPU usage, %' },
          showlegend: true,
          legend: {
            orientation: 'h',
            y: -0.2,
            x: 0.5,
            xanchor: 'center',
          },
          dragmode: 'pan',
        }
        """.trimMargin()
    )

    val timer = Timer(1000) {
      val processes = cefProcesses.getProcesses()
      var total = 0.0

      val timeNow = (System.currentTimeMillis() - startTime) / 1000.0
      for (process in processes) {
        if (process.cpuUsage == null) continue
        total += process.cpuUsage
        if (!traceIndexes.containsKey(process.pid)) {
          traceIndexes[process.pid] = nextTraceIndex++
          chart.addTraces(
            """
              {
                x: [${timeNow}],
                y: [${process.cpuUsage}],
                mode: 'lines',
                name: '${process.processType}(${process.pid})'
              }
            """.trimIndent()
          )
        }
        else {
          chart.extendTraces(
            """
              {
                x: [[${timeNow}]],
                y: [[${process.cpuUsage}]]
              }
          """.trimIndent(),
            "[${traceIndexes[process.pid]}]"
          )
        }
      }

      chart.extendTraces(
        """
          {
            x: [[${timeNow}]],
            y: [[${total}]]
          }
        """.trimIndent(),
        "[0]"
      )
    }

    timer.isRepeats = true
    timer.start()
  }
}