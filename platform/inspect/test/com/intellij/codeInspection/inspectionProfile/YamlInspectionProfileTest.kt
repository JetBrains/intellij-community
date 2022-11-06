// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.inspectionProfile

import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.TestDataPath
import junit.framework.TestCase
import org.jdom.Element
import java.io.File

@TestDataPath("\$CONTENT_ROOT/testData/")
class YamlInspectionProfileTest: LightPlatformTestCase() {

  override fun getTestDirectoryName(): String = PathManager.getCommunityHomePath() + "/platform/inspect/testData/"

  fun testBasic(){ checkEffectiveProfile() }

  fun testChainedInclude(){ checkEffectiveProfile() }

  fun testChainedOverride(){ checkEffectiveProfile() }

  fun testGroup(){ checkEffectiveProfile() }

  fun testPredefinedGroup(){ checkEffectiveProfile() }

  fun testGroupExclusion(){ checkEffectiveProfile() }

  private fun checkEffectiveProfile(){
    val fileName = getTestName(true)
    val expectedProfileText = FileUtil.loadFile(File("$testDirectoryName/${fileName}_profile.xml"))
    InspectionProfileImpl.INIT_INSPECTIONS = true
    val effectiveProfile = YamlInspectionProfileImpl.loadFrom(project, "$testDirectoryName/$fileName.yml").buildEffectiveProfile()
    val externalizedText = getExternalizedText(effectiveProfile)
    TestCase.assertEquals(expectedProfileText, externalizedText)
  }

  private fun getExternalizedText(profile: InspectionProfileImpl): String {
    val element = Element("profile")
    profile.writeExternal(element)
    return JDOMUtil.write(element)
  }
}