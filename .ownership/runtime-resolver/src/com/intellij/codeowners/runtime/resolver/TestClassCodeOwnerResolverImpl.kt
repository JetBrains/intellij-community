// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeowners.runtime.resolver

import com.intellij.codeowners.monorepo.resolver.TestOwnerResolver
import com.intellij.codeowners.scripts.common.ultimateRoot
import com.intellij.testFramework.RuntimeCodeOwnerResolver
import com.intellij.tests.TestLocationStorage
import kotlin.io.path.div

class TestClassCodeOwnerResolverImpl : RuntimeCodeOwnerResolver {
  private val resolver: TestOwnerResolver? by lazy { createResolver() }

  override fun getOwnerGroupName(testClass: Class<*>): String? {
    val r = resolver ?: return null
    val locationInfo = TestLocationStorage.getTestLocationInfo(testClass) ?: return null
    return r.getOwner(locationInfo.moduleName(), locationInfo.packagePath(), locationInfo.fileName())?.group?.fullName
  }

  private fun createResolver(): TestOwnerResolver? {
    val dir = ultimateRoot / "out" / "artifacts" / "codeowners"
    return TestOwnerResolver.create(ultimateRoot, dir / "file-locations.ndjson", dir / "module-paths.ndjson")
  }
}
