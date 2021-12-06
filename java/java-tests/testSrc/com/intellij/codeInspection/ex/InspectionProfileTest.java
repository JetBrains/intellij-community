// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.ex;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.LocalInspectionEP;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.dataFlow.DataFlowInspection;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspectionBase;
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
import com.intellij.util.SmartList;
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
import java.util.Collections;
import java.util.List;
import java.util.Objects;

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
    assertThat(profile.wasInitialized()).isTrue();
    assertThat(profile.myTools).isNotEmpty();
    profile.modifyProfile(m -> {});
    assertThat(profile.wasInitialized()).isTrue();
    assertThat(profile.myTools).isNotEmpty();
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
    assertThat(called[0]).isTrue();

    ProjectInspectionProfileManager projectProfileManager = ProjectInspectionProfileManager.getInstance(getProject());
    try {
      called[0] = false;
      final InspectionProfileImpl profile2 = createProfile();
      projectProfileManager.addProfile(profile2);
      projectProfileManager.setRootProfile(PROFILE);
      assertThat(called[0]).isTrue();
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
      assertThat(projectProfileManager.getCurrentProfile()).isEqualTo(applicationProfile);

      final InspectionProfileImpl projectProfile = createProfile();
      projectProfileManager.addProfile(projectProfile);
      projectProfileManager.setRootProfile(PROFILE);

      assertThat(projectProfileManager.getCurrentProfile()).isSameAs(projectProfile);
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
    assertThat(serialize(profile)).isEqualTo(serialized);

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
    assertThat(serialize(profile)).isEqualTo(mergedText);

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
    assertThat(serialize(profile)).isEqualTo(unchanged);
    ToolsImpl tools = profile.getToolsOrNull("NewClassNamingConvention", getProject());
    assertThat(tools.isEnabled()).isTrue();
    for (ScopeToolState toolState : tools.getTools()) {
      NamedScope scope = toolState.getScope(getProject());
      assertThat(scope).isNotNull();
      String scopeName = scope.getScopeId();
      NewClassNamingConventionInspection tool = (NewClassNamingConventionInspection)toolState.getTool().getTool();
      if ("Production".equals(scopeName)) {
        assertThat(tool.isConventionEnabled(ClassNamingConvention.CLASS_NAMING_CONVENTION_SHORT_NAME)).isTrue();
        assertThat(tool.isConventionEnabled("TypeParameterNamingConvention")).isTrue();
        assertThat(tool.getNamingConventionBean("TypeParameterNamingConvention").m_minLength).isEqualTo(16);
      }
      else if ("Tests".equals(scopeName)) {
        assertThat(tool.isConventionEnabled("TypeParameterNamingConvention")).isTrue();
        assertThat(tool.getNamingConventionBean("TypeParameterNamingConvention").m_minLength).isEqualTo(16);
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
    assertThat(serialize(profile)).isEqualTo(unchanged);

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
    assertThat(serialize(profile)).isEqualTo(customSettingsText);
    InspectionToolWrapper<?, ?> wrapper = profile.getInspectionTool("NewClassNamingConvention", getProject());
    assertThat(wrapper).isNotNull();
    NewClassNamingConventionInspection tool = (NewClassNamingConventionInspection)wrapper.getTool();
    assertThat(tool.getNamingConventionBean("AnnotationNamingConvention").m_maxLength).isEqualTo(256);
    assertThat(tool.getNamingConventionBean("EnumeratedClassNamingConvention").m_minLength).isEqualTo(1);
    assertThat(profile.isToolEnabled(HighlightDisplayKey.find("NewClassNamingConvention"), null)).isTrue();
    assertThat(tool.isConventionEnabled("TypeParameterNamingConvention")).isFalse();
    assertThat(tool.isConventionEnabled(ClassNamingConvention.CLASS_NAMING_CONVENTION_SHORT_NAME)).isFalse();

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
      assertThat(serialize(profile)).isEqualTo(unchanged);
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
    assertThat(serialize(profile)).isEqualTo(customSettingsText);

    InspectionToolWrapper wrapper = profile.getInspectionTool("NewMethodNamingConvention", getProject());
    assertThat(wrapper).isNotNull();
    NewMethodNamingConventionInspection tool = (NewMethodNamingConventionInspection)wrapper.getTool();
    assertThat(tool.getNamingConventionBean("InstanceMethodNamingConvention").m_regex).isEqualTo("i_[a-z][A-Za-z\\d]*");
    assertThat(tool.getNamingConventionBean("NativeMethodNamingConvention").m_regex).isEqualTo("n_[a-z][A-Za-z\\d]*");
    assertThat(profile.isToolEnabled(HighlightDisplayKey.find("NewMethodNamingConvention"), null)).isTrue();
    assertThat(tool.isConventionEnabled("JUnit4MethodNamingConvention")).isFalse();
    assertThat(tool.isConventionEnabled("JUnit3MethodNamingConvention")).isFalse();

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
    assertThat(serialize(profile)).isEqualTo(customSettingsText);

    InspectionToolWrapper wrapper = profile.getInspectionTool("FieldNamingConvention", getProject());
    assertThat(wrapper).isNotNull();
    FieldNamingConventionInspection tool = (FieldNamingConventionInspection)wrapper.getTool();
    assertThat(profile.isToolEnabled(HighlightDisplayKey.find("FieldNamingConvention"), null)).isTrue();
    assertThat(tool.isConventionEnabled("StaticVariableNamingConvention")).isTrue();
    assertThat(tool.isConventionEnabled("ConstantNamingConvention")).isTrue();
    assertThat(tool.isConventionEnabled("ConstantWithMutableFieldTypeNamingConvention")).isTrue();

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
                                               "  <inspection_tool class=\"unused\" enabled=\"true\" level=\"WARNING\" enabled_by_default=\"true\" checkParameterExcludingHierarchy=\"false\">\n" +
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
                            "  <inspection_tool class=\"unused\" enabled=\"true\" level=\"WARNING\" enabled_by_default=\"true\" checkParameterExcludingHierarchy=\"false\">\n" +
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
    assertThat(serialize(profile)).isEqualTo(mergedText);
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
    assertThat(profile.isToolEnabled(HighlightDisplayKey.find("StringOperationCanBeSimplified"), null)).isFalse();
  }

  public void testSecondMergedRedundantStringOperationsInspections() throws Exception {
    InspectionProfileImpl bothDisabled = checkMergedNoChanges("<profile version=\"1.0\">\n" +
                                                         "  <option name=\"myName\" value=\"ToConvert\" />\n" +
                                                         "  <inspection_tool class=\"RedundantStringOperation\" enabled=\"false\" level=\"WARNING\" enabled_by_default=\"false\" />\n" +
                                                         "  <inspection_tool class=\"StringConstructor\" enabled=\"false\" level=\"WARNING\" enabled_by_default=\"false\">\n" +
                                                         "    <option name=\"ignoreSubstringArguments\" value=\"false\" />\n" +
                                                         "  </inspection_tool>\n" +
                                                         "</profile>");
    assertThat(bothDisabled.isToolEnabled(HighlightDisplayKey.find("StringOperationCanBeSimplified"), null)).isFalse();

    InspectionProfileImpl oneEnabled = checkMergedNoChanges("<profile version=\"1.0\">\n" +
                                                         "  <option name=\"myName\" value=\"ToConvert\" />\n" +
                                                         "  <inspection_tool class=\"RedundantStringOperation\" enabled=\"false\" level=\"WARNING\" enabled_by_default=\"false\" />\n" +
                                                         "  <inspection_tool class=\"StringConstructor\" enabled=\"true\" level=\"WARNING\" enabled_by_default=\"false\">\n" +
                                                         "    <option name=\"ignoreSubstringArguments\" value=\"false\" />\n" +
                                                         "  </inspection_tool>\n" +
                                                         "</profile>");
    assertThat(oneEnabled.isToolEnabled(HighlightDisplayKey.find("StringOperationCanBeSimplified"), null)).isTrue();
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
    assertThat(serialize(profile)).isEqualTo(initialText);
    return profile;
  }

  public void testLockProfile() {
    List<InspectionToolWrapper<?, ?>> list = new ArrayList<>();
    list.add(createTool("foo", true));

    InspectionToolsSupplier.Simple toolSupplier = new InspectionToolsSupplier.Simple(list);
    Disposer.register(getTestRootDisposable(), toolSupplier);
    InspectionProfileImpl profile = createProfile(toolSupplier);

    List<ScopeToolState> tools = profile.getAllTools();
    assertThat(tools).hasSize(1);
    assertThat(profile.isToolEnabled(HighlightDisplayKey.find("foo"))).isTrue();
    assertThat(profile.getToolDefaultState("foo", getProject()).isEnabled()).isTrue();

    profile.modifyProfile(it -> {
      it.lockProfile(true);
      it.initInspectionTools(getProject()); // todo commit should take care of initialization
    });

    assertThat(serialize(profile)).isEqualTo(
      "<profile version=\"1.0\" is_locked=\"true\">\n" +
      "  <option name=\"myName\" value=\"Foo\" />\n" +
      "  <inspection_tool class=\"foo\" enabled=\"true\" level=\"ERROR\" enabled_by_default=\"true\" />\n" +
      "</profile>");

    Element element = profile.writeScheme();

    list.add(createTool("bar", true));
    list.add(createTool("disabled", false));

    profile = createProfile(toolSupplier);
    profile.readExternal(element);

    tools = profile.getAllTools();
    assertThat(tools).hasSize(3);

    assertThat(profile.isProfileLocked()).isTrue();
    assertThat(profile.isToolEnabled(HighlightDisplayKey.find("bar"))).isFalse();
    assertThat(profile.isToolEnabled(HighlightDisplayKey.find("disabled"))).isFalse();

    assertThat(profile.getToolDefaultState("bar", getProject()).isEnabled()).isFalse();
    assertThat(profile.getToolDefaultState("disabled", getProject()).isEnabled()).isFalse();

    assertThat(serialize(profile)).isEqualTo(
      "<profile version=\"1.0\" is_locked=\"true\">\n" +
      "  <option name=\"myName\" value=\"Foo\" />\n" +
      "  <inspection_tool class=\"bar\" enabled=\"false\" level=\"ERROR\" enabled_by_default=\"false\" />\n" +
      "  <inspection_tool class=\"disabled\" enabled=\"false\" level=\"ERROR\" enabled_by_default=\"false\" />\n" +
      "  <inspection_tool class=\"foo\" enabled=\"true\" level=\"ERROR\" enabled_by_default=\"true\" />\n" +
      "</profile>");
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
    assertThat(getInitializedTools(foo)).isEmpty();
    foo.initInspectionTools(getProject());
    assertThat(getInitializedTools(foo)).isEmpty();

    InspectionProfileModifiableModel model = foo.getModifiableModel();
    assertThat(getInitializedTools(model)).isEmpty();
    model.commit();
    assertThat(getInitializedTools(model)).isEmpty();
    assertThat(getInitializedTools(foo)).isEmpty();

    model = foo.getModifiableModel();
    assertThat(getInitializedTools(model)).isEmpty();
    List<ScopeToolState> tools = model.getAllTools();
    for (ScopeToolState tool : tools) {
      if (!tool.isEnabled()) {
        tool.setEnabled(true);
      }
    }
    model.commit();
    assertThat(getInitializedTools(model)).isEmpty();
  }

  public void testDoNotInstantiateOnSave() {
    InspectionProfileImpl profile = new InspectionProfileImpl("profile", InspectionToolRegistrar.getInstance(), (InspectionProfileImpl)null);
    assertThat(getInitializedTools(profile)).isEmpty();
    List<InspectionToolWrapper<?, ?>> toolWrappers = profile.getInspectionTools(null);
    assertThat(toolWrappers).isNotEmpty();
    InspectionToolWrapper<?, ?> toolWrapper = profile.getInspectionTool(new DataFlowInspection().getShortName(), getProject());
    assertThat(toolWrapper).isNotNull();
    String id = toolWrapper.getShortName();
    profile.setToolEnabled(id, !profile.isToolEnabled(HighlightDisplayKey.findById(id)));
    assertThat(getInitializedTools(profile)).isEmpty();
    profile.writeScheme();
    assertThat(getInitializedTools(profile)).isEmpty();
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
    assertThat(getInitializedTools(foo)).hasSize(1);
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
