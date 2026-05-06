package com.intellij.platform.lsp.impl.logging

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.application
import com.intellij.util.io.sanitizeFileName
import kotlinx.coroutines.CoroutineScope
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.div
import org.jetbrains.annotations.TestOnly
import kotlin.math.min

private val LOG = logger<LanguageServiceLoggerService>()

/**
 * This service manages [LanguageServiceLogger] instances.
 * [LanguageServiceLogger] logs communication between the IDE and external language services (for example, LSP servers).
 * Communication logs are written to separate files in the `language-services` folder, which is located in the IDE log directory.
 */
@Service(Level.APP)
class LanguageServiceLoggerService(private val cs: CoroutineScope) {
  companion object {
    fun getInstance(): LanguageServiceLoggerService = service<LanguageServiceLoggerService>()
    fun isDebugLogEnabled(): Boolean = LOG.isDebugEnabled
  }

  private val loggers: ConcurrentHashMap<String, LoggerBucket> = ConcurrentHashMap()

  fun connect(logFileName: String, writeStandardLogIfLimitReached: Boolean = false): LanguageServiceLogger? {
    val sanitizedFileName = sanitizeFileName(logFileName)
    val bucket = loggers.getOrPut(sanitizedFileName) { LoggerBucket(sanitizedFileName, cs) }
    return bucket.connect(writeStandardLogIfLimitReached)
  }

  fun disconnect(logger: LanguageServiceLogger) {
    val bucket = loggers.values.find { it.containsLogger(logger) }
    if (bucket == null) {
      LOG.error("Cannot find a bucket of ${logger.logPath.fileName}")
      return
    }
    bucket.disconnect(logger)
  }

  @TestOnly
  fun getActiveLogPaths(): List<Path> {
    return loggers.values.flatMap { it.getActiveLogPaths() }
  }
}

private const val MAX_LOG_FILE_COUNT = 5
private val LOG_FILENAME_SUFFIX_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")
private val LOG_PATH_DIR = Path.of(PathManager.getLogPath()) / "language-services"

private class LoggerBucket(private val fileName: String, private val cs: CoroutineScope) {

  private val lock = Any()
  private val activeLoggers: MutableList<LanguageServiceLogger> = mutableListOf()

  fun connect(writeStandardLogIfLimitReached: Boolean): LanguageServiceLogger? {
    synchronized(lock) {
      if (activeLoggers.size >= MAX_LOG_FILE_COUNT) {
        LOG.warn("Cannot create a logger for $fileName, too many active loggers")
        return null
      }
      var currentTimeMillis = System.currentTimeMillis()
      var newFileName: String
      while (true) {
        newFileName = "$fileName-${millisToPrintableTime(currentTimeMillis)}.log"
        if (activeLoggers.any { it.logPath.fileName.toString() == newFileName } ||
            Files.exists(LOG_PATH_DIR / newFileName)) {
          currentTimeMillis++
        }
        else {
          break
        }
      }

      Files.createDirectories(LOG_PATH_DIR)
      val logPath = LOG_PATH_DIR / newFileName
      val logger = LanguageServiceLogger(logPath, cs, writeStandardLogIfLimitReached)

      activeLoggers.add(logger)

      // uri is clickable in the debugger console but not when opening logs in the editor
      LOG.info("Created service logger at: ${logPath.toUri()}")

      application.executeOnPooledThread {
        cleanup()
      }

      return logger
    }
  }

  private fun cleanup() {
    val fileNamePattern = Regex("$fileName-\\d{8}-\\d{6}-\\d{3}\\.log")
    synchronized(lock) {
      val files = Files.list(LOG_PATH_DIR)
        .filter {
          val fileName = it.fileName.toString()
          fileNamePattern.matches(fileName) && !activeLoggers.any { activeLogger ->
            activeLogger.logPath.fileName.toString() == fileName
          }
        }
        .sorted()
        .toList()
      val toKeep = MAX_LOG_FILE_COUNT - activeLoggers.size
      if (toKeep >= files.size) return
      // support the case when MAX_LOG_FILE_COUNT can change, so toKeep can be negative
      val toRemove = min(files.size, files.size - toKeep)
      files.take(toRemove).forEach {
        Files.deleteIfExists(it)
      }
    }
  }

  private fun millisToPrintableTime(millis: Long): String {
    return LOG_FILENAME_SUFFIX_FORMAT.format(millisToDate(millis))
  }

  fun containsLogger(logger: LanguageServiceLogger): Boolean {
    synchronized(lock) {
      return activeLoggers.contains(logger)
    }
  }

  fun disconnect(logger: LanguageServiceLogger) {
    logger.close()
    synchronized(lock) {
      if (!activeLoggers.remove(logger)) {
        LOG.error("Logger ${logger.logPath.fileName} was not registered.")
      }
    }
  }

  fun getActiveLogPaths(): List<Path> {
    synchronized(lock) {
      return activeLoggers.map { it.logPath }
    }
  }
}

internal fun millisToDate(millis: Long): LocalDateTime {
  // similar to com.intellij.openapi.diagnostic.IdeaLogRecordFormatter#format
  return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())
}
