package org.jetbrains.intellij.build.impl

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.BuildContext

@ApiStatus.Internal
class LibraryEntriesLayoutPatcher(
  @JvmField val libraryName: String,
  @JvmField val libraryModuleName: String,
  @JvmField val prefix: String,
  @JvmField val targetModuleName: String,
) : LayoutPatcher {
  override fun invoke(patcher: ModuleOutputPatcher, platformLayout: PlatformLayout, context: BuildContext) {
    val jars = context.outputProvider.findLibraryRoots(libraryName, moduleLibraryModuleName = libraryModuleName)
    if (jars.size != 1) {
      throw IllegalStateException("$libraryName is expected to have only one jar")
    }

    consumeDataByPrefix(jars[0], prefix) { name, data ->
      patcher.patchModuleOutput(moduleName = targetModuleName, path = name, content = data)
    }
  }
}
