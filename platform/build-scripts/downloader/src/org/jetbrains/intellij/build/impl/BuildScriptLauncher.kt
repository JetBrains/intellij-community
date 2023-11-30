// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import jetbrains.buildServer.messages.serviceMessages.Message
import jetbrains.buildServer.messages.serviceMessages.ServiceMessage
import jetbrains.buildServer.messages.serviceMessages.ServiceMessageTypes.BUILD_PORBLEM
import jetbrains.buildServer.messages.serviceMessages.ServiceMessageTypes.BUILD_STOP
import org.jetbrains.intellij.build.BuildCancellationException
import org.jetbrains.intellij.build.dependencies.TeamCityHelper
import java.io.PrintWriter
import java.io.StringWriter
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import kotlin.system.exitProcess

/**
 * jps-bootstrap launchers main classes via this wrapper to correctly log exceptions
 * please do not add any more logic here as it won't be run if you start your target
 * from IDE
 */
object BuildScriptLauncher {
  private const val MAIN_CLASS_PROPERTY = "build.script.launcher.main.class"

  @JvmStatic
  fun main(args: Array<String>) {
    try {
      val mainClassName = System.getProperty(MAIN_CLASS_PROPERTY)
      val mainClass = BuildScriptLauncher::class.java.classLoader.loadClass(mainClassName)
      val lookup = MethodHandles.lookup()
      try {
        lookup
          .findStatic(mainClass, "main", MethodType.methodType(Void.TYPE, Array<String>::class.java))
          .invokeExact(args)
      }
      catch (_: NoSuchMethodException) {
        val method = lookup.findStatic(mainClass, "main", MethodType.methodType(Void.TYPE))
        check(args.isEmpty()) {
          "$mainClass.main method doesn't accept arguments: ${args.joinToString()}"
        }
        method.invokeExact()
      }
      exitProcess(0)
    }
    catch (t: Throwable) {
      val message = StringWriter().apply {
        PrintWriter(this).use { printWriter ->
          val cause = (t as? BuildCancellationException)?.cause ?: t
          cause.printStackTrace(printWriter)
        }
      }.toString()

      if (TeamCityHelper.isUnderTeamCity) {
        // Under TeamCity non-zero exit code will be displayed as a separate build error
        println(Message(message, "FAILURE", null).asString())
        if (t is BuildCancellationException) {
          println(ServiceMessage.asString(BUILD_STOP, mapOf("comment" to message)))
        }
        else {
          // Make sure it fails the build, see
          // https://www.jetbrains.com/help/teamcity/service-messages.html#Reporting+Build+Problems
          println(ServiceMessage.asString(BUILD_PORBLEM, mapOf("description" to message)))
        }
        exitProcess(0)
      }
      else {
        System.err.println("\nFATAL: $message")
        exitProcess(1)
      }
    }
  }
}
