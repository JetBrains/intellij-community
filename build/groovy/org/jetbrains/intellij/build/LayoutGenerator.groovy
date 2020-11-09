// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build

import com.intellij.openapi.application.PathManager
import com.intellij.util.execution.ParametersListUtil
import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.impl.PluginLayout

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

@CompileStatic
final class LayoutGenerator {
  static void main(String[] args) {
    String homePath = PathManager.getHomePath(false)
    String className = args[0]
    Class<?> clazz = Class.forName(className)
    ProductProperties properties = clazz.getConstructor(String.class).newInstance(homePath) as ProductProperties
    List<PluginLayout> plugins = properties.productLayout.allNonTrivialPlugins
    Path file = Paths.get(PathManager.getSystemPath(), Objects.requireNonNullElse(properties.platformPrefix, "idea") + ".txt")

    Files.createDirectories(file.parent)
    Files.newBufferedWriter(file).withWriter {
      println("write to " + file)
      Set<String> modules = new LinkedHashSet<>()
      for (PluginLayout plugin : plugins) {
        modules.clear()
        modules.add(plugin.getMainModule())

        plugin.moduleJars.entrySet()
          .findAll { !it.key.contains("/") }
          .collectMany(modules) {it.value}

        modules.remove("intellij.platform.commercial.verifier")
        if (modules.size() == 1) {
          continue
        }

        it.write(ParametersListUtil.join(new ArrayList<CharSequence>(modules)) + "\n")
      }
    }
  }
}
