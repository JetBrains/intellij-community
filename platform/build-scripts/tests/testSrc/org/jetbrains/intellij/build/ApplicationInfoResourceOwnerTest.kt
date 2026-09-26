// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.intellij.build.impl.checkApplicationInfoResourceOwner
import org.junit.jupiter.api.Test

private const val APP_INFO_MODULE = "intellij.idea.ultimate.customization"
private const val RESOURCE = "idea/ApplicationInfo.xml"

/** The platform layout packs `idea/<prefix>ApplicationInfo.xml` from the application-info module and from no other module. */
class ApplicationInfoResourceOwnerTest {
  @Test
  fun `the application-info module is the one owner`() {
    val owners = setOf(APP_INFO_MODULE)
    assertThatCode {
      checkApplicationInfoResourceOwner(
        platformModules = listOf("intellij.platform.core", APP_INFO_MODULE, "intellij.platform.ide.impl"),
        applicationInfoModule = APP_INFO_MODULE,
        resourcePath = RESOURCE,
        holdsResource = owners::contains,
      )
    }.doesNotThrowAnyException()
  }

  @Test
  fun `a second owner fails the layout and is named`() {
    val owners = setOf(APP_INFO_MODULE, "intellij.qodanaMin")
    assertThatThrownBy {
      checkApplicationInfoResourceOwner(
        platformModules = listOf("intellij.platform.core", APP_INFO_MODULE, "intellij.qodanaMin"),
        applicationInfoModule = APP_INFO_MODULE,
        resourcePath = RESOURCE,
        holdsResource = owners::contains,
      )
    }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining(RESOURCE)
      .hasMessageContaining(APP_INFO_MODULE)
      .hasMessageContaining("intellij.qodanaMin")
  }

  @Test
  fun `the application-info module itself is not asked`() {
    val asked = ArrayList<String>()
    checkApplicationInfoResourceOwner(
      platformModules = listOf("intellij.platform.core", APP_INFO_MODULE),
      applicationInfoModule = APP_INFO_MODULE,
      resourcePath = RESOURCE,
    ) {
      asked.add(it)
      false
    }
    assertThat(asked).containsExactly("intellij.platform.core")
  }
}
