// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.testFramework

import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.ProductProperties
import org.jetbrains.intellij.build.ProprietaryBuildTools

fun createBuildContext(homePath: String, productProperties: ProductProperties,
                       buildTools: ProprietaryBuildTools?,
                       skipDependencySetup: Boolean = false, communityHomePath: String = "$homePath/community"
): BuildContext {
  val options = BuildOptions()
  options.isSkipDependencySetup = skipDependencySetup
  options.isIsTestBuild = true
  options.buildStepsToSkip.add(BuildOptions.getTEAMCITY_ARTIFACTS_PUBLICATION())
  options.outputRootPath = FileUtil.createTempDirectory("test-build-${productProperties.baseFileName}", null, true).absolutePath
  if (options.pathToCompiledClassesArchive == null && options.pathToCompiledClassesArchivesMetadata == null && options.isIsInDevelopmentMode) {
    //skip compilation when running tests locally
    options.isUseCompiledClassesFromProjectOutput = true
  }
  return BuildContext.createContext(communityHomePath, homePath, productProperties, buildTools, options)
}
