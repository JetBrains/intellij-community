// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection.ex

import com.intellij.JavaTestUtil
import com.intellij.analysis.AnalysisScope
import com.intellij.codeInsight.HeavyJavaCodeInsightFixture.Companion.heavyJavaCodeInsightFixture
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool
import com.intellij.codeInspection.GlobalInspectionContext
import com.intellij.codeInspection.HTMLComposer
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ex.GlobalInspectionContextBase
import com.intellij.codeInspection.ex.InspectionManagerEx
import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.intellij.codeInspection.ex.InspectionToolWrapper
import com.intellij.codeInspection.ex.InspectionToolsSupplier
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper
import com.intellij.codeInspection.ex.PairedUnfairLocalInspectionTool
import com.intellij.codeInspection.ex.Tools
import com.intellij.codeInspection.lang.GlobalInspectionContextExtension
import com.intellij.codeInspection.lang.HTMLComposerExtension
import com.intellij.codeInspection.lang.InspectionExtensionsFactory
import com.intellij.codeInspection.lang.RefManagerExtension
import com.intellij.codeInspection.reference.RefManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.util.Key
import com.intellij.profile.codeInspection.BaseInspectionProfileManager
import com.intellij.profile.codeInspection.InspectionProfileManager
import com.intellij.psi.PsiElement
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.disableAllTools
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicBoolean

@TestApplication
class GlobalInspectionContextToolsTest {
  private val fixture by heavyJavaCodeInsightFixture(JavaTestUtil.getJavaTestDataPath() + "/inspection/globalContext/")

  @BeforeEach
  fun setUp() {
    InspectionProfileImpl.INIT_INSPECTIONS = true
  }

  @AfterEach
  fun tearDown() {
    InspectionProfileImpl.INIT_INSPECTIONS = false
  }

  @Test
  fun testPreInitTools(): Unit = timeoutRunBlocking {
    val preInitCalled = AtomicBoolean()
    val preRunCalled = AtomicBoolean()
    InspectionExtensionsFactory.EP_NAME.point
      .registerExtension(GICTestExtensionFactory(preInitCalled, preRunCalled), fixture.testRootDisposable)
    val profile = InspectionProfileImpl("Foo", object : InspectionToolsSupplier() {
      override fun createTools(): List<InspectionToolWrapper<*, *>> {
        val tools = ArrayList<InspectionToolWrapper<*, *>>()
        tools.add(LocalInspectionToolWrapper(JavaDummyTestInspection()))
        tools.add(LocalInspectionToolWrapper(JavaDummyPairedTestInspection()))
        return tools
      }
    }, (InspectionProfileManager.getInstance() as BaseInspectionProfileManager))
    profile.disableAllTools()
    profile.enableTool(JavaDummyTestInspection().shortName, fixture.project)

    val context = (InspectionManager.getInstance(fixture.project) as InspectionManagerEx).createNewGlobalContext()
    context.setExternalProfile(profile)
    fixture.configureByFile("Foo.java")

    val scope = readAction { AnalysisScope(fixture.file!!) }
    withContext(Dispatchers.EDT) {
      context.doInspections(scope)
      UIUtil.dispatchAllInvocationEvents() // wait for launchInspections in invokeLater
    }
    assertTrue(preInitCalled.get())
    assertTrue(preRunCalled.get())
  }
}

private class JavaDummyPairedTestInspection : AbstractBaseJavaLocalInspectionTool() {
  override fun getShortName(): String {
    return "JavaDummyPairedTestInspection"
  }
}

private class JavaDummyTestInspection : AbstractBaseJavaLocalInspectionTool(), PairedUnfairLocalInspectionTool {
  override fun getShortName(): String {
    return "JavaDummyTestInspection"
  }

  override fun getInspectionForBatchShortName(): String {
    return "JavaDummyPairedTestInspection"
  }
}

private class GICTestExtension(private val myPreInitCalled: AtomicBoolean, private val myPreRunCalled: AtomicBoolean)
  : GlobalInspectionContextExtension<GICTestExtension> {
  override fun getID(): Key<GICTestExtension> {
    return ID
  }

  override fun performPreInitToolsActivities(usedTools: List<Tools>, context: GlobalInspectionContext) {
    myPreInitCalled.set(true)
    assertExpectedTools(usedTools, JavaDummyTestInspection::class.java)
    assertFalse((context as GlobalInspectionContextBase).areToolsInitialized())
  }

  override fun performPreRunActivities(globalTools: List<Tools>,
                                       localTools: List<Tools>,
                                       context: GlobalInspectionContext) {
    myPreRunCalled.set(true)
    assertExpectedTools(localTools, JavaDummyTestInspection::class.java, JavaDummyPairedTestInspection::class.java)
    assertTrue((context as GlobalInspectionContextBase).areToolsInitialized())
  }

  override fun performPostRunActivities(inspections: List<InspectionToolWrapper<*, *>?>,
                                        context: GlobalInspectionContext) {
  }

  override fun cleanup() {}

  companion object {
    private val ID: Key<GICTestExtension> = Key.create("GICTestExtension")

    private fun assertExpectedTools(tools: List<Tools>, vararg expectedClasses: Class<out LocalInspectionTool?>) {
      assertEquals(expectedClasses.size, tools.size)
      val toolsSet = ContainerUtil.map2Set<Tools, Class<out InspectionProfileEntry?>>(tools) { tts: Tools -> tts.tool.tool.javaClass }
      for (expectedCls in expectedClasses) {
        assertTrue(toolsSet.contains(expectedCls))
      }
    }
  }
}

private class GICTestExtensionFactory(private val myPreInitCalled: AtomicBoolean, private val myPreRunCalled: AtomicBoolean) : InspectionExtensionsFactory() {
  override fun createGlobalInspectionContextExtension(): GICTestExtension {
    return GICTestExtension(myPreInitCalled, myPreRunCalled)
  }

  override fun createRefManagerExtension(refManager: RefManager): RefManagerExtension<*>? {
    return null
  }

  override fun createHTMLComposerExtension(composer: HTMLComposer): HTMLComposerExtension<*>? {
    return null
  }

  override fun isToCheckMember(element: PsiElement, id: String): Boolean {
    return false
  }

  override fun getSuppressedInspectionIdsIn(element: PsiElement): String? {
    return ""
  }
}