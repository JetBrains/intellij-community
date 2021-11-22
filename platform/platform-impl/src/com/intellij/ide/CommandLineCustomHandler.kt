// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide

import com.intellij.openapi.extensions.ExtensionPointName
import java.util.concurrent.Future

interface CommandLineCustomHandler {

  companion object {
    @JvmStatic
    val EP_NAME = ExtensionPointName.create<CommandLineCustomHandler>("com.intellij.commandLineCustomHandler")

    fun process(args: List<String>): Future<CliResult>? {
      return EP_NAME.computeSafeIfAny { handler: CommandLineCustomHandler ->
        handler.process(args)
      }
    }
  }

  fun process(args: List<String>): Future<CliResult>?

  class StartupService {
    companion object {
      @JvmField
      var initialArguments: List<String>? = null
    }

    init {
      val initialArguments = initialArguments
      if (initialArguments != null && initialArguments.any()) {
        process(initialArguments)
      }
    }
  }
}