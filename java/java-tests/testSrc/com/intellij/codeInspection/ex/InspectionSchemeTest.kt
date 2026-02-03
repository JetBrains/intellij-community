// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.ex

import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.configurationStore.schemeManager.SchemeManagerFactoryBase
import com.intellij.openapi.options.SchemeManager
import com.intellij.openapi.options.SchemeState
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.rules.InMemoryFsRule
import com.intellij.testFramework.runInInitMode
import com.intellij.util.io.write
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlin.io.path.readText

class InspectionSchemeTest {
  companion object {
    @JvmField
    @ClassRule
    val projectRule = ProjectRule()
    @JvmField
    @ClassRule
    val testRootDisposable = DisposableRule()
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
    profileManager.forceInitProfilesInTestUntil(testRootDisposable.disposable)
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

  @Test fun bundledProfileUsesSelfAsBaseAndResetsToBundled() {
    val schemeManagerFactory = SchemeManagerFactoryBase.TestSchemeManagerFactory(fsRule.fs.getPath(""))

    class TestAppIPM : ApplicationInspectionProfileManagerBase(schemeManagerFactory) {
      val schemeManagerPublic: SchemeManager<InspectionProfileImpl>
        get() = super.schemeManager
    }

    val profileManager = TestAppIPM()

    val profileName = "BundledProfile"
    val resourcePath = "/com/intellij/codeInspection/ex/test/${profileName}.xml"
    val xml = """
      <component name="InspectionProjectProfileManager">
        <profile version="1.0">
          <option name="myName" value="${profileName}"/>
          <inspection_tool class="AssignmentToForLoopParameter" enabled="false" level="WARNING" enabled_by_default="false" />
        </profile>
      </component>
    """.trimIndent()

    val cl = object : ClassLoader(InspectionSchemeTest::class.java.getClassLoader()) {
      override fun getResourceAsStream(name: String): InputStream? {
        if (name == resourcePath.substring(1)) {
          return ByteArrayInputStream(xml.toByteArray())
        }
        return super.getResourceAsStream(name)
      }
    }

    val schemeManager = profileManager.schemeManagerPublic
    val bundledProfile = schemeManager.loadBundledScheme(resourcePath, cl, null)?.modifiableModel

    Assertions.assertThat(bundledProfile).isNotNull()
    Assertions.assertThat(bundledProfile?.name).isEqualTo(profileName)

    runInInitMode<Any?> {
      bundledProfile?.initInspectionTools(projectRule.project)
    }

    bundledProfile?.enableTool("AssignmentToForLoopParameter", projectRule.project)
    val key = HighlightDisplayKey.find("AssignmentToForLoopParameter")
    Assertions.assertThat(bundledProfile?.isToolEnabled(key, null)).isTrue()

    bundledProfile?.resetToBase(projectRule.project)
    Assertions.assertThat(bundledProfile?.isToolEnabled(key, null)).isFalse()
  }
}
