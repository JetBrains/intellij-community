// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon

import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.intellij.codeInspection.ex.InspectionToolRegistrar.Companion.getInstance
import com.intellij.codeInspection.ex.ToolsImpl
import com.intellij.codeInspection.javaDoc.JavadocDeclarationInspection
import com.intellij.codeInspection.javaDoc.MissingJavadocInspection
import com.intellij.openapi.util.JDOMUtil
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase5
import org.jdom.Element
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class JavadocSettingsMergerTest: LightJavaCodeInsightFixtureTestCase5() {

  @Test fun testDeclarationInspectionIsDisabled(){
    val tool = getMergedTool(disabledEverything, "JavadocDeclaration")
    assertFalse(tool.isEnabled)
  }

  @Test fun testDeclarationInspectionIsEnabled(){
    val tool = getMergedTool(enabledEverything, "JavadocDeclaration")
    assertTrue(tool.isEnabled)
  }

  @Test fun testMissingJavadocInspectionIsDisabled(){
    val tool = getMergedTool(disabledEverything, "MissingJavadoc")
    assertFalse(tool.isEnabled)
  }

  @Test fun testMissingJavadocInspectionIsDisabledByDefault(){
    val tool = getMergedTool("", "MissingJavadoc")
    assertFalse(tool.isEnabled)
  }

  @Test fun testMissingJavadocInspectionIsEnabled(){
    val tool = getMergedTool(enabledEverything, "MissingJavadoc")
    assertTrue(tool.isEnabled)
  }

  @Test fun testDeclarationInspectionEverythingEnabled(){
    val inspection: JavadocDeclarationInspection = getMergedInspection(enabledEverything, "JavadocDeclaration")
    assertTrue(inspection.IGNORE_PERIOD_PROBLEM)
    assertTrue(inspection.IGNORE_SELF_REFS)
    assertTrue(inspection.IGNORE_THROWS_DUPLICATE)
    assertEquals(inspection.ADDITIONAL_TAGS, "tag1, tag2")
  }

  @Test fun testDeclarationInspectionEverythingDisabled(){
    val inspection: JavadocDeclarationInspection = getMergedInspection(disabledEverything, "JavadocDeclaration")
    assertFalse(inspection.IGNORE_PERIOD_PROBLEM)
    assertFalse(inspection.IGNORE_SELF_REFS)
    assertFalse(inspection.IGNORE_THROWS_DUPLICATE)
    assertEquals(inspection.ADDITIONAL_TAGS, "")
  }

  @Test fun testMissingJavadocInspectionEverythingDisabled(){
    val inspection: MissingJavadocInspection = getMergedInspection(disabledEverything, "MissingJavadoc")
    assertFalse(inspection.IGNORE_ACCESSORS)
    assertFalse(inspection.IGNORE_DEPRECATED_ELEMENTS)
    val elementSettings = with (inspection) {
      listOf(METHOD_SETTINGS, FIELD_SETTINGS, MODULE_SETTINGS, PACKAGE_SETTINGS, INNER_CLASS_SETTINGS, TOP_LEVEL_CLASS_SETTINGS)
    }
    elementSettings.forEach { settings ->
      assertFalse(settings.ENABLED)
      assertEquals(settings.MINIMAL_VISIBILITY, "public")
    }
  }

  @Test fun testMissingJavadocInspectionEverythingEnabled(){
    val inspection: MissingJavadocInspection = getMergedInspection(enabledEverything, "MissingJavadoc")
    assertTrue(inspection.IGNORE_ACCESSORS)
    assertTrue(inspection.IGNORE_DEPRECATED_ELEMENTS)
    val elementSettings = with (inspection) {
      listOf(METHOD_SETTINGS, FIELD_SETTINGS, MODULE_SETTINGS, PACKAGE_SETTINGS, INNER_CLASS_SETTINGS, TOP_LEVEL_CLASS_SETTINGS)
    }
    elementSettings.forEach { settings ->
      assertTrue(settings.ENABLED)
      assertEquals(settings.MINIMAL_VISIBILITY, "protected")
      assertTrue(settings.isTagRequired("tag1"))
    }
  }

  @Test fun testJavadocDeclarationDefaultsNotWritten(){
    val inspection: JavadocDeclarationInspection = getMergedInspection(partiallyChanged, "JavadocDeclaration")
    val element = Element("tool").also { inspection.writeSettings(it) }
    val expected = """
      <tool>
        <option name="IGNORE_PERIOD_PROBLEM" value="false" />
      </tool>
    """
    assertEquals(expected.trimIndent(), JDOMUtil.write(element))
  }

  @Test fun testMissingJavadocDefaultsNotWritten(){
    val inspection: MissingJavadocInspection = getMergedInspection(partiallyChanged, "MissingJavadoc")
    val element = Element("tool").also { inspection.writeSettings(it) }
    val expected = """
      <tool>
        <option name="IGNORE_DEPRECATED_ELEMENTS" value="true" />
        <option name="TOP_LEVEL_CLASS_SETTINGS">
          <Options>
            <option name="MINIMAL_VISIBILITY" value="package" />
            <option name="REQUIRED_TAGS" value="@since" />
          </Options>
        </option>
      </tool>
    """
    assertEquals(expected.trimIndent(), JDOMUtil.write(element))
  }

  private fun getMergedTool(inspectionSettings: String, inspectionName: String): ToolsImpl {
    val profile = createProfile(inspectionSettings)
    InspectionProfileImpl.INIT_INSPECTIONS = true
    val tool = profile.getToolsOrNull(inspectionName, null)
    InspectionProfileImpl.INIT_INSPECTIONS = false
    return tool ?: throw IllegalArgumentException("Inspection is not found: $inspectionName")
  }

  private fun createProfile(inspectionSettings: String): InspectionProfileImpl {
    val profileName = "Test"
    val profileSettings = """
      <profile version="1.0">
      <option name="myName" value="$profileName" />
      $inspectionSettings
      </profile>
    """
    val profile = InspectionProfileImpl(profileName, getInstance(), InspectionProfileImpl("base"))
    profile.readExternal(JDOMUtil.load(profileSettings))
    return profile
  }

  private inline fun <reified T: InspectionProfileEntry> getMergedInspection(profileSettings: String, inspectionName: String): T {
    return getMergedTool(profileSettings, inspectionName).getInspectionTool(null).tool as T
  }

  private val disabledEverything = """
    <inspection_tool class="JavaDoc" enabled="false" level="WARNING" enabled_by_default="false">
      <option name="TOP_LEVEL_CLASS_OPTIONS">
        <value>
          <option name="ACCESS_JAVADOC_REQUIRED_FOR" value="none" />
          <option name="REQUIRED_TAGS" value="" />
        </value>
      </option>
      <option name="INNER_CLASS_OPTIONS">
        <value>
          <option name="ACCESS_JAVADOC_REQUIRED_FOR" value="none" />
          <option name="REQUIRED_TAGS" value="" />
        </value>
      </option>
      <option name="METHOD_OPTIONS">
        <value>
          <option name="ACCESS_JAVADOC_REQUIRED_FOR" value="none" />
          <option name="REQUIRED_TAGS" value="" />
        </value>
      </option>
      <option name="FIELD_OPTIONS">
        <value>
          <option name="ACCESS_JAVADOC_REQUIRED_FOR" value="none" />
          <option name="REQUIRED_TAGS" value="" />
        </value>
      </option>
      <option name="IGNORE_DEPRECATED" value="false" />
      <option name="IGNORE_JAVADOC_PERIOD" value="false" />
      <option name="IGNORE_DUPLICATED_THROWS" value="false" />
      <option name="IGNORE_POINT_TO_ITSELF" value="false" />
      <option name="myAdditionalJavadocTags" value="" />
      <IGNORE_DUPLICATED_THROWS_TAGS value="false" />
    </inspection_tool>
  """

  private val enabledEverything = """
    <inspection_tool class="JavaDoc" enabled="true" level="WARNING" enabled_by_default="true">
      <option name="TOP_LEVEL_CLASS_OPTIONS">
        <value>
          <option name="ACCESS_JAVADOC_REQUIRED_FOR" value="protected" />
          <option name="REQUIRED_TAGS" value="@tag1" />
        </value>
      </option>
      <option name="INNER_CLASS_OPTIONS">
        <value>
          <option name="ACCESS_JAVADOC_REQUIRED_FOR" value="protected" />
          <option name="REQUIRED_TAGS" value="@tag1" />
        </value>
      </option>
      <option name="METHOD_OPTIONS">
        <value>
          <option name="ACCESS_JAVADOC_REQUIRED_FOR" value="protected" />
          <option name="REQUIRED_TAGS" value="@return@param@throws or @exception@tag1" />
        </value>
      </option>
      <option name="FIELD_OPTIONS">
        <value>
          <option name="ACCESS_JAVADOC_REQUIRED_FOR" value="protected" />
          <option name="REQUIRED_TAGS" value="@tag1" />
        </value>
      </option>
      <option name="IGNORE_DEPRECATED" value="true" />
      <option name="IGNORE_JAVADOC_PERIOD" value="true" />
      <option name="IGNORE_DUPLICATED_THROWS" value="false" />
      <option name="IGNORE_POINT_TO_ITSELF" value="true" />
      <option name="myAdditionalJavadocTags" value="tag1, tag2" />
      <IGNORE_ACCESSORS value="true" />
      <option name="MODULE_OPTIONS">
        <option name="ACCESS_JAVADOC_REQUIRED_FOR" value="protected" />
        <option name="REQUIRED_TAGS" value="@tag1" />
      </option>
      <option name="ACCESS_JAVADOC_REQUIRED_FOR" value="protected" />
      <option name="REQUIRED_TAGS" value="@tag1" />
    </inspection_tool>
  """

  private val partiallyChanged = """
    <inspection_tool class="JavaDoc" enabled="true" level="WARNING" enabled_by_default="true">
      <option name="TOP_LEVEL_CLASS_OPTIONS">
        <value>
          <option name="ACCESS_JAVADOC_REQUIRED_FOR" value="package" />
          <option name="REQUIRED_TAGS" value="@since" />
        </value>
      </option>
      <option name="INNER_CLASS_OPTIONS">
        <value>
          <option name="ACCESS_JAVADOC_REQUIRED_FOR" value="public" />
          <option name="REQUIRED_TAGS" value="" />
        </value>
      </option>
      <option name="METHOD_OPTIONS">
        <value>
          <option name="ACCESS_JAVADOC_REQUIRED_FOR" value="public" />
          <option name="REQUIRED_TAGS" value="@return@param@throws or @exception" />
        </value>
      </option>
      <option name="FIELD_OPTIONS">
        <value>
          <option name="ACCESS_JAVADOC_REQUIRED_FOR" value="public" />
          <option name="REQUIRED_TAGS" value="" />
        </value>
      </option>
      <option name="IGNORE_DEPRECATED" value="true" />
      <option name="IGNORE_JAVADOC_PERIOD" value="false" />
      <option name="IGNORE_DUPLICATED_THROWS" value="false" />
      <option name="IGNORE_POINT_TO_ITSELF" value="false" />
      <option name="myAdditionalJavadocTags" value="" />
      <option name="MODULE_OPTIONS">
        <option name="ACCESS_JAVADOC_REQUIRED_FOR" value="public" />
        <option name="REQUIRED_TAGS" value="" />
      </option>
      <option name="ACCESS_JAVADOC_REQUIRED_FOR" value="public" />
      <option name="REQUIRED_TAGS" value="" />
    </inspection_tool>
  """
}