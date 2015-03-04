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
package com.intellij.codeInspection;

import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionProfileTest;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.codeInspection.javaDoc.JavaDocLocalInspection;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.profile.codeInspection.ui.SingleInspectionProfilePanel;
import com.intellij.testFramework.LightIdeaTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 *         Date: 5/10/12
 */
public class SingleInspectionProfilePanelTest extends LightIdeaTestCase {
  // see IDEA-85700
  public void testSettingsModification() throws Exception {
    Project project = ProjectManager.getInstance().getDefaultProject();
    InspectionProjectProfileManager profileManager = InspectionProjectProfileManager.getInstance(project);
    InspectionProfileImpl profile = (InspectionProfileImpl)profileManager.getProfile(PROFILE);
    profile.initInspectionTools(project);

    InspectionProfileImpl model = (InspectionProfileImpl)profile.getModifiableModel();
    SingleInspectionProfilePanel panel = new SingleInspectionProfilePanel(profileManager, PROFILE, model, profile);
    panel.setVisible(true);
    panel.reset();

    JavaDocLocalInspection tool = getInspection(model);
    assertEquals("", tool.myAdditionalJavadocTags);
    tool.myAdditionalJavadocTags = "foo";
    model.setModified(true);
    panel.apply();
    assertEquals(1, InspectionProfileTest.countInitializedTools(model));

    assertEquals("foo", getInspection(profile).myAdditionalJavadocTags);
    panel.disposeUI();
  }

  public void testModifyInstantiatedTool() throws Exception {
    Project project = ProjectManager.getInstance().getDefaultProject();
    InspectionProjectProfileManager profileManager = InspectionProjectProfileManager.getInstance(project);
    InspectionProfileImpl profile = (InspectionProfileImpl)profileManager.getProfile(PROFILE);
    profile.initInspectionTools(project);

    JavaDocLocalInspection originalTool = getInspection(profile);
    originalTool.myAdditionalJavadocTags = "foo";

    InspectionProfileImpl model = (InspectionProfileImpl)profile.getModifiableModel();

    SingleInspectionProfilePanel panel = new SingleInspectionProfilePanel(profileManager, PROFILE, model, profile);
    panel.setVisible(true);
    panel.reset();
    assertEquals(InspectionProfileTest.getInitializedTools(model).toString(), 1, InspectionProfileTest.countInitializedTools(model));

    JavaDocLocalInspection copyTool = getInspection(model);
    copyTool.myAdditionalJavadocTags = "bar";

    model.setModified(true);
    panel.apply();
    assertEquals(1, InspectionProfileTest.countInitializedTools(model));

    assertEquals("bar", getInspection(profile).myAdditionalJavadocTags);
    panel.disposeUI();
  }

  public void testDoNotChangeSettingsOnCancel() throws Exception {
    Project project = ProjectManager.getInstance().getDefaultProject();
    InspectionProjectProfileManager profileManager = InspectionProjectProfileManager.getInstance(project);
    InspectionProfileImpl profile = (InspectionProfileImpl)profileManager.getProfile(PROFILE);
    profile.initInspectionTools(project);

    JavaDocLocalInspection originalTool = getInspection(profile);
    assertEquals("", originalTool.myAdditionalJavadocTags);

    InspectionProfileImpl model = (InspectionProfileImpl)profile.getModifiableModel();
    JavaDocLocalInspection copyTool = getInspection(model);
    copyTool.myAdditionalJavadocTags = "foo";
    // this change IS NOT COMMITTED

    assertEquals("", getInspection(profile).myAdditionalJavadocTags);
  }

  private JavaDocLocalInspection getInspection(InspectionProfileImpl profile) {
    LocalInspectionToolWrapper original = (LocalInspectionToolWrapper)profile.getInspectionTool(myInspection.getShortName(), getProject());
    assert original != null;
    return (JavaDocLocalInspection)original.getTool();
  }

  @Override
  public void setUp() throws Exception {
    InspectionProfileImpl.INIT_INSPECTIONS = true;
    super.setUp();
  }

  @Override
  public void tearDown() throws Exception {
    InspectionProfileImpl.INIT_INSPECTIONS = false;
    super.tearDown();
  }

  private final JavaDocLocalInspection myInspection = new JavaDocLocalInspection();

  @NotNull
  @Override
  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return new LocalInspectionTool[] {myInspection};
  }
}
