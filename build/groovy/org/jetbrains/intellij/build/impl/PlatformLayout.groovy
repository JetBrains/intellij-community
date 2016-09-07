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

/**
 * @author nik
 */
class PlatformLayout extends BaseLayout {
  static PlatformLayout platform(@DelegatesTo(PlatformLayoutSpec) Closure body = {}) {
    def layout = new PlatformLayout()
    def spec = new PlatformLayoutSpec(layout)
    body.delegate = spec
    body()
    return layout
  }

  @Override
  String basePath(BuildContext buildContext) {
    buildContext.paths.communityHome
  }

  static class PlatformLayoutSpec extends BaseLayoutSpec {
    PlatformLayoutSpec(PlatformLayout layout) {
      super(layout)
    }

    /**
     * Include all project libraries from dependencies of modules already included into layout to 'lib' directory
     */
    void withProjectLibrariesFromIncludedModules(BuildContext context) {
      layout.moduleJars.values().each {
        def module = context.findRequiredModule(it)
        JpsJavaExtensionService.dependencies(module).includedIn(JpsJavaClasspathKind.PRODUCTION_RUNTIME).libraries.findAll {
          !(it.createReference().parentReference instanceof JpsModuleReference) && !layout.projectLibrariesToUnpack.values().contains(it.name)
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