// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.intellij.build.impl.PluginLayout

object JavaPluginLayout {
  const val MAIN_MODULE_NAME = "intellij.java.plugin"

  /** The main module of `com.intellij.java.basic`, which holds the Java parser, the PSI, and the lexer-based highlighting. */
  const val BASIC_PLUGIN_MAIN_MODULE_NAME: String = "intellij.java.basic.plugin"

  /**
   * The main modules of `com.intellij.java` and `com.intellij.java.basic`. A product that ignores the Java plugin as a
   * compatible plugin ignores all of them.
   */
  val ALL_MAIN_MODULE_NAMES: PersistentList<String> = persistentListOf(MAIN_MODULE_NAME, BASIC_PLUGIN_MAIN_MODULE_NAME)

  private fun javaPlugin(addition: ((PluginLayout.PluginLayoutSpec) -> Unit)? = null): PluginLayout {
    return PluginLayout.plugin(mainModuleName = MAIN_MODULE_NAME, auto = true) { spec ->
      spec.withModule("intellij.platform.jps.build.launcher", "jps-launcher.jar")
      // run configurations put this jar on the classpath of a user process, so it stays a jar of its own
      // A layout jar and not a content module: the test plugins of every product declare intellij.java.rt themselves.
      spec.withModule("intellij.java.rt", "idea_rt.jar")

      spec.withProjectLibrary("Eclipse", "ecj")

      spec.withModuleLibrary("debugger-memory-agent", "intellij.java.debugger.memory.agent", "")
      // explicitly pack and sa-jdwp as a separate JARs
      spec.withModuleLibrary("sa-jdwp", "intellij.java.debugger.impl", "sa-jdwp.jar")

      spec.withModule("intellij.java.jdkAnnotations", "resources/jdkAnnotations.jar")

      addition?.invoke(spec)
    }
  }

  /**
   * The layout of `com.intellij.java` and the layout of `com.intellij.java.basic`, in this order. [addition] goes to the
   * Java layout.
   */
  fun javaPluginLayouts(addition: ((PluginLayout.PluginLayoutSpec) -> Unit)? = null): PersistentList<PluginLayout> {
    return persistentListOf(javaPlugin(addition), javaBasicPlugin())
  }

  /**
   * The layout of `com.intellij.java.basic`.
   *
   * [javaPluginLayouts] registers it next to the Java layout. Register it alone only in a product without an explicit
   * Java layout, as the JetBrains Client does. The Java main module depends on [BASIC_PLUGIN_MAIN_MODULE_NAME], and the
   * name has the `intellij.java.` prefix. So without this layout, the `auto` Java layout packs the module as its own child.
   */
  fun javaBasicPlugin(): PluginLayout = PluginLayout.pluginAuto(listOf(BASIC_PLUGIN_MAIN_MODULE_NAME))
}
