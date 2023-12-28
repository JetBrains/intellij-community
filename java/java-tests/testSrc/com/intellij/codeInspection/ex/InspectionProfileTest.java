// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ex;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.LocalInspectionEP;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.dataFlow.DataFlowInspection;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspectionBase;
import com.intellij.codeInspection.incorrectFormatting.IncorrectFormattingInspection;
import com.intellij.codeInspection.unusedSymbol.UnusedSymbolLocalInspectionBase;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.profile.ProfileChangeAdapter;
import com.intellij.profile.codeInspection.BaseInspectionProfileManager;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager;
import com.intellij.profile.codeInspection.ui.header.InspectionProfileSchemesPanel;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.testFramework.InspectionsKt;
import com.intellij.testFramework.LightIdeaTestCase;
import com.intellij.execution.junit.codeInspection.naming.TestClassNamingConvention;
import com.siyeh.ig.naming.ClassNamingConvention;
import com.siyeh.ig.naming.FieldNamingConventionInspection;
import com.siyeh.ig.naming.NewClassNamingConventionInspection;
import com.siyeh.ig.naming.NewMethodNamingConventionInspection;
import org.intellij.lang.annotations.Language;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
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

  private static @NotNull BaseInspectionProfileManager getApplicationProfileManager() {
    return (BaseInspectionProfileManager)InspectionProfileManager.getInstance();
  }

  public void testCopyProjectProfile() throws Exception {
    final Element element = loadProfile();
    final InspectionProfileImpl profile = createProfile();
    profile.readExternal(element);
    profile.getModifiableModel().commit();
    assertThat(profile.writeScheme()).isEqualTo(element);
  }

  private static @NotNull InspectionProfileImpl createEmptyProfile() throws IOException, JDOMException {
    InspectionProfileImpl profile = createProfile(new InspectionProfileImpl("foo"));
    final Element element = readFromXml(profile, "<profile version=\"1.0\">\n" +
                                                 "  <option name=\"myName\" value=\"" + PROFILE + "\" />\n" +
                                                 "</profile>");
    assertThat(profile.writeScheme()).isEqualTo(element);
    return profile;
  }

  private static @NotNull Element readFromXml(InspectionProfileImpl profile, @Language("XML") String serialized) throws IOException, JDOMException {
    final Element root = JDOMUtil.load(serialized);
    profile.readExternal(root);
    profile.getModifiableModel().commit();
    return root;
  }

  private @NotNull InspectionProfileImpl importProfile(Element toImportElement) {
    InspectionProfileImpl profile =
      InspectionProfileSchemesPanel.importInspectionProfile(toImportElement, getApplicationProfileManager(), getProject());
    return Objects.requireNonNull(profile);
  }

  private static InspectionProfileImpl createProfile() {
    return createProfile(InspectionProfileImpl.BASE_PROFILE.get());
  }

  private static InspectionProfileImpl createProfile(@NotNull InspectionProfileImpl base) {
    return new InspectionProfileImpl(PROFILE, InspectionToolRegistrar.getInstance(), base);
  }

  public void testModificationWithoutModification() {
    InspectionProfileImpl profile = createProfile();
    assertNotEmpty(profile.getAllTools());
    assertTrue(profile.wasInitialized());
    profile.modifyProfile(m -> {});
    assertTrue(profile.wasInitialized());
    assertNotEmpty(profile.getAllTools());
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

  public void testProfileChangeAdapter() {
    boolean[] called = new boolean[1];
    getProject().getMessageBus().connect(getTestRootDisposable()).subscribe(ProfileChangeAdapter.TOPIC, new ProfileChangeAdapter() {
      @Override
      public void profileActivated(@Nullable InspectionProfile oldProfile, @Nullable InspectionProfile profile) {
        called[0] = true;
      }
    });

    final InspectionProfileImpl profile1 = createProfile();
    final BaseInspectionProfileManager applicationProfileManager = getApplicationProfileManager();
    applicationProfileManager.addProfile(profile1);
    applicationProfileManager.setRootProfile(PROFILE);
    assertTrue(called[0]);

    ProjectInspectionProfileManager projectProfileManager = ProjectInspectionProfileManager.getInstance(getProject());
    try {
      called[0] = false;
      final InspectionProfileImpl profile2 = createProfile();
      projectProfileManager.addProfile(profile2);
      projectProfileManager.setRootProfile(PROFILE);
      assertTrue(called[0]);
    }
    finally {
      projectProfileManager.deleteProfile(PROFILE);
    }
  }

  public void testSetProfileWithSameName() {
    final InspectionProfileImpl applicationProfile = createProfile();
    final BaseInspectionProfileManager applicationProfileManager = getApplicationProfileManager();
    applicationProfileManager.addProfile(applicationProfile);

    ProjectInspectionProfileManager projectProfileManager = ProjectInspectionProfileManager.getInstance(getProject());
    try {
      applicationProfileManager.setRootProfile(PROFILE);
      projectProfileManager.useApplicationProfile(PROFILE); // see ProjectInspectionToolsConfigurable.applyRootProfile()
      assertEquals(applicationProfile, projectProfileManager.getCurrentProfile());

      final InspectionProfileImpl projectProfile = createProfile();
      projectProfileManager.addProfile(projectProfile);
      projectProfileManager.setRootProfile(PROFILE);

      assertSame(projectProfile, projectProfileManager.getCurrentProfile());
    }
    finally {
      projectProfileManager.deleteProfile(PROFILE);
    }
  }

  private static void updateProfile(BaseInspectionProfileManager profileManager, InspectionProfileImpl localProfile) {
    profileManager.addProfile(localProfile);
    profileManager.fireProfileChanged(localProfile);
  }

  public void testCustomTextAttributes() throws IOException, JDOMException {
    @Language("XML") String content = """
      <profile version="1.0">
        <option name="myName" value="default" />
        <inspection_tool class="Convert2Lambda" enabled="false" level="WARNING" enabled_by_default="false"/>
      </profile>""";
    var profile = createProfile();
    readFromXml(profile, content);
    var tool = profile.getInspectionTool("Convert2Lambda", getProject());
    assertNotNull(tool);
    var editorAttributes = profile.getEditorAttributes("Convert2Lambda", null);
    assertNotNull(editorAttributes);
    assertEquals("NOT_USED_ELEMENT_ATTRIBUTES", editorAttributes.getExternalName());
    assertThat(profile.writeScheme()).isEqualTo(JDOMUtil.load(content));
  }

  public void testConvertOldProfile() throws Exception {
    @Language("XML") String content = """
      <inspections version="1.0">
        <option name="myName" value="ToConvert" />
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
              <option name="REQUIRED_TAGS" value="@return@param@throws or @exception" />
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
          <option name="myAdditionalJavadocTags" value="tag1,tag2 " />
        </inspection_tool>
      </inspections>""";
    InspectionProfileImpl profile = createProfile();
    readFromXml(profile, content);

    assertThat(profile.writeScheme()).isEqualTo(loadProfile());
  }

  private static Element loadProfile() throws IOException, JDOMException {
    return JDOMUtil.load("""
                           <profile version="1.0">
                             <option name="myName" value="ToConvert" />
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
                                   <option name="REQUIRED_TAGS" value="@return@param@throws or @exception" />
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
                               <option name="myAdditionalJavadocTags" value="tag1,tag2 " />
                             </inspection_tool>
                           </profile>""");
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

  public void testDefaultScope(){

  }

  public void testMergeUnusedDeclarationAndUnusedSymbol() throws Exception {
    //no specific settings
    InspectionProfileImpl profile = createEmptyProfile();


    //settings to merge
    @Language("XML") String serialized =
      "<profile version=\"1.0\">\n" +
      "  <option name=\"myName\" value=\"" + PROFILE + "\" />\n" +
      "  <inspection_tool class=\"UNUSED_SYMBOL\" enabled=\"true\" level=\"WARNING\" enabled_by_default=\"true\">\n" +
      "    <option name=\"LOCAL_VARIABLE\" value=\"true\" />\n" +
      "    <option name=\"FIELD\" value=\"true\" />\n" +
      "    <option name=\"METHOD\" value=\"true\" />\n" +
      "    <option name=\"CLASS\" value=\"true\" />\n" +
      "    <option name=\"PARAMETER\" value=\"true\" />\n" +
      "    <option name=\"REPORT_PARAMETER_FOR_PUBLIC_METHODS\" value=\"false\" />\n" +
      "  </inspection_tool>\n" +
      "  <inspection_tool class=\"UnusedDeclaration\" enabled=\"true\" level=\"WARNING\" enabled_by_default=\"true\">\n" +
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
    InspectionToolRegistrar.getInstance().createTools();
    profile.modifyProfile(it -> {
      InspectionToolWrapper<?, ?> toolWrapper = it.getInspectionTool("unused", getProject());
      UnusedDeclarationInspectionBase tool = (UnusedDeclarationInspectionBase)toolWrapper.getTool();
      tool.ADD_NONJAVA_TO_ENTRIES = true;
      UnusedSymbolLocalInspectionBase inspectionTool = tool.getSharedLocalInspectionTool();
      inspectionTool.setParameterVisibility(PsiModifier.PUBLIC);
    });
    @Language("XML") String mergedText = """
      <profile version="1.0">
        <option name="myName" value="ToConvert" />
        <inspection_tool class="UNUSED_SYMBOL" enabled="true" level="WARNING" enabled_by_default="true">
          <option name="LOCAL_VARIABLE" value="true" />
          <option name="FIELD" value="true" />
          <option name="METHOD" value="true" />
          <option name="CLASS" value="true" />
          <option name="PARAMETER" value="true" />
          <option name="REPORT_PARAMETER_FOR_PUBLIC_METHODS" value="false" />
        </inspection_tool>
        <inspection_tool class="UnusedDeclaration" enabled="true" level="WARNING" enabled_by_default="true">
          <option name="ADD_MAINS_TO_ENTRIES" value="true" />
          <option name="ADD_APPLET_TO_ENTRIES" value="true" />
          <option name="ADD_SERVLET_TO_ENTRIES" value="true" />
          <option name="ADD_NONJAVA_TO_ENTRIES" value="false" />
        </inspection_tool>
        <inspection_tool class="unusedMerged" />
      </profile>""";
    assertEquals(mergedText, serialize(profile));

    Element toImportElement = profile.writeScheme();
    final InspectionProfileImpl importedProfile = importProfile(toImportElement);

    //check merged
    profile = createProfile(new InspectionProfileImpl("foo"));
    Element mergedElement = readFromXml(profile, mergedText);
    assertThat(profile.writeScheme()).isEqualTo(mergedElement);

    assertThat(importedProfile.writeScheme()).isEqualTo(mergedElement);
  }

  public void testDefaultScopeDisabled() throws Exception {
    @Language("XML") String content = """
      <inspections version="1.0">
        <option name="myName" value="ToConvert" />
        <inspection_tool class="JavaDoc" enabled="true" level="WARNING" enabled_by_default="false">
        <scope name="Open Files" level="WARNING" enabled="true"/>
        </inspection_tool>
      </inspections>""";
    InspectionProfileImpl profile = createProfile();
    readFromXml(profile, content);
    ToolsImpl tools = profile.getTools("MissingJavadoc", getProject());
    assertFalse(tools.getDefaultState().isEnabled());
  }

  public void testDefaultScopeEnabled() throws Exception {
    @Language("XML") String content = """
      <inspections version="1.0">
        <option name="myName" value="ToConvert" />
        <inspection_tool class="JavaDoc" enabled="true" level="WARNING" enabled_by_default="true">
        <scope name="Open Files" level="WARNING" enabled="false"/>
        </inspection_tool>
      </inspections>""";
    InspectionProfileImpl profile = createProfile();
    readFromXml(profile, content);
    ToolsImpl tools = profile.getTools("MissingJavadoc", getProject());
    assertTrue(tools.getDefaultState().isEnabled());
  }

  public void testScopesInNamingConventions() throws Exception {
    @Language("XML") String unchanged = """
      <profile version="1.0">
        <option name="myName" value="Name convention" />
        <inspection_tool class="AnnotationNamingConvention" enabled="true" level="WARNING" enabled_by_default="true">
          <option name="m_regex" value="[A-Z][A-Za-z\\d]*Ann" />
          <option name="m_minLength" value="8" />
          <option name="m_maxLength" value="58" />
        </inspection_tool>
        <inspection_tool class="ClassNamingConvention" enabled="true" level="WARNING" enabled_by_default="true">
          <scope name="Production" level="ERROR" enabled="true">
            <option name="m_regex" value="[A-Z][A-Za-z\\d]*" />
            <option name="m_minLength" value="8" />
            <option name="m_maxLength" value="64" />
          </scope>
          <option name="m_regex" value="[A-Z][A-Za-z\\d]*Class" />
          <option name="m_minLength" value="10" />
          <option name="m_maxLength" value="60" />
        </inspection_tool>
        <inspection_tool class="EnumeratedClassNamingConvention" enabled="true" level="WARNING" enabled_by_default="true">
          <scope name="Tests" level="WEAK WARNING" enabled="true">
            <option name="m_regex" value="[A-Z][A-Za-z\\d]*" />
            <option name="m_minLength" value="8" />
            <option name="m_maxLength" value="64" />
          </scope>
          <option name="m_regex" value="[A-Z][A-Za-z\\d]*Enum" />
          <option name="m_minLength" value="12" />
          <option name="m_maxLength" value="62" />
        </inspection_tool>
        <inspection_tool class="InterfaceNamingConvention" enabled="true" level="WARNING" enabled_by_default="true">
          <option name="m_regex" value="[A-Z][A-Za-z\\d]*I" />
          <option name="m_minLength" value="14" />
          <option name="m_maxLength" value="64" />
        </inspection_tool>
        <inspection_tool class="JUnitAbstractTestClassNamingConvention" enabled="true" level="WARNING" enabled_by_default="true">
          <option name="m_regex" value="[A-Z][A-Za-z\\d]*TestCase" />
          <option name="m_minLength" value="2" />
          <option name="m_maxLength" value="52" />
        </inspection_tool>
        <inspection_tool class="JUnitTestClassNamingConvention" enabled="true" level="ERROR" enabled_by_default="true">
          <option name="m_regex" value="[A-Z][A-Za-z\\d]*Test" />
          <option name="m_minLength" value="4" />
          <option name="m_maxLength" value="54" />
        </inspection_tool>
        <inspection_tool class="TypeParameterNamingConvention" enabled="true" level="WARNING" enabled_by_default="true">
          <option name="m_regex" value="[A-Z][A-Za-z\\d]*TP" />
          <option name="m_minLength" value="16" />
          <option name="m_maxLength" value="66" />
        </inspection_tool>
      </profile>""";
    InspectionProfileImpl profile = createProfile(new InspectionProfileImpl("foo"));
    readFromXml(profile, unchanged);
    assertEquals(unchanged, serialize(profile));
    ToolsImpl tools = profile.getToolsOrNull("NewClassNamingConvention", getProject());
    assertTrue(tools.isEnabled());
    for (ScopeToolState toolState : tools.getTools()) {
      NamedScope scope = toolState.getScope(getProject());
      assertNotNull(scope);
      String scopeName = scope.getScopeId();
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
    @Language("XML") String customSettingsText = """
      <profile version="1.0">
        <option name="myName" value="ToConvert" />
        <inspection_tool class="AbstractClassNamingConvention" enabled="true" level="WARNING" enabled_by_default="true" />
        <inspection_tool class="AnnotationNamingConvention" enabled="true" level="WARNING" enabled_by_default="true">
          <option name="m_regex" value="[A-Z][A-Za-z\\d]*" />
          <option name="m_minLength" value="8" />
          <option name="m_maxLength" value="256" />
        </inspection_tool>
        <inspection_tool class="EnumeratedClassNamingConvention" enabled="true" level="WARNING" enabled_by_default="true">
          <option name="m_regex" value="[A-Z][A-Za-z\\d]*" />
          <option name="m_minLength" value="1" />
          <option name="m_maxLength" value="64" />
        </inspection_tool>
        <inspection_tool class="InterfaceNamingConvention" enabled="true" level="WARNING" enabled_by_default="true">
          <option name="m_regex" value="[A-Z][A-Za-z\\d]*" />
          <option name="m_minLength" value="8" />
          <option name="m_maxLength" value="64" />
        </inspection_tool>
        <inspection_tool class="TypeParameterNamingConvention" enabled="false" level="WARNING" enabled_by_default="true">
          <option name="m_regex" value="[A-Z][A-Za-z\\d]*" />
          <option name="m_minLength" value="1" />
          <option name="m_maxLength" value="1" />
        </inspection_tool>
      </profile>""";
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
      String unchanged = """
        <profile version="1.0">
          <option name="myName" value="ToConvert" />
          <inspection_tool class="NewClassNamingConvention" enabled="true" level="WARNING" enabled_by_default="true">
            <extension name="AnnotationNamingConvention" enabled="true">
              <option name="inheritDefaultSettings" value="false" />
              <option name="m_regex" value="[A-Z][A-Za-z\\d]*" />
              <option name="m_minLength" value="8" />
              <option name="m_maxLength" value="66" />
            </extension>
            <extension name="AnnotationNamingConventionUnknown" enabled="true">
              <option name="m_regex" value="[A-Z][A-Za-z\\d]*" />
              <option name="m_minLength" value="8" />
              <option name="m_maxLength" value="66" />
            </extension>
            <extension name="ClassNamingConvention" enabled="true">
              <option name="m_regex" value="[A-Z][A-Za-z\\d]*" />
              <option name="m_minLength" value="8" />
              <option name="m_maxLength" value="66" />
            </extension>
          </inspection_tool>
        </profile>""";
      final Element allEnabledProfile = JDOMUtil.load(unchanged);
      InspectionProfileImpl profile = createProfile(new InspectionProfileImpl("foo"));
      profile.readExternal(allEnabledProfile);
      profile.initInspectionTools();
      assertEquals(unchanged, serialize(profile));
    }

  public void testMergeMethodNamingConventions() throws Exception {
    var profile = createEmptyProfile();

    @Language("XML") String customSettingsText = """
      <profile version="1.0">
        <option name="myName" value="empty" />
        <inspection_tool class="InstanceMethodNamingConvention" enabled="true" level="WARNING" enabled_by_default="true">
          <option name="m_regex" value="i_[a-z][A-Za-z\\d]*" />
          <option name="m_minLength" value="4" />
          <option name="m_maxLength" value="32" />
        </inspection_tool>
        <inspection_tool class="NativeMethodNamingConvention" enabled="true" level="WARNING" enabled_by_default="true">
          <option name="m_regex" value="n_[a-z][A-Za-z\\d]*" />
        </inspection_tool>
        <inspection_tool class="StaticMethodNamingConvention" enabled="true" level="WARNING" enabled_by_default="true">
          <option name="m_regex" value="s_[a-z][A-Za-z\\d]*" />
          <option name="m_minLength" value="4" />
          <option name="m_maxLength" value="32" />
        </inspection_tool>
      </profile>""";

    readFromXml(profile, customSettingsText);
    assertEquals(customSettingsText, serialize(profile));

    var wrapper = profile.getInspectionTool("NewMethodNamingConvention", getProject());
    assertNotNull(wrapper);
    var tool = (NewMethodNamingConventionInspection)wrapper.getTool();
    assertEquals("i_[a-z][A-Za-z\\d]*", tool.getNamingConventionBean("InstanceMethodNamingConvention").m_regex);
    assertEquals("n_[a-z][A-Za-z\\d]*", tool.getNamingConventionBean("NativeMethodNamingConvention").m_regex);
    assertTrue(profile.isToolEnabled(HighlightDisplayKey.find("NewMethodNamingConvention"), null));
    assertFalse(tool.isConventionEnabled("JUnit4MethodNamingConvention"));
    assertFalse(tool.isConventionEnabled("JUnit3MethodNamingConvention"));

    var toImportElement = profile.writeScheme();
    final InspectionProfileImpl importedProfile = importProfile(toImportElement);

    //check merged
    profile = createProfile(new InspectionProfileImpl("foo"));
    var mergedElement = readFromXml(profile, customSettingsText);
    assertThat(profile.writeScheme()).isEqualTo(mergedElement);

    assertThat(importedProfile.writeScheme()).isEqualTo(mergedElement);
  }

  public void testMergeFieldNamingConventions() throws Exception {
    var profile = createEmptyProfile();

    @Language("XML") String customSettingsText = """
      <profile version="1.0">
        <option name="myName" value="empty" />
        <inspection_tool class="ConstantNamingConvention" enabled="true" level="WARNING" enabled_by_default="true">
          <option name="m_regex" value="i_[a-z][A-Za-z\\d]*" />
          <option name="m_minLength" value="4" />
          <option name="m_maxLength" value="32" />
          <option name="onlyCheckImmutables" value="true" />
        </inspection_tool>
        <inspection_tool class="StaticVariableNamingConvention" enabled="true" level="WARNING" enabled_by_default="true">
          <option name="m_regex" value="i_[a-z][A-Za-z\\d]*" />
          <option name="m_minLength" value="4" />
          <option name="m_maxLength" value="32" />
          <option name="checkMutableFinals" value="true" />
        </inspection_tool>
      </profile>""";

    readFromXml(profile, customSettingsText);
    assertEquals(customSettingsText, serialize(profile));

    var wrapper = profile.getInspectionTool("FieldNamingConvention", getProject());
    assertNotNull(wrapper);
    var tool = (FieldNamingConventionInspection)wrapper.getTool();
    assertTrue(profile.isToolEnabled(HighlightDisplayKey.find("FieldNamingConvention"), null));
    assertTrue(tool.isConventionEnabled("StaticVariableNamingConvention"));
    assertTrue(tool.isConventionEnabled("ConstantNamingConvention"));
    assertTrue(tool.isConventionEnabled("ConstantWithMutableFieldTypeNamingConvention"));

    var toImportElement = profile.writeScheme();
    final InspectionProfileImpl importedProfile = importProfile(toImportElement);

    //check merged
    profile = createProfile(new InspectionProfileImpl("foo"));
    var mergedElement = readFromXml(profile, customSettingsText);
    assertThat(profile.writeScheme()).isEqualTo(mergedElement);

    assertThat(importedProfile.writeScheme()).isEqualTo(mergedElement);
  }

  public void testDisabledNamingConvention() throws Exception {
    InspectionProfileImpl profile = createEmptyProfile();
    @Language("XML") String customSettingsText = """
      <profile version="1.0">
        <option name="myName" value="Project Default" />
        <inspection_tool class="NewClassNamingConvention" enabled="true" level="WARNING" enabled_by_default="true">
          <extension name="JUnitTestClassNamingConvention" enabled="false" />
        </inspection_tool>
      </profile>""";

    readFromXml(profile, customSettingsText);
    assertEquals(customSettingsText, serialize(profile));

    InspectionToolWrapper<?, ?> wrapper = profile.getInspectionTool("NewClassNamingConvention", getProject());
    assertNotNull(wrapper);
    NewClassNamingConventionInspection tool = (NewClassNamingConventionInspection)wrapper.getTool();
    assertTrue(new TestClassNamingConvention().isEnabledByDefault());
    assertFalse(tool.isConventionEnabled("JUnitTestClassNamingConvention"));
  }

  public void testStoredMemberVisibility() throws Exception {
    InspectionProfileImpl profile = createProfile(new InspectionProfileImpl("foo"));
    profile.readExternal(JDOMUtil.load("""
                                         <profile version="1.0">
                                           <inspection_tool class="unused" enabled="true" level="WARNING" enabled_by_default="true" checkParameterExcludingHierarchy="false">
                                             <option name="LOCAL_VARIABLE" value="true" />
                                             <option name="FIELD" value="true" />
                                             <option name="METHOD" value="true" />
                                             <option name="CLASS" value="true" />
                                             <option name="PARAMETER" value="true" />
                                             <option name="REPORT_PARAMETER_FOR_PUBLIC_METHODS" value="true" />
                                             <option name="ADD_MAINS_TO_ENTRIES" value="true" />
                                             <option name="ADD_APPLET_TO_ENTRIES" value="true" />
                                             <option name="ADD_SERVLET_TO_ENTRIES" value="true" />
                                             <option name="ADD_NONJAVA_TO_ENTRIES" value="false" />
                                           </inspection_tool>
                                         </profile>"""));
    profile.modifyProfile(it -> {
      InspectionToolWrapper<?, ?> toolWrapper = it.getInspectionTool("unused", getProject());
      UnusedDeclarationInspectionBase tool = (UnusedDeclarationInspectionBase)toolWrapper.getTool();
      UnusedSymbolLocalInspectionBase inspectionTool = tool.getSharedLocalInspectionTool();
      inspectionTool.setClassVisibility(PsiModifier.PUBLIC);
      inspectionTool.CLASS = false;
    });
    String mergedText = """
      <profile version="1.0">
        <option name="myName" value="ToConvert" />
        <inspection_tool class="unused" enabled="true" level="WARNING" enabled_by_default="true" checkParameterExcludingHierarchy="false">
          <option name="LOCAL_VARIABLE" value="true" />
          <option name="FIELD" value="true" />
          <option name="METHOD" value="true" />
          <option name="CLASS" value="false" />
          <option name="PARAMETER" value="true" />
          <option name="REPORT_PARAMETER_FOR_PUBLIC_METHODS" value="true" />
          <option name="ADD_MAINS_TO_ENTRIES" value="true" />
          <option name="ADD_APPLET_TO_ENTRIES" value="true" />
          <option name="ADD_SERVLET_TO_ENTRIES" value="true" />
          <option name="ADD_NONJAVA_TO_ENTRIES" value="false" />
        </inspection_tool>
      </profile>""";
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

  public void testJUnitMalformedMemberInspections() throws Exception {
    checkMergedNoChanges("<profile version=\"1.0\">\n" +
                         "  <option name=\"myName\" value=\"" + PROFILE + "\" />\n" +
                         "  <inspection_tool class=\"BeforeClassOrAfterClassIsPublicStaticVoidNoArg\" enabled=\"true\" level=\"WARNING\" enabled_by_default=\"true\" />\n" +
                         "  <inspection_tool class=\"BeforeOrAfterIsPublicVoidNoArg\" enabled=\"true\" level=\"WARNING\" enabled_by_default=\"true\" />\n" +
                         "  <inspection_tool class=\"JUnit5MalformedExtensions\" enabled=\"true\" level=\"WARNING\" enabled_by_default=\"true\" />\n" +
                         "  <inspection_tool class=\"JUnit5MalformedNestedClass\" enabled=\"true\" level=\"WARNING\" enabled_by_default=\"true\" />\n" +
                         "  <inspection_tool class=\"JUnit5MalformedRepeated\" enabled=\"true\" level=\"WARNING\" enabled_by_default=\"true\" />\n" +
                         "  <inspection_tool class=\"JUnitDatapoint\" enabled=\"true\" level=\"WARNING\" enabled_by_default=\"false\" />\n" +
                         "  <inspection_tool class=\"JUnitRule\" enabled=\"true\" level=\"ERROR\" enabled_by_default=\"false\" />\n" +
                         "  <inspection_tool class=\"Junit5MalformedParameterized\" enabled=\"true\" level=\"WARNING\" enabled_by_default=\"true\" />\n" +
                         "  <inspection_tool class=\"MalformedSetUpTearDown\" enabled=\"true\" level=\"WARNING\" enabled_by_default=\"false\" />\n" +
                         "  <inspection_tool class=\"SetupIsPublicVoidNoArg\" enabled=\"true\" level=\"WARNING\" enabled_by_default=\"false\" />\n" +
                         "  <inspection_tool class=\"TeardownIsPublicVoidNoArg\" enabled=\"true\" level=\"WARNING\" enabled_by_default=\"false\" />\n" +
                         "  <inspection_tool class=\"TestMethodIsPublicVoidNoArg\" enabled=\"true\" level=\"WARNING\" enabled_by_default=\"true\" />\n" +
                         "</profile>");
    checkMergedNoChanges("<profile version=\"1.0\">\n" +
                         "  <option name=\"myName\" value=\"" + PROFILE + "\" />\n" +
                         "  <inspection_tool class=\"JUnitMalformedDeclaration\" enabled=\"true\" level=\"ERROR\" enabled_by_default=\"false\" />\n" +
                         "</profile>");
  }

  public void testTestInProductSourceInspections() throws Exception {
    checkMergedNoChanges("<profile version=\"1.0\">\n" +
                         "  <option name=\"myName\" value=\"" + PROFILE + "\" />\n" +
                         "  <inspection_tool class=\"TestCaseInProductCode\" enabled=\"false\" level=\"WARNING\" enabled_by_default=\"false\" />\n" +
                         "  <inspection_tool class=\"TestMethodInProductCode\" enabled=\"false\" level=\"WARNING\" enabled_by_default=\"false\" />\n" +
                         "</profile>");
    checkMergedNoChanges("<profile version=\"1.0\">\n" +
                         "  <option name=\"myName\" value=\"" + PROFILE + "\" />\n" +
                         "  <inspection_tool class=\"TestInProductSource\" enabled=\"false\" level=\"WARNING\" enabled_by_default=\"false\" />\n" +
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

  public void testMergedReformatInspection() throws Exception {
    var profile = checkMergedNoChanges("<profile version=\"1.0\">\n" +
                                       "  <option name=\"myName\" value=\"" + PROFILE + "\" />\n" +
                                       "  <inspection_tool class=\"Reformat\" enabled=\"true\" level=\"WARNING\" enabled_by_default=\"true\" />\n" +
                                       "</profile>");
    var toolWrapper = profile.getInspectionTool("IncorrectFormatting", getProject()); //call to initialize inspections
    assertTrue(profile.isToolEnabled(HighlightDisplayKey.find("IncorrectFormatting"), null));
    var tool = (IncorrectFormattingInspection)toolWrapper.getTool();
    assertTrue("Should be enabled for kotlin only", tool.kotlinOnly);
  }
  
  public void testMergedReformatInspectionDisabled() throws Exception {
    InspectionProfileImpl profile = checkMergedNoChanges("<profile version=\"1.0\">\n" +
                                                         "  <option name=\"myName\" value=\"" + PROFILE + "\" />\n" +
                                                         "</profile>");
    assertFalse("Should be disabled by default", profile.isToolEnabled(HighlightDisplayKey.find("IncorrectFormatting"), null));
  }

  public void testSecondMergedRedundantStringOperationsInspections() throws Exception {
    InspectionProfileImpl bothDisabled = checkMergedNoChanges("""
                                                                <profile version="1.0">
                                                                  <option name="myName" value="ToConvert" />
                                                                  <inspection_tool class="RedundantStringOperation" enabled="false" level="WARNING" enabled_by_default="false" />
                                                                  <inspection_tool class="StringConstructor" enabled="false" level="WARNING" enabled_by_default="false">
                                                                    <option name="ignoreSubstringArguments" value="false" />
                                                                  </inspection_tool>
                                                                </profile>""");
    assertFalse(bothDisabled.isToolEnabled(HighlightDisplayKey.find("StringOperationCanBeSimplified"), null));

    InspectionProfileImpl oneEnabled = checkMergedNoChanges("""
                                                              <profile version="1.0">
                                                                <option name="myName" value="ToConvert" />
                                                                <inspection_tool class="RedundantStringOperation" enabled="false" level="WARNING" enabled_by_default="false" />
                                                                <inspection_tool class="StringConstructor" enabled="true" level="WARNING" enabled_by_default="true">
                                                                  <option name="ignoreSubstringArguments" value="false" />
                                                                </inspection_tool>
                                                              </profile>""");
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

    assertEquals("""
                   <profile version="1.0" is_locked="true">
                     <option name="myName" value="Foo" />
                     <inspection_tool class="foo" enabled="true" level="ERROR" enabled_by_default="true" />
                   </profile>""",
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

    assertEquals("""
                   <profile version="1.0" is_locked="true">
                     <option name="myName" value="Foo" />
                     <inspection_tool class="bar" enabled="false" level="ERROR" enabled_by_default="false" />
                     <inspection_tool class="disabled" enabled="false" level="ERROR" enabled_by_default="false" />
                     <inspection_tool class="foo" enabled="true" level="ERROR" enabled_by_default="true" />
                   </profile>""", serialize(profile));
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
    InspectionProfileImpl profile = new InspectionProfileImpl("profile", InspectionToolRegistrar.getInstance(), (InspectionProfileImpl)null);
    assertEquals(0, countInitializedTools(profile));
    List<InspectionToolWrapper<?, ?>> toolWrappers = profile.getInspectionTools(null);
    assertFalse(toolWrappers.isEmpty());
    InspectionToolWrapper<?, ?> toolWrapper = profile.getInspectionTool(new DataFlowInspection().getShortName(), getProject());
    assertNotNull(toolWrapper);
    String id = toolWrapper.getShortName();
    profile.setToolEnabled(id, !profile.isToolEnabled(HighlightDisplayKey.findById(id)));
    assertThat(countInitializedTools(profile)).isEqualTo(0);
    profile.writeScheme();
    var initializedTools = getInitializedTools(profile);
    assertEmpty(initializedTools.stream().map(InspectionToolWrapper::getShortName).collect(Collectors.joining(", ")), initializedTools);
  }

  public void testInspectionInitializationForSerialization() throws Exception {
    InspectionProfileImpl foo = new InspectionProfileImpl("foo");
    foo.readExternal(JDOMUtil.load("""
                                     <profile version="1.0">
                                         <option name="myName" value="idea.default" />
                                         <inspection_tool class="AbstractMethodCallInConstructor" enabled="true" level="WARNING" enabled_by_default="true" />
                                         <inspection_tool class="AssignmentToForLoopParameter" enabled="true" level="WARNING" enabled_by_default="true">
                                           <option name="m_checkForeachParameters" value="false" />
                                         </inspection_tool>
                                     </profile>"""));
    foo.initInspectionTools(getProject());
    assertEquals(1, countInitializedTools(foo));
  }

  public static int countInitializedTools(@NotNull InspectionProfileImpl foo) {
    return getInitializedTools(foo).size();
  }

  public static @NotNull List<? extends InspectionToolWrapper<?, ?>> getInitializedTools(@NotNull InspectionProfileImpl foo) {
    return foo.getAllTools().stream().map(ScopeToolState::getTool).filter(InspectionToolWrapper::isInitialized).toList();
  }

  private static LocalInspectionToolWrapper createTool(String name, boolean enabled) {
    LocalInspectionEP ep = new LocalInspectionEP();
    ep.shortName = ep.displayName = ep.groupDisplayName = name;
    ep.level = "ERROR";
    ep.enabledByDefault = enabled;
    ep.implementationClass = TestTool.class.getName();
    return new LocalInspectionToolWrapper(ep);
  }

  public static final class TestTool extends LocalInspectionTool { }
}
