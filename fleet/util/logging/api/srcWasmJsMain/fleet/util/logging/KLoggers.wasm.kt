// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util.logging

import fleet.util.multiplatform.Actual
import kotlin.math.absoluteValue
import web.console.console
import kotlin.reflect.KClass

@Actual("getLoggerFactory")
internal fun getLoggerFactoryWasmJs(): KLoggerFactory {
  return object : KLoggerFactory {
    private val mdc = mutableMapOf<String, String>()

    override fun logger(owner: KClass<*>): KLogger {
      return getLogger(owner.simpleName ?: owner.toString()) { mdc.toMap() }
    }

    override fun logger(owner: Any): KLogger {
      return logger(owner::class)
    }

    override fun logger(name: String): KLogger {
      return getLogger(name) { mdc.toMap() }
    }

    override fun setLoggingContext(map: Map<String, String>?) {
      mdc.clear()
      if (map != null) {
        mdc.putAll(map)
      }
    }

    override fun getLoggingContext(): Map<String, String>? {
      return mdc.toMap()
    }
  }
}

private enum class BrowserConsoleLogLevel(
  val label: String,
  val style: String,
) {
  ERROR("ERROR", "background:#d93025;color:#fff;padding:1px 6px;border-radius:999px;font-weight:700"),
  WARN("WARN ", "color:#e8a317;font-weight:700"),
  INFO("INFO ", "color:#4ec9b0;font-weight:600"),
  DEBUG("DEBUG", "color:#8b949e"),
  TRACE("TRACE", "color:#6e7681"),
}

private val componentPalette = listOf(
  "color:#7aa2f7;font-weight:600",
  "color:#56d4c8;font-weight:600",
  "color:#cf8ae8;font-weight:600",
  "color:#e0a370;font-weight:600",
  "color:#76c7f0;font-weight:600",
)

private const val MDC_KEY_STYLE = "color:#8b949e"
private const val MDC_VALUE_STYLE = "color:#9da7b3"
private const val ROLE_STYLE = "color:#c9d1d9;font-weight:600"
private const val WORKSPACE_UID_STYLE = "color:#9da7b3;font-style:italic"

private const val ROLE_COLUMN_WIDTH = 2
private const val LOGGER_COLUMN_WIDTH = 28

private fun getLogger(name: String, contextProvider: () -> Map<String, String>): KLogger {
  return KLogger(object : BaseLogger {
    override val isTraceEnabled: Boolean = false
    override val isDebugEnabled: Boolean = true
    override val isInfoEnabled: Boolean = true
    override val isWarnEnabled: Boolean = true
    override val isErrorEnabled: Boolean = true

    override fun trace(message: Any?) {
      log(BrowserConsoleLogLevel.TRACE, message)
    }

    override fun trace(t: Throwable?, message: Any?) {
      log(BrowserConsoleLogLevel.TRACE, message, t)
    }

    override fun debug(message: Any?) {
      log(BrowserConsoleLogLevel.DEBUG, message)
    }

    override fun debug(t: Throwable?, message: Any?) {
      log(BrowserConsoleLogLevel.DEBUG, message, t)
    }

    override fun info(message: Any?) {
      log(BrowserConsoleLogLevel.INFO, message)
    }

    override fun info(t: Throwable?, message: Any?) {
      log(BrowserConsoleLogLevel.INFO, message, t)
    }

    override fun warn(message: Any?) {
      log(BrowserConsoleLogLevel.WARN, message)
    }

    override fun warn(t: Throwable?, message: Any?) {
      log(BrowserConsoleLogLevel.WARN, message, t)
    }

    override fun error(message: Any?) {
      log(BrowserConsoleLogLevel.ERROR, message)
    }

    override fun error(t: Throwable?, message: Any?) {
      log(BrowserConsoleLogLevel.ERROR, message, t)
    }

    private fun log(
      level: BrowserConsoleLogLevel,
      message: Any?,
      t: Throwable? = null,
    ) {
      val renderedMessage = message?.toString().orEmpty()
      val context = contextProvider()
      val role = context.firstNonBlankValue("role", "ROLE", "fleet.role")
      val shipId = context.firstNonBlankValue("shipId", "ship_id", "SHIP_ID", "fleet.shipId")
      val prefix = context.firstNonBlankValue("prefix", "PREFIX", "fleet.prefix")
      val workspaceUID = context.firstNonBlankValue("workspaceUID")
      val extraContext = context.filterKeys { key ->
        key !in setOf("role", "ROLE", "fleet.role", "shipId", "ship_id", "SHIP_ID", "fleet.shipId", "prefix", "PREFIX", "fleet.prefix", "workspaceUID")
      }

      val header = buildConsoleHeader(
        level = level,
        loggerName = name,
        role = role,
        workspaceUID = workspaceUID,
        message = renderedMessage,
      )
      val mdc = buildMdcRecord(
        shipId = shipId,
        prefix = prefix,
        extraContext = extraContext,
      )

      val hasGroup = mdc != null || t != null
      if (hasGroup) {
        console.groupCollapsed(*header.toTypedArray())
        if (mdc != null) {
          console.log(*mdc.toTypedArray())
        }
        if (t != null) {
          val details = buildThrowableDetails(t)
          when (level) {
            BrowserConsoleLogLevel.ERROR -> console.error(details)
            BrowserConsoleLogLevel.WARN -> console.warn(details)
            BrowserConsoleLogLevel.INFO -> console.info(details)
            BrowserConsoleLogLevel.DEBUG, BrowserConsoleLogLevel.TRACE -> console.debug(details)
          }
        }
        console.groupEnd()
      }
      else {
        when (level) {
          BrowserConsoleLogLevel.ERROR -> console.error(*header.toTypedArray())
          BrowserConsoleLogLevel.WARN -> console.warn(*header.toTypedArray())
          BrowserConsoleLogLevel.INFO -> console.info(*header.toTypedArray())
          BrowserConsoleLogLevel.DEBUG, BrowserConsoleLogLevel.TRACE -> console.debug(*header.toTypedArray())
        }
      }
    }
  })
}

private fun buildConsoleHeader(
  level: BrowserConsoleLogLevel,
  loggerName: String,
  role: String?,
  workspaceUID: String?,
  message: String,
): List<String> {
  val format = StringBuilder()
  val args = mutableListOf<String>("")

  format.append("%c${level.label}%c")
  args += level.style
  args += ""

  val roleLabel = (role ?: "").padEnd(ROLE_COLUMN_WIDTH)
  format.append(" %c$roleLabel")
  args += if (role != null) ROLE_STYLE else ""

  format.append(" %c${loggerName.padEnd(LOGGER_COLUMN_WIDTH)}%c")
  args += componentStyle(loggerName)
  args += ""

  if (message.isNotBlank()) {
    format.append(" %c%s")
    args += "font-weight:normal"
    args += message
  }

  workspaceUID?.let {
    format.append(" %c$it")
    args += WORKSPACE_UID_STYLE
  }

  args[0] = format.toString()
  return args
}

private fun buildMdcRecord(
  shipId: String?,
  prefix: String?,
  extraContext: Map<String, String>,
): List<String>? {
  val entries = mutableMapOf<String, String>()
  shipId?.let { entries["ship"] = it }
  if (!prefix.isNullOrBlank()) entries["prefix"] = prefix
  entries.putAll(extraContext)

  if (entries.isEmpty()) return null

  val format = StringBuilder()
  val args = mutableListOf<String>("")

  entries.forEach { (key, value) ->
    format.append(" %c$key %c= %c$value")
    args += MDC_KEY_STYLE
    args += MDC_KEY_STYLE
    args += MDC_VALUE_STYLE
  }

  args[0] = format.toString()
  return args
}

private fun Map<String, String>.firstNonBlankValue(vararg keys: String): String? {
  return keys.firstNotNullOfOrNull { key -> this[key]?.takeIf { it.isNotBlank() } }
}

private fun componentStyle(name: String): String {
  val index = name.hashCode().absoluteValue % componentPalette.size
  return componentPalette[index]
}

private fun buildThrowableDetails(t: Throwable): String {
  return buildString {
    t.message?.takeIf { it.isNotBlank() }?.let {
      append(it)
      appendLine()
    }
    append(t.stackTraceToString())
  }
}
