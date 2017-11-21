/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.intellij.build.impl

import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.jps.model.java.JpsJavaClasspathKind
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.module.JpsModuleReference

import java.util.function.Consumer

/**
 * Describes layout of the platform (*.jar files in IDE_HOME/lib directory). By default it includes all modules specified in {@link org.jetbrains.intellij.build.ProductModulesLayout},
 * all libraries these modules depend on with scope 'Compile' or 'Runtime', and all project libraries from dependencies (with scope 'Compile'
 * or 'Runtime') of plugin modules for plugins which are {@link org.jetbrains.intellij.build.ProductModulesLayout#bundledPluginModules bundled}
 * (or prepared to be {@link org.jetbrains.intellij.build.ProductModulesLayout#pluginModulesToPublish published}) with the product.
 *
 * @author nik
 */
class PlatformLayout extends BaseLayout {
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

  static class PlatformLayoutSpec extends BaseLayoutSpec {
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
      layout.projectLibrariesWithRemovedVersionFromJarNames << libraryName
    }

    /**
     * Include all project libraries from dependencies of modules already included into layout to 'lib' directory
     */
    void withProjectLibrariesFromIncludedModules(BuildContext context) {
      layout.moduleJars.values().each {
        def module = context.findRequiredModule(it)
        JpsJavaExtensionService.dependencies(module).includedIn(JpsJavaClasspathKind.PRODUCTION_RUNTIME).libraries.findAll {
          !(it.createReference().parentReference instanceof JpsModuleReference) &&
          !layout.projectLibrariesToUnpack.values().contains(it.name) &&
          !layout.excludedProjectLibraries.contains(it.name)
        }.each {
          withProjectLibrary(it.name)
        }
      }
    }

    /**
     * Include contents of JARs of the project library {@code libraryName} into JAR {@code jarName}
     */
    void withProjectLibraryUnpackedIntoJar(String libraryName, String jarName) {
      layout.projectLibrariesToUnpack.put(jarName, libraryName)
    }
  }
}