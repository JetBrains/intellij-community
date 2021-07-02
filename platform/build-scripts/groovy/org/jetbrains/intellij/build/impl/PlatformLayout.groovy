// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl


import com.intellij.util.containers.MultiMap
import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.jps.model.java.JpsJavaClasspathKind
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.module.JpsModuleReference

import java.util.function.Consumer
/**
 * Describes layout of the platform (*.jar files in IDE_HOME/lib directory).
 * <p>
 * By default it includes all modules specified in {@link org.jetbrains.intellij.build.ProductModulesLayout},
 * all libraries these modules depend on with scope 'Compile' or 'Runtime', and all project libraries from dependencies (with scope 'Compile'
 * or 'Runtime') of plugin modules for plugins which are {@link org.jetbrains.intellij.build.ProductModulesLayout#bundledPluginModules bundled}
 * (or prepared to be {@link org.jetbrains.intellij.build.ProductModulesLayout#setPluginModulesToPublish published}) with the product (except
 * project libraries which are explicitly included into layouts of all plugins depending on them by {@link BaseLayoutSpec#withProjectLibrary}).
 */
@CompileStatic
final class PlatformLayout extends BaseLayout {
  List<String> excludedProjectLibraries = []
  final List<String> projectLibrariesWithRemovedVersionFromJarNames = []

  static PlatformLayout platform(Consumer<PlatformLayout> customizer, @DelegatesTo(PlatformLayoutSpec) Closure body = {}) {
    def layout = new PlatformLayout()
    customizer.accept(layout)
    layout.customize(body)
    return layout
  }

  void customize(@DelegatesTo(PlatformLayoutSpec) Closure body) {
    def spec = new PlatformLayoutSpec(this)
    body.delegate = spec
    body()
  }

  static final class PlatformLayoutSpec extends BaseLayoutSpec {
    final PlatformLayout layout

    PlatformLayoutSpec(PlatformLayout layout) {
      super(layout)
      this.layout = layout
    }

    /**
     * Exclude project library {@code libraryName} even if it's added to dependencies of some module or plugin included into the product
     */
    void withoutProjectLibrary(String libraryName) {
      layout.excludedProjectLibraries << libraryName
    }

    /**
     * Remove version numbers from {@code libraryName}'s JAR file names before copying to the product distributions. Currently it's needed
     * for libraries included into bootstrap classpath of the platform, because their names are hardcoded in startup scripts and it's not
     * convenient to change them each time the library is updated. <strong>Do not use this method for anything else.</strong> This method
     * will be removed when build scripts automatically compose bootstrap classpath.
     */
    void removeVersionFromProjectLibraryJarNames(String libraryName) {
      layout.projectLibrariesWithRemovedVersionFromJarNames.add(libraryName)
    }

    /**
     * Include all project libraries from dependencies of modules already included into layout to 'lib' directory
     */
    void withProjectLibrariesFromIncludedModules(BuildContext context) {
      context.messages.debug("Collecting project libraries used by platform modules")

      def librariesToInclude = layout.computeProjectLibrariesFromIncludedModules(context)
      librariesToInclude.entrySet().each { entry ->
        context.messages.debug(" library '${entry.key.name}': used in ${entry.value.collect { "'$it.name'" }.join(", ")}")
        withProjectLibrary(entry.key.name)
      }
    }
  }

  MultiMap<JpsLibrary, JpsModule> computeProjectLibrariesFromIncludedModules(BuildContext context) {
    MultiMap<JpsLibrary, JpsModule> result = MultiMap.createLinked()
    Collection<String> libsToUnpack = projectLibrariesToUnpack.values()
    for (String moduleName in moduleJars.values()) {
      JpsModule module = context.findRequiredModule(moduleName)
      for (
        JpsLibrary library : JpsJavaExtensionService.dependencies(module).includedIn(JpsJavaClasspathKind.PRODUCTION_RUNTIME).libraries) {
        if (library.createReference().parentReference instanceof JpsModuleReference ||
            libsToUnpack.contains(library.name) ||
            excludedProjectLibraries.contains(library.name)) {
          continue
        }

        result.putValue(library, module)
      }
    }
    return result
  }
}