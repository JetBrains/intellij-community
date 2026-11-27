// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes;

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils;
import com.intellij.codeInspection.UpdateInspectionOptionFix;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.java18api.Java8MapApiInspection;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandExecutor;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class UpdateInspectionOptionFixTest {
  private static final TestFixture<LightJavaProject> projectFixture =
    lightJavaProjectFixture(LightProjectDescriptor.EMPTY_PROJECT_DESCRIPTOR);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUp() {
    InspectionProfileImpl.INIT_INSPECTIONS = true;
  }

  @AfterEach
  void tearDown() {
    InspectionProfileImpl.INIT_INSPECTIONS = false;
  }

  @Test
  public void testUpdateOption() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("Test.java", "class X {}");
    Java8MapApiInspection inspection = new Java8MapApiInspection();
    myFixture.enableInspections(inspection);
    ModCommand command = ReadAction.computeBlocking(() -> ModCommand.updateInspectionOption(myFixture.getFile(), inspection, tool -> {
      tool.mySuggestMapComputeIfAbsent = false;
      tool.mySuggestMapGetOrDefault = false;
    }));
    assertEquals("""
                   ModUpdateSystemOptions[options=[\
                   ModifiedOption[bindId=currentProfile.Java8MapApi.options.mySuggestMapComputeIfAbsent, oldValue=true, newValue=false], \
                   ModifiedOption[bindId=currentProfile.Java8MapApi.options.mySuggestMapGetOrDefault, oldValue=true, newValue=false]]]""", command.toString());
    IntentionPreviewInfo preview =
      ReadAction.computeBlocking(() -> IntentionPreviewUtils.getModCommandPreview(command, myFixture.getActionContext()));
    assertTrue(preview instanceof IntentionPreviewInfo.Html);
    assertEquals("Uncheck option:<br/><br/><table><tr><td><input readonly=\"true\" type=\"checkbox\"/></td><td>Suggest conversion to &#39;Map.computeIfAbsent()&#39;</td></tr></table>" +
                 "Uncheck option:<br/><br/><table><tr><td><input readonly=\"true\" type=\"checkbox\"/></td><td>Suggest conversion to &#39;Map.getOrDefault()&#39;</td></tr></table>",
                 ((IntentionPreviewInfo.Html)preview).content().toString());
  }

  @Test
  public void testFix() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("Test.java", "class X {}");
    Java8MapApiInspection inspection = new Java8MapApiInspection();
    myFixture.enableInspections(inspection);
    UpdateInspectionOptionFix fix = new UpdateInspectionOptionFix(inspection, "mySuggestMapComputeIfAbsent", "Update", false);
    IntentionPreviewInfo info = ReadAction.computeBlocking(() -> fix.generatePreview(myFixture.getActionContext()));
    assertTrue(info instanceof IntentionPreviewInfo.Html);
    assertEquals("Uncheck option:<br/><br/><table><tr><td><input readonly=\"true\" type=\"checkbox\"/></td>" +
                 "<td>Suggest conversion to &#39;Map.computeIfAbsent()&#39;</td></tr></table>", ((IntentionPreviewInfo.Html)info).content().toString());
    assertTrue(((Java8MapApiInspection)InspectionProfileManager.getInstance(myFixture.getProject()).getCurrentProfile()
      .getUnwrappedTool("Java8MapApi", myFixture.getFile())).mySuggestMapComputeIfAbsent);
    EdtTestUtil.runInEdtAndWait(() -> {
      ModCommand command = fix.perform(myFixture.getActionContext());
      Runnable cmd = () -> ModCommandExecutor.getInstance().executeInteractively(myFixture.getActionContext(), command, myFixture.getEditor());
      CommandProcessor.getInstance().executeCommand(myFixture.getProject(), cmd, null, null);
    });
    assertFalse(((Java8MapApiInspection)InspectionProfileManager.getInstance(myFixture.getProject()).getCurrentProfile()
      .getUnwrappedTool("Java8MapApi", myFixture.getFile())).mySuggestMapComputeIfAbsent);
  }
}
