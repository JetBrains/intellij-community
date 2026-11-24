// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.generation;

import com.intellij.codeInsight.generation.GenerateConstructorModCommandAction;
import com.intellij.codeInspection.options.OptMultiSelector;
import com.intellij.codeInspection.options.OptRadioButton;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandExecutor;
import com.intellij.modcommand.ModDisplayMessage;
import com.intellij.modcommand.ModEditOptions;
import com.intellij.modcommand.ModNavigate;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

@TestApplication
public final class GenerateConstructorModCommandActionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  private static final String RECORD = """
    record Point(int x, int y) {
      <caret>
    }
    """;

  private final GenerateConstructorModCommandAction myAction = new GenerateConstructorModCommandAction();

  @Test
  public void defaultsInitializeFinalFields() {
    LightJavaCodeInsightTestFixture fixture = codeInsightFixture.get();
    fixture.configureByText("Test.java", """
      class Test {
        private final int a;
        private int b;
        private final int c = 1;
        private static int d;
        <caret>
      }
      """);
    assertEquals(List.of("a: int", "b: int"), ReadAction.computeBlocking(() -> {
      OptMultiSelector selector = findSelector(perform(fixture), "fields");
      return ContainerUtil.map(selector.elements(), e -> e.getText());
    }));
    launch(fixture);
    fixture.checkResult("""
      class Test {
        private final int a;
        private int b;
        private final int c = 1;
        private static int d;

          public Test(int a) {
              this.a = a;
          }
      }
      """);
  }

  @Test
  public void noFormWithoutFields() {
    LightJavaCodeInsightTestFixture fixture = codeInsightFixture.get();
    fixture.configureByText("Test.java", """
      class Test {
        <caret>
      }
      """);
    assertNull(ReadAction.computeBlocking(() -> findEditOptions(perform(fixture))));
    launch(fixture);
    fixture.checkResult("""
      class Test {
          public Test() {
          }
      }
      """);
  }

  @Test
  public void alreadyExists() {
    LightJavaCodeInsightTestFixture fixture = codeInsightFixture.get();
    fixture.configureByText("Test.java", """
      class Test {
        Test() {}
        <caret>
      }
      """);
    List<ModCommand> commands = ReadAction.computeBlocking(() -> perform(fixture)).unpack();
    assertEquals(2, commands.size());
    ModNavigate navigate = assertInstanceOf(ModNavigate.class, commands.getFirst());
    int nameOffset = fixture.getFile().getText().indexOf("Test() {}");
    assertEquals(nameOffset, navigate.caret());
    assertEquals(nameOffset, navigate.selectionStart());
    assertEquals(nameOffset, navigate.selectionEnd());
    ModDisplayMessage message = assertInstanceOf(ModDisplayMessage.class, commands.getLast());
    assertEquals(ModDisplayMessage.MessageKind.ERROR, message.kind());
    assertEquals("Constructor already exists", message.messageText());
  }

  @Test
  public void notAvailable() {
    LightJavaCodeInsightTestFixture fixture = codeInsightFixture.get();
    fixture.configureByText("Test.java", """
      interface I {
        <caret>
      }
      """);
    assertNull(ReadAction.computeBlocking(() -> myAction.getPresentation(ActionContext.from(fixture.getEditor(), fixture.getFile()))));
    fixture.configureByText("Test.java", """
      class Test {
        Object o = new Object() {
          <caret>
        };
      }
      """);
    assertNull(ReadAction.computeBlocking(() -> myAction.getPresentation(ActionContext.from(fixture.getEditor(), fixture.getFile()))));
  }

  @Test
  public void superConstructors() {
    LightJavaCodeInsightTestFixture fixture = codeInsightFixture.get();
    fixture.configureByText("Test.java", """
      class Base {
        Base(int i) {}
        Base(String s) {}
        private Base(long l) {}
      }
      class Test extends Base {
        private final int a;
        <caret>
      }
      """);
    apply(fixture, editOptions -> {
      OptMultiSelector selector = findSelector(editOptions, "superConstructors");
      assertEquals(2, selector.elements().size());
      return Map.of("superConstructors", selector.elements());
    });
    fixture.checkResult("""
      class Base {
        Base(int i) {}
        Base(String s) {}
        private Base(long l) {}
      }
      class Test extends Base {
        private final int a;

          public Test(int i, int a) {
              super(i);
              this.a = a;
          }

          public Test(String s, int a) {
              super(s);
              this.a = a;
          }
      }
      """);
  }

  @Test
  public void recordFormHasRadioButtons() {
    LightJavaCodeInsightTestFixture fixture = codeInsightFixture.get();
    fixture.configureByText("Point.java", RECORD);
    ReadAction.runBlocking(() -> {
      ModEditOptions<?> editOptions = Objects.requireNonNull(findEditOptions(perform(fixture)));
      List<OptRadioButton> radioButtons = editOptions.containerSupplier().get().getOptionsPane().components().stream()
        .map(c -> assertInstanceOf(OptRadioButton.class, c))
        .toList();
      assertEquals(List.of("COMPACT", "CANONICAL", "CUSTOM"), ContainerUtil.map(radioButtons, OptRadioButton::key));
      OptMultiSelector fields = assertInstanceOf(OptMultiSelector.class, radioButtons.getLast().children().getFirst());
      assertEquals("fields", fields.bindId());
    });
  }

  @Test
  public void recordCompact() {
    LightJavaCodeInsightTestFixture fixture = codeInsightFixture.get();
    fixture.configureByText("Point.java", RECORD);
    launch(fixture);
    fixture.checkResult("""
      record Point(int x, int y) {
          Point {
          }
      }
      """);
  }

  @Test
  public void recordCanonical() {
    LightJavaCodeInsightTestFixture fixture = codeInsightFixture.get();
    fixture.configureByText("Point.java", RECORD);
    apply(fixture, editOptions -> Map.of("recordConstructor", recordKind(editOptions, "CANONICAL")));
    fixture.checkResult("""
      record Point(int x, int y) {
          Point(int x, int y) {
              this.x = x;
              this.y = y;
          }
      }
      """);
  }

  @Test
  public void recordCustom() {
    LightJavaCodeInsightTestFixture fixture = codeInsightFixture.get();
    fixture.configureByText("Point.java", RECORD);
    apply(fixture, editOptions -> {
      OptMultiSelector fields = findSelector(editOptions, "fields");
      return Map.of("recordConstructor", recordKind(editOptions, "CUSTOM"), "fields", List.of(fields.elements().getFirst()));
    });
    fixture.checkResult("""
      record Point(int x, int y) {
          public Point(int x) {
              this(x, 0);
          }
      }
      """);
  }

  private @NotNull ModCommand perform(@NotNull LightJavaCodeInsightTestFixture fixture) {
    return myAction.perform(ActionContext.from(fixture.getEditor(), fixture.getFile()));
  }

  private void launch(@NotNull LightJavaCodeInsightTestFixture fixture) {
    EdtTestUtil.runInEdtAndWait(() -> fixture.launchAction(myAction.asIntention()));
  }

  /**
   * Performs the action, applies the options that {@code options} gives for the form, and executes the next command.
   */
  private void apply(@NotNull LightJavaCodeInsightTestFixture fixture, @NotNull Function<ModEditOptions<?>, Map<String, Object>> options) {
    var pair = ReadAction.computeBlocking(() -> {
      ActionContext context = ActionContext.from(fixture.getEditor(), fixture.getFile());
      ModEditOptions<?> editOptions = Objects.requireNonNull(findEditOptions(myAction.perform(context)));
      return Map.entry(context, editOptions.applyOptions(new HashMap<>(options.apply(editOptions))));
    });
    EdtTestUtil.runInEdtAndWait(() -> CommandProcessor.getInstance().executeCommand(
      fixture.getProject(),
      () -> ModCommandExecutor.getInstance().executeInteractively(pair.getKey(), pair.getValue(), fixture.getEditor()),
      null, null));
  }

  private static ModEditOptions<?> findEditOptions(@NotNull ModCommand command) {
    return (ModEditOptions<?>)ContainerUtil.find(command.unpack(), c -> c instanceof ModEditOptions);
  }

  private static @NotNull OptMultiSelector findSelector(@NotNull ModCommand command, @NotNull String bindId) {
    return findSelector(Objects.requireNonNull(findEditOptions(command)), bindId);
  }

  private static @NotNull OptMultiSelector findSelector(@NotNull ModEditOptions<?> editOptions, @NotNull String bindId) {
    return assertInstanceOf(OptMultiSelector.class, editOptions.containerSupplier().get().getOptionsPane().findControl(bindId));
  }

  /**
   * @return the constant of the private enum of the action, which the radio buttons of a record set
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  private static @NotNull Object recordKind(@NotNull ModEditOptions<?> editOptions, @NotNull String name) {
    Object current = editOptions.containerSupplier().get().getOptionController().getOption("recordConstructor");
    return Enum.valueOf(((Enum)Objects.requireNonNull(current)).getDeclaringClass(), name);
  }
}
