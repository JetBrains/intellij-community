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
import com.intellij.codeInspection.dataFlow.DataFlowInspection;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.codeInspection.ex.*;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.profile.Profile;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.testFramework.LightIdeaTestCase;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;

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
    final InspectionProfileImpl profile = createProfile();
    profile.readExternal(element);
    final ModifiableModel model = profile.getModifiableModel();
    model.commit();
    final Element copy = new Element("inspections");
    profile.writeExternal(copy);
    assertElementsEqual(element, copy);
  }

  private static InspectionProfileImpl createProfile() {
    InspectionProfileImpl profile = new InspectionProfileImpl(PROFILE);
    profile.setBaseProfile(InspectionProfileImpl.getDefaultProfile());
    return profile;
  }

  public void testConvertOldProfile() throws Exception {
    final Element element = loadOldStyleProfile();
    final InspectionProfileImpl profile = createProfile();
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
    final InspectionProfileImpl profile = createProfile();
    profile.readExternal(element);
    final ModifiableModel model = profile.getModifiableModel();
    model.commit();
    final Element copy = new Element("inspections");
    profile.writeExternal(copy);
    assertElementsEqual(element, copy);
  }

  public void testLockProfile() throws Exception {
    final List<InspectionToolWrapper> list = new ArrayList<InspectionToolWrapper>();
    list.add(createTool("foo", true));

    InspectionToolRegistrar registrar = new InspectionToolRegistrar() {
      @NotNull
      @Override
      public List<InspectionToolWrapper> createTools() {
        return list;
      }
    };

    InspectionProfileImpl profile = createProfile(registrar);

    List<ScopeToolState> tools = profile.getAllTools(getProject());
    assertEquals(1, tools.size());
    assertTrue(profile.isToolEnabled(HighlightDisplayKey.find("foo")));
    assertTrue(profile.getToolDefaultState("foo", getProject()).isEnabled());

    InspectionProfileImpl model = (InspectionProfileImpl)profile.getModifiableModel();
    model.lockProfile(true);
    model.initInspectionTools(getProject()); // todo commit should take care of initialization
    model.commit();

    assertEquals("<profile version=\"1.0\" is_locked=\"true\">\n" +
                 "  <option name=\"myName\" value=\"Foo\" />\n" +
                 "  <option name=\"myLocal\" value=\"true\" />\n" +
                 "  <inspection_tool class=\"foo\" enabled=\"true\" level=\"ERROR\" enabled_by_default=\"true\" />\n" +
                 "</profile>",
                 serialize(profile));

    Element element = new Element("element");
    profile.writeExternal(element);

    list.add(createTool("bar", true));
    list.add(createTool("disabled", false));

    profile = createProfile(registrar);
    profile.readExternal(element);

    tools = profile.getAllTools(getProject());
    assertEquals(3, tools.size());

    assertTrue(profile.isProfileLocked());
    assertFalse(profile.isToolEnabled(HighlightDisplayKey.find("bar")));
    assertFalse(profile.isToolEnabled(HighlightDisplayKey.find("disabled")));

    assertTrue(profile.getToolDefaultState("bar", getProject()).isEnabled());
    assertFalse(profile.getToolDefaultState("disabled", getProject()).isEnabled());

    assertEquals("<profile version=\"1.0\" is_locked=\"true\">\n" +
                 "  <option name=\"myName\" value=\"Foo\" />\n" +
                 "  <option name=\"myLocal\" value=\"true\" />\n" +
                 "  <inspection_tool class=\"bar\" enabled=\"false\" level=\"ERROR\" enabled_by_default=\"true\" />\n" +
                 "  <inspection_tool class=\"disabled\" enabled=\"false\" level=\"ERROR\" enabled_by_default=\"false\" />\n" +
                 "  <inspection_tool class=\"foo\" enabled=\"true\" level=\"ERROR\" enabled_by_default=\"true\" />\n" +
                 "</profile>", serialize(profile));
  }

  private static String serialize(InspectionProfileImpl profile) throws WriteExternalException {
    Element element;
    element = new Element("profile");
    profile.writeExternal(element);
    return JDOMUtil.writeElement(element);
  }

  private static InspectionProfileImpl createProfile(InspectionToolRegistrar registrar) {
    InspectionProfileImpl base = new InspectionProfileImpl("Base", registrar, InspectionProfileManager.getInstance());
    base.setBaseProfile(null);
    InspectionProfileImpl profile = new InspectionProfileImpl("Foo", registrar, InspectionProfileManager.getInstance());
    profile.setBaseProfile(base);
    return profile;
  }

  public void testGlobalInspectionContext() throws Exception {
    InspectionProfileImpl profile = new InspectionProfileImpl("Foo");
    profile.disableAllTools(getProject());
    profile.enableTool(new UnusedDeclarationInspection().getShortName(), getProject());

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
    List<ScopeToolState> tools = ((InspectionProfileImpl)model).getAllTools(getProject());
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
    InspectionToolWrapper[] toolWrappers = profile.getInspectionTools(null);
    assertTrue(toolWrappers.length > 0);
    InspectionToolWrapper toolWrapper = profile.getInspectionTool(new DataFlowInspection().getShortName(), getProject());
    assertNotNull(toolWrapper);
    String id = toolWrapper.getShortName();
    System.out.println(id);
    if (profile.isToolEnabled(HighlightDisplayKey.findById(id))) {
      profile.disableTool(id, getProject());
    }
    else {
      profile.enableTool(id, getProject());
    }
    assertEquals(0, countInitializedTools(profile));
    profile.writeExternal(new Element("profile"));
    List<InspectionToolWrapper> initializedTools = getInitializedTools(profile);
    if (initializedTools.size() != 1) {
      for (InspectionToolWrapper initializedTool : initializedTools) {
        System.out.println(initializedTool.getShortName());
      }
      fail();
    }
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

  @NotNull
  public static List<InspectionToolWrapper> getInitializedTools(@NotNull Profile foo) {
    List<InspectionToolWrapper> initialized = new ArrayList<InspectionToolWrapper>();
    List<ScopeToolState> tools = ((InspectionProfileImpl)foo).getAllTools(getProject());
    for (ScopeToolState tool : tools) {
      InspectionToolWrapper toolWrapper = tool.getTool();
      if (toolWrapper.isInitialized()) {
        initialized.add(toolWrapper);
      }
    }
    return initialized;
  }

  private static LocalInspectionToolWrapper createTool(String s, boolean enabled) {
    LocalInspectionEP foo = new LocalInspectionEP();
    foo.shortName = s;
    foo.displayName = s;
    foo.groupDisplayName = s;
    foo.level = "ERROR";
    foo.enabledByDefault = enabled;
    foo.implementationClass = TestTool.class.getName();
    return new LocalInspectionToolWrapper(foo);
  }

  @SuppressWarnings("InspectionDescriptionNotFoundInspection")
  public static class TestTool extends LocalInspectionTool {

  }
}
