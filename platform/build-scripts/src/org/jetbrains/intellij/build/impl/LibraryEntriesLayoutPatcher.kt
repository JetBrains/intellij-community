package org.jetbrains.intellij.build.impl

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.BuildContext
import java.nio.ByteBuffer
import java.nio.file.Files

/**
 * Copies the entries under [prefix] of the module library [libraryName] into the output of [targetModuleName].
 *
 * The copied entries skip the native handling of a library jar, so the patcher signs a macOS native itself,
 * as [NativeFileHandlerImpl.sign] does for a library jar.
 */
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

    val nativeFileHandler = NativeFileHandlerImpl(context)
    consumeDataByPrefix(jars[0], prefix) { name, data ->
      val signed = if (nativeFileHandler.isNative(name)) nativeFileHandler.sign(name) { ByteBuffer.wrap(data) } else null
      val content = if (signed == null) {
        data
      }
      else {
        try {
          Files.readAllBytes(signed)
        }
        finally {
          Files.delete(signed)
        }
      }
      patcher.patchModuleOutput(moduleName = targetModuleName, path = name, content = content)
    }
  }
}
