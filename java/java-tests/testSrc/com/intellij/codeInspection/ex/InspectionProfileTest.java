/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.codeInspection.ex;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalInspectionEP;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ModifiableModel;
import com.intellij.codeInspection.dataFlow.DataFlowInspection;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspectionBase;
import com.intellij.codeInspection.unusedSymbol.UnusedSymbolLocalInspectionBase;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.profile.Profile;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.profile.codeInspection.ui.header.InspectionToolsConfigurable;
import com.intellij.testFramework.LightIdeaTestCase;
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
    InspectionToolRegistrar.getInstance().createTools();
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
    return new InspectionProfileImpl(PROFILE, InspectionToolRegistrar.getInstance(), InspectionProfileManager.getInstance(), InspectionProfileImpl.getDefaultProfile());
  }
  private static InspectionProfileImpl createProfile(@NotNull InspectionProfileImpl base) {
    return new InspectionProfileImpl(PROFILE, InspectionToolRegistrar.getInstance(), InspectionProfileManager.getInstance(), base);
  }

  public void testSameNameSharedProfile() throws Exception {
    InspectionProfileManager profileManager = InspectionProfileManager.getInstance();
    InspectionProfileImpl localProfile = createProfile();
    profileManager.updateProfile(localProfile);

    InspectionProjectProfileManager projectProfileManager = InspectionProjectProfileManager.getInstance(getProject());
    try {
      //normally on open project profile wrappers are init for both managers
      profileManager.updateProfile(localProfile);
      InspectionProfileImpl profile = new InspectionProfileImpl(PROFILE, InspectionToolRegistrar.getInstance(), projectProfileManager,
                                                                InspectionProfileImpl.getDefaultProfile());
      projectProfileManager.updateProfile(profile);
      projectProfileManager.setProjectProfile(profile.getName());

      assertTrue(projectProfileManager.getInspectionProfile() == profile);
    }
    finally {
      projectProfileManager.deleteProfile(PROFILE);
    }
  }

  public void testConvertOldProfile() throws Exception {
    Element element = JDOMUtil.loadDocument("<inspections version=\"1.0\">\n" +
                                            "  <option name=\"myName\" value=\"ToConvert\" />\n" +
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
                                            "</inspections>").getRootElement();
    InspectionProfileImpl profile = createProfile();
    profile.readExternal(element);
    ModifiableModel model = profile.getModifiableModel();
    model.commit();

    Element copy = new Element("inspections");
    profile.writeExternal(copy);
    assertElementsEqual(loadProfile(), copy);
  }

  private static Element loadProfile() throws IOException, JDOMException {
    return JDOMUtil.loadDocument("<inspections version=\"1.0\">\n" +
                                 "  <option name=\"myName\" value=\"ToConvert\" />\n" +
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
                                 "</inspections>").getRootElement();
  }

  public void testReloadProfileWithUnknownScopes() throws Exception {
    final Element element = JDOMUtil.loadDocument("<inspections version=\"1.0\">\n" +
                                                  "  <option name=\"myName\" value=\"" + PROFILE + "\" />\n" +
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

  public void testMergeUnusedDeclarationAndUnusedSymbol() throws Exception {
    //no specific settings
    final Element element = JDOMUtil.loadDocument("<inspections version=\"1.0\">\n" +
                                                  "  <option name=\"myName\" value=\"" + PROFILE + "\" />\n" +
                                                  "</inspections>").getRootElement();
    InspectionProfileImpl profile = createProfile(new InspectionProfileImpl("foo"));
    profile.readExternal(element);
    ModifiableModel model = profile.getModifiableModel();
    model.commit();
    final Element copy = new Element("inspections");
    profile.writeExternal(copy);
    assertElementsEqual(element, copy);


    //settings to merge
    final Element unusedProfile = JDOMUtil.loadDocument("<inspections version=\"1.0\">\n" +
                                                        "  <option name=\"myName\" value=\"" + PROFILE + "\" />\n" +
                                                        "  <inspection_tool class=\"UNUSED_SYMBOL\" enabled=\"true\" level=\"WARNING\" enabled_by_default=\"false\">\n" +
                                                        "      <option name=\"LOCAL_VARIABLE\" value=\"true\" />\n" +
                                                        "      <option name=\"FIELD\" value=\"true\" />\n" +
                                                        "      <option name=\"METHOD\" value=\"true\" />\n" +
                                                        "      <option name=\"CLASS\" value=\"true\" />\n" +
                                                        "      <option name=\"PARAMETER\" value=\"true\" />\n" +
                                                        "      <option name=\"REPORT_PARAMETER_FOR_PUBLIC_METHODS\" value=\"false\" />\n" +
                                                        "  </inspection_tool>\n" +
                                                        "  <inspection_tool class=\"UnusedDeclaration\" enabled=\"true\" level=\"WARNING\" enabled_by_default=\"false\">\n" +
                                                        "      <option name=\"ADD_MAINS_TO_ENTRIES\" value=\"true\" />\n" +
                                                        "      <option name=\"ADD_APPLET_TO_ENTRIES\" value=\"true\" />\n" +
                                                        "      <option name=\"ADD_SERVLET_TO_ENTRIES\" value=\"true\" />\n" +
                                                        "      <option name=\"ADD_NONJAVA_TO_ENTRIES\" value=\"false\" />\n" +
                                                        "   </inspection_tool>\n" +
                                                        "</inspections>").getRootElement();
    profile.readExternal(unusedProfile);
    model = profile.getModifiableModel();
    model.commit();
    assertEquals("<profile version=\"1.0\">\n" +
                 "  <option name=\"myName\" value=\"ToConvert\" />\n" +
                 "  <inspection_tool class=\"UNUSED_SYMBOL\" enabled=\"true\" level=\"WARNING\" enabled_by_default=\"false\">\n" +
                 "    <option name=\"LOCAL_VARIABLE\" value=\"true\" />\n" +
                 "    <option name=\"FIELD\" value=\"true\" />\n" +
                 "    <option name=\"METHOD\" value=\"true\" />\n" +
                 "    <option name=\"CLASS\" value=\"true\" />\n" +
                 "    <option name=\"PARAMETER\" value=\"true\" />\n" +
                 "    <option name=\"REPORT_PARAMETER_FOR_PUBLIC_METHODS\" value=\"false\" />\n" +
                 "  </inspection_tool>\n" +
                 "  <inspection_tool class=\"UnusedDeclaration\" enabled=\"true\" level=\"WARNING\" enabled_by_default=\"false\">\n" +
                 "    <option name=\"ADD_MAINS_TO_ENTRIES\" value=\"true\" />\n" +
                 "    <option name=\"ADD_APPLET_TO_ENTRIES\" value=\"true\" />\n" +
                 "    <option name=\"ADD_SERVLET_TO_ENTRIES\" value=\"true\" />\n" +
                 "    <option name=\"ADD_NONJAVA_TO_ENTRIES\" value=\"false\" />\n" +
                 "  </inspection_tool>\n" +
                 "</profile>", serialize(profile));

    //make them default
    profile = createProfile(new InspectionProfileImpl("foo"));
    profile.readExternal(unusedProfile);
    model = profile.getModifiableModel();
    InspectionToolWrapper toolWrapper = ((InspectionProfileImpl)model).getInspectionTool("unused", getProject());
    UnusedDeclarationInspectionBase tool = (UnusedDeclarationInspectionBase)toolWrapper.getTool();
    tool.ADD_NONJAVA_TO_ENTRIES = true;
    UnusedSymbolLocalInspectionBase inspectionTool = tool.getSharedLocalInspectionTool();
    inspectionTool.REPORT_PARAMETER_FOR_PUBLIC_METHODS = true;
    model.commit();
    String mergedText = "<profile version=\"1.0\">\n" +
                        "  <option name=\"myName\" value=\"ToConvert\" />\n" +
                        "  <inspection_tool class=\"UNUSED_SYMBOL\" enabled=\"true\" level=\"WARNING\" enabled_by_default=\"false\">\n" +
                        "    <option name=\"LOCAL_VARIABLE\" value=\"true\" />\n" +
                        "    <option name=\"FIELD\" value=\"true\" />\n" +
                        "    <option name=\"METHOD\" value=\"true\" />\n" +
                        "    <option name=\"CLASS\" value=\"true\" />\n" +
                        "    <option name=\"PARAMETER\" value=\"true\" />\n" +
                        "    <option name=\"REPORT_PARAMETER_FOR_PUBLIC_METHODS\" value=\"false\" />\n" +
                        "  </inspection_tool>\n" +
                        "  <inspection_tool class=\"UnusedDeclaration\" enabled=\"true\" level=\"WARNING\" enabled_by_default=\"false\">\n" +
                        "    <option name=\"ADD_MAINS_TO_ENTRIES\" value=\"true\" />\n" +
                        "    <option name=\"ADD_APPLET_TO_ENTRIES\" value=\"true\" />\n" +
                        "    <option name=\"ADD_SERVLET_TO_ENTRIES\" value=\"true\" />\n" +
                        "    <option name=\"ADD_NONJAVA_TO_ENTRIES\" value=\"false\" />\n" +
                        "  </inspection_tool>\n" +
                        "  <inspection_tool class=\"unusedMerged\" />\n" +
                        "</profile>";
    assertEquals(mergedText, serialize(profile));

    Element toImportElement = new Element("profile");
    profile.writeExternal(toImportElement);
    final InspectionProfileImpl importedProfile =
      InspectionToolsConfigurable.importInspectionProfile(toImportElement, InspectionProfileManager.getInstance(), getProject(), null);

    //check merged
    Element mergedElement = JDOMUtil.loadDocument(mergedText).getRootElement();
    profile = createProfile(new InspectionProfileImpl("foo"));
    profile.readExternal(mergedElement);
    model = profile.getModifiableModel();
    model.commit();
    Element copyMerged = new Element("profile");
    profile.writeExternal(copyMerged);
    assertElementsEqual(mergedElement, copyMerged);

    Element imported = new Element("profile");
    importedProfile.writeExternal(imported);
    assertElementsEqual(mergedElement, imported);
  }

  public void testDisabledUnusedDeclarationWithoutChanges() throws Exception {
    checkMergedNoChanges("<profile version=\"1.0\">\n" +
                         "  <option name=\"myName\" value=\"" + PROFILE + "\" />\n" +
                         "  <inspection_tool class=\"UnusedDeclaration\" enabled=\"false\" level=\"WARNING\" enabled_by_default=\"false\">\n" +
                         "    <option name=\"ADD_MAINS_TO_ENTRIES\" value=\"true\" />\n" +
                         "    <option name=\"ADD_APPLET_TO_ENTRIES\" value=\"true\" />\n" +
                         "    <option name=\"ADD_SERVLET_TO_ENTRIES\" value=\"true\" />\n" +
                         "    <option name=\"ADD_NONJAVA_TO_ENTRIES\" value=\"true\" />\n" +
                         "  </inspection_tool>\n" +
                         "</profile>");
  }

  public void testDisabledUnusedDeclarationWithChanges() throws Exception {
    checkMergedNoChanges("<profile version=\"1.0\">\n" +
                         "  <option name=\"myName\" value=\"" + PROFILE + "\" />\n" +
                         "  <inspection_tool class=\"UnusedDeclaration\" enabled=\"false\" level=\"WARNING\" enabled_by_default=\"false\">\n" +
                         "    <option name=\"ADD_MAINS_TO_ENTRIES\" value=\"true\" />\n" +
                         "    <option name=\"ADD_APPLET_TO_ENTRIES\" value=\"true\" />\n" +
                         "    <option name=\"ADD_SERVLET_TO_ENTRIES\" value=\"true\" />\n" +
                         "    <option name=\"ADD_NONJAVA_TO_ENTRIES\" value=\"false\" />\n" +
                         "  </inspection_tool>\n" +
                         "</profile>");
  }

  public void testEnabledUnusedDeclarationWithChanges() throws Exception {
    checkMergedNoChanges("<profile version=\"1.0\">\n" +
                         "  <option name=\"myName\" value=\"" + PROFILE + "\" />\n" +
                         "  <inspection_tool class=\"UnusedDeclaration\" enabled=\"true\" level=\"WARNING\" enabled_by_default=\"true\">\n" +
                         "    <option name=\"ADD_MAINS_TO_ENTRIES\" value=\"true\" />\n" +
                         "    <option name=\"ADD_APPLET_TO_ENTRIES\" value=\"true\" />\n" +
                         "    <option name=\"ADD_SERVLET_TO_ENTRIES\" value=\"true\" />\n" +
                         "    <option name=\"ADD_NONJAVA_TO_ENTRIES\" value=\"false\" />\n" +
                         "  </inspection_tool>\n" +
                         "</profile>");
  }

  public void testDisabledUnusedSymbolWithoutChanges() throws Exception {
    checkMergedNoChanges("<profile version=\"1.0\">\n" +
                         "  <option name=\"myName\" value=\"" +
                         PROFILE +
                         "\" />\n" +
                         "  <inspection_tool class=\"UNUSED_SYMBOL\" enabled=\"false\" level=\"WARNING\" enabled_by_default=\"false\">\n" +
                         "    <option name=\"LOCAL_VARIABLE\" value=\"true\" />\n" +
                         "    <option name=\"FIELD\" value=\"true\" />\n" +
                         "    <option name=\"METHOD\" value=\"true\" />\n" +
                         "    <option name=\"CLASS\" value=\"true\" />\n" +
                         "    <option name=\"PARAMETER\" value=\"true\" />\n" +
                         "    <option name=\"REPORT_PARAMETER_FOR_PUBLIC_METHODS\" value=\"true\" />\n" +
                         "  </inspection_tool>\n" +
                         "</profile>");
  }

  public void testEnabledUnusedSymbolWithChanges() throws Exception {
    checkMergedNoChanges("<profile version=\"1.0\">\n" +
                         "  <option name=\"myName\" value=\"" +
                         PROFILE +
                         "\" />\n" +
                         "  <inspection_tool class=\"UNUSED_SYMBOL\" enabled=\"true\" level=\"WARNING\" enabled_by_default=\"true\">\n" +
                         "    <option name=\"LOCAL_VARIABLE\" value=\"true\" />\n" +
                         "    <option name=\"FIELD\" value=\"true\" />\n" +
                         "    <option name=\"METHOD\" value=\"true\" />\n" +
                         "    <option name=\"CLASS\" value=\"true\" />\n" +
                         "    <option name=\"PARAMETER\" value=\"true\" />\n" +
                         "    <option name=\"REPORT_PARAMETER_FOR_PUBLIC_METHODS\" value=\"false\" />\n" +
                         "  </inspection_tool>\n" +
                         "</profile>");
  }

  private void checkMergedNoChanges(String initialText) throws Exception {
    final Element element = JDOMUtil.loadDocument(initialText).getRootElement();
    InspectionProfileImpl profile = createProfile(new InspectionProfileImpl("foo"));
    profile.readExternal(element);
    ModifiableModel model = profile.getModifiableModel();
    model.commit();
    assertEquals(initialText, serialize(profile));
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
                 "  <inspection_tool class=\"bar\" enabled=\"false\" level=\"ERROR\" enabled_by_default=\"true\" />\n" +
                 "  <inspection_tool class=\"disabled\" enabled=\"false\" level=\"ERROR\" enabled_by_default=\"false\" />\n" +
                 "  <inspection_tool class=\"foo\" enabled=\"true\" level=\"ERROR\" enabled_by_default=\"true\" />\n" +
                 "</profile>", serialize(profile));
  }

  private static String serialize(InspectionProfileImpl profile) throws WriteExternalException {
    Element element = new Element("profile");
    profile.writeExternal(element);
    return JDOMUtil.writeElement(element);
  }

  private static InspectionProfileImpl createProfile(InspectionToolRegistrar registrar) {
    InspectionProfileImpl base = new InspectionProfileImpl("Base", registrar, InspectionProfileManager.getInstance(), null);
    return new InspectionProfileImpl("Foo", registrar, InspectionProfileManager.getInstance(), base);
  }

  public void testGlobalInspectionContext() throws Exception {
    InspectionProfileImpl profile = new InspectionProfileImpl("Foo");
    profile.disableAllTools(getProject());
    profile.enableTool(new UnusedDeclarationInspectionBase(true).getShortName(), getProject());

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
    InspectionProfileImpl profile = new InspectionProfileImpl("profile", InspectionToolRegistrar.getInstance(), InspectionProfileManager.getInstance(), InspectionProfileImpl.getDefaultProfile());
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
    if (initializedTools.size() > 0) {
      for (InspectionToolWrapper initializedTool : initializedTools) {
        System.out.println(initializedTool.getShortName());
      }
      fail();
    }
  }

  public void testInspectionInitializationForSerialization() throws Exception {
    InspectionProfileImpl foo = new InspectionProfileImpl("foo");
    foo.readExternal(JDOMUtil.loadDocument("<profile version=\"1.0\">\n" +
                                           "    <option name=\"myName\" value=\"idea.default\" />\n" +
                                           "    <inspection_tool class=\"AbstractMethodCallInConstructor\" enabled=\"true\" level=\"WARNING\" enabled_by_default=\"true\" />\n" +
                                           "    <inspection_tool class=\"AssignmentToForLoopParameter\" enabled=\"true\" level=\"WARNING\" enabled_by_default=\"true\">\n" +
                                           "      <option name=\"m_checkForeachParameters\" value=\"false\" />\n" +
                                           "    </inspection_tool>\n" +
                                           "</profile>").getRootElement());
    foo.initInspectionTools(getProject());
    assertEquals(1, countInitializedTools(foo));
  }

  public void testPreserveCompatibility() throws Exception {
    InspectionProfileImpl foo = new InspectionProfileImpl("foo", InspectionToolRegistrar.getInstance(), InspectionProjectProfileManager.getInstance(getProject()));
    String test = "<profile version=\"1.0\" is_locked=\"false\">\n" +
                 "  <option name=\"myName\" value=\"idea.default\" />\n" +
                 "  <option name=\"myLocal\" value=\"false\" />\n" +
                 "  <inspection_tool class=\"AbstractMethodCallInConstructor\" enabled=\"true\" level=\"WARNING\" enabled_by_default=\"true\" />\n" +
                 "  <inspection_tool class=\"AssignmentToForLoopParameter\" enabled=\"true\" level=\"WARNING\" enabled_by_default=\"true\">\n" +
                 "    <option name=\"m_checkForeachParameters\" value=\"false\" />\n" +
                 "  </inspection_tool>\n" +
                 "</profile>";
    foo.readExternal(JDOMUtil.loadDocument(test).getRootElement());
    foo.initInspectionTools(getProject());
    Element serialized = new Element("profile");
    foo.writeExternal(serialized);
    assertEquals(test, JDOMUtil.writeElement(serialized));
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
