// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util.logging

import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import kotlin.io.path.readText

data class FleetLogConfiguration(val refreshTimeout: Int?,
                                 val default: Level?,
                                 val perTarget: Map<Target, Level>,
                                 val loggers: Collection<Entry>) {
  companion object {
    const val LOG_FILE_NAME = "log.conf.toml"
    val userHomeConfigPath = System.getProperty("user.home")?.let { Paths.get(it, ".fleet", LOG_FILE_NAME) }
    fun getLogFiles(configDir: Path?): List<Path> {
      return listOfNotNull(
        configDir?.resolve(LOG_FILE_NAME),
        userHomeConfigPath
      ).distinct()
    }

    val productionDefaultConfiguration: FleetLogConfiguration
      get() = FleetLogConfiguration(
        refreshTimeout = 30, default = Level.WARN, perTarget = emptyMap(),
        loggers = listOf(
          Entry(loggerName = "fleet", default = null, perTarget = mapOf(
            Target.CONSOLE to Level.WARN,
            Target.FILE to Level.INFO,
            )),
          Entry(loggerName = "noria", default = null, perTarget = mapOf(
            Target.CONSOLE to Level.WARN,
            Target.FILE to Level.INFO,
            )),
          ),
        )

    //@fleet.kernel.plugins.InternalInPluginModules(where = ["fleet.testFramework", "fleet.util.logging.impl", "fleet.util.logging.test"])
    val testDefaultConfiguration: FleetLogConfiguration
      get() = FleetLogConfiguration(
        refreshTimeout = 30,
        default = Level.INFO,
        perTarget = emptyMap(),
        loggers = listOf(
          Entry(loggerName = "fleet", default = null, perTarget = mapOf(
            Target.CONSOLE to Level.WARN,
            Target.FILE to Level.DEBUG,
            )),
          Entry(loggerName = "noria", default = null, perTarget = mapOf(
            Target.CONSOLE to Level.WARN,
            Target.FILE to Level.INFO,
            )),
          )
      )

    //language=RegExp
    private const val KEY_PATTERN = "(?<key>\\w+)\\s*=\\s*\"(?<value>[^\"]*)\""

    private val regex by lazy {
      Regex("(?<comment>#[^\\n]*)|(?<entry>\\s*$KEY_PATTERN\\s*)|(?<logger>\\s*\\[\\[logger]]\\s*)")
    }

    @Suppress("EXPERIMENTAL_IS_NOT_ENABLED")
    fun fetchFromFiles(files: List<Path>, defaults: FleetLogConfiguration, forced: FleetLogConfiguration?): FleetLogConfiguration? {
      val filesConfigs = files
        .mapNotNull { runCatching { it.readText() }.getOrNull() }
        .map { parseConfigurationFromText(it) }
      return (listOfNotNull(forced) + filesConfigs + defaults).reduceOrNull { left, right ->
        left.appendPrioritizingCurrent(right)
      }
    }

    fun parseConfigurationFromText(fileText: String): FleetLogConfiguration {
      var result: FleetLogConfiguration? = null
      var refreshTimeout: Int? = null
      var default: Level? = null
      var perTarget = EnumMap<Target, Level>(Target::class.java)
      var name = ""
      val loggers = ArrayList<Entry>()


      for (match in regex.findAll(fileText)) {
        if (match.groups["comment"] != null) {
          // do nothing
        }
        else if (match.groups["logger"] != null) {
          // Means toplevel settings
          if (result == null) {
            result = FleetLogConfiguration(refreshTimeout, default, perTarget, loggers)
            default = null
            perTarget = EnumMap(Target::class.java)
          }
          else {
            if (name != "") {
              loggers.add(Entry(name, default, perTarget))
            }
            name = ""
            default = null
            perTarget = EnumMap(Target::class.java)
          }
        }
        else {
          val key = requireNotNull(match.groups["key"]).value
          val value = requireNotNull(match.groups["value"]).value
          when (key) {
            "name" -> name = value
            "all",
            "level",
            "default" -> default = value.toLevel()
            "console" -> perTarget[Target.CONSOLE] = value.toLevel()
            "file" -> perTarget[Target.FILE] = value.toLevel()
            "json" -> perTarget[Target.STRUCTURED_LOG] = value.toLevel()
            "refresh" -> refreshTimeout = value.toIntOrNull()
            else -> System.err.println("error parsing log config: unknown key [$key]")
          }
        }
      }
      if (name != "") {
        loggers.add(Entry(name, default, perTarget))
      }
      return result ?: FleetLogConfiguration(refreshTimeout, default, perTarget, loggers)
    }

    private fun String.toLevel(): Level = Level.toLevel(this, Level.INFO)
  }

  enum class Level(val intValue: Int) {
    ERROR(40000),
    WARN(30000),
    INFO(20000),
    DEBUG(10000),
    TRACE(5000),
    ALL(Int.MIN_VALUE);

    companion object {
      fun toLevel(string: String, def: Level): Level {
        return entries.find { it.name.equals(string, ignoreCase = true) } ?: def
      }
    }
  }

  enum class Target {
    CONSOLE,
    FILE,
    STRUCTURED_LOG,
  }

  data class Entry(val loggerName: String, val default: Level?, val perTarget: Map<Target, Level>)


  private fun appendPrioritizingCurrent(other: FleetLogConfiguration): FleetLogConfiguration {
    val current = this
    return FleetLogConfiguration(
      refreshTimeout = current.refreshTimeout ?: other.refreshTimeout,
      default = current.default ?: other.default,
      perTarget = other.perTarget + current.perTarget,
      loggers = (current.loggers + other.loggers).distinctBy { it.loggerName },
    )
  }
}

@Deprecated("do not use")
const val FLEET_LOG_CONFIG_PATH_PROPERTY = "fleet.log.config.path"