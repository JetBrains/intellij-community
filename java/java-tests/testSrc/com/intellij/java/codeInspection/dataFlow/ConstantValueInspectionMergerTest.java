// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection.dataFlow;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.dataFlow.ConstantValueInspection;
import com.intellij.codeInspection.dataFlow.DataFlowInspection;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionToolRegistrar;
import com.intellij.codeInspection.ex.ToolsImpl;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase5;
import org.jdom.JDOMException;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

public class ConstantValueInspectionMergerTest extends LightJavaCodeInsightFixtureTestCase5 {
  private static final String OLD_DISABLED = """
    <inspection_tool class="ConstantConditions" enabled="false" level="WARNING" enabled_by_default="false">
      <option name="SUGGEST_NULLABLE_ANNOTATIONS" value="false" />
      <option name="DONT_REPORT_TRUE_ASSERT_STATEMENTS" value="false" />
    </inspection_tool>
    """;

  private static final String OLD_ENABLED_IGNORE_ASSERT = """
    <inspection_tool class="ConstantConditions" enabled="true" level="WARNING" enabled_by_default="true">
      <option name="SUGGEST_NULLABLE_ANNOTATIONS" value="false" />
      <option name="DONT_REPORT_TRUE_ASSERT_STATEMENTS" value="false" />
      <option name="IGNORE_ASSERT_STATEMENTS" value="true" />
    </inspection_tool>
    """;

  private static final String OLD_ENABLED_DONT_REPORT_ASSERT = """
    <inspection_tool class="ConstantConditions" enabled="true" level="WARNING" enabled_by_default="true">
      <option name="SUGGEST_NULLABLE_ANNOTATIONS" value="false" />
      <option name="DONT_REPORT_TRUE_ASSERT_STATEMENTS" value="true" />
    </inspection_tool>
    """;

  @Test
  public void testDisabled() {
    var tool = getMergedTool(OLD_DISABLED, "DataFlowIssue");
    assertFalse(tool.isEnabled());
    tool = getMergedTool(OLD_DISABLED, "ConstantValue");
    assertFalse(tool.isEnabled());
  }

  @Test
  public void testDefault() {
    var tool = getMergedTool("", "DataFlowIssue");
    assertTrue(tool.isEnabled());
    tool = getMergedTool("", "ConstantValue");
    assertTrue(tool.isEnabled());
  }

  @Test
  public void testConstantValueOptions() {
    ConstantValueInspection cvi = (ConstantValueInspection)getMergedInspection("", "ConstantValue");
    assertFalse(cvi.IGNORE_ASSERT_STATEMENTS);
    assertFalse(cvi.DONT_REPORT_TRUE_ASSERT_STATEMENTS);
    cvi = (ConstantValueInspection)getMergedInspection(OLD_ENABLED_IGNORE_ASSERT, "ConstantValue");
    assertTrue(cvi.IGNORE_ASSERT_STATEMENTS);
    assertFalse(cvi.DONT_REPORT_TRUE_ASSERT_STATEMENTS);
    cvi = (ConstantValueInspection)getMergedInspection(OLD_ENABLED_DONT_REPORT_ASSERT, "ConstantValue");
    assertFalse(cvi.IGNORE_ASSERT_STATEMENTS);
    assertTrue(cvi.DONT_REPORT_TRUE_ASSERT_STATEMENTS);
  }

  @Test
  public void testDataFlowOptions() {
    DataFlowInspection dfi = (DataFlowInspection)getMergedInspection("", "DataFlowIssue");
    assertFalse(dfi.IGNORE_ASSERT_STATEMENTS);
    dfi = (DataFlowInspection)getMergedInspection(OLD_ENABLED_IGNORE_ASSERT, "DataFlowIssue");
    assertTrue(dfi.IGNORE_ASSERT_STATEMENTS);
  }

  private static InspectionProfileEntry getMergedInspection(String profileSettings, String inspectionName) {
    return getMergedTool(profileSettings, inspectionName).getInspectionTool(null).getTool();
  }

  private static ToolsImpl getMergedTool(String inspectionSettings, String inspectionName) {
    var profile = createProfile(inspectionSettings);
    InspectionProfileImpl.INIT_INSPECTIONS = true;
    var tool = profile.getToolsOrNull(inspectionName, null);
    InspectionProfileImpl.INIT_INSPECTIONS = false;
    assertNotNull(tool, inspectionName);
    return tool;
  }

  private static InspectionProfileImpl createProfile(String inspectionSettings) {
    var profileSettings = """
      <profile version="1.0">
      <option name="myName" value="Test" />
      %s
      </profile>
    """.formatted(inspectionSettings);
    var profile = new InspectionProfileImpl("Test", InspectionToolRegistrar.getInstance(), new InspectionProfileImpl("base"));
    try {
      profile.readExternal(JDOMUtil.load(profileSettings));
    }
    catch (IOException | JDOMException e) {
      throw new RuntimeException(e);
    }
    return profile;
  }
}
