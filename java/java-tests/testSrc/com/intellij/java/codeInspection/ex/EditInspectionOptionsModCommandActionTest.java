// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection.ex;

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ex.EditInspectionOptionsModCommandAction;
import com.intellij.codeInspection.java18api.Java8MapApiInspection;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModEditOptions;
import com.intellij.modcommand.ModUpdateSystemOptions;
import com.intellij.modcommand.ModUpdateSystemOptions.ModifiedOption;
import com.intellij.modcommand.Presentation;
import com.intellij.openapi.application.ReadAction;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import com.siyeh.ig.imports.SingleClassImportInspection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class EditInspectionOptionsModCommandActionTest {
  private static final String MAP_API = "Java8MapApi";
  private static final String COMPUTE_IF_ABSENT = "mySuggestMapComputeIfAbsent";
  private static final String COMPUTE_IF_ABSENT_BIND_ID = "currentProfile." + MAP_API + ".options." + COMPUTE_IF_ABSENT;

  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new Java8MapApiInspection());

  @BeforeEach
  void setUp() {
    fixture().enableInspections(new SingleClassImportInspection());
    fixture().configureByText("Test.java", "class X {}");
  }

  @Test
  void presentationShowsTheInspectionName() {
    Presentation presentation = ReadAction.compute(() -> new EditInspectionOptionsModCommandAction(MAP_API).getPresentation(context()));
    assertNotNull(presentation);
    assertEquals(InspectionsBundle.message("edit.inspection.options", mapApiTool().getDisplayName()), presentation.name());
  }

  @Test
  void previewDescribesTheDialog() {
    IntentionPreviewInfo preview = ReadAction.compute(() -> new EditInspectionOptionsModCommandAction(MAP_API).generatePreview(context()));
    assertEquals(InspectionsBundle.message("edit.inspection.options.preview", mapApiTool().getDisplayName()),
                 assertInstanceOf(IntentionPreviewInfo.Html.class, preview).content().toString());
  }

  @Test
  void notAvailableForUnknownInspection() {
    assertNotAvailable("UnknownInspectionId");
  }

  @Test
  void notAvailableForInspectionWithoutOptions() {
    assertNotAvailable("SingleClassImport");
  }

  @Test
  void formShowsCurrentOptionsAndEditsCopy() {
    mapApiTool().mySuggestMapComputeIfAbsent = false;
    ModCommand command = ReadAction.compute(() -> {
      ModEditOptions<?> form = editOptions();
      Java8MapApiInspection container = assertInstanceOf(Java8MapApiInspection.class, form.containerSupplier().get());
      assertFalse(container.mySuggestMapComputeIfAbsent);
      container.mySuggestMapComputeIfAbsent = true;
      return form.applyOptions(Map.of());
    });
    assertFalse(mapApiTool().mySuggestMapComputeIfAbsent, "the form must not change the inspection in the profile");
    assertTrue(command.isEmpty(), "the options have the old values: " + command);
  }

  @Test
  void editUpdatesChangedOptions() {
    ModCommand command = ReadAction.compute(() -> editOptions().applyOptions(Map.of(COMPUTE_IF_ABSENT, false)));
    assertEquals(List.of(new ModifiedOption(COMPUTE_IF_ABSENT_BIND_ID, true, false)),
                 assertInstanceOf(ModUpdateSystemOptions.class, command).options());
    assertTrue(mapApiTool().mySuggestMapComputeIfAbsent, "the command must not change the inspection in the profile");
  }

  @Test
  void editSetsOptionBackToDefaultValue() {
    mapApiTool().mySuggestMapComputeIfAbsent = false;
    ModCommand command = ReadAction.compute(() -> editOptions().applyOptions(Map.of(COMPUTE_IF_ABSENT, true)));
    assertEquals(List.of(new ModifiedOption(COMPUTE_IF_ABSENT_BIND_ID, false, true)),
                 assertInstanceOf(ModUpdateSystemOptions.class, command).options());
  }

  private JavaCodeInsightTestFixture fixture() {
    return inspectionFixture.get().getFixture();
  }

  private ActionContext context() {
    return ActionContext.from(null, fixture().getFile());
  }

  private void assertNotAvailable(String toolId) {
    ReadAction.run(() -> {
      EditInspectionOptionsModCommandAction action = new EditInspectionOptionsModCommandAction(toolId);
      assertNull(action.getPresentation(context()));
      assertTrue(action.perform(context()).isEmpty());
    });
  }

  /** The form of the action for {@link #MAP_API}. Must run in a read action. */
  private ModEditOptions<?> editOptions() {
    EditInspectionOptionsModCommandAction action = new EditInspectionOptionsModCommandAction(MAP_API);
    assertNotNull(action.getPresentation(context()));
    return assertInstanceOf(ModEditOptions.class, action.perform(context()));
  }

  private Java8MapApiInspection mapApiTool() {
    return ReadAction.compute(() -> (Java8MapApiInspection)InspectionProfileManager.getInstance(fixture().getProject())
      .getCurrentProfile().getUnwrappedTool(MAP_API, fixture().getFile()));
  }
}
