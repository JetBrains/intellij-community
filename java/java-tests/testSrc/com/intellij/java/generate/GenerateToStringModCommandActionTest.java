// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.generate;

import com.intellij.codeInspection.options.OptCheckbox;
import com.intellij.codeInspection.options.OptDropdown;
import com.intellij.codeInspection.options.OptHorizontalStack;
import com.intellij.codeInspection.options.OptMultiSelector;
import com.intellij.codeInspection.options.OptRegularComponent;
import com.intellij.codeInspection.options.OptionContainer;
import com.intellij.codeInspection.options.OptionController;
import com.intellij.codeInspection.options.OptionControllerProvider;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModChooseAction;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandExecutor;
import com.intellij.modcommand.ModEditOptions;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.psi.PsiField;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.java.generate.GenerateToStringContext;
import org.jetbrains.java.generate.GenerateToStringModCommandAction;
import org.jetbrains.java.generate.config.Config;
import org.jetbrains.java.generate.template.TemplateResource;
import org.jetbrains.java.generate.template.TemplatesManager;
import org.jetbrains.java.generate.template.toString.ToStringTemplatesManager;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@TestApplication
public final class GenerateToStringModCommandActionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  private static final String CONCAT_TEMPLATE = "String concat (+)";
  private static final String CUSTOM_TEMPLATE = "Custom template";
  private static final String BUILDER_TEMPLATE = "StringBuilder (JDK 1.5)";
  private static final String GUAVA_TEMPLATE = "MoreObjects.toStringHelper (Guava 18+)";
  private static final String DEFAULT_TEMPLATE_BIND_ID = "TemplatesManager.ToStringTemplates.defaultTemplateName";
  private static final String BEFORE = """
    class Point {
        private int x;
        private int y;
        <caret>
    }
    """;

  private static final String FIELDS_AND_METHOD = """
    class Point {
        private int <caret>x;
        private int y;

        void move() {}
    }
    """;

  private final GenerateToStringModCommandAction myAction = new GenerateToStringModCommandAction();

  @Test
  public void form() {
    LightJavaCodeInsightTestFixture fixture = configure(BEFORE);
    ReadAction.runBlocking(() -> {
      List<OptRegularComponent> components = components(perform(fixture));
      assertEquals(3, components.size());
      OptMultiSelector selector = assertInstanceOf(OptMultiSelector.class, components.get(0));
      assertEquals(List.of("x: int", "y: int"), ContainerUtil.map(selector.elements(), OptMultiSelector.OptElement::getText));
      OptHorizontalStack stack = assertInstanceOf(OptHorizontalStack.class, components.get(1));
      OptDropdown dropdown = assertInstanceOf(OptDropdown.class, stack.children().getFirst());
      Map<String, String> labels = ContainerUtil.map2Map(dropdown.options(), option -> Pair.create(option.key(), option.label().label()));
      assertEquals(CONCAT_TEMPLATE, labels.get(CONCAT_TEMPLATE));
      assertEquals(GUAVA_TEMPLATE + " (incompatible)", labels.get(GUAVA_TEMPLATE));
      assertInstanceOf(OptCheckbox.class, components.get(2));
    });
  }

  @Test
  public void formShowsChangedTemplates() {
    LightJavaCodeInsightTestFixture fixture = configure(BEFORE);
    TemplatesManager templatesManager = ToStringTemplatesManager.getInstance();
    List<TemplateResource> savedTemplates = new ArrayList<>(templatesManager.getAllTemplates());
    try {
      ReadAction.runBlocking(() -> {
        OptionContainer container = perform(fixture).containerSupplier().get();
        assertNull(dropdownLabels(container).get(CUSTOM_TEMPLATE));

        templatesManager.addTemplate(new TemplateResource(CUSTOM_TEMPLATE, "public String toString() { return \"\"; }", false, null));
        assertEquals(CUSTOM_TEMPLATE, dropdownLabels(container).get(CUSTOM_TEMPLATE));
        assertEquals(GUAVA_TEMPLATE + " (incompatible)", dropdownLabels(container).get(GUAVA_TEMPLATE));

        OptionController controller = container.getOptionController();
        controller.setOption("template", CUSTOM_TEMPLATE);
        templatesManager.setTemplates(savedTemplates);
        assertNull(dropdownLabels(container).get(CUSTOM_TEMPLATE));
        assertEquals(templatesManager.getDefaultTemplate().getFileName(), controller.getOption("template"));
      });
    }
    finally {
      templatesManager.setTemplates(savedTemplates);
    }
  }

  @Test
  public void incompatibleDefaultTemplateIsNotSelected() {
    LightJavaCodeInsightTestFixture fixture = configure(BEFORE);
    TemplatesManager templatesManager = ToStringTemplatesManager.getInstance();
    String savedDefault = templatesManager.getState().defaultTemplateName;
    try {
      templatesManager.getState().defaultTemplateName = GUAVA_TEMPLATE;
      ReadAction.runBlocking(() -> {
        OptionContainer container = perform(fixture).containerSupplier().get();
        assertEquals(CONCAT_TEMPLATE, container.getOptionController().getOption("template"));
      });
    }
    finally {
      templatesManager.getState().defaultTemplateName = savedDefault;
    }
  }

  @Test
  public void selectedTemplateBecomesDefault() {
    LightJavaCodeInsightTestFixture fixture = configure(BEFORE);
    TemplatesManager templatesManager = ToStringTemplatesManager.getInstance();
    String savedDefault = templatesManager.getState().defaultTemplateName;
    try {
      execute(fixture, () -> perform(fixture).applyOptions(Map.of("template", BUILDER_TEMPLATE, "insertOverride", true)));
      assertEquals(BUILDER_TEMPLATE, templatesManager.getDefaultTemplate().getFileName());
      ReadAction.runBlocking(() -> {
        assertEquals(BUILDER_TEMPLATE, OptionControllerProvider.getOption(fixture.getFile(), DEFAULT_TEMPLATE_BIND_ID));
        assertEquals(BUILDER_TEMPLATE, perform(fixture).containerSupplier().get().getOptionController().getOption("template"));
      });
    }
    finally {
      templatesManager.getState().defaultTemplateName = savedDefault;
    }
  }

  @Test
  public void formShowsChangedMembers() {
    LightJavaCodeInsightTestFixture fixture = configure("""
      class Point {
          private int x;
          private int y;
          private int z;
          <caret>
      }
      """);
    Config savedConfig = GenerateToStringContext.getConfig();
    try {
      GenerateToStringContext.setConfig(configExcluding("z"));
      ReadAction.runBlocking(() -> {
        OptionContainer container = perform(fixture).containerSupplier().get();
        assertEquals(List.of("x: int", "y: int"), memberTexts(container));
        OptionController controller = container.getOptionController();
        controller.setOption("members", List.of(assertInstanceOf(List.class, controller.getOption("members")).get(1)));

        // The template settings change the filter: the new member of the class becomes selected.
        GenerateToStringContext.setConfig(configExcluding("x"));
        assertEquals(List.of("y: int", "z: int"), memberTexts(container));
        assertEquals(List.of("y: int", "z: int"), selectedTexts(controller));

        GenerateToStringContext.setConfig(configExcluding("y"));
        assertEquals(List.of("x: int", "z: int"), memberTexts(container));
        assertEquals(List.of("z: int", "x: int"), selectedTexts(controller));
      });
    }
    finally {
      GenerateToStringContext.setConfig(savedConfig);
    }
  }

  private static @NotNull Config configExcluding(@NotNull String fieldName) {
    Config config = new Config();
    config.getFilterPattern().setFieldName(fieldName);
    return config;
  }

  private static @NotNull List<String> selectedTexts(@NotNull OptionController controller) {
    List<?> selection = assertInstanceOf(List.class, controller.getOption("members"));
    return ContainerUtil.map(selection, member -> ((OptMultiSelector.OptElement)member).getText());
  }

  private static @NotNull List<String> memberTexts(@NotNull OptionContainer container) {
    OptMultiSelector selector = assertInstanceOf(OptMultiSelector.class, container.getOptionsPane().components().getFirst());
    return ContainerUtil.map(selector.elements(), OptMultiSelector.OptElement::getText);
  }

  private static @NotNull Map<String, String> dropdownLabels(@NotNull OptionContainer container) {
    OptHorizontalStack stack = assertInstanceOf(OptHorizontalStack.class, container.getOptionsPane().components().get(1));
    OptDropdown dropdown = assertInstanceOf(OptDropdown.class, stack.children().getFirst());
    return ContainerUtil.map2Map(dropdown.options(), option -> Pair.create(option.key(), option.label().label()));
  }

  @Test
  public void notAvailableInInterface() {
    LightJavaCodeInsightTestFixture fixture = configure("interface Shape {<caret>}");
    ReadAction.runBlocking(() -> assertNull(myAction.getPresentation(ActionContext.from(fixture.getEditor(), fixture.getFile()))));
  }

  @Test
  public void generateForAllMembers() {
    LightJavaCodeInsightTestFixture fixture = configure(BEFORE);
    execute(fixture, () -> perform(fixture).applyOptions(Map.of("template", CONCAT_TEMPLATE, "insertOverride", true)));
    fixture.checkResult("""
      class Point {
          private int x;
          private int y;

          @Override
          public String toString() {
              return "Point{" +
                      "x=" + x +
                      ", y=" + y +
                      '}';
          }
      }
      """);
  }

  @Test
  public void generateForSelectedMemberWithoutOverride() {
    LightJavaCodeInsightTestFixture fixture = configure(BEFORE);
    execute(fixture, () -> {
      ModEditOptions<?> editOptions = perform(fixture);
      OptMultiSelector selector = (OptMultiSelector)components(editOptions).get(0);
      return editOptions.applyOptions(Map.of("members", List.of(selector.elements().get(1)),
                                             "template", CONCAT_TEMPLATE,
                                             "insertOverride", false));
    });
    fixture.checkResult("""
      class Point {
          private int x;
          private int y;

          public String toString() {
              return "Point{" +
                      "y=" + y +
                      '}';
          }
      }
      """);
  }

  @Test
  public void fieldElementInsertsAtEndOfClass() {
    LightJavaCodeInsightTestFixture fixture = configure(FIELDS_AND_METHOD);
    execute(fixture, () -> {
      PsiField field = Objects.requireNonNull(PsiTreeUtil.getParentOfType(fixture.getFile().findElementAt(fixture.getCaretOffset()),
                                                                          PsiField.class));
      // The fix of FieldNotUsedInToStringInspection has this context: the element is the name of the field.
      ActionContext context = ActionContext.from(fixture.getEditor(), fixture.getFile()).withElement(field.getNameIdentifier());
      return assertInstanceOf(ModEditOptions.class, myAction.perform(context))
        .applyOptions(Map.of("template", CONCAT_TEMPLATE, "insertOverride", true));
    });
    fixture.checkResult("""
      class Point {
          private int x;
          private int y;

          void move() {}

          @Override
          public String toString() {
              return "Point{" +
                      "x=" + x +
                      ", y=" + y +
                      '}';
          }
      }
      """);
  }

  @Test
  public void caretInFieldInsertsAtCaret() {
    LightJavaCodeInsightTestFixture fixture = configure(FIELDS_AND_METHOD);
    execute(fixture, () -> perform(fixture).applyOptions(Map.of("template", CONCAT_TEMPLATE, "insertOverride", true)));
    fixture.checkResult("""
      class Point {
          @Override
          public String toString() {
              return "Point{" +
                      "x=" + x +
                      ", y=" + y +
                      '}';
          }

          private int x;
          private int y;

          void move() {}
      }
      """);
  }

  @Test
  public void caretInTypeParameter() {
    LightJavaCodeInsightTestFixture fixture = configure("class Box<<caret>T> {}");
    execute(fixture, () -> perform(fixture).applyOptions(Map.of("template", CONCAT_TEMPLATE, "insertOverride", true)));
    fixture.checkResult("""
      class Box<T> {
          @Override
          public String toString() {
              return "Box{}";
          }
      }""");
  }

  @Test
  public void replaceExistingMethod() {
    LightJavaCodeInsightTestFixture fixture = configure("""
      class Point {
          private int x;
          <caret>
          public String toString() {
              return "";
          }
      }
      """);
    execute(fixture, () -> {
      ModCommand command = perform(fixture).applyOptions(Map.of("template", CONCAT_TEMPLATE, "insertOverride", true));
      ModChooseAction chooseAction = assertInstanceOf(ModChooseAction.class, command);
      assertEquals(2, chooseAction.actions().size());
      return chooseAction.actions().get(0).perform(ActionContext.from(fixture.getEditor(), fixture.getFile()));
    });
    fixture.checkResult("""
      class Point {
          private int x;

          @Override
          public String toString() {
              return "Point{" +
                      "x=" + x +
                      '}';
          }
      }
      """);
  }

  private @NotNull LightJavaCodeInsightTestFixture configure(@NotNull String text) {
    LightJavaCodeInsightTestFixture fixture = codeInsightFixture.get();
    fixture.configureByText("Point.java", text);
    return fixture;
  }

  private @NotNull ModEditOptions<?> perform(@NotNull LightJavaCodeInsightTestFixture fixture) {
    ActionContext context = ActionContext.from(fixture.getEditor(), fixture.getFile());
    assertNotNull(myAction.getPresentation(context));
    return assertInstanceOf(ModEditOptions.class, myAction.perform(context));
  }

  private static @NotNull List<OptRegularComponent> components(@NotNull ModEditOptions<?> editOptions) {
    return editOptions.containerSupplier().get().getOptionsPane().components();
  }

  /**
   * Computes the command in a read action, and then executes it on the EDT.
   */
  private static void execute(@NotNull LightJavaCodeInsightTestFixture fixture,
                              @NotNull ThrowableComputable<ModCommand, RuntimeException> commandSupplier) {
    Pair<ActionContext, ModCommand> pair = ReadAction.computeBlocking(
      () -> Pair.create(ActionContext.from(fixture.getEditor(), fixture.getFile()), commandSupplier.compute()));
    EdtTestUtil.runInEdtAndWait(() -> CommandProcessor.getInstance().executeCommand(
      fixture.getProject(),
      () -> ModCommandExecutor.getInstance().executeInteractively(pair.first, pair.second, fixture.getEditor()),
      null, null));
  }
}
