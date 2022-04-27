// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.projectStructureMapping

import java.nio.file.Path

/**
 * Represents production classes of a module
 */
class ModuleOutputEntry @JvmOverloads constructor(path: Path?,
                                                  val moduleName: String,
                                                  val size: Int,
                                                  val reason: String? = null) : DistributionFileEntry(path, "module-output")