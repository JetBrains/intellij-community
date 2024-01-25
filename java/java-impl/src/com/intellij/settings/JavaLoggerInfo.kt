// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.settings

import com.siyeh.ig.psiutils.JavaLoggingUtils

enum class JavaLoggerInfo(
  val loggerName : String,
  val factoryName : String,
  val methodName: String,
  val classNamePattern: String
) {
  JAVA_LOGGING(
    JavaLoggingUtils.JAVA_LOGGING,
    JavaLoggingUtils.JAVA_LOGGING_FACTORY,
    "getLogger",
    "%s.class.getName()"
  ) {
    override fun toString(): String {
      return "java.util.logging"
    }
  },
  SLF4J(
    JavaLoggingUtils.SLF4J,
    JavaLoggingUtils.SLF4J_FACTORY,
    "getLogger",
    "%s.class",
  ) {
    override fun toString(): String {
      return "Slf4j"
    }
  },
  COMMONS_LOGGING(
    JavaLoggingUtils.COMMONS_LOGGING,
    JavaLoggingUtils.COMMONS_LOGGING_FACTORY,
    "getLog",
    "%s.class",
  ) {
    override fun toString(): String {
      return "Apache Commons Logging"
    }
  },
  LOG4J(
    JavaLoggingUtils.LOG4J,
    JavaLoggingUtils.LOG4J_FACTORY,
    "getLogger",
    "%s.class"
  ) {
    override fun toString(): String {
      return "Log4j"
    }
  },
  LOG4J2(
    JavaLoggingUtils.LOG4J2,
    JavaLoggingUtils.LOG4J2_FACTORY,
    "getLogger",
    "%s.class"
  ) {
    override fun toString(): String {
      return "Log4j2"
    }
  };

  fun createLoggerFieldText(className : String) : String {
    return "$loggerName $LOGGER_IDENTIFIER = ${factoryName}.$methodName(${String.format(classNamePattern, className)});"
  }

  companion object {
    const val LOGGER_IDENTIFIER = "LOGGER"

    val allLoggers: List<JavaLoggerInfo> = listOf(
      JAVA_LOGGING,
      SLF4J,
      COMMONS_LOGGING,
      LOG4J,
      LOG4J2
    )
  }
}
