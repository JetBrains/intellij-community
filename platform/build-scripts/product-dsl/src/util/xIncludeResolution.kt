// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.util

import org.jetbrains.intellij.build.DescriptorDependencyWalk
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.resolveDescriptor
import org.jetbrains.jps.model.module.JpsModule

/**
 * Resolves an `xi:include` path relative to a JPS module. See [resolveDescriptor] for the order and the reason for it.
 *
 * [prefix] keeps the dependency walk inside one product, so a walk of a shared module does not answer from another one.
 */
internal fun resolveXIncludeBytes(
  path: String,
  module: JpsModule,
  outputProvider: ModuleOutputProvider,
  prefix: String?,
  declaredOwner: JpsModule? = null,
): ByteArray? {
  return resolveDescriptor(
    module = module,
    path = path,
    outputProvider = outputProvider,
    declaredOwner = declaredOwner,
    walk = DescriptorDependencyWalk(includePrefix = prefix),
  )
}
