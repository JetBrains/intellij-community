// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

class BaseLayoutSpec {
  protected final BaseLayout layout

  BaseLayoutSpec(BaseLayout layout) {
    this.layout = layout
  }

  /**
   * Register an additional module to be included into the plugin distribution. Module-level libraries from
   * {@code moduleName} with scopes 'Compile' and 'Runtime' will be also copied to the 'lib' directory of the plugin.
   */
  final void withModule(String moduleName) {
    layout.withModule(moduleName)
  }

  /**
   * Register an additional module to be included into the plugin distribution. If {@code relativeJarPath} doesn't contain '/' (i.e. the
   * JAR will be added to the plugin's classpath) this will also cause modules library from {@code moduleName} with scopes 'Compile' and
   * 'Runtime' to be copied to the 'lib' directory of the plugin.
   *
   * @param relativeJarPath target JAR path relative to 'lib' directory of the plugin; different modules may be packed into the same JAR,
   * but <strong>don't use this for new plugins</strong>; this parameter is temporary added to keep layout of old plugins.
   */
  void withModule(String moduleName, String relativeJarPath) {
    layout.withModule(moduleName, relativeJarPath)
  }

  /**
   * @deprecated localizable resources are now put to the module JAR, so {@code localizableResourcesJars} parameter is ignored now
   */
  @SuppressWarnings("unused")
  @Deprecated
  void withModule(String moduleName, String relativeJarPath, String localizableResourcesJar) {
    withModule(moduleName, relativeJarPath)
  }

  /**
   * Include the project library to 'lib' directory or its subdirectory of the plugin distribution
   * @relativeOutputPath path relative to 'lib' plugin directory
   */
  void withProjectLibrary(String libraryName, String outPath = null) {
    layout.includedProjectLibraries.add(new ProjectLibraryData(libraryName, outPath, ProjectLibraryData.PackMode.MERGED))
  }

  void withProjectLibrary(String libraryName, ProjectLibraryData.PackMode packMode) {
    layout.includedProjectLibraries.add(new ProjectLibraryData(libraryName, null, packMode))
  }

  /**
   * Include the module library to the plugin distribution. Please note that it makes sense to call this method only
   * for additional modules which aren't copied directly to the 'lib' directory of the plugin distribution, because for ordinary modules
   * their module libraries are included into the layout automatically.
   * @param relativeOutputPath target path relative to 'lib' directory
   */
  void withModuleLibrary(String libraryName, String moduleName, String relativeOutputPath) {
    layout.includedModuleLibraries.add(new ModuleLibraryData(moduleName: moduleName, libraryName: libraryName,
                                                            relativeOutputPath: relativeOutputPath))
  }

  /**
   * Exclude the module library from plugin distribution.
   */
  void withoutModuleLibrary(String moduleName, String libraryName) {
    layout.excludedModuleLibraries.putValue(moduleName, libraryName)
  }

  /**
   * Exclude the specified files when {@code moduleName} is packed into JAR file.
   * <strong>This is a temporary method added to keep layout of some old plugins. If some files from a module shouldn't be included into the
   * module JAR it's strongly recommended to move these files outside of the module source roots.</strong>
   * @param excludedPattern Ant-like pattern describing files to be excluded (relatively to the module output root); e.g. {@code "foo/**"}
   * to exclude 'foo' directory
   */
  void excludeFromModule(String moduleName, String excludedPattern) {
    layout.excludeFromModule(moduleName, excludedPattern)
  }

  /**
   * Include an artifact output to the plugin distribution.
   * @param artifactName name of the project configuration
   * @param relativeOutputPath target path relative to 'lib' directory
   */
  void withArtifact(String artifactName, String relativeOutputPath) {
    layout.includedArtifacts.put(artifactName, relativeOutputPath)
  }

  /**
   * Include contents of JARs of the project library {@code libraryName} into JAR {@code jarName}
   */
  void withProjectLibraryUnpackedIntoJar(String libraryName, String jarName) {
    layout.withProjectLibraryUnpackedIntoJar(libraryName, jarName)
  }
}
