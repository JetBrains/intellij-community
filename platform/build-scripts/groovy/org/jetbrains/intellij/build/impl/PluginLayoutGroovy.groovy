// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import groovy.transform.CompileStatic
import org.jetbrains.annotations.NotNull

import java.util.function.Consumer

@CompileStatic
final class PluginLayoutGroovy {
  /**
   * Creates the plugin layout description. The default plugin layout is composed of a jar with name {@code mainModuleName}.jar containing
   * production output of {@code mainModuleName} module, and the module libraries of {@code mainModuleName} with scopes 'Compile' and 'Runtime'
   * placed under 'lib' directory in a directory with name {@code mainModuleName}.
   * If you need to include additional resources or modules into the plugin layout specify them in
   * {@code body} parameter. If you don't need to change the default layout there is no need to call this method at all, it's enough to
   * specify the plugin module in {@link org.jetbrains.intellij.build.ProductModulesLayout#bundledPluginModules bundledPluginModules/pluginModulesToPublish} list.
   *
   * <p>Note that project-level libraries on which the plugin modules depend, are automatically put to 'IDE_HOME/lib' directory for all IDEs
   * which are compatible with the plugin. If this isn't desired (e.g. a library is used in a single plugin only, or if plugins where
   * a library is used aren't bundled with IDEs so we don't want to increase size of the distribution, you may invoke {@link PluginLayoutSpec#withProjectLibrary}
   * to include such a library to the plugin distribution.</p>
   * @param mainModuleName name of the module containing META-INF/plugin.xml file of the plugin
   */
  static PluginLayout plugin(@NotNull String mainModuleName, @DelegatesTo(PluginLayout.PluginLayoutSpec) Closure body) {
    PluginLayout.plugin(mainModuleName, new Consumer<PluginLayout.PluginLayoutSpec>() {
      @Override
      void accept(PluginLayout.PluginLayoutSpec spec) {
        body.delegate = spec
        body()
      }
    })
  }
}
