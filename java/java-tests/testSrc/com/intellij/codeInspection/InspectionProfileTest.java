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

import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionToolRegistrar;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.testFramework.fixtures.IdeaTestFixture;
import com.intellij.testFramework.fixtures.JavaTestFixtureFactory;
import junit.framework.TestCase;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;

import java.io.IOException;
import java.io.StringWriter;

public class InspectionProfileTest extends TestCase {
  private static final String PROFILE = "ToConvert";
  private final IdeaTestFixture myFixture = JavaTestFixtureFactory.getFixtureFactory().createLightFixtureBuilder().getFixture();

  protected void setUp() throws Exception {
    InspectionProfileImpl.INIT_INSPECTIONS = true;
    super.setUp();
    myFixture.setUp();
    InspectionToolRegistrar.getInstance().ensureInitialized();
  }

  protected void tearDown() throws Exception {
    myFixture.tearDown();
    super.tearDown();
    InspectionProfileImpl.INIT_INSPECTIONS = false;
    InspectionProfileManager.getInstance().deleteProfile(PROFILE);
  }

  public void testCopyProjectProfile() throws Exception {
    final Element element = loadProfile();
    final InspectionProfileImpl profile = new InspectionProfileImpl(PROFILE);
    profile.readExternal(element);
    final ModifiableModel model = profile.getModifiableModel();
    model.commit();
    final Element copy = new Element("inspections");
    profile.writeExternal(copy);
    StringWriter writer = new StringWriter();
    JDOMUtil.writeElement(copy, writer, "\n");
    assertTrue(writer.getBuffer().toString(), JDOMUtil.areElementsEqual(element, copy));
  }

  public void testConvertOldProfile() throws Exception {
    final Element element = loadOldStyleProfile();
    final InspectionProfileImpl profile = new InspectionProfileImpl(PROFILE);
    profile.readExternal(element);
    final ModifiableModel model = profile.getModifiableModel();
    model.commit();
    final Element copy = new Element("inspections");
    profile.writeExternal(copy);
    StringWriter writer = new StringWriter();
    JDOMUtil.writeElement(copy, writer, "\n");
    assertTrue(writer.getBuffer().toString(), JDOMUtil.areElementsEqual(loadProfile(), copy));
  }

  private static Element loadOldStyleProfile() throws IOException, JDOMException {
    final Document document = JDOMUtil.loadDocument("<inspections version=\"1.0\" is_locked=\"false\">\n" +
                                                    "  <option name=\"myName\" value=\"" + PROFILE + "\" />\n" +
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
                                                    "    <server>\n" +
                                                    "      <option name=\"myName\" value=\"SERVER PROBLEM\" />\n" +
                                                    "      <option name=\"myVal\" value=\"100\" />\n" +
                                                    "    </server>\n" +
                                                    "  </used_levels>\n" +
                                                    "  <inspection_tool class=\"JavaDoc\" enabled=\"false\" level=\"WARNING\" enabled_by_default=\"false\">\n" +
                                                    "    <option name=\"TOP_LEVEL_CLASS_OPTIONS\">\n" +
                                                    "      <value>\n" +
                                                    "        <option name=\"ACCESS_JAVADOC_REQUIRED_FOR\" value=\"none\" />\n" +
                                                    "        <option name=\"REQUIRED_TAGS\" value=\"\" />\n" +
                                                    "      </value>\n" +
                                                    "    </option>\n" +
                                                    "    <option name=\"INNER_CLASS_OPTIONS\">\n" +
                                                    "      <value>\n" +
                                                    "        <option name=\"ACCESS_JAVADOC_REQUIRED_FOR\" value=\"none\" />\n" +
                                                    "        <option name=\"REQUIRED_TAGS\" value=\"\" />\n" +
                                                    "      </value>\n" +
                                                    "    </option>\n" +
                                                    "    <option name=\"METHOD_OPTIONS\">\n" +
                                                    "      <value>\n" +
                                                    "        <option name=\"ACCESS_JAVADOC_REQUIRED_FOR\" value=\"none\" />\n" +
                                                    "        <option name=\"REQUIRED_TAGS\" value=\"@return@param@throws or @exception\" />\n" +
                                                    "      </value>\n" + "    </option>\n" +
                                                    "    <option name=\"FIELD_OPTIONS\">\n" +
                                                    "      <value>\n" +
                                                    "        <option name=\"ACCESS_JAVADOC_REQUIRED_FOR\" value=\"none\" />\n" +
                                                    "        <option name=\"REQUIRED_TAGS\" value=\"\" />\n" +
                                                    "      </value>\n" +
                                                    "    </option>\n" +
                                                    "    <option name=\"IGNORE_DEPRECATED\" value=\"false\" />\n" +
                                                    "    <option name=\"IGNORE_JAVADOC_PERIOD\" value=\"false\" />\n" +
                                                    "    <option name=\"IGNORE_DUPLICATED_THROWS\" value=\"false\" />\n" +
                                                    "    <option name=\"myAdditionalJavadocTags\" value=\"tag1,tag2 \" />\n" +
                                                    "  </inspection_tool>\n" +
                                                    "</inspections>");
    return document.getRootElement();
  }

  private static Element loadProfile() throws IOException, JDOMException {
    final Document document = JDOMUtil.loadDocument("<inspections version=\"1.0\" is_locked=\"false\">\n" +
                                                    "  <option name=\"myName\" value=\"" + PROFILE + "\" />\n" +
                                                    "  <option name=\"myLocal\" value=\"true\" />\n" +
                                                    "  <inspection_tool class=\"JavaDoc\" enabled=\"false\" level=\"WARNING\" enabled_by_default=\"false\">\n" +
                                                    "    <option name=\"TOP_LEVEL_CLASS_OPTIONS\">\n" +
                                                    "      <value>\n" +
                                                    "        <option name=\"ACCESS_JAVADOC_REQUIRED_FOR\" value=\"none\" />\n" +
                                                    "        <option name=\"REQUIRED_TAGS\" value=\"\" />\n" +
                                                    "      </value>\n" +
                                                    "    </option>\n" +
                                                    "    <option name=\"INNER_CLASS_OPTIONS\">\n" +
                                                    "      <value>\n" +
                                                    "        <option name=\"ACCESS_JAVADOC_REQUIRED_FOR\" value=\"none\" />\n" +
                                                    "        <option name=\"REQUIRED_TAGS\" value=\"\" />\n" +
                                                    "      </value>\n" +
                                                    "    </option>\n" +
                                                    "    <option name=\"METHOD_OPTIONS\">\n" +
                                                    "      <value>\n" +
                                                    "        <option name=\"ACCESS_JAVADOC_REQUIRED_FOR\" value=\"none\" />\n" +
                                                    "        <option name=\"REQUIRED_TAGS\" value=\"@return@param@throws or @exception\" />\n" +
                                                    "      </value>\n" + "    </option>\n" +
                                                    "    <option name=\"FIELD_OPTIONS\">\n" +
                                                    "      <value>\n" +
                                                    "        <option name=\"ACCESS_JAVADOC_REQUIRED_FOR\" value=\"none\" />\n" +
                                                    "        <option name=\"REQUIRED_TAGS\" value=\"\" />\n" +
                                                    "      </value>\n" +
                                                    "    </option>\n" +
                                                    "    <option name=\"IGNORE_DEPRECATED\" value=\"false\" />\n" +
                                                    "    <option name=\"IGNORE_JAVADOC_PERIOD\" value=\"false\" />\n" +
                                                    "    <option name=\"IGNORE_DUPLICATED_THROWS\" value=\"false\" />\n" +
                                                    "    <option name=\"myAdditionalJavadocTags\" value=\"tag1,tag2 \" />\n" +
                                                    "  </inspection_tool>\n" +
                                                    "</inspections>");

    return document.getRootElement();
  }

   public void testReloadProfileWithUnknownScopes() throws Exception {
    final Element element = JDOMUtil.loadDocument("<inspections version=\"1.0\" is_locked=\"false\">\n" +
                                                  "  <option name=\"myName\" value=\"" + PROFILE + "\" />\n" +
                                                  "  <option name=\"myLocal\" value=\"true\" />\n" +
                                                  "  <inspection_tool class=\"ArgNamesErrorsInspection\" enabled=\"true\" level=\"ERROR\" enabled_by_default=\"false\" />\n" +
                                                  "  <inspection_tool class=\"ArgNamesWarningsInspection\" enabled=\"true\" level=\"WARNING\" enabled_by_default=\"false\" />\n" +
                                                  "  <inspection_tool class=\"AroundAdviceStyleInspection\" enabled=\"true\" level=\"WARNING\" enabled_by_default=\"false\" />\n" +
                                                  "  <inspection_tool class=\"DeclareParentsInspection\" enabled=\"true\" level=\"ERROR\" enabled_by_default=\"false\" />\n" +
                                                  /*"  <inspection_tool class=\"ManifestDomInspection\" enabled=\"true\" level=\"ERROR\" enabled_by_default=\"false\" />\n" +*/
                                                  "  <inspection_tool class=\"MissingAspectjAutoproxyInspection\" enabled=\"true\" level=\"WARNING\" enabled_by_default=\"false\" />\n" +
                                                  "  <inspection_tool class=\"UNUSED_IMPORT\" enabled=\"true\" level=\"WARNING\" enabled_by_default=\"true\">\n" +
                                                  "    <scope name=\"Unknown scope name\" level=\"WARNING\" enabled=\"true\" />\n" +
                                                  "  </inspection_tool>\n" +
                                                  "</inspections>").getRootElement();
    final InspectionProfileImpl profile = new InspectionProfileImpl(PROFILE);
    profile.readExternal(element);
    final ModifiableModel model = profile.getModifiableModel();
    model.commit();
    final Element copy = new Element("inspections");
    profile.writeExternal(copy);
    StringWriter writer = new StringWriter();
    JDOMUtil.writeElement(copy, writer, "\n");
    assertTrue(writer.getBuffer().toString(), JDOMUtil.areElementsEqual(element, copy));
  }
}
