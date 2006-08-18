/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: Anna.Kozlova
 * Date: 18-Aug-2006
 * Time: 13:42:59
 */
package com.intellij.codeInspection;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionToolRegistrar;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.testFramework.fixtures.IdeaTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import junit.framework.TestCase;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;

import java.io.IOException;

public class InspectionProfileTest extends TestCase {
  private IdeaTestFixture myFixture = IdeaTestFixtureFactory.getFixtureFactory().createLightFixtureBuilder().getFixture();

  protected void setUp() throws Exception {
    super.setUp();
    myFixture.setUp();
    InspectionToolRegistrar.getInstance().registerTools(ApplicationManager.getApplication().getComponents(InspectionToolProvider.class));
  }

  protected void tearDown() throws Exception {
    myFixture.tearDown();
    super.tearDown();
  }

  public void testCopyProjectProfile() throws Exception {
    final Element element = loadProfile();
    final InspectionProfileImpl profile = new InspectionProfileImpl("Default");
    profile.readExternal(element);
    final ModifiableModel model = profile.getModifiableModel();
    model.commit(InspectionProfileManager.getInstance());
    final Element copy = new Element("inspections");
    profile.writeExternal(copy);
    /*StringWriter writer = new StringWriter();
    JDOMUtil.writeElement(copy, writer, "\n");
    System.out.println(writer.getBuffer().toString());*/
    assertTrue(JDOMUtil.areElementsEqual(element, copy));
  }

  private static Element loadProfile() throws IOException, JDOMException {
    final Document document = JDOMUtil.loadDocument("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                                                    "<inspections version=\"1.0\" is_locked=\"false\">\n" +
                                                    "  <option name=\"myName\" value=\"Default\" />\n" +
                                                    "  <option name=\"myLocal\" value=\"true\" />\n" +
                                                    "  <used_levels>\n" +
                                                    "    <error>\n" +
                                                    "      <option name=\"myName\" value=\"ERROR\" />\n" +
                                                    "      <option name=\"myVal\" value=\"400\" />\n" +
                                                    "    </error>\n" +
                                                    "    <warning>\n" +
                                                    "      <option name=\"myName\" value=\"WARNING\" />\n" +
                                                    "      <option name=\"myVal\" value=\"300\" />\n" +
                                                    "    </warning>\n" +
                                                    "    <information>\n" +
                                                    "      <option name=\"myName\" value=\"INFO\" />\n" +
                                                    "      <option name=\"myVal\" value=\"200\" />\n" +
                                                    "    </information>\n" +
                                                    "    <server>\n" + "    " +
                                                    "      <option name=\"myName\" value=\"SERVER PROBLEM\" />\n" +
                                                    "      <option name=\"myVal\" value=\"100\" />\n" +
                                                    "    </server>\n" +
                                                    "  </used_levels>\n" +
                                                    "  <inspection_tool class=\"JavaDoc\" level=\"WARNING\" enabled=\"false\">\n" +
                                                    "     <option name=\"TOP_LEVEL_CLASS_OPTIONS\">\n" +
                                                    "       <value>\n" +
                                                    "         <option name=\"ACCESS_JAVADOC_REQUIRED_FOR\" value=\"none\" />\n" +
                                                    "         <option name=\"REQUIRED_TAGS\" value=\"\" />\n" +
                                                    "       </value>\n" +
                                                    "     </option>\n" +
                                                    "     <option name=\"INNER_CLASS_OPTIONS\">\n" +
                                                    "       <value>\n" +
                                                    "         <option name=\"ACCESS_JAVADOC_REQUIRED_FOR\" value=\"none\" />\n" +
                                                    "         <option name=\"REQUIRED_TAGS\" value=\"\" />\n" +
                                                    "       </value>\n" +
                                                    "     </option>\n" +
                                                    "     <option name=\"METHOD_OPTIONS\">\n" +
                                                    "       <value>\n" +
                                                    "         <option name=\"ACCESS_JAVADOC_REQUIRED_FOR\" value=\"none\" />\n" +
                                                    "         <option name=\"REQUIRED_TAGS\" value=\"@return@param@throws or @exception\" />\n" +
                                                    "       </value>\n" + "          </option>\n" +
                                                    "     <option name=\"FIELD_OPTIONS\">\n" +
                                                    "       <value>\n" +
                                                    "          <option name=\"ACCESS_JAVADOC_REQUIRED_FOR\" value=\"none\" />\n" +
                                                    "          <option name=\"REQUIRED_TAGS\" value=\"\" />\n" +
                                                    "       </value>\n" +
                                                    "     </option>\n" +
                                                    "     <option name=\"IGNORE_DEPRECATED\" value=\"false\" />\n" +
                                                    "     <option name=\"IGNORE_JAVADOC_PERIOD\" value=\"false\" />\n" +
                                                    "     <option name=\"myAdditionalJavadocTags\" value=\"tag1,tag2 \" />\n" +
                                                    "  </inspection_tool>" +
                                                    "</inspections>");
    HighlightDisplayKey.register("JavaDoc"); //InspectionProfileImpl.DEFAULT wasn't setup because of tests optimizations
    return document.getRootElement();
  }
}