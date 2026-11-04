// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.python

import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuildHttpSession
import org.jetbrains.intellij.build.dependencies.BuildDependenciesCommunityRoot
import org.jetbrains.intellij.build.dependencies.BuildDependenciesDownloader
import org.jetbrains.intellij.build.dev.ClassicDevRun
import org.jetbrains.intellij.build.dev.DevPluginLayoutAssetSpec
import org.jetbrains.intellij.build.impl.PluginLayout
import org.jetbrains.intellij.build.resolveAndExtractToCacheLocation
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * PY-92333: the native `pydevd_attach_to_process` binaries of the Python debugger helpers are built on TeamCity, not
 * committed. The `Python.Attach binaries` pipeline publishes one archive for both helpers trees to Space Files, keyed by
 * the revision it was built from, and `dependencies.properties` pins that revision.
 */
private const val PYTHON_ATTACH_TO_PROCESS_BUILD_PROPERTY: String = "pythonAttachToProcessBuild"

private const val PYDEVD_NATIVE_DEPS_URL: String = "https://cache-redirector.jetbrains.com/files/p/ij/intellij-dependencies/pydevd-native-deps"

private const val PYTHON_ATTACH_SPACE_FOLDER: String = "python-attach-to-process"

private const val PYTHON_ATTACH_ARCHIVE_NAME: String = "python-attach-to-process.tar.gz"

/**
 * One helpers tree with its own attach binaries. The two trees build from different sources, so their binaries differ.
 *
 * [archiveDir] is the directory of the tree in the archive. [helpersDir] is where the debugger of the tree looks the
 * binaries up, relative to the helpers root. The plugin and the checkout both use this layout.
 */
private class AttachBinariesTree(val archiveDir: String, val helpersDir: String, val binaries: List<String>)

/**
 * The binaries both trees build: Windows for both bitnesses, macOS, and Linux for both architectures. The plugin is
 * cross-platform, so all of them go into every distribution. The archive also holds the `.pdb` files, which nothing
 * loads, so they are not used.
 */
private val COMMON_ATTACH_BINARIES: List<String> = listOf(
  "attach_amd64.dll",
  "attach_x86.dll",
  "inject_dll_amd64.exe",
  "inject_dll_x86.exe",
  "run_code_on_dllmain_amd64.dll",
  "run_code_on_dllmain_x86.dll",
  "attach.dylib",
  "attach_linux_amd64.so",
  "attach_linux_aarch64.so",
)

private val ATTACH_BINARIES_TREES: List<AttachBinariesTree> = listOf(
  // pydev also builds the 32-bit Linux library.
  AttachBinariesTree("pydev", "pydev/pydevd_attach_to_process", COMMON_ATTACH_BINARIES + "attach_linux_x86.so"),
  AttachBinariesTree("debugpy", "debugpy/_vendored/pydevd/pydevd_attach_to_process", COMMON_ATTACH_BINARIES),
)

/**
 * Bundles the attach binaries of both helpers trees into the plugin. The binaries go through the checkout, see
 * [setupPythonAttachBinaries], so the classic dev build also runs this: it reads the helpers from the checkout.
 */
fun PluginLayout.PluginLayoutSpec.withBundledPydevdNativeDeps() {
  withGeneratedResources(DevPluginLayoutAssetSpec(omitted = true, classicDev = ClassicDevRun.RUN)) { targetDir, context ->
    copyPythonAttachBinaries(targetDir, context)
  }
}

private fun copyPythonAttachBinaries(targetDir: Path, context: BuildContext) {
  val communityRoot = context.paths.communityHomeDirRoot
  setupPythonAttachBinaries(communityRoot, context.httpSession)
  val helpersRoot = helpersRoot(communityRoot)
  val pluginHelpersRoot = targetDir.resolve(PythonCommunityPluginModules.HELPERS_PLUGIN_DIR)
  for (tree in ATTACH_BINARIES_TREES) {
    for (name in tree.binaries) {
      copyIfChanged(helpersRoot.resolve(tree.helpersDir).resolve(name), pluginHelpersRoot.resolve(tree.helpersDir).resolve(name))
    }
  }
}

/**
 * Puts the pinned attach binaries of both helpers trees into the checkout, where `.gitignore` keeps them out of git.
 * The IDE and the tests that run from sources read the helpers there. Call it before such a run.
 */
fun setupPythonAttachBinaries(communityRoot: BuildDependenciesCommunityRoot, httpSession: BuildHttpSession? = null) {
  val revision = BuildDependenciesDownloader.getDependencyProperties(communityRoot).property(PYTHON_ATTACH_TO_PROCESS_BUILD_PROPERTY)
  val url = "$PYDEVD_NATIVE_DEPS_URL/$PYTHON_ATTACH_SPACE_FOLDER/$revision/$PYTHON_ATTACH_ARCHIVE_NAME"
  val extractedDir = resolveAndExtractToCacheLocation(url, communityRoot, httpSession)
  val helpersRoot = helpersRoot(communityRoot)
  for (tree in ATTACH_BINARIES_TREES) {
    for (name in tree.binaries) {
      val binary = extractedDir.resolve(tree.archiveDir).resolve(name)
      check(Files.isRegularFile(binary)) {
        "The ${tree.archiveDir} attach binary '$name' is missing from $url"
      }
      copyIfChanged(binary, helpersRoot.resolve(tree.helpersDir).resolve(name))
    }
  }
}

/**
 * Calls [setupPythonAttachBinaries] when [testMainModule] is a Python test module. The debugger tests run the helpers
 * from the checkout and load the attach binaries, other tests do not need the download.
 */
fun setupPythonAttachBinariesForTests(communityRoot: BuildDependenciesCommunityRoot, testMainModule: String?) {
  if (testMainModule != null && testMainModule.startsWith("intellij.python.")) {
    setupPythonAttachBinaries(communityRoot)
  }
}

private fun helpersRoot(communityRoot: BuildDependenciesCommunityRoot): Path = communityRoot.communityRoot.resolve("python/helpers")

/**
 * Leaves an equal [target] in place: a running IDE or test can hold it open. Otherwise writes a sibling and moves it over
 * [target], so a parallel run never reads a partly written binary.
 */
private fun copyIfChanged(source: Path, target: Path) {
  if (Files.isRegularFile(target) && Files.mismatch(source, target) == -1L) {
    return
  }
  Files.createDirectories(target.parent)
  val temp = Files.createTempFile(target.parent, target.fileName.toString(), ".tmp")
  try {
    Files.copy(source, temp, StandardCopyOption.REPLACE_EXISTING)
    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
  }
  finally {
    Files.deleteIfExists(temp)
  }
}
