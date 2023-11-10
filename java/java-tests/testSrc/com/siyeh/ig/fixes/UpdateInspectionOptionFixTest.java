// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes;

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils;
import com.intellij.codeInspection.UpdateInspectionOptionFix;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.java18api.Java8MapApiInspection;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandExecutor;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

public class UpdateInspectionOptionFixTest extends BasePlatformTestCase {
  @Override
  protected void setUp() throws Exception {
    InspectionProfileImpl.INIT_INSPECTIONS = true;
    super.setUp();
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
    InspectionProfileImpl.INIT_INSPECTIONS = false;
  }

  public void testUpdateOption() {
    myFixture.configureByText("Test.java", "class X {}");
    Java8MapApiInspection inspection = new Java8MapApiInspection();
    myFixture.enableInspections(inspection);
    ModCommand command = ModCommand.updateOption(myFixture.getFile(), inspection, tool -> {
      tool.mySuggestMapComputeIfAbsent = false;
      tool.mySuggestMapGetOrDefault = false;
    });
    assertEquals("""
                   ModUpdateSystemOptions[options=[\
                   ModifiedOption[bindId=currentProfile.Java8MapApi.options.mySuggestMapComputeIfAbsent, oldValue=true, newValue=false], \
                   ModifiedOption[bindId=currentProfile.Java8MapApi.options.mySuggestMapGetOrDefault, oldValue=true, newValue=false]]]""", command.toString());
    IntentionPreviewInfo preview = IntentionPreviewUtils.getModCommandPreview(command, myFixture.getActionContext());
    assertTrue(preview instanceof IntentionPreviewInfo.Html);
    assertEquals("Uncheck inspection option:<br/><br/><table><tr><td><input readonly=\"true\" type=\"checkbox\"/></td><td>Suggest conversion to Map.computeIfAbsent</td></tr></table>" +
                 "Uncheck inspection option:<br/><br/><table><tr><td><input readonly=\"true\" type=\"checkbox\"/></td><td>Suggest conversion to Map.getOrDefault</td></tr></table>",
                 ((IntentionPreviewInfo.Html)preview).content().toString());
  }
  
  public void testFix() {
    myFixture.configureByText("Test.java", "class X {}");
    Java8MapApiInspection inspection = new Java8MapApiInspection();
    myFixture.enableInspections(inspection);
    UpdateInspectionOptionFix fix = new UpdateInspectionOptionFix(inspection, "mySuggestMapComputeIfAbsent", "Update", false);
    IntentionPreviewInfo info = fix.generatePreview(myFixture.getActionContext());
    assertTrue(info instanceof IntentionPreviewInfo.Html);
    assertEquals("Uncheck inspection option:<br/><br/><table><tr><td><input readonly=\"true\" type=\"checkbox\"/></td>" +
                 "<td>Suggest conversion to Map.computeIfAbsent</td></tr></table>", ((IntentionPreviewInfo.Html)info).content().toString());
    assertTrue(((Java8MapApiInspection)InspectionProfileManager.getInstance(getProject()).getCurrentProfile()
      .getUnwrappedTool("Java8MapApi", myFixture.getFile())).mySuggestMapComputeIfAbsent);
    ModCommand command = fix.perform(myFixture.getActionContext());
    Runnable cmd = () -> ModCommandExecutor.getInstance().executeInteractively(myFixture.getActionContext(), command, myFixture.getEditor());
    CommandProcessor.getInstance().executeCommand(getProject(), cmd, null, null);
    assertFalse(((Java8MapApiInspection)InspectionProfileManager.getInstance(getProject()).getCurrentProfile()
      .getUnwrappedTool("Java8MapApi", myFixture.getFile())).mySuggestMapComputeIfAbsent);
  }

}
