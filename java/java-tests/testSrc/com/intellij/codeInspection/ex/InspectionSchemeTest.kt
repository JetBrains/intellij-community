// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.ex

import com.intellij.configurationStore.schemeManager.SchemeManagerFactoryBase
import com.intellij.openapi.options.SchemeState
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.rules.InMemoryFsRule
import com.intellij.testFramework.runInInitMode
import com.intellij.util.io.readText
import com.intellij.util.io.write
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test

class InspectionSchemeTest {
  companion object {
    @JvmField
    @ClassRule
    val projectRule = ProjectRule()
  }

  @JvmField
  @Rule
  val fsRule = InMemoryFsRule()

  @Test fun loadSchemes() {
    val schemeFile = fsRule.fs.getPath("inspection/Bar.xml")
    val schemeData = """
    <inspections version="1.0">
      <option name="myName" value="Bar" />
      <inspection_tool class="Since15" enabled="true" level="ERROR" enabled_by_default="true" />
    "</inspections>""".trimIndent()
    schemeFile.write(schemeData)
    val schemeManagerFactory = SchemeManagerFactoryBase.TestSchemeManagerFactory(fsRule.fs.getPath(""))
    val profileManager = ApplicationInspectionProfileManager(schemeManagerFactory)
    profileManager.forceInitProfiles(true)
    profileManager.initProfiles()

    assertThat(profileManager.profiles).hasSize(1)
    val scheme = profileManager.profiles.first()
    assertThat(scheme.schemeState).isEqualTo(SchemeState.UNCHANGED)
    assertThat(scheme.name).isEqualTo("Bar")

    runInInitMode { scheme.initInspectionTools(null) }

    runBlocking {
      schemeManagerFactory.save()
    }

    assertThat(scheme.schemeState).isEqualTo(SchemeState.UNCHANGED)

    assertThat(schemeFile.readText()).isEqualTo(schemeData)
    profileManager.profiles

    // test reload
    schemeManagerFactory.process {
      it.reload()
    }

    assertThat(profileManager.profiles).hasSize(1)
  }
}
