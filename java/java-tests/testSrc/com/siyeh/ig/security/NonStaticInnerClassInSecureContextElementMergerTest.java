// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.security;

import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionToolRegistrar;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.testFramework.junit5.TestApplication;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
public class NonStaticInnerClassInSecureContextElementMergerTest {
  private InspectionProfileImpl myProfile;

  @BeforeEach
  void setUp() {
    InspectionProfileImpl.INIT_INSPECTIONS = true;
    myProfile = new InspectionProfileImpl("Test", InspectionToolRegistrar.getInstance(), new InspectionProfileImpl("base"));
  }

  @AfterEach
  void tearDown() {
    InspectionProfileImpl.INIT_INSPECTIONS = false;
  }

  @Test
  public void testDefaultValues() {
    myProfile.initInspectionTools();
    assertFalse(myProfile.getToolsOrNull("PrivateMemberAccessBetweenOuterAndInnerClass", null).isEnabled());
    assertNull(myProfile.getToolsOrNull("NonStaticInnerClassInSecureContext", null));
    myProfile.setToolEnabled("PrivateMemberAccessBetweenOuterAndInnerClass", true);
    final Element out = new Element("profile");
    myProfile.writeExternal(out);
    assertEquals("""
                   <profile version="1.0">
                     <option name="myName" value="Test" />
                     <inspection_tool class="PrivateMemberAccessBetweenOuterAndInnerClass" enabled="true" level="WARNING" enabled_by_default="true" />
                   </profile>""", JDOMUtil.writeElement(out));
  }

  @Test
  public void testNotMerged() throws IOException, JDOMException {
    final Element in = JDOMUtil.load(
      """
        <profile version="1.0">
          <option name="myName" value="Test" />
          <inspection_tool class="NonStaticInnerClassInSecureContext" enabled="true" level="WARNING" enabled_by_default="true" />
        </profile>""");
    myProfile.readExternal(in);
    assertTrue(myProfile.getToolsOrNull("PrivateMemberAccessBetweenOuterAndInnerClass", null).isEnabled());
    myProfile.setToolEnabled("PrivateMemberAccessBetweenOuterAndInnerClass", false);
    final Element out = new Element("profile");
    myProfile.writeExternal(out);
    assertEquals("""
                   <profile version="1.0">
                     <option name="myName" value="Test" />
                     <inspection_tool class="NonStaticInnerClassInSecureContext" enabled="true" level="WARNING" enabled_by_default="true" />
                     <inspection_tool class="PrivateMemberAccessBetweenOuterAndInnerClassMerged" />
                   </profile>""", JDOMUtil.writeElement(out));
  }

  @Test
  public void testMerged() throws IOException, JDOMException {
    final Element in = JDOMUtil.load(
      """
        <profile version="1.0">
          <option name="myName" value="Test" />
          <inspection_tool class="NonStaticInnerClassInSecureContext" enabled="true" level="WARNING" enabled_by_default="true" />
          <inspection_tool class="PrivateMemberAccessBetweenOuterAndInnerClassMerged" />
        </profile>""");
    myProfile.readExternal(in);
    assertFalse(myProfile.getToolsOrNull("PrivateMemberAccessBetweenOuterAndInnerClass", null).isEnabled());
  }
}