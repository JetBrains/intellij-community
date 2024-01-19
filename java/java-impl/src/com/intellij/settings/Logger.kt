// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.settings

enum class Logger {
  JAVA_LOGGING {
    override fun toString(): String {
      return "JAVA LOGGING"
    }
  },
  SLF4J {
    override fun toString(): String {
      return "SLF4J"
    }
  },
  COMMONS_LOGGING {
    override fun toString(): String {
      return "COMMONS_LOGGING"
    }
  },
  LOG4J {
    override fun toString(): String {
      return "LOG4J"
    }
  },
  LOG4J2 {
    override fun toString(): String {
      return "LOG4J2"
    }
  };

  companion object {
    val allLoggers: List<Logger> = listOf(
      JAVA_LOGGING,
      SLF4J,
      COMMONS_LOGGING,
      LOG4J,
      LOG4J2
    )
  }
}
