// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring;

import com.intellij.model.SymbolPointer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.RefactoringSettings;
import com.intellij.refactoring.modplan.ModPlan;
import com.intellij.refactoring.modplan.ModPlanExecutionResult;
import com.intellij.refactoring.modplan.ModPlanExecutor;
import com.intellij.refactoring.modplan.ModPlanOption;
import com.intellij.refactoring.modplan.ModPlanOptionValues;
import com.intellij.refactoring.modplan.ModPlanQuestion;
import com.intellij.refactoring.modplan.ModPlanRequest;
import com.intellij.refactoring.modplan.impl.ModPlanFlow;
import com.intellij.refactoring.modplan.impl.ModPlanFlowUi;
import com.intellij.refactoring.modplan.impl.ModPlanTestFlowUi;
import com.intellij.refactoring.modplan.safeDelete.ModPlanSafeDeleteOptions;
import com.intellij.refactoring.modplan.safeDelete.ModPlanSafeDeleteRefactoring;
import com.intellij.refactoring.modplan.safeDelete.impl.ModPlanSafeDeleteFlow;
import com.intellij.refactoring.safeDelete.ModPlanJavaSafeDeleteOptions;
import com.intellij.refactoring.safeDelete.SafeDeleteHandler;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.ModPlanTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The steps of the interactive safe delete on the ModPlan engine that the unit-test mode of the legacy flow does not cover.
 */
@TestApplication
public class InteractiveModPlanSafeDeleteTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  /** The comment is not next to the method: a comment right before a member is deleted with it. */
  private static final String WITH_NON_CODE_USAGES = """
    class A {
      void <caret>foo() {}

      void bar() { /* foo is old */ }
    }
    """;

  private boolean myOldSearchInComments;

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    Registry.get("refactoring.safeDelete.modPlan").setValue(true, myFixture.getTestRootDisposable());
    RefactoringSettings settings = RefactoringSettings.getInstance();
    myOldSearchInComments = settings.SAFE_DELETE_SEARCH_IN_COMMENTS;
    settings.SAFE_DELETE_SEARCH_IN_COMMENTS = true;
  }

  @AfterEach
  void tearDown() {
    RefactoringSettings.getInstance().SAFE_DELETE_SEARCH_IN_COMMENTS = myOldSearchInComments;
  }

  /** The legacy flow shows no conflicts for a non-code usage, and previews it, which a unit test skips: the deletion goes through. */
  @Test
  public void testNonCodeUsagesAloneDoNotBlockTheDeletion() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("A.java", WITH_NON_CODE_USAGES);

    EdtTestUtil.runInEdtAndWait(() -> {
      SafeDeleteHandler.invoke(myFixture.getProject(), new PsiElement[]{myFixture.getElementAtCaret()}, true);

      String text = myFixture.getFile().getText();
      assertFalse(text.contains("void foo"), text);
      assertTrue(text.contains("/* foo is old */"), "The non-code usage is left as it is: " + text);
    });
  }

  /** The non-code usages are shown in the preview, each with its range in the file. */
  @Test
  public void testNonCodeUsagesAreShownInThePreview() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("A.java", WITH_NON_CODE_USAGES);
    PsiElement method = ReadAction.computeBlocking(() -> myFixture.getElementAtCaret());

    List<UsageInfo> usages = inBackground(() -> {
      ModPlanOptionValues optionValues = ModPlanOptionValues.EMPTY.with(ModPlanSafeDeleteOptions.SEARCH_IN_COMMENTS, true);
      ModPlan.AcceptedPlan acceptedPlan = (ModPlan.AcceptedPlan)ModPlanSafeDeleteRefactoring.getInstance().plan(
        new ModPlanRequest(myFixture.getProject(), List.of(SymbolPointer.of(method)), optionValues));
      return List.of(ModPlanFlow.nonCodeUsages(acceptedPlan));
    });

    assertEquals(1, usages.size());
    UsageInfo usage = usages.getFirst();
    assertTrue(usage.isNonCodeUsage);
    ReadAction.runBlocking(() -> {
      PsiElement file = usage.getElement();
      assertNotNull(file);
      TextRange range = usage.getRangeInElement();
      assertNotNull(range);
      assertEquals("foo", range.substring(file.getText()));
    });
  }

  /** A conflict is shown with the non-code usages listed in it, and nothing is previewed after it, as in the legacy flow. */
  @Test
  public void testNonCodeUsagesAreListedWithTheConflicts() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("A.java", """
      class A {
        void <caret>foo() {}

        void use() { foo(); }

        void bar() { /* foo is old */ }
      }
      """);

    EdtTestUtil.runInEdtAndWait(() -> {
      try {
        SafeDeleteHandler.invoke(myFixture.getProject(), new PsiElement[]{myFixture.getElementAtCaret()}, true);
        fail("The usage of the method is a conflict");
      }
      catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
        List<String> messages = new ArrayList<>(e.getMessages());
        assertEquals(2, messages.size(), messages.toString());
      }
    });
  }

  /** As in the legacy dialog, the options of one element are kept by the delegate of its language, not by the common settings. */
  @Test
  public void testSearchOptionsOfOneElementAreKeptByItsDelegate() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("A.java", WITH_NON_CODE_USAGES);
    ModPlanQuestion.Storage storage = searchStorageOf(ReadAction.computeBlocking(() -> List.of(myFixture.getElementAtCaret())));
    JavaRefactoringSettings javaSettings = JavaRefactoringSettings.getInstance();
    RefactoringSettings commonSettings = RefactoringSettings.getInstance();
    boolean oldJava = javaSettings.RENAME_SEARCH_IN_COMMENTS_FOR_METHOD;
    boolean oldNonJava = commonSettings.SAFE_DELETE_SEARCH_IN_NON_JAVA;
    try {
      javaSettings.RENAME_SEARCH_IN_COMMENTS_FOR_METHOD = false;
      assertFalse(ReadAction.computeBlocking(() -> storage.load(ModPlanOptionValues.EMPTY)).get(ModPlanSafeDeleteOptions.SEARCH_IN_COMMENTS));

      ReadAction.runBlocking(() -> storage.store(
        ModPlanOptionValues.EMPTY.with(ModPlanSafeDeleteOptions.SEARCH_IN_COMMENTS, true).with(ModPlanSafeDeleteOptions.SEARCH_IN_NON_CODE, !oldNonJava)));

      assertTrue(javaSettings.RENAME_SEARCH_IN_COMMENTS_FOR_METHOD);
      assertEquals(oldNonJava, commonSettings.SAFE_DELETE_SEARCH_IN_NON_JAVA);
    }
    finally {
      javaSettings.RENAME_SEARCH_IN_COMMENTS_FOR_METHOD = oldJava;
      commonSettings.SAFE_DELETE_SEARCH_IN_NON_JAVA = oldNonJava;
    }
  }

  /** Unlike the legacy dialog, which uses the common settings for several elements, the first element keeps the options. */
  @Test
  public void testSearchOptionsOfSeveralElementsAreKeptByTheFirstElement() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("A.java", WITH_NON_CODE_USAGES);
    ModPlanQuestion.Storage storage = searchStorageOf(
      ReadAction.computeBlocking(() -> List.of(myFixture.getElementAtCaret(), ((PsiJavaFile)myFixture.getFile()).getClasses()[0])));
    JavaRefactoringSettings javaSettings = JavaRefactoringSettings.getInstance();
    RefactoringSettings commonSettings = RefactoringSettings.getInstance();
    boolean oldJava = javaSettings.RENAME_SEARCH_IN_COMMENTS_FOR_METHOD;
    try {
      javaSettings.RENAME_SEARCH_IN_COMMENTS_FOR_METHOD = true;
      commonSettings.SAFE_DELETE_SEARCH_IN_COMMENTS = false;
      assertTrue(ReadAction.computeBlocking(() -> storage.load(ModPlanOptionValues.EMPTY)).get(ModPlanSafeDeleteOptions.SEARCH_IN_COMMENTS));

      javaSettings.RENAME_SEARCH_IN_COMMENTS_FOR_METHOD = false;
      ReadAction.runBlocking(() -> storage.store(ModPlanOptionValues.EMPTY.with(ModPlanSafeDeleteOptions.SEARCH_IN_COMMENTS, true)));

      assertTrue(javaSettings.RENAME_SEARCH_IN_COMMENTS_FOR_METHOD);
      assertFalse(commonSettings.SAFE_DELETE_SEARCH_IN_COMMENTS);
    }
    finally {
      javaSettings.RENAME_SEARCH_IN_COMMENTS_FOR_METHOD = oldJava;
    }
  }

  /** A file that is deleted with its class asks nothing about the search: the class does, so its delegate keeps the options. */
  @Test
  public void testSearchOptionsOfAFileAndItsClassAreKeptByTheDelegateOfTheClass() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("A.java", WITH_NON_CODE_USAGES);
    PsiJavaFile file = (PsiJavaFile)myFixture.getFile();
    ModPlanQuestion.Storage storage = searchStorageOf(ReadAction.computeBlocking(() -> List.of(file, file.getClasses()[0])));
    JavaRefactoringSettings javaSettings = JavaRefactoringSettings.getInstance();
    boolean oldJava = javaSettings.RENAME_SEARCH_IN_COMMENTS_FOR_CLASS;
    try {
      ReadAction.runBlocking(() -> storage.store(ModPlanOptionValues.EMPTY.with(ModPlanSafeDeleteOptions.SEARCH_IN_COMMENTS, !oldJava)));

      assertEquals(!oldJava, javaSettings.RENAME_SEARCH_IN_COMMENTS_FOR_CLASS);
    }
    finally {
      javaSettings.RENAME_SEARCH_IN_COMMENTS_FOR_CLASS = oldJava;
    }
  }

  private @NotNull ModPlanQuestion.Storage searchStorageOf(@NotNull List<PsiElement> elements) {
    List<ModPlanQuestion> questions = askedQuestionsOf(elements, (askedQuestion, currentValues) -> currentValues);
    return questions.stream()
      .filter(ModPlanQuestion.Form.class::isInstance)
      .map(ModPlanQuestion.Form.class::cast)
      .filter(question -> question.options().contains(ModPlanSafeDeleteOptions.SEARCH_IN_COMMENTS))
      .findFirst().orElseThrow().storage();
  }

  /** An element of a library is refused at once, as in the legacy flow, and not after the search for its usages. */
  @Test
  public void testElementInJarIsRefusedBeforeTheSearch() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("A.java", "class A { String s; }");

    EdtTestUtil.runInEdtAndWait(() -> {
      PsiClass string = myFixture.findClass("java.lang.String");
      try {
        SafeDeleteHandler.invoke(myFixture.getProject(), new PsiElement[]{string}, true);
        fail("The class is in a jar file");
      }
      catch (RuntimeException e) {
        assertTrue(e.getMessage().contains("String.class") && e.getMessage().contains("read-only"), e.getMessage());
      }
    });
  }

  /** The members around the deleted one keep the indent that the file has, whatever the style of the project is. */
  @Test
  public void testDeletionKeepsTheIndentOfTheNeighbours() {
    String text = """
      class A {
        public int used() { return 1; }

        void <caret>foo() {}

        private int field;
      }
      """;

    String legacyText = deleteFooWithTheFlow(text, false);
    String modPlanText = deleteFooWithTheFlow(text, true);

    assertEquals(legacyText, modPlanText);
    assertTrue(modPlanText.contains("\n  private int field;"), modPlanText);
  }

  private String deleteFooWithTheFlow(String text, boolean onModPlan) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    Registry.get("refactoring.safeDelete.modPlan").setValue(onModPlan, myFixture.getTestRootDisposable());
    myFixture.configureByText("A.java", text);
    return EdtTestUtil.runInEdtAndGet(() -> {
      // The indent of the file that the editor detected: the unit-test mode detects nothing, so the test sets what the detection finds.
      CommonCodeStyleSettings.IndentOptions detectedIndentOptions = new CommonCodeStyleSettings.IndentOptions();
      detectedIndentOptions.INDENT_SIZE = 2;
      detectedIndentOptions.TAB_SIZE = 2;
      detectedIndentOptions.CONTINUATION_INDENT_SIZE = 4;
      detectedIndentOptions.associateWithDocument(myFixture.getEditor().getDocument());
      SafeDeleteHandler.invoke(myFixture.getProject(), new PsiElement[]{myFixture.getElementAtCaret()}, true);
      return myFixture.getFile().getText();
    });
  }

  /**
   * With the base method chosen, the overriding methods of each method are asked one method at a time, after the question of the base
   * method, and the answers delete all four methods. The legacy flow asks one question for the overriding methods of both.
   */
  @Test
  public void testOverridersOfTheMethodAndOfTheBaseAreAskedOneMethodAtATime() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiMethod impl = addClassesWithOverridersOfTheMethodAndOfTheBase();
    List<ModPlanQuestion> askedQuestions = askedQuestionsOf(List.of(impl), (askedQuestion, currentValues) ->
      askedQuestion instanceof ModPlanQuestion.Custom custom && custom.options().contains(ModPlanJavaSafeDeleteOptions.DELETE_SUPER_METHODS)
      ? currentValues.with(ModPlanJavaSafeDeleteOptions.DELETE_SUPER_METHODS, true)
      : currentValues);

    assertEquals(1, customQuestionsOf(askedQuestions, ModPlanJavaSafeDeleteOptions.DELETE_SUPER_METHODS));
    assertTrue(customQuestionsOf(askedQuestions, ModPlanJavaSafeDeleteOptions.DELETE_OVERRIDERS) >= 1, askedQuestions.toString());
    ModPlanRequest request = ReadAction.computeBlocking(() -> new ModPlanRequest(
      myFixture.getProject(), List.of(SymbolPointer.of(impl)), ModPlanOptionValues.EMPTY.with(ModPlanJavaSafeDeleteOptions.DELETE_SUPER_METHODS, true)));
    ModPlan plan = inBackground(() -> ModPlanSafeDeleteRefactoring.getInstance().plan(request));
    EdtTestUtil.runInEdtAndWait(() -> {
      assertInstanceOf(ModPlanExecutionResult.Applied.class, ModPlanExecutor.execute(((ModPlan.AcceptedPlan)plan).getExecution(), true));
      for (String name : List.of("Base", "Impl", "Sub", "Other")) {
        assertThat(myFixture.findClass(name).findMethodsByName("a", false)).as(name).isEmpty();
      }
    });
  }

  /** The same case through the flow: the UI is asked about the base method, then about the overriders, one method at a time. */
  @Test
  public void testTheFlowAsksAboutTheBaseMethodThenAboutTheOverriders() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiMethod impl = addClassesWithOverridersOfTheMethodAndOfTheBase();
    List<ModPlanQuestion.Custom> askedQuestions = new ArrayList<>();
    ServiceContainerUtil.replaceService(ApplicationManager.getApplication(), ModPlanFlowUi.class, new ModPlanTestFlowUi() {
      @Override
      public @NotNull ModPlanOptionValues ask(@NotNull Project project,
                                             @NotNull String title,
                                             @NotNull ModPlanQuestion question,
                                             @NotNull ModPlanOptionValues initialValues) {
        if (!(question instanceof ModPlanQuestion.Custom custom)) return initialValues;
        askedQuestions.add(custom);
        return custom.options().contains(ModPlanJavaSafeDeleteOptions.DELETE_SUPER_METHODS)
               ? initialValues.with(ModPlanJavaSafeDeleteOptions.DELETE_SUPER_METHODS, true)
               : initialValues;
      }
    }, myFixture.getTestRootDisposable());

    EdtTestUtil.runInEdtAndWait(() -> {
      assertTrue(ModPlanSafeDeleteFlow.INSTANCE.start(myFixture.getProject(), new PsiElement[]{impl}, true, true, null, null));

      assertTrue(askedQuestions.size() >= 2, askedQuestions.toString());
      assertTrue(askedQuestions.getFirst().options().contains(ModPlanJavaSafeDeleteOptions.DELETE_SUPER_METHODS));
      for (ModPlanQuestion.Custom question : askedQuestions.subList(1, askedQuestions.size())) {
        assertTrue(question.options().contains(ModPlanJavaSafeDeleteOptions.DELETE_OVERRIDERS), askedQuestions.toString());
      }
      for (String name : List.of("Base", "Impl", "Sub", "Other")) {
        assertThat(myFixture.findClass(name).findMethodsByName("a", false)).as(name).isEmpty();
      }
    });
  }

  private @NotNull PsiMethod addClassesWithOverridersOfTheMethodAndOfTheBase() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("public class Base { public void a() {} }");
    myFixture.addClass("public class Impl extends Base { @Override public void a() {} }");
    myFixture.addClass("public class Sub extends Impl { @Override public void a() {} }");
    myFixture.addClass("public class Other extends Base { @Override public void a() {} }");
    return ReadAction.computeBlocking(() -> myFixture.findClass("Impl").getMethods()[0]);
  }

  private static long customQuestionsOf(@NotNull List<ModPlanQuestion> questions, @NotNull ModPlanOption<?> option) {
    return questions.stream().filter(question -> question instanceof ModPlanQuestion.Custom custom && custom.options().contains(option)).count();
  }

  /**
   * @param answers answers each question that the run asks, with the answers so far
   * @return the questions that a headless run of the safe delete of {@code elements} asks, in their order
   */
  private @NotNull List<ModPlanQuestion> askedQuestionsOf(@NotNull List<? extends PsiElement> elements,
                                                         @NotNull BiFunction<ModPlanQuestion, ModPlanOptionValues, ModPlanOptionValues> answers) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    List<ModPlanQuestion> askedQuestions = new ArrayList<>();
    inBackground(() -> {
      ModPlanRequest request = new ModPlanRequest(myFixture.getProject(), ContainerUtil.map(elements, SymbolPointer::of), ModPlanOptionValues.EMPTY);
      return ModPlanTestUtil.runAnswering(request, ModPlanSafeDeleteRefactoring.getInstance()::chain, (question, currentValues) -> {
        askedQuestions.add(question);
        return answers.apply(question, currentValues);
      });
    });
    return askedQuestions;
  }

  private static <T> T inBackground(@NotNull Supplier<T> action) {
    return PlatformTestUtil.waitForFuture(ApplicationManager.getApplication().executeOnPooledThread(() -> ReadAction.computeBlocking(action::get)));
  }
}
