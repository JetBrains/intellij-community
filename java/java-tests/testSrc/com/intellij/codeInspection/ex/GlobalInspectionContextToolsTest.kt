// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ex

import com.intellij.JavaTestUtil
import com.intellij.analysis.AnalysisScope
import com.intellij.codeInsight.JavaCodeInsightTestCase
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool
import com.intellij.codeInspection.GlobalInspectionContext
import com.intellij.codeInspection.HTMLComposer
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.lang.GlobalInspectionContextExtension
import com.intellij.codeInspection.lang.HTMLComposerExtension
import com.intellij.codeInspection.lang.InspectionExtensionsFactory
import com.intellij.codeInspection.lang.RefManagerExtension
import com.intellij.codeInspection.reference.RefManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Key
import com.intellij.profile.codeInspection.BaseInspectionProfileManager
import com.intellij.profile.codeInspection.InspectionProfileManager
import com.intellij.psi.PsiElement
import com.intellij.testFramework.disableAllTools
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.UIUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import java.util.concurrent.atomic.AtomicBoolean

class GlobalInspectionContextToolsTest : JavaCodeInsightTestCase() {
  @Throws(java.lang.Exception::class)
  public override fun setUp() {
    super.setUp()
    InspectionProfileImpl.INIT_INSPECTIONS = true
    ThreadingAssertions.assertEventDispatchThread()
    assertFalse(ApplicationManager.getApplication().isWriteAccessAllowed)
  }

  @Throws(java.lang.Exception::class)
  public override fun tearDown() {
    InspectionProfileImpl.INIT_INSPECTIONS = false
    super.tearDown()
  }

  override fun getTestDataPath(): String {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection/globalContext/"
  }

  @Throws(Exception::class)
  fun testPreInitTools() {
    val preInitCalled = AtomicBoolean()
    val preRunCalled = AtomicBoolean()
    InspectionExtensionsFactory.EP_NAME.point
      .registerExtension(GICTestExtensionFactory(preInitCalled, preRunCalled), getTestRootDisposable())
    val profile = InspectionProfileImpl("Foo", object : InspectionToolsSupplier() {
      override fun createTools(): List<InspectionToolWrapper<*, *>> {
        val tools = ArrayList<InspectionToolWrapper<*, *>>()
        tools.add(LocalInspectionToolWrapper(JavaDummyTestInspection()))
        tools.add(LocalInspectionToolWrapper(JavaDummyPairedTestInspection()))
        return tools
      }
    }, (InspectionProfileManager.getInstance() as BaseInspectionProfileManager))
    profile.disableAllTools()
    profile.enableTool(JavaDummyTestInspection().shortName, getProject())

    val context = (InspectionManager.getInstance(project) as InspectionManagerEx).createNewGlobalContext()
    context.setExternalProfile(profile)
    configureByFile("Foo.java")

    val scope = AnalysisScope(file)
    context.doInspections(scope)
    UIUtil.dispatchAllInvocationEvents() // wait for launchInspections in invokeLater
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