// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import org.jetbrains.intellij.build.impl.PluginLayout

object JavaPluginLayout {
  const val MAIN_MODULE_NAME = "intellij.java.plugin"

  fun javaPlugin(addition: ((PluginLayout.PluginLayoutSpec) -> Unit)? = null): PluginLayout {
    return PluginLayout.plugin(mainModuleName = MAIN_MODULE_NAME, auto = true) { spec ->
      spec.directoryName = "java"
      spec.mainJarName = "java-impl.jar"

      spec.withModule("intellij.platform.jps.build.launcher", "jps-launcher.jar")
      // run configurations put this jar on the classpath of a user process, so it stays a jar of its own
      // A layout jar and not a content module: the test plugins of every product declare intellij.java.rt themselves.
      spec.withModule("intellij.java.rt", "idea_rt.jar")

      spec.withProjectLibrary("Eclipse", "ecj")

      spec.withModuleLibrary("debugger-memory-agent", "intellij.java.debugger.memory.agent", "")
      // explicitly pack and sa-jdwp as a separate JARs
      spec.withModuleLibrary("sa-jdwp", "intellij.java.debugger.impl", "sa-jdwp.jar")

      spec.withResourceArchiveFromModule("intellij.java.jdkAnnotations", "resources", "lib/resources/jdkAnnotations.jar")

      addition?.invoke(spec)
    }
  }
}
