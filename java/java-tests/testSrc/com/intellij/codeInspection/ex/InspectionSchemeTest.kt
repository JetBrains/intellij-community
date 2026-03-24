// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.ex

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.codeInsight.daemon.impl.SeveritiesProvider
import com.intellij.codeInsight.daemon.impl.SeverityRegistrar
import com.intellij.configurationStore.schemeManager.SchemeManagerFactoryBase
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.options.SchemeManager
import com.intellij.openapi.options.SchemeState
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.rules.InMemoryFsExtension
import com.intellij.testFramework.runInInitMode
import com.intellij.util.io.write
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.awt.Component
import java.awt.Graphics
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.Icon
import kotlin.io.path.readText

@TestApplication
class InspectionSchemeTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val project get() = projectFixture.get()

  @JvmField
  @RegisterExtension
  val fsRule = InMemoryFsExtension()

  @Test
  fun loadSchemes(@TestDisposable testRootDisposable: Disposable): Unit = runBlocking(Dispatchers.Default) {
    val schemeFile = fsRule.fs.getPath("inspection/Bar.xml")
    val schemeData = """
    <inspections version="1.0">
      <option name="myName" value="Bar" />
      <inspection_tool class="Since15" enabled="true" level="ERROR" enabled_by_default="true" />
    "</inspections>""".trimIndent()
    schemeFile.write(schemeData)
    val schemeManagerFactory = SchemeManagerFactoryBase.TestSchemeManagerFactory(fsRule.fs.getPath(""))
    val profileManager = ApplicationInspectionProfileManager(this@runBlocking, schemeManagerFactory)
    profileManager.forceInitProfilesInTestUntil(testRootDisposable)
    profileManager.initProfiles()

    assertThat(profileManager.profiles).hasSize(1)
    val scheme = profileManager.profiles.first()
    assertThat(scheme.schemeState).isEqualTo(SchemeState.UNCHANGED)
    assertThat(scheme.name).isEqualTo("Bar")

    runInInitMode { scheme.initInspectionTools(null) }

    schemeManagerFactory.save()

    assertThat(scheme.schemeState).isEqualTo(SchemeState.UNCHANGED)

    assertThat(schemeFile.readText()).isEqualTo(schemeData)
    profileManager.profiles

    // test reload
    schemeManagerFactory.process {
      it.reload()
    }

    assertThat(profileManager.profiles).hasSize(1)
  }

  @Test
  fun preloadedSeverityProviderDoesNotPublishEventsDuringProfileManagerInit(@TestDisposable disposable: Disposable): Unit =
    runBlocking(Dispatchers.Default) {
      val infoType = HighlightInfoType.HighlightInfoTypeImpl(
        HighlightSeverity("PRELOADED_DYNAMIC", 400),
        TextAttributesKey.createTextAttributesKey("PRELOADED_DYNAMIC_KEY"),
      )
      SeveritiesProvider.EP_NAME.point.registerExtension(object : SeveritiesProvider() {
        override fun getSeveritiesHighlightInfoTypes(): List<HighlightInfoType> = listOf(infoType)
      }, disposable)

      val notifications = AtomicInteger()
      project.messageBus.connect(disposable).subscribe(SeverityRegistrar.SEVERITIES_CHANGED_TOPIC, Runnable { notifications.incrementAndGet() })

      val profileManager = ApplicationInspectionProfileManager(
        this@runBlocking,
        SchemeManagerFactoryBase.TestSchemeManagerFactory(fsRule.fs.getPath("")),
      )

      assertThat(profileManager.severityRegistrar.getSeverity("PRELOADED_DYNAMIC")).isNotNull()
      assertThat(notifications.get()).isZero()
    }

  @Test
  fun iconableSeverityProviderIsLazyAndPreservedInStandardTypes(@TestDisposable disposable: Disposable): Unit =
    runBlocking(Dispatchers.Default) {
      val iconRequests = AtomicInteger()
      val infoType = CountingIconableInfoType(
        HighlightSeverity("ICONABLE_DYNAMIC", 352),
        TextAttributesKey.createTextAttributesKey("ICONABLE_DYNAMIC_KEY"),
        iconRequests,
        TestIcon(width = 11, height = 13),
      )
      val profileManager = ApplicationInspectionProfileManager(
        this@runBlocking,
        SchemeManagerFactoryBase.TestSchemeManagerFactory(fsRule.fs.getPath("")),
      )

      SeveritiesProvider.EP_NAME.point.registerExtension(object : SeveritiesProvider() {
        override fun getSeveritiesHighlightInfoTypes(): List<HighlightInfoType> = listOf(infoType)
      }, disposable)

      assertThat(iconRequests.get()).isZero()

      val severity = profileManager.severityRegistrar.getSeverity("ICONABLE_DYNAMIC")
      assertThat(severity).isNotNull()
      val level = HighlightDisplayLevel.find(checkNotNull(severity))
      assertThat(level).isNotNull()
      assertThat(findIconableDynamicStandardSeverityType()).isInstanceOf(HighlightInfoType.Iconable::class.java)
      assertThat(HighlightDisplayLevel.find("ICONABLE_DYNAMIC")).isSameAs(level)
      assertThat(level!!.icon).isSameAs(level.outlineIcon)

      level.icon.iconWidth
      level.outlineIcon.iconWidth
      assertThat(iconRequests.get()).isEqualTo(1)
    }

  @Test
  fun bundledProfileUsesSelfAsBaseAndResetsToBundled() {
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

    assertThat(bundledProfile).isNotNull()
    assertThat(bundledProfile?.name).isEqualTo(profileName)

    runInInitMode<Any?> {
      bundledProfile?.initInspectionTools(project)
    }

    bundledProfile?.enableTool("AssignmentToForLoopParameter", project)
    val key = HighlightDisplayKey.find("AssignmentToForLoopParameter")
    assertThat(bundledProfile?.isToolEnabled(key, null)).isTrue()

    bundledProfile?.resetToBase(project)
    assertThat(bundledProfile?.isToolEnabled(key, null)).isFalse()
  }

  private fun findIconableDynamicStandardSeverityType(): HighlightInfoType {
    return SeverityRegistrar.standardSeverities().first { "ICONABLE_DYNAMIC" == it.getSeverity(null).name }
  }

  private class CountingIconableInfoType(
    severity: HighlightSeverity,
    attributesKey: TextAttributesKey,
    private val iconRequests: AtomicInteger,
    private val icon: Icon,
  ) : HighlightInfoType.HighlightInfoTypeImpl(severity, attributesKey), HighlightInfoType.Iconable {
    override fun getIcon(): Icon {
      iconRequests.incrementAndGet()
      return icon
    }
  }

  private class TestIcon(private val width: Int, private val height: Int) : Icon {
    override fun paintIcon(c: Component?, g: Graphics?, x: Int, y: Int) {
    }

    override fun getIconWidth(): Int = width

    override fun getIconHeight(): Int = height
  }
}
