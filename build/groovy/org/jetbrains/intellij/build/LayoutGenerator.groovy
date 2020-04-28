// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build

import com.intellij.openapi.application.PathManager
import com.intellij.util.ObjectUtils
import com.intellij.util.execution.ParametersListUtil
import org.jetbrains.intellij.build.impl.PluginLayout

import java.nio.file.Files
import java.nio.file.Paths

final class LayoutGenerator {
  static void main(String[] args) {
    String homePath = PathManager.getHomePath(false)
    def className = args[0]
    def clazz = Class.forName(className)
    JetBrainsProductProperties properties = clazz.getConstructor(String.class).newInstance(homePath) as JetBrainsProductProperties
    List<PluginLayout> plugins = properties.getProductLayout().getAllNonTrivialPlugins()
    def file = Paths.get(PathManager.getSystemPath(), ObjectUtils.notNull(properties.platformPrefix, "idea") + ".txt")
    Files.createDirectories(file.parent)
    BufferedWriter stream = Files.newBufferedWriter(file)
    try {
      println("write to " + file)
      Set<String> modules = new LinkedHashSet<>()
      for (PluginLayout plugin : plugins) {
        modules.clear()
        modules.add(plugin.getMainModule())

        plugin.moduleJars.entrySet().findAll { !it.key.contains("/") }.collectMany(modules) {it.value}

        modules.remove("intellij.platform.commercial.verifier")
        if (modules.size() == 1) {
          continue
        }

        stream.write((ParametersListUtil.join(new ArrayList<CharSequence>(modules)) + "\n"))
      }
    }
    finally {
      stream.close()
    }
  }
}
