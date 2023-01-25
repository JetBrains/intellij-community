// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.dependencies

import jetbrains.buildServer.messages.serviceMessages.Message
import jetbrains.buildServer.messages.serviceMessages.MessageWithAttributes
import jetbrains.buildServer.messages.serviceMessages.ServiceMessageTypes

object BuildDependenciesLogging {
  @JvmStatic
  fun setVerboseEnabled(verboseEnabled: Boolean) {
    BuildDependenciesLogging.verboseEnabled = verboseEnabled
  }

  private var verboseEnabled = false
  @JvmStatic
  fun warn(message: String?) {
    if (TeamCityHelper.isUnderTeamCity) {
      println(Message(message!!, "WARNING", null).asString())
    }
    else {
      println(message)
    }
  }

  @JvmStatic
  fun info(message: String?) {
    if (TeamCityHelper.isUnderTeamCity) {
      println(Message(message!!, "NORMAL", null).asString())
    }
    else {
      println(message)
    }
  }

  @JvmStatic
  fun verbose(message: String) {
    if (TeamCityHelper.isUnderTeamCity) {
      val attributes: MutableMap<String, String> = HashMap()
      attributes["text"] = message
      attributes["status"] = "NORMAL"
      attributes["tc:tags"] = "tc:internal"
      println(object : MessageWithAttributes(ServiceMessageTypes.MESSAGE, attributes) {}.asString())
    }
    else {
      if (verboseEnabled) {
        println(message)
      }
    }
  }

  @JvmStatic
  fun error(message: String) {
    if (TeamCityHelper.isUnderTeamCity) {
      println(Message(message, "ERROR", null).asString())
    }
    else {
      println("ERROR: $message")
    }
  }

  @JvmStatic
  fun fatal(message: String) {
    if (TeamCityHelper.isUnderTeamCity) {
      println(Message(message, "FAILURE", null).asString())
    }
    else {
      System.err.println("\nFATAL: $message")
    }
  }
}
