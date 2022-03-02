// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.jps.model.java.JpsJavaClasspathKind
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.module.JpsModuleReference

import java.util.function.BiConsumer

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
  final Set<String> excludedProjectLibraries = new HashSet<>()

  void customize(@DelegatesTo(PlatformLayoutSpec) Closure body) {
    def spec = new PlatformLayoutSpec(this)
    body.delegate = spec
    body()
  }

  void withProjectLibrary(String libraryName) {
    includedProjectLibraries.add(new ProjectLibraryData(libraryName, "", ProjectLibraryData.PackMode.MERGED))
  }

  void withProjectLibrary(String libraryName, ProjectLibraryData.PackMode packMode) {
    includedProjectLibraries.add(new ProjectLibraryData(libraryName, "", packMode))
  }

  void withProjectLibrary(ProjectLibraryData data) {
    includedProjectLibraries.add(data)
  }

  /**
   * Exclude project library {@code libraryName} even if it's added to dependencies of some module or plugin included into the product
   */
  void withoutProjectLibrary(String libraryName) {
    excludedProjectLibraries.add(libraryName)
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
      layout.withoutProjectLibrary(libraryName)
    }
  }

  void collectProjectLibrariesFromIncludedModules(BuildContext context, BiConsumer<JpsLibrary, JpsModule> consumer) {
    Collection<String> libsToUnpack = projectLibrariesToUnpack.values()
    for (String moduleName in includedModuleNames) {
      JpsModule module = context.findRequiredModule(moduleName)
      for (
        JpsLibrary library : JpsJavaExtensionService.dependencies(module).includedIn(JpsJavaClasspathKind.PRODUCTION_RUNTIME).libraries) {
        if (library.createReference().parentReference instanceof JpsModuleReference ||
            libsToUnpack.contains(library.name) ||
            excludedProjectLibraries.contains(library.name)) {
          continue
        }

        consumer.accept(library, module)
      }
    }
  }
}