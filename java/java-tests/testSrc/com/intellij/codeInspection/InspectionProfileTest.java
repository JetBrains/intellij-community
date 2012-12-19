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
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.codeInspection.ex.*;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.profile.Profile;
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

  public void testGlobalInspectionContext() throws Exception {
    InspectionProfileImpl profile = new InspectionProfileImpl("Foo");
    profile.disableAllTools();
    profile.enableTool(new UnusedDeclarationInspection().getShortName());

    GlobalInspectionContextImpl context = ((InspectionManagerEx)InspectionManager.getInstance(getProject())).createNewGlobalContext(false);
    context.setExternalProfile(profile);
    context.initializeTools(new ArrayList<Tools>(), new ArrayList<Tools>(), new ArrayList<Tools>());
  }

  public void testInspectionsInitialization() throws Exception {

    InspectionProfileImpl foo = new InspectionProfileImpl("foo");
    assertEquals(0, countInitializedTools(foo));
    foo.initInspectionTools(getProject());
    assertEquals(0, countInitializedTools(foo));

    ModifiableModel model = foo.getModifiableModel();
    assertEquals(0, countInitializedTools(model));
    model.commit();
    assertEquals(0, countInitializedTools(model));
    assertEquals(0, countInitializedTools(foo));

    model = foo.getModifiableModel();
    assertEquals(0, countInitializedTools(model));
    List<ScopeToolState> tools = ((InspectionProfileImpl)model).getAllTools();
    for (ScopeToolState tool : tools) {
      if (!tool.isEnabled()) {
        tool.setEnabled(true);
      }
    }
    model.commit();
    assertEquals(0, countInitializedTools(model));
  }

  public void testDoNotInstantiateOnSave() throws Exception {
    InspectionProfileImpl profile = new InspectionProfileImpl("profile");
    profile.setBaseProfile(InspectionProfileImpl.getDefaultProfile());
    assertEquals(0, countInitializedTools(profile));
    InspectionProfileEntry[] tools = profile.getInspectionTools(null);
    assertTrue(tools.length > 0);
    InspectionProfileEntry tool = tools[0];
    String id = tool.getShortName();
    System.out.println(id);
    if (profile.isToolEnabled(HighlightDisplayKey.findById(id))) {
      profile.disableTool(id);
    }
    else {
      profile.enableTool(id);
    }
    profile.writeExternal(new Element("profile"));
    List<InspectionProfileEntry> initializedTools = getInitializedTools(profile);
    assertEquals(initializedTools.toString(), 1, initializedTools.size());
  }

  public void testInspectionInitializationForSerialization() throws Exception {
    InspectionProfileImpl foo = new InspectionProfileImpl("foo");
    foo.readExternal(JDOMUtil.loadDocument("<profile version=\"1.0\" is_locked=\"false\">\n" +
                                           "    <option name=\"myName\" value=\"idea.default\" />\n" +
                                           "    <option name=\"myLocal\" value=\"false\" />\n" +
                                           "    <inspection_tool class=\"AbstractMethodCallInConstructor\" enabled=\"true\" level=\"WARNING\" enabled_by_default=\"true\" />\n" +
                                           "    <inspection_tool class=\"AssignmentToForLoopParameter\" enabled=\"true\" level=\"WARNING\" enabled_by_default=\"true\">\n" +
                                           "      <option name=\"m_checkForeachParameters\" value=\"false\" />\n" +
                                           "    </inspection_tool>\n" +
                                           "</profile>").getRootElement());
    foo.initInspectionTools(getProject());
    assertEquals(1, countInitializedTools(foo));
  }

  public static int countInitializedTools(Profile foo) {
    return getInitializedTools(foo).size();
  }

  public static List<InspectionProfileEntry> getInitializedTools(Profile foo) {
    List<InspectionProfileEntry> initialized = new ArrayList<InspectionProfileEntry>();
    List<ScopeToolState> tools = ((InspectionProfileImpl)foo).getAllTools();
    for (ScopeToolState tool : tools) {
      InspectionProfileEntry entry = tool.getTool();
      assertTrue(entry instanceof InspectionToolWrapper);
      if (entry.isInitialized()) {
        initialized.add(entry);
      }
    }
    return initialized;
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
