// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.images

import org.junit.runners.Parameterized.Parameters

class CommunityImageResourcesSanityTest : ImageResourcesTestBase() {
  companion object {
    @JvmStatic
    @Parameters(name = "{0}")
    fun data(): Collection<Array<Any?>> {
      return collectBadIcons(TestRoot.COMMUNITY)
    }
  }
}
