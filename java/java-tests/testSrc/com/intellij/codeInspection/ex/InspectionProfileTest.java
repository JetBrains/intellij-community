// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.ex;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalInspectionEP;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.dataFlow.DataFlowInspection;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspectionBase;
import com.intellij.codeInspection.unusedSymbol.UnusedSymbolLocalInspectionBase;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.profile.codeInspection.BaseInspectionProfileManager;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager;
import com.intellij.profile.codeInspection.ui.header.InspectionProfileSchemesPanel;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.testFramework.InspectionsKt;
import com.intellij.testFramework.LightIdeaTestCase;
import com.intellij.util.SmartList;
import com.siyeh.ig.naming.ClassNamingConvention;
import com.siyeh.ig.naming.FieldNamingConventionInspection;
import com.siyeh.ig.naming.NewClassNamingConventionInspection;
import com.siyeh.ig.naming.NewMethodNamingConventionInspection;
import org.intellij.lang.annotations.Language;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.intellij.testFramework.assertions.Assertions.assertThat;

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
    try {
      //noinspection SuperTearDownInFinally
      super.tearDown();
    }
    finally {
      //noinspection AssignmentToStaticFieldFromInstanceMethod
      InspectionProfileImpl.INIT_INSPECTIONS = false;
      getApplicationProfileManager().deleteProfile(PROFILE);
    }
  }

  @NotNull
  private static BaseInspectionProfileManager getApplicationProfileManager() {
    return (BaseInspectionProfileManager)InspectionProfileManager.getInstance();
  }

  public void testCopyProjectProfile() throws Exception {
    final Element element = loadProfile();
    final InspectionProfileImpl profile = createProfile();
    profile.readExternal(element);
    profile.getModifiableModel().commit();
    assertThat(profile.writeScheme()).isEqualTo(element);
  }

  @NotNull
  private static InspectionProfileImpl createEmptyProfile() throws IOException, JDOMException {
    InspectionProfileImpl profile = createProfile(new InspectionProfileImpl("foo"));
    final Element element = readFromXml(profile, "<profile version=\"1.0\">\n" +
                                                 "  <option name=\"myName\" value=\"" + PROFILE + "\" />\n" +
                                                 "</profile>");
    assertThat(profile.writeScheme()).isEqualTo(element);
    return profile;
  }

  @NotNull
  private static Element readFromXml(InspectionProfileImpl profile, @Language("XML") String serialized) throws IOException, JDOMException {
    final Element root = JDOMUtil.load(serialized);
    profile.readExternal(root);
    profile.getModifiableModel().commit();
    return root;
  }

  @NotNull
  private InspectionProfileImpl importProfile(Element toImportElement) {
    InspectionProfileImpl profile =
      InspectionProfileSchemesPanel.importInspectionProfile(toImportElement, getApplicationProfileManager(), getProject());
    return Objects.requireNonNull(profile);
  }

  private static InspectionProfileImpl createProfile() {
    return createProfile(InspectionProfileKt.getBASE_PROFILE());
  }

  private static InspectionProfileImpl createProfile(@NotNull InspectionProfileImpl base) {
    return new InspectionProfileImpl(PROFILE, InspectionToolRegistrar.getInstance(), base);
  }

  public void testModificationWithoutModification() {
    InspectionProfileImpl profile = createProfile();
    profile.getAllTools();
    assertTrue(profile.wasInitialized());
    assertNotEmpty(profile.myTools.keySet());
    profile.modifyProfile(m -> {});
    assertTrue(profile.wasInitialized());
    assertNotEmpty(profile.myTools.keySet());
  }

  public void testSameNameSharedProfile() {
    BaseInspectionProfileManager profileManager = getApplicationProfileManager();
    InspectionProfileImpl localProfile = createProfile();
    updateProfile(profileManager, localProfile);

    ProjectInspectionProfileManager projectProfileManager = ProjectInspectionProfileManager.getInstance(getProject());
    try {
      // normally on open project profile wrappers are init for both managers
      updateProfile(profileManager, localProfile);
      InspectionProfileImpl profile = new InspectionProfileImpl(PROFILE, InspectionToolRegistrar.getInstance(), projectProfileManager, null);
      updateProfile(projectProfileManager, profile);
      projectProfileManager.setRootProfile(profile.getName());

      assertThat(profile).isEqualTo(projectProfileManager.getCurrentProfile());
    }
    finally {
      projectProfileManager.deleteProfile(PROFILE);
    }
  }

  private static void updateProfile(BaseInspectionProfileManager profileManager, InspectionProfileImpl localProfile) {
    profileManager.addProfile(localProfile);
    profileManager.fireProfileChanged(localProfile);
  }

  public void testConvertOldProfile() throws Exception {
    @Language("XML") String content = "<inspections version=\"1.0\">\n" +
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
                                      "      </value>\n" +
                                      "    </option>\n" +
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
                                      "</inspections>";
    InspectionProfileImpl profile = createProfile();
    readFromXml(profile, content);

    assertThat(profile.writeScheme()).isEqualTo(loadProfile());
  }

  private static Element loadProfile() throws IOException, JDOMException {
    return JDOMUtil.load("<profile version=\"1.0\">\n" +
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
                       "</profile>");
  }

  public void testReloadProfileWithUnknownScopes() throws Exception {
    @Language("XML") String content = "<profile version=\"1.0\">\n" +
                                      "  <option name=\"myName\" value=\"" +
                                      PROFILE +
                                      "\" />\n" +
                                      "  <inspection_tool class=\"ArgNamesErrorsInspection\" enabled=\"true\" level=\"ERROR\" enabled_by_default=\"false\" />\n" +
                                      "  <inspection_tool class=\"ArgNamesWarningsInspection\" enabled=\"true\" level=\"WARNING\" enabled_by_default=\"false\" />\n" +
                                      "  <inspection_tool class=\"AroundAdviceStyleInspection\" enabled=\"true\" level=\"WARNING\" enabled_by_default=\"false\" />\n" +
                                      "  <inspection_tool class=\"DeclareParentsInspection\" enabled=\"true\" level=\"ERROR\" enabled_by_default=\"false\" />\n" +
                                      /*"  <inspection_tool class=\"ManifestDomInspection\" enabled=\"true\" level=\"ERROR\" enabled_by_default=\"false\" />\n" +*/
                                      "  <inspection_tool class=\"MissingAspectjAutoproxyInspection\" enabled=\"true\" level=\"WARNING\" enabled_by_default=\"false\" />\n" +
                                      "  <inspection_tool class=\"UNUSED_IMPORT\" enabled=\"true\" level=\"WARNING\" enabled_by_default=\"true\">\n" +
                                      "    <scope name=\"Unknown scope name\" level=\"WARNING\" enabled=\"true\" />\n" +
                                      "  </inspection_tool>\n" +
                                      "</profile>";
    final InspectionProfileImpl profile = createProfile();
    Element element = readFromXml(profile, content);
    assertThat(profile.writeScheme()).isEqualTo(element);
  }

  public void testMergeUnusedDeclarationAndUnusedSymbol() throws Exception {
    //no specific settings
    InspectionProfileImpl profile = createEmptyProfile();


    //settings to merge
    @Language("XML") String serialized =
      "<profile version=\"1.0\">\n" +
      "  <option name=\"myName\" value=\"" + PROFILE + "\" />\n" +
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
      "</profile>";
    final Element unusedProfile = readFromXml(profile, serialized);
    assertEquals(serialized, serialize(profile));

    //make them default
    profile = createProfile(new InspectionProfileImpl("foo"));
    profile.readExternal(unusedProfile);
    profile.modifyProfile(it -> {
      InspectionToolWrapper<?, ?> toolWrapper = it.getInspectionTool("unused", getProject());
      UnusedDeclarationInspectionBase tool = (UnusedDeclarationInspectionBase)toolWrapper.getTool();
      tool.ADD_NONJAVA_TO_ENTRIES = true;
      UnusedSymbolLocalInspectionBase inspectionTool = tool.getSharedLocalInspectionTool();
      inspectionTool.setParameterVisibility(PsiModifier.PUBLIC);
    });
    @Language("XML") String mergedText = "<profile version=\"1.0\">\n" +
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

    Element toImportElement = profile.writeScheme();
    final InspectionProfileImpl importedProfile = importProfile(toImportElement);

    //check merged
    profile = createProfile(new InspectionProfileImpl("foo"));
    Element mergedElement = readFromXml(profile, mergedText);
    assertThat(profile.writeScheme()).isEqualTo(mergedElement);

    assertThat(importedProfile.writeScheme()).isEqualTo(mergedElement);
  }

  public void testScopesInNamingConventions() throws Exception {
    @Language("XML") String unchanged = "<profile version=\"1.0\">\n" +
                                        "  <option name=\"myName\" value=\"Name convention\" />\n" +
                                        "  <inspection_tool class=\"AnnotationNamingConvention\" enabled=\"true\" level=\"WARNING\" enabled_by_default=\"true\">\n" +
                                        "    <option name=\"m_regex\" value=\"[A-Z][A-Za-z\\d]*Ann\" />\n" +
                                        "    <option name=\"m_minLength\" value=\"8\" />\n" +
                                        "    <option name=\"m_maxLength\" value=\"58\" />\n" +
                                        "  </inspection_tool>\n" +
                                        "  <inspection_tool class=\"ClassNamingConvention\" enabled=\"true\" level=\"WARNING\" enabled_by_default=\"true\">\n" +
                                        "    <scope name=\"Production\" level=\"ERROR\" enabled=\"true\">\n" +
                                        "      <option name=\"m_regex\" value=\"[A-Z][A-Za-z\\d]*\" />\n" +
                                        "      <option name=\"m_minLength\" value=\"8\" />\n" +
                                        "      <option name=\"m_maxLength\" value=\"64\" />\n" +
                                        "    </scope>\n" +
                                        "    <option name=\"m_regex\" value=\"[A-Z][A-Za-z\\d]*Class\" />\n" +
                                        "    <option name=\"m_minLength\" value=\"10\" />\n" +
                                        "    <option name=\"m_maxLength\" value=\"60\" />\n" +
                                        "  </inspection_tool>\n" +
                                        "  <inspection_tool class=\"EnumeratedClassNamingConvention\" enabled=\"true\" level=\"WARNING\" enabled_by_default=\"true\">\n" +
                                        "    <scope name=\"Tests\" level=\"WEAK WARNING\" enabled=\"true\">\n" +
                                        "      <option name=\"m_regex\" value=\"[A-Z][A-Za-z\\d]*\" />\n" +
                                        "      <option name=\"m_minLength\" value=\"8\" />\n" +
                                        "      <option name=\"m_maxLength\" value=\"64\" />\n" +
                                        "    </scope>\n" +
                                        "    <option name=\"m_regex\" value=\"[A-Z][A-Za-z\\d]*Enum\" />\n" +
                                        "    <option name=\"m_minLength\" value=\"12\" />\n" +
                                        "    <option name=\"m_maxLength\" value=\"62\" />\n" +
                                        "  </inspection_tool>\n" +
                                        "  <inspection_tool class=\"InterfaceNamingConvention\" enabled=\"true\" level=\"WARNING\" enabled_by_default=\"true\">\n" +
                                        "    <option name=\"m_regex\" value=\"[A-Z][A-Za-z\\d]*I\" />\n" +
                                        "    <option name=\"m_minLength\" value=\"14\" />\n" +
                                        "    <option name=\"m_maxLength\" value=\"64\" />\n" +
                                        "  </inspection_tool>\n" +
                                        "  <inspection_tool class=\"JUnitAbstractTestClassNamingConvention\" enabled=\"true\" level=\"WARNING\" enabled_by_default=\"true\">\n" +
                                        "    <option name=\"m_regex\" value=\"[A-Z][A-Za-z\\d]*TestCase\" />\n" +
                                        "    <option name=\"m_minLength\" value=\"2\" />\n" +
                                        "    <option name=\"m_maxLength\" value=\"52\" />\n" +
                                        "  </inspection_tool>\n" +
                                        "  <inspection_tool class=\"JUnitTestClassNamingConvention\" enabled=\"true\" level=\"ERROR\" enabled_by_default=\"true\">\n" +
                                        "    <option name=\"m_regex\" value=\"[A-Z][A-Za-z\\d]*Test\" />\n" +
                                        "    <option name=\"m_minLength\" value=\"4\" />\n" +
                                        "    <option name=\"m_maxLength\" value=\"54\" />\n" +
                                        "  </inspection_tool>\n" +
                                        "  <inspection_tool class=\"TypeParameterNamingConvention\" enabled=\"true\" level=\"WARNING\" enabled_by_default=\"true\">\n" +
                                        "    <option name=\"m_regex\" value=\"[A-Z][A-Za-z\\d]*TP\" />\n" +
                                        "    <option name=\"m_minLength\" value=\"16\" />\n" +
                                        "    <option name=\"m_maxLength\" value=\"66\" />\n" +
                                        "  </inspection_tool>\n" +
                                        "</profile>";
    InspectionProfileImpl profile = createProfile(new InspectionProfileImpl("foo"));
    readFromXml(profile, unchanged);
    assertEquals(unchanged, serialize(profile));
    ToolsImpl tools = profile.getToolsOrNull("NewClassNamingConvention", getProject());
    assertTrue(tools.isEnabled());
    for (ScopeToolState toolState : tools.getTools()) {
      NamedScope scope = toolState.getScope(getProject());
      assertNotNull(scope);
      String scopeName = scope.getName();
      NewClassNamingConventionInspection tool = (NewClassNamingConventionInspection)toolState.getTool().getTool();
      if ("Production".equals(scopeName)) {
        assertTrue(tool.isConventionEnabled(ClassNamingConvention.CLASS_NAMING_CONVENTION_SHORT_NAME));
        assertTrue(tool.isConventionEnabled("TypeParameterNamingConvention"));
        assertEquals(16, tool.getNamingConventionBean("TypeParameterNamingConvention").m_minLength);
      }
      else if ("Tests".equals(scopeName)) {
        assertTrue(tool.isConventionEnabled("TypeParameterNamingConvention"));
        assertEquals(16, tool.getNamingConventionBean("TypeParameterNamingConvention").m_minLength);
      }
    }
  }

  public void testMergeNamingConventions() throws Exception {
    //no specific settings
    InspectionProfileImpl profile = createEmptyProfile();

    @Language("XML") String unchanged = "<profile version=\"1.0\">\n" +
                                        "  <option name=\"myName\" value=\"" +
                                        PROFILE +
                                        "\" />\n" +
                                        "  <inspection_tool class=\"AbstractClassNamingConvention\" enabled=\"true\" level=\"WARNING\" enabled_by_default=\"true\" />\n" +
                                        "  <inspection_tool class=\"AnnotationNamingConvention\" enabled=\"true\" level=\"WARNING\" enabled_by_default=\"true\">\n" +
                                        "    <option name=\"m_regex\" value=\"[A-Z][A-Za-z\\d]*\" />\n" +
                                        "    <option name=\"m_minLength\" value=\"8\" />\n" +
                                        "    <option name=\"m_maxLength\" value=\"64\" />\n" +
                                        "  </inspection_tool>\n" +
                                        "  <inspection_tool class=\"ClassNamingConvention\" enabled=\"true\" level=\"WARNING\" enabled_by_default=\"true\">\n" +
                                        "    <option name=\"m_regex\" value=\"[A-Z][A-Za-z\\d]*\" />\n" +
                                        "    <option name=\"m_minLength\" value=\"8\" />\n" +
                                        "    <option name=\"m_maxLength\" value=\"64\" />\n" +
                                        "  </inspection_tool>\n" +
                                        "  <inspection_tool class=\"EnumeratedClassNamingConvention\" enabled=\"true\" level=\"WARNING\" enabled_by_default=\"true\">\n" +
                                        "    <option name=\"m_regex\" value=\"[A-Z][A-Za-z\\d]*\" />\n" +
                                        "    <option name=\"m_minLength\" value=\"8\" />\n" +
                                        "    <option name=\"m_maxLength\" value=\"64\" />\n" +
                                        "  </inspection_tool>\n" +
                                        "  <inspection_tool class=\"InterfaceNamingConvention\" enabled=\"true\" level=\"WARNING\" enabled_by_default=\"true\">\n" +
                                        "    <option name=\"m_regex\" value=\"[A-Z][A-Za-z\\d]*\" />\n" +
                                        "    <option name=\"m_minLength\" value=\"8\" />\n" +
                                        "    <option name=\"m_maxLength\" value=\"64\" />\n" +
                                        "  </inspection_tool>\n" +
                                        "  <inspection_tool class=\"TypeParameterNamingConvention\" enabled=\"true\" level=\"WARNING\" enabled_by_default=\"true\">\n" +
                                        "    <option name=\"m_regex\" value=\"[A-Z][A-Za-z\\d]*\" />\n" +
                                        "    <option name=\"m_minLength\" value=\"1\" />\n" +
                                        "    <option name=\"m_maxLength\" value=\"1\" />\n" +
                                        "  </inspection_tool>\n" +
                                        "</profile>";
    readFromXml(profile, unchanged);
    assertEquals(unchanged, serialize(profile));

    //make them default
    profile = createProfile(new InspectionProfileImpl("foo"));
    @Language("XML") String customSettingsText = "<profile version=\"1.0\">\n" +
                                                 "  <option name=\"myName\" value=\"ToConvert\" />\n" +
                                                 "  <inspection_tool class=\"AbstractClassNamingConvention\" enabled=\"true\" level=\"WARNING\" enabled_by_default=\"true\" />\n" +
                                                 "  <inspection_tool class=\"AnnotationNamingConvention\" enabled=\"true\" level=\"WARNING\" enabled_by_default=\"true\">\n" +
                                                 "    <option name=\"m_regex\" value=\"[A-Z][A-Za-z\\d]*\" />\n" +
                                                 "    <option name=\"m_minLength\" value=\"8\" />\n" +
                                                 "    <option name=\"m_maxLength\" value=\"256\" />\n" +
                                                 "  </inspection_tool>\n" +
                                                 "  <inspection_tool class=\"EnumeratedClassNamingConvention\" enabled=\"true\" level=\"WARNING\" enabled_by_default=\"true\">\n" +
                                                 "    <option name=\"m_regex\" value=\"[A-Z][A-Za-z\\d]*\" />\n" +
                                                 "    <option name=\"m_minLength\" value=\"1\" />\n" +
                                                 "    <option name=\"m_maxLength\" value=\"64\" />\n" +
                                                 "  </inspection_tool>\n" +
                                                 "  <inspection_tool class=\"InterfaceNamingConvention\" enabled=\"true\" level=\"WARNING\" enabled_by_default=\"true\">\n" +
                                                 "    <option name=\"m_regex\" value=\"[A-Z][A-Za-z\\d]*\" />\n" +
                                                 "    <option name=\"m_minLength\" value=\"8\" />\n" +
                                                 "    <option name=\"m_maxLength\" value=\"64\" />\n" +
                                                 "  </inspection_tool>\n" +
                                                 "  <inspection_tool class=\"TypeParameterNamingConvention\" enabled=\"false\" level=\"WARNING\" enabled_by_default=\"true\">\n" +
                                                 "    <option name=\"m_regex\" value=\"[A-Z][A-Za-z\\d]*\" />\n" +
                                                 "    <option name=\"m_minLength\" value=\"1\" />\n" +
                                                 "    <option name=\"m_maxLength\" value=\"1\" />\n" +
                                                 "  </inspection_tool>\n" +
                                                 "</profile>";
    profile.readExternal(JDOMUtil.load(customSettingsText));
    assertEquals(customSettingsText, serialize(profile));
    InspectionToolWrapper<?, ?> wrapper = profile.getInspectionTool("NewClassNamingConvention", getProject());
    assertNotNull(wrapper);
    NewClassNamingConventionInspection tool = (NewClassNamingConventionInspection)wrapper.getTool();
    assertEquals(256, tool.getNamingConventionBean("AnnotationNamingConvention").m_maxLength);
    assertEquals(1, tool.getNamingConventionBean("EnumeratedClassNamingConvention").m_minLength);
    assertTrue(profile.isToolEnabled(HighlightDisplayKey.find("NewClassNamingConvention"), null));
    assertFalse(tool.isConventionEnabled("TypeParameterNamingConvention"));
    assertFalse(tool.isConventionEnabled(ClassNamingConvention.CLASS_NAMING_CONVENTION_SHORT_NAME));

    Element toImportElement = profile.writeScheme();
    final InspectionProfileImpl importedProfile = importProfile(toImportElement);

    //check merged
    profile = createProfile(new InspectionProfileImpl("foo"));
    Element mergedElement = readFromXml(profile, customSettingsText);
    assertThat(profile.writeScheme()).isEqualTo(mergedElement);

    assertThat(importedProfile.writeScheme()).isEqualTo(mergedElement);
  }

  public void testKeepUnloadedMergeNamingConventions() throws Exception {
      String unchanged = "<profile version=\"1.0\">\n" +
                         "  <option name=\"myName\" value=\"ToConvert\" />\n" +
                         "  <inspection_tool class=\"NewClassNamingConvention\" enabled=\"true\" level=\"WARNING\" enabled_by_default=\"true\">\n" +
                         "    <extension name=\"AnnotationNamingConvention\" enabled=\"true\">\n" +
                         "      <option name=\"inheritDefaultSettings\" value=\"false\" />\n" +
                         "      <option name=\"m_regex\" value=\"[A-Z][A-Za-z\\d]*\" />\n" +
                         "      <option name=\"m_minLength\" value=\"8\" />\n" +
                         "      <option name=\"m_maxLength\" value=\"66\" />\n" +
                         "    </extension>\n" +
                         "    <extension name=\"AnnotationNamingConventionUnknown\" enabled=\"true\">\n" +
                         "      <option name=\"m_regex\" value=\"[A-Z][A-Za-z\\d]*\" />\n" +
                         "      <option name=\"m_minLength\" value=\"8\" />\n" +
                         "      <option name=\"m_maxLength\" value=\"66\" />\n" +
                         "    </extension>\n" +
                         "    <extension name=\"ClassNamingConvention\" enabled=\"true\">\n" +
                         "      <option name=\"m_regex\" value=\"[A-Z][A-Za-z\\d]*\" />\n" +
                         "      <option name=\"m_minLength\" value=\"8\" />\n" +
                         "      <option name=\"m_maxLength\" value=\"66\" />\n" +
                         "    </extension>\n" +
                         "  </inspection_tool>\n" +
                         "</profile>";
      final Element allEnabledProfile = JDOMUtil.load(unchanged);
      InspectionProfileImpl profile = createProfile(new InspectionProfileImpl("foo"));
      profile.readExternal(allEnabledProfile);
      profile.initInspectionTools();
      assertEquals(unchanged, serialize(profile));
    }

  public void testMergeMethodNamingConventions() throws Exception {
    InspectionProfileImpl profile = createEmptyProfile();

    @Language("XML") String customSettingsText = "<profile version=\"1.0\">\n" +
                                                 "  <option name=\"myName\" value=\"empty\" />\n" +
                                                 "  <inspection_tool class=\"InstanceMethodNamingConvention\" enabled=\"true\" level=\"WARNING\" enabled_by_default=\"true\">\n" +
                                                 "    <option name=\"m_regex\" value=\"i_[a-z][A-Za-z\\d]*\" />\n" +
                                                 "    <option name=\"m_minLength\" value=\"4\" />\n" +
                                                 "    <option name=\"m_maxLength\" value=\"32\" />\n" +
                                                 "  </inspection_tool>\n" +
                                                 "  <inspection_tool class=\"NativeMethodNamingConvention\" enabled=\"true\" level=\"WARNING\" enabled_by_default=\"true\">\n" +
                                                 "    <option name=\"m_regex\" value=\"n_[a-z][A-Za-z\\d]*\" />\n" +
                                                 "  </inspection_tool>\n" +
                                                 "  <inspection_tool class=\"StaticMethodNamingConvention\" enabled=\"true\" level=\"WARNING\" enabled_by_default=\"true\">\n" +
                                                 "    <option name=\"m_regex\" value=\"s_[a-z][A-Za-z\\d]*\" />\n" +
                                                 "    <option name=\"m_minLength\" value=\"4\" />\n" +
                                                 "    <option name=\"m_maxLength\" value=\"32\" />\n" +
                                                 "  </inspection_tool>\n" +
                                                 "</profile>";

    readFromXml(profile, customSettingsText);
    assertEquals(customSettingsText, serialize(profile));

    InspectionToolWrapper wrapper = profile.getInspectionTool("NewMethodNamingConvention", getProject());
    assertNotNull(wrapper);
    NewMethodNamingConventionInspection tool = (NewMethodNamingConventionInspection)wrapper.getTool();
    assertEquals("i_[a-z][A-Za-z\\d]*", tool.getNamingConventionBean("InstanceMethodNamingConvention").m_regex);
    assertEquals("n_[a-z][A-Za-z\\d]*", tool.getNamingConventionBean("NativeMethodNamingConvention").m_regex);
    assertTrue(profile.isToolEnabled(HighlightDisplayKey.find("NewMethodNamingConvention"), null));
    assertFalse(tool.isConventionEnabled("JUnit4MethodNamingConvention"));
    assertFalse(tool.isConventionEnabled("JUnit3MethodNamingConvention"));

    Element toImportElement = profile.writeScheme();
    final InspectionProfileImpl importedProfile = importProfile(toImportElement);

    //check merged
    profile = createProfile(new InspectionProfileImpl("foo"));
    Element mergedElement = readFromXml(profile, customSettingsText);
    assertThat(profile.writeScheme()).isEqualTo(mergedElement);

    assertThat(importedProfile.writeScheme()).isEqualTo(mergedElement);
  }

  public void testMergeFieldNamingConventions() throws Exception {
    InspectionProfileImpl profile = createEmptyProfile();

    @Language("XML") String customSettingsText = "<profile version=\"1.0\">\n" +
                                                 "  <option name=\"myName\" value=\"empty\" />\n" +
                                                 "  <inspection_tool class=\"ConstantNamingConvention\" enabled=\"true\" level=\"WARNING\" enabled_by_default=\"true\">\n" +
                                                 "    <option name=\"m_regex\" value=\"i_[a-z][A-Za-z\\d]*\" />\n" +
                                                 "    <option name=\"m_minLength\" value=\"4\" />\n" +
                                                 "    <option name=\"m_maxLength\" value=\"32\" />\n" +
                                                 "    <option name=\"onlyCheckImmutables\" value=\"true\" />\n" +
                                                 "  </inspection_tool>\n" +
                                                 "  <inspection_tool class=\"StaticVariableNamingConvention\" enabled=\"true\" level=\"WARNING\" enabled_by_default=\"true\">\n" +
                                                 "    <option name=\"m_regex\" value=\"i_[a-z][A-Za-z\\d]*\" />\n" +
                                                 "    <option name=\"m_minLength\" value=\"4\" />\n" +
                                                 "    <option name=\"m_maxLength\" value=\"32\" />\n" +
                                                 "    <option name=\"checkMutableFinals\" value=\"true\" />\n" +
                                                 "  </inspection_tool>\n" +
                                                 "</profile>";

    readFromXml(profile, customSettingsText);
    assertEquals(customSettingsText, serialize(profile));

    InspectionToolWrapper wrapper = profile.getInspectionTool("FieldNamingConvention", getProject());
    assertNotNull(wrapper);
    FieldNamingConventionInspection tool = (FieldNamingConventionInspection)wrapper.getTool();
    assertTrue(profile.isToolEnabled(HighlightDisplayKey.find("FieldNamingConvention"), null));
    assertTrue(tool.isConventionEnabled("StaticVariableNamingConvention"));
    assertTrue(tool.isConventionEnabled("ConstantNamingConvention"));
    assertTrue(tool.isConventionEnabled("ConstantWithMutableFieldTypeNamingConvention"));

    Element toImportElement = profile.writeScheme();
    final InspectionProfileImpl importedProfile = importProfile(toImportElement);

    //check merged
    profile = createProfile(new InspectionProfileImpl("foo"));
    Element mergedElement = readFromXml(profile, customSettingsText);
    assertThat(profile.writeScheme()).isEqualTo(mergedElement);

    assertThat(importedProfile.writeScheme()).isEqualTo(mergedElement);
  }

  public void testStoredMemberVisibility() throws Exception {
    InspectionProfileImpl profile = createProfile(new InspectionProfileImpl("foo"));
    profile.readExternal(JDOMUtil.load("<profile version=\"1.0\">\n" +
                                               "  <inspection_tool class=\"unused\" enabled=\"true\" level=\"WARNING\" enabled_by_default=\"true\">\n" +
                                               "    <option name=\"LOCAL_VARIABLE\" value=\"true\" />\n" +
                                               "    <option name=\"FIELD\" value=\"true\" />\n" +
                                               "    <option name=\"METHOD\" value=\"true\" />\n" +
                                               "    <option name=\"CLASS\" value=\"true\" />\n" +
                                               "    <option name=\"PARAMETER\" value=\"true\" />\n" +
                                               "    <option name=\"REPORT_PARAMETER_FOR_PUBLIC_METHODS\" value=\"true\" />\n" +
                                               "    <option name=\"ADD_MAINS_TO_ENTRIES\" value=\"true\" />\n" +
                                               "    <option name=\"ADD_APPLET_TO_ENTRIES\" value=\"true\" />\n" +
                                               "    <option name=\"ADD_SERVLET_TO_ENTRIES\" value=\"true\" />\n" +
                                               "    <option name=\"ADD_NONJAVA_TO_ENTRIES\" value=\"false\" />\n" +
                                               "  </inspection_tool>\n" +
                                               "</profile>"));
    profile.modifyProfile(it -> {
      InspectionToolWrapper<?, ?> toolWrapper = it.getInspectionTool("unused", getProject());
      UnusedDeclarationInspectionBase tool = (UnusedDeclarationInspectionBase)toolWrapper.getTool();
      UnusedSymbolLocalInspectionBase inspectionTool = tool.getSharedLocalInspectionTool();
      inspectionTool.setClassVisibility(PsiModifier.PUBLIC);
      inspectionTool.CLASS = false;
    });
    String mergedText = "<profile version=\"1.0\">\n" +
                            "  <option name=\"myName\" value=\"ToConvert\" />\n" +
                            "  <inspection_tool class=\"unused\" enabled=\"true\" level=\"WARNING\" enabled_by_default=\"true\">\n" +
                            "    <option name=\"LOCAL_VARIABLE\" value=\"true\" />\n" +
                            "    <option name=\"FIELD\" value=\"true\" />\n" +
                            "    <option name=\"METHOD\" value=\"true\" />\n" +
                            "    <option name=\"CLASS\" value=\"false\" />\n" +
                            "    <option name=\"PARAMETER\" value=\"true\" />\n" +
                            "    <option name=\"REPORT_PARAMETER_FOR_PUBLIC_METHODS\" value=\"true\" />\n" +
                            "    <option name=\"ADD_MAINS_TO_ENTRIES\" value=\"true\" />\n" +
                            "    <option name=\"ADD_APPLET_TO_ENTRIES\" value=\"true\" />\n" +
                            "    <option name=\"ADD_SERVLET_TO_ENTRIES\" value=\"true\" />\n" +
                            "    <option name=\"ADD_NONJAVA_TO_ENTRIES\" value=\"false\" />\n" +
                            "  </inspection_tool>\n" +
                            "</profile>";
    assertEquals(mergedText, serialize(profile));
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

  public void testMergedMalformedSetUpTearDownInspections() throws Exception {
    checkMergedNoChanges("<profile version=\"1.0\">\n" +
                         "  <option name=\"myName\" value=\"" + PROFILE + "\" />\n" +
                         "  <inspection_tool class=\"SetupIsPublicVoidNoArg\" enabled=\"true\" level=\"WARNING\" enabled_by_default=\"false\" />\n" +
                         "  <inspection_tool class=\"TeardownIsPublicVoidNoArg\" enabled=\"true\" level=\"WARNING\" enabled_by_default=\"false\" />\n" +
                         "</profile>");
    checkMergedNoChanges("<profile version=\"1.0\">\n" +
                         "  <option name=\"myName\" value=\"" + PROFILE + "\" />\n" +
                         "  <inspection_tool class=\"MalformedSetUpTearDown\" enabled=\"true\" level=\"WARNING\" enabled_by_default=\"false\" />\n" +
                         "</profile>");
  }

  public void testMergedMethodDoesntCallSuperMethodInspections() throws Exception {
    checkMergedNoChanges("<profile version=\"1.0\">\n" +
                         "  <option name=\"myName\" value=\"" + PROFILE + "\" />\n" +
                         "  <inspection_tool class=\"CloneCallsSuperClone\" enabled=\"false\" level=\"WARNING\" enabled_by_default=\"false\" />\n" +
                         "  <inspection_tool class=\"FinalizeCallsSuperFinalize\" enabled=\"false\" level=\"WARNING\" enabled_by_default=\"false\">\n" +
                         "    <option name=\"ignoreObjectSubclasses\" value=\"false\" />\n" +
                         "    <option name=\"ignoreTrivialFinalizers\" value=\"true\" />\n" +
                         "  </inspection_tool>\n" +
                         "  <inspection_tool class=\"RefusedBequest\" enabled=\"false\" level=\"WARNING\" enabled_by_default=\"false\">\n" +
                         "    <option name=\"ignoreEmptySuperMethods\" value=\"false\" />\n" +
                         "    <option name=\"onlyReportWhenAnnotated\" value=\"true\" />\n" +
                         "  </inspection_tool>\n" +
                         "  <inspection_tool class=\"SetupCallsSuperSetup\" enabled=\"true\" level=\"WARNING\" enabled_by_default=\"true\" />\n" +
                         "  <inspection_tool class=\"TeardownCallsSuperTeardown\" enabled=\"true\" level=\"WARNING\" enabled_by_default=\"true\" />\n" +
                         "</profile>");
  }

  public void testMergedThrowableNotThrownInspections() throws Exception {
    checkMergedNoChanges("<profile version=\"1.0\">\n" +
                         "  <option name=\"myName\" value=\"" + PROFILE + "\" />\n" +
                         "  <inspection_tool class=\"ThrowableInstanceNeverThrown\" enabled=\"false\" level=\"WARNING\" enabled_by_default=\"false\" />\n" +
                         "  <inspection_tool class=\"ThrowableResultOfMethodCallIgnored\" enabled=\"false\" level=\"WARNING\" enabled_by_default=\"false\" />\n" +
                         "</profile>");
    checkMergedNoChanges("<profile version=\"1.0\">\n" +
                         "  <option name=\"myName\" value=\"" + PROFILE + "\" />\n" +
                         "  <inspection_tool class=\"ThrowableNotThrown\" enabled=\"false\" level=\"WARNING\" enabled_by_default=\"false\" />\n" +
                         "</profile>");
  }

  public void testMergedRedundantStringOperationsInspections() throws Exception {
    InspectionProfileImpl profile = checkMergedNoChanges("<profile version=\"1.0\">\n" +
                                                         "  <option name=\"myName\" value=\"" + PROFILE + "\" />\n" +
                                                         "  <inspection_tool class=\"ConstantStringIntern\" enabled=\"false\" level=\"WARNING\" enabled_by_default=\"false\" />\n" +
                                                         "  <inspection_tool class=\"StringConstructor\" enabled=\"false\" level=\"WARNING\" enabled_by_default=\"false\">\n" +
                                                         "    <option name=\"ignoreSubstringArguments\" value=\"false\" />\n" +
                                                         "  </inspection_tool>\n" +
                                                         "  <inspection_tool class=\"StringToString\" enabled=\"false\" level=\"WARNING\" enabled_by_default=\"false\" />\n" +
                                                         "  <inspection_tool class=\"SubstringZero\" enabled=\"false\" level=\"WARNING\" enabled_by_default=\"false\" />\n" +
                                                         "</profile>");
    assertFalse(profile.isToolEnabled(HighlightDisplayKey.find("StringOperationCanBeSimplified"), null));
  }

  public void testSecondMergedRedundantStringOperationsInspections() throws Exception {
    InspectionProfileImpl bothDisabled = checkMergedNoChanges("<profile version=\"1.0\">\n" +
                                                         "  <option name=\"myName\" value=\"ToConvert\" />\n" +
                                                         "  <inspection_tool class=\"RedundantStringOperation\" enabled=\"false\" level=\"WARNING\" enabled_by_default=\"false\" />\n" +
                                                         "  <inspection_tool class=\"StringConstructor\" enabled=\"false\" level=\"WARNING\" enabled_by_default=\"false\">\n" +
                                                         "    <option name=\"ignoreSubstringArguments\" value=\"false\" />\n" +
                                                         "  </inspection_tool>\n" +
                                                         "</profile>");
    assertFalse(bothDisabled.isToolEnabled(HighlightDisplayKey.find("StringOperationCanBeSimplified"), null));

    InspectionProfileImpl oneEnabled = checkMergedNoChanges("<profile version=\"1.0\">\n" +
                                                         "  <option name=\"myName\" value=\"ToConvert\" />\n" +
                                                         "  <inspection_tool class=\"RedundantStringOperation\" enabled=\"false\" level=\"WARNING\" enabled_by_default=\"false\" />\n" +
                                                         "  <inspection_tool class=\"StringConstructor\" enabled=\"true\" level=\"WARNING\" enabled_by_default=\"false\">\n" +
                                                         "    <option name=\"ignoreSubstringArguments\" value=\"false\" />\n" +
                                                         "  </inspection_tool>\n" +
                                                         "</profile>");
    assertTrue(oneEnabled.isToolEnabled(HighlightDisplayKey.find("StringOperationCanBeSimplified"), null));
  }

  public void testMergedCallToSuspiciousStringMethodInspections() throws Exception {
    checkMergedNoChanges("<profile version=\"1.0\">\n" +
                         "  <option name=\"myName\" value=\"" + PROFILE + "\" />\n" +
                         "  <inspection_tool class=\"StringCompareTo\" enabled=\"true\" level=\"WARNING\" enabled_by_default=\"true\" />\n" +
                         "  <inspection_tool class=\"StringEquals\" enabled=\"true\" level=\"WARNING\" enabled_by_default=\"true\" />\n" +
                         "  <inspection_tool class=\"StringEqualsIgnoreCase\" enabled=\"true\" level=\"WARNING\" enabled_by_default=\"true\" />\n" +
                         "</profile>" );
  }

  public void testMergedMisspelledInspections() throws Exception {
    checkMergedNoChanges("<profile version=\"1.0\">\n" +
                         "  <option name=\"myName\" value=\"" + PROFILE + "\" />\n" +
                         "  <inspection_tool class=\"MethodNamesDifferOnlyByCase\" enabled=\"true\" level=\"WARNING\" enabled_by_default=\"false\">\n" +
                         "    <option name=\"ignoreIfMethodIsOverride\" value=\"false\" />\n" +
                         "  </inspection_tool>\n" +
                         "</profile>");
    checkMergedNoChanges("<profile version=\"1.0\">\n" +
                         "  <option name=\"myName\" value=\"" + PROFILE + "\" />\n" +
                         "  <inspection_tool class=\"MethodNamesDifferOnlyByCase\" enabled=\"true\" level=\"WARNING\" enabled_by_default=\"false\">\n" +
                         "    <option name=\"ignoreIfMethodIsOverride\" value=\"false\" />\n" +
                         "  </inspection_tool>\n" +
                         "  <inspection_tool class=\"MisspelledSetUp\" enabled=\"true\" level=\"WARNING\" enabled_by_default=\"false\" />\n" +
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

  private static InspectionProfileImpl checkMergedNoChanges(@Language("XML") String initialText) throws Exception {
    InspectionProfileImpl profile = createProfile(new InspectionProfileImpl("foo"));
    readFromXml(profile, initialText);
    assertEquals(initialText, serialize(profile));
    return profile;
  }

  public void testLockProfile() {
    List<InspectionToolWrapper<?, ?>> list = new ArrayList<>();
    list.add(createTool("foo", true));

    InspectionToolsSupplier.Simple toolSupplier = new InspectionToolsSupplier.Simple(list);
    Disposer.register(getTestRootDisposable(), toolSupplier);
    InspectionProfileImpl profile = createProfile(toolSupplier);

    List<ScopeToolState> tools = profile.getAllTools();
    assertEquals(1, tools.size());
    assertTrue(profile.isToolEnabled(HighlightDisplayKey.find("foo")));
    assertTrue(profile.getToolDefaultState("foo", getProject()).isEnabled());

    profile.modifyProfile(it -> {
      it.lockProfile(true);
      it.initInspectionTools(getProject()); // todo commit should take care of initialization
    });

    assertEquals("<profile version=\"1.0\" is_locked=\"true\">\n" +
                 "  <option name=\"myName\" value=\"Foo\" />\n" +
                 "  <inspection_tool class=\"foo\" enabled=\"true\" level=\"ERROR\" enabled_by_default=\"true\" />\n" +
                 "</profile>",
                 serialize(profile));

    Element element = profile.writeScheme();

    list.add(createTool("bar", true));
    list.add(createTool("disabled", false));

    profile = createProfile(toolSupplier);
    profile.readExternal(element);

    tools = profile.getAllTools();
    assertEquals(3, tools.size());

    assertTrue(profile.isProfileLocked());
    assertFalse(profile.isToolEnabled(HighlightDisplayKey.find("bar")));
    assertFalse(profile.isToolEnabled(HighlightDisplayKey.find("disabled")));

    assertFalse(profile.getToolDefaultState("bar", getProject()).isEnabled());
    assertFalse(profile.getToolDefaultState("disabled", getProject()).isEnabled());

    assertEquals("<profile version=\"1.0\" is_locked=\"true\">\n" +
                 "  <option name=\"myName\" value=\"Foo\" />\n" +
                 "  <inspection_tool class=\"bar\" enabled=\"false\" level=\"ERROR\" enabled_by_default=\"false\" />\n" +
                 "  <inspection_tool class=\"disabled\" enabled=\"false\" level=\"ERROR\" enabled_by_default=\"false\" />\n" +
                 "  <inspection_tool class=\"foo\" enabled=\"true\" level=\"ERROR\" enabled_by_default=\"true\" />\n" +
                 "</profile>", serialize(profile));
  }

  private static String serialize(InspectionProfileImpl profile) {
    return JDOMUtil.writeElement(profile.writeScheme());
  }

  private static InspectionProfileImpl createProfile(@NotNull InspectionToolsSupplier toolSupplier) {
    InspectionProfileImpl base = new InspectionProfileImpl("Base", toolSupplier, (InspectionProfileImpl)null);
    return new InspectionProfileImpl("Foo", toolSupplier, base);
  }

  public void testGlobalInspectionContext() {
    InspectionProfileImpl profile = new InspectionProfileImpl("Foo");
    InspectionsKt.disableAllTools(profile);
    profile.enableTool(new UnusedDeclarationInspectionBase(true).getShortName(), getProject());

    GlobalInspectionContextImpl context = ((InspectionManagerEx)InspectionManager.getInstance(getProject())).createNewGlobalContext();
    context.setExternalProfile(profile);
    context.initializeTools(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
  }

  public void testInspectionsInitialization() {
    InspectionProfileImpl foo = new InspectionProfileImpl("foo");
    assertEquals(0, countInitializedTools(foo));
    foo.initInspectionTools(getProject());
    assertEquals(0, countInitializedTools(foo));

    InspectionProfileModifiableModel model = foo.getModifiableModel();
    assertEquals(0, countInitializedTools(model));
    model.commit();
    assertEquals(0, countInitializedTools(model));
    assertEquals(0, countInitializedTools(foo));

    model = foo.getModifiableModel();
    assertEquals(0, countInitializedTools(model));
    List<ScopeToolState> tools = model.getAllTools();
    for (ScopeToolState tool : tools) {
      if (!tool.isEnabled()) {
        tool.setEnabled(true);
      }
    }
    model.commit();
    assertEquals(0, countInitializedTools(model));
  }

  public void testDoNotInstantiateOnSave() {
    InspectionProfileImpl profile = new InspectionProfileImpl("profile", InspectionToolRegistrar.getInstance(), InspectionProfileKt.getBASE_PROFILE());
    assertEquals(0, countInitializedTools(profile));
    List<InspectionToolWrapper<?, ?>> toolWrappers = profile.getInspectionTools(null);
    assertTrue(toolWrappers.size() > 0);
    InspectionToolWrapper<?, ?> toolWrapper = profile.getInspectionTool(new DataFlowInspection().getShortName(), getProject());
    assertNotNull(toolWrapper);
    String id = toolWrapper.getShortName();
    profile.setToolEnabled(id, !profile.isToolEnabled(HighlightDisplayKey.findById(id)));
    assertThat(countInitializedTools(profile)).isEqualTo(0);
    profile.writeScheme();
    List<InspectionToolWrapper<?, ?>> initializedTools = getInitializedTools(profile);
    assertEmpty(initializedTools.stream().map(InspectionToolWrapper::getShortName).collect(Collectors.joining(", ")), initializedTools);
  }

  public void testInspectionInitializationForSerialization() throws Exception {
    InspectionProfileImpl foo = new InspectionProfileImpl("foo");
    foo.readExternal(JDOMUtil.load("<profile version=\"1.0\">\n" +
                                           "    <option name=\"myName\" value=\"idea.default\" />\n" +
                                           "    <inspection_tool class=\"AbstractMethodCallInConstructor\" enabled=\"true\" level=\"WARNING\" enabled_by_default=\"true\" />\n" +
                                           "    <inspection_tool class=\"AssignmentToForLoopParameter\" enabled=\"true\" level=\"WARNING\" enabled_by_default=\"true\">\n" +
                                           "      <option name=\"m_checkForeachParameters\" value=\"false\" />\n" +
                                           "    </inspection_tool>\n" +
                                           "</profile>"));
    foo.initInspectionTools(getProject());
    assertEquals(1, countInitializedTools(foo));
  }

  public static int countInitializedTools(@NotNull InspectionProfileImpl foo) {
    return getInitializedTools(foo).size();
  }

  @NotNull
  public static List<InspectionToolWrapper<?, ?>> getInitializedTools(@NotNull InspectionProfileImpl foo) {
    List<InspectionToolWrapper<?, ?>> initialized = null;
    List<ScopeToolState> tools = foo.getAllTools();
    for (ScopeToolState tool : tools) {
      InspectionToolWrapper toolWrapper = tool.getTool();
      if (toolWrapper.isInitialized()) {
        if (initialized == null) {
          initialized = new SmartList<>();
        }
        initialized.add(toolWrapper);
      }
    }
    return initialized == null ? Collections.emptyList() : initialized;
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
