// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import jetbrains.buildServer.messages.serviceMessages.Message
import jetbrains.buildServer.messages.serviceMessages.ServiceMessage
import jetbrains.buildServer.messages.serviceMessages.ServiceMessageTypes.BUILD_PROBLEM
import jetbrains.buildServer.messages.serviceMessages.ServiceMessageTypes.BUILD_STOP
import org.jetbrains.intellij.build.BuildCancellationException
import org.jetbrains.intellij.build.dependencies.TeamCityHelper
import java.io.PrintWriter
import java.io.StringWriter
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.lang.reflect.Modifier
import kotlin.system.exitProcess

/**
 * jps-bootstrap launchers main classes via this wrapper to correctly log exceptions
 * please do not add any more logic here as it won't be run if you start your target
 * from IDE
 */
object BuildScriptLauncher {
  private const val MAIN_CLASS_PROPERTY = "build.script.launcher.main.class"
  private const val MESSAGE_SPLIT_THRESHOLD = 4_000

  @JvmStatic
  fun main(args: Array<String>) {
    try {
      val mainClassName = System.getProperty(MAIN_CLASS_PROPERTY)
      val mainClass = BuildScriptLauncher::class.java.classLoader.loadClass(mainClassName)
      val lookup = MethodHandles.lookup()
      val mainMethod = findMainMethod(lookup, mainClass, arrayOf(Array<String>::class.java))
        ?: findMainMethod(lookup, mainClass, emptyArray())
        ?: findNonPublicMainMethod(lookup, mainClass)
        ?: throw IllegalStateException("No eligible main method found in ${mainClass.name}")
      if (mainMethod.type().parameterCount() == 0 && args.isNotEmpty()) {
        throw IllegalStateException("$mainClass.main method doesn't accept arguments: ${args.joinToString()}")
      }
      val invocationArguments = if (mainMethod.type().parameterCount() == 0) emptyList() else listOf(args)
      mainMethod.invokeWithArguments(invocationArguments)
      exitProcess(0)
    }
    catch (t: Throwable) {
      val cause = (t as? BuildCancellationException)?.cause ?: t

      // BuildLifetime installs a failure reporter on this thread;
      // it stops the span export, prints the stack trace once, and prints the failure summary
      val reporter = Thread.currentThread().uncaughtExceptionHandler.takeUnless { it is ThreadGroup }
      reporter?.uncaughtException(Thread.currentThread(), cause)

      val message = StringWriter().apply {
        PrintWriter(this).use { printWriter ->
          cause.printStackTrace(printWriter)
        }
      }.toString()

      if (TeamCityHelper.isUnderTeamCity) {
        if (reporter == null) {
          message.chunked(MESSAGE_SPLIT_THRESHOLD).forEach { chunk ->
            // Under TeamCity non-zero exit code will be displayed as a separate build error
            println(Message(chunk, "FAILURE", null).asString())
          }
        }

        if (t is BuildCancellationException) {
          println(ServiceMessage.asString(BUILD_STOP, mapOf("comment" to message.take(MESSAGE_SPLIT_THRESHOLD))))
        }
        else {
          // Make sure it fails the build, see
          // https://www.jetbrains.com/help/teamcity/service-messages.html#Reporting+Build+Problems
          println(ServiceMessage.asString(BUILD_PROBLEM, mapOf("description" to message.take(MESSAGE_SPLIT_THRESHOLD))))
        }
        exitProcess(0)
      }
      else {
        if (reporter == null) {
          System.err.println("\nFATAL: $message")
        }
        exitProcess(1)
      }
    }
  }

  private fun findMainMethod(lookup: MethodHandles.Lookup, mainClass: Class<*>, parameterTypes: Array<Class<*>>): MethodHandle? = try {
    lookup.findStatic(mainClass, "main", MethodType.methodType(Void.TYPE, parameterTypes))
  }
  catch (_: NoSuchMethodException) {
    null
  }
  catch (_: IllegalAccessException) {
    null
  }

  private fun findNonPublicMainMethod(lookup: MethodHandles.Lookup, mainClass: Class<*>): MethodHandle? {
    try {
      val method = mainClass.getDeclaredMethod("main")
      if (
        method != null &&
        method.returnType == Void.TYPE &&
        Modifier.isStatic(method.modifiers) &&
        !Modifier.isPrivate(method.modifiers) &&
        method.parameterCount == 0
      ) {
        check(method.trySetAccessible()) {
          "Cannot access the 'main' method in ${mainClass.name}"
        }
        return lookup.unreflect(method)
      }
    }
    catch (_: NoSuchMethodException) {}
    return null
  }
}
