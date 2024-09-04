// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection

import com.intellij.openapi.application.ModernApplicationStarter
import java.util.concurrent.CompletableFuture
import kotlin.system.exitProcess

internal class InspectionMain : ModernApplicationStarter() {
  private lateinit var application: InspectionApplicationBase

  override fun premain(args: List<String>) {
    InspectionApplicationBase.LOG.info("Command line arguments: $args")
    application = buildInspectionApplication(args)
  }

  private fun buildInspectionApplication(args: List<String>): InspectionApplicationBase {
    val app = InspectionApplicationBase()
    if (args.size < 4) {
      System.err.println("invalid args:$args")
      printHelpAndExit()
    }

    app.myHelpProvider = InspectionToolCmdlineOptionHelpProvider { printHelpAndExit() }
    app.myProjectPath = args[1]
    app.myStubProfile = args[2]
    app.myOutPath = args[3]

    if (app.myProjectPath == null || app.myOutPath == null || app.myStubProfile == null) {
      System.err.println(app.myProjectPath + app.myOutPath + app.myStubProfile)
      printHelpAndExit()
    }

    try {
      var i = 4
      while (i < args.size) {
        when (val arg = args[i]) {
          "-profileName" -> {
            app.myProfileName = args[++i]
          }
          "-profilePath" -> {
            app.myProfilePath = args[++i]
          }
          "-d" -> {
            app.mySourceDirectory = args[++i]
          }
          "-scope" -> {
            app.myScopePattern = args[++i]
          }
          "-targets" -> {
            app.myTargets = args[++i]
          }
          "-format" -> {
            app.myOutputFormat = args[++i]
          }
          "-v0" -> {
            app.setVerboseLevel(0)
          }
          "-v1" -> {
            app.setVerboseLevel(1)
          }
          "-v2" -> {
            app.setVerboseLevel(2)
          }
          "-v3" -> {
            app.setVerboseLevel(3)
          }
          "-e" -> {
            app.myRunWithEditorSettings = true
          }
          "-t" -> {
            app.myErrorCodeRequired = false
          }
          "-changes" -> {
            app.myAnalyzeChanges = true
          }
          "-qodana" -> { /* do nothing */
          }
          else -> {
            System.err.println("unexpected argument: $arg")
            printHelpAndExit()
          }
        }
        i++
      }
    }
    catch (e: IndexOutOfBoundsException) {
      e.printStackTrace()
      printHelpAndExit()
    }

    app.myRunGlobalToolsOnly = System.getProperty("idea.no.local.inspections") != null
    return app
  }

  override suspend fun start(args: List<String>) {
    /*
     todo https://youtrack.jetbrains.com/issue/IDEA-298594
     See also com.intellij.platform.ide.bootstrap.ApplicationLoader.executeApplicationStarter
     */
    CompletableFuture.runAsync(application::startup)
  }


  private fun printHelpAndExit() {
    println(InspectionsBundle.message("inspection.command.line.explanation"))
    exitProcess(1)
  }
}
