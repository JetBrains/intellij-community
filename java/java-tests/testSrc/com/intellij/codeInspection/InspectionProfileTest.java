/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInspection;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.ex.*;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.testFramework.LightIdeaTestCase;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.intellij.testFramework.PlatformTestUtil.assertElementsEqual;

/**
 * @author Anna.Kozlova
 * Date: 18-Aug-2006
 */
public class InspectionProfileTest extends LightIdeaTestCase {
  private static final String PROFILE = "ToConvert";

  @Override
  protected void setUp() throws Exception {
    //noinspection AssignmentToStaticFieldFromInstanceMethod
    InspectionProfileImpl.INIT_INSPECTIONS = true;
    super.setUp();
    InspectionToolRegistrar.getInstance().ensureInitialized();
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
    //noinspection AssignmentToStaticFieldFromInstanceMethod
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
    assertElementsEqual(element, copy);
  }

  public void testConvertOldProfile() throws Exception {
    final Element element = loadOldStyleProfile();
    final InspectionProfileImpl profile = new InspectionProfileImpl(PROFILE);
    profile.readExternal(element);
    final ModifiableModel model = profile.getModifiableModel();
    model.commit();
    final Element copy = new Element("inspections");
    profile.writeExternal(copy);
    assertElementsEqual(loadProfile(), copy);
  }

  private static Element loadOldStyleProfile() throws IOException, JDOMException {
    final Document document = JDOMUtil.loadDocument("<inspections version=\"1.0\" is_locked=\"false\">\n" +
                                                    "  <option name=\"myName\" value=\"ToConvert\" />\n" +
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
                                                    "    <option name=\"IGNORE_POINT_TO_ITSELF\" value=\"false\" />\n" +
                                                    "    <option name=\"myAdditionalJavadocTags\" value=\"tag1,tag2 \" />\n" +
                                                    "  </inspection_tool>\n" +
                                                    "</inspections>");
    return document.getRootElement();
  }

  private static Element loadProfile() throws IOException, JDOMException {
    final Document document = JDOMUtil.loadDocument("<inspections version=\"1.0\" is_locked=\"false\">\n" +
                                                    "  <option name=\"myName\" value=\"ToConvert\" />\n" +
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
                                                    "    <option name=\"IGNORE_POINT_TO_ITSELF\" value=\"false\" />\n" +
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
    assertElementsEqual(element, copy);
  }

  public void testLockProfile() throws Exception {

    final List<InspectionToolWrapper> list = new ArrayList<InspectionToolWrapper>();
    list.add(createTool("foo"));

    InspectionToolRegistrar registrar = new InspectionToolRegistrar(null) {
      @Override
      public List<InspectionToolWrapper> createTools() {
        LocalInspectionEP foo = new LocalInspectionEP();
        foo.shortName = "foo";
        return list;
      }
    };

    InspectionProfileImpl profile = new InspectionProfileImpl("Foo", registrar, InspectionProfileManager.getInstance());
    profile.setBaseProfile(null);
    List<ScopeToolState> tools = profile.getAllTools();
    assertEquals(1, tools.size());
    ModifiableModel model = profile.getModifiableModel();
    model.lockProfile(true);
    model.commit();
    Element element = new Element("element");
    profile.writeExternal(element);

    list.add(createTool("bar"));

    profile = new InspectionProfileImpl("Foo", registrar, InspectionProfileManager.getInstance());
    profile.readExternal(element);

    profile.setBaseProfile(null);
    tools = profile.getAllTools();
    assertEquals(2, tools.size());

    assertTrue(profile.isProfileLocked());
    assertFalse(profile.isToolEnabled(HighlightDisplayKey.find("bar")));
  }

  private static LocalInspectionToolWrapper createTool(String s) {
    LocalInspectionEP foo = new LocalInspectionEP();
    foo.shortName = s;
    foo.displayName = s;
    foo.groupDisplayName = s;
    foo.level = "ERROR";
    foo.enabledByDefault = true;
    return new LocalInspectionToolWrapper(foo);
  }
}
