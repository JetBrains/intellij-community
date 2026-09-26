// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.idea

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object ApplicationStartArguments {
  @JvmField
  val DISABLE_NON_BUNDLED_PLUGINS: WellKnownArgument = WellKnownArgument("disableNonBundledPlugins", ignoreCase = true)

  @JvmField
  val DONT_REOPEN_PROJECTS: WellKnownArgument = WellKnownArgument("dontReopenProjects", ignoreCase = true)

  @JvmField
  val SPLASH: WellKnownArgument = WellKnownArgument("splash")

  @JvmField
  val NO_SPLASH: WellKnownArgument = WellKnownArgument("nosplash")

  private val allArguments = listOf(
    DISABLE_NON_BUNDLED_PLUGINS,
    DONT_REOPEN_PROJECTS,
    SPLASH,
    NO_SPLASH
  )

  @JvmStatic
  fun stripKnownArguments(args: List<String>): List<String> = args.filterNot { arg -> allArguments.any { it.matchArgument(arg) } }
}

@ApiStatus.Internal
class WellKnownArgument(val argument: String, val ignoreCase: Boolean = false) {
  fun isSet(args: List<String>): Boolean = args.any { matchArgument(it) }
  internal fun matchArgument(arg: String): Boolean = arg.equals(argument, ignoreCase)
}
