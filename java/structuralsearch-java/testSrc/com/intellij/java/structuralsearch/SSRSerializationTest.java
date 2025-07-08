// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.structuralsearch;

import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ex.InspectionToolsSupplier;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.profile.codeInspection.BaseInspectionProfileManager;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.structuralsearch.MatchOptions;
import com.intellij.structuralsearch.inspection.SSBasedInspection;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import com.intellij.structuralsearch.plugin.ui.SearchConfiguration;
import com.intellij.testFramework.LightPlatformTestCase;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * @author Bas Leijdekkers
 */
public class SSRSerializationTest extends LightPlatformTestCase {

  private InspectionProfileImpl myProfile;
  private SSBasedInspection myInspection;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    InspectionProfileImpl.INIT_INSPECTIONS = true;
    myInspection = new SSBasedInspection();
    final SearchConfiguration configuration = new SearchConfiguration("i", "user defined");
    final MatchOptions options = configuration.getMatchOptions();
    options.setFileType(JavaFileType.INSTANCE);
    options.setSearchPattern("int i;");
    myInspection.addConfiguration(configuration);
    final InspectionToolsSupplier supplier = new InspectionToolsSupplier() {

      @Override
      public @NotNull List<InspectionToolWrapper<?, ?>> createTools() {
        return Collections.singletonList(new LocalInspectionToolWrapper(myInspection));
      }
    };
    myProfile = new InspectionProfileImpl("test", supplier, (BaseInspectionProfileManager)InspectionProfileManager.getInstance());
    myProfile.enableTool(SSBasedInspection.SHORT_NAME, getProject());
    myProfile.lockProfile(true);
    myProfile.initInspectionTools(getProject());
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      InspectionProfileImpl.INIT_INSPECTIONS = false;
      myProfile.getProfileManager().deleteProfile(myProfile);
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  private String buildXmlFromProfile() {
    final Element node = new Element("profile");
    myProfile.writeExternal(node);
    return JDOMUtil.writeElement(node);
  }

  public void testSimple() {
    List<InspectionToolWrapper<?, ?>> tools = myProfile.getInspectionTools(null);
    assertEquals("SSBasedInspection and 1 child tool should be available", 2, tools.size());
  }

  public void testDefaultToolsNotWritten() {
    final String expected = """
      <profile version="1.0" is_locked="true">
        <option name="myName" value="test" />
        <inspection_tool class="SSBasedInspection" enabled="true" level="WARNING" enabled_by_default="true">
          <searchConfiguration name="i" text="int i;" recursive="false" caseInsensitive="false" type="JAVA" />
        </inspection_tool>
      </profile>""";
    assertEquals(expected, buildXmlFromProfile());
  }

  public void testModifiedToolShouldBeWritten() {
    final Configuration configuration = myInspection.getConfigurations().get(0);
    myProfile.setToolEnabled(configuration.getUuid(), false);

    final String expected =
      """
        <profile version="1.0" is_locked="true">
          <option name="myName" value="test" />
          <inspection_tool class="865c0c0b-4ab0-3063-a5ca-a3387c1a8741" enabled="false" level="WARNING" enabled_by_default="false" />
          <inspection_tool class="SSBasedInspection" enabled="true" level="WARNING" enabled_by_default="true">
            <searchConfiguration name="i" text="int i;" recursive="false" caseInsensitive="false" type="JAVA" />
          </inspection_tool>
        </profile>""";
    assertEquals(expected, buildXmlFromProfile());
  }

  public void testWriteUuidWhenNameChanged() {
    final Configuration configuration = myInspection.getConfigurations().get(0);
    configuration.setName("j");

    final String expected =
      """
        <profile version="1.0" is_locked="true">
          <option name="myName" value="test" />
          <inspection_tool class="SSBasedInspection" enabled="true" level="WARNING" enabled_by_default="true">
            <searchConfiguration name="j" uuid="865c0c0b-4ab0-3063-a5ca-a3387c1a8741" text="int i;" recursive="false" caseInsensitive="false" type="JAVA" />
          </inspection_tool>
        </profile>""";
    assertEquals(expected, buildXmlFromProfile());
  }
}
