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
import com.intellij.refactoring.modplan.ModPlanOptionValues;
import com.intellij.refactoring.modplan.ModPlanOption;
import com.intellij.refactoring.modplan.ModPlanQuestion;
import com.intellij.refactoring.modplan.impl.ModPlanFlow;
import com.intellij.refactoring.modplan.impl.ModPlanFlowUi;
import com.intellij.refactoring.modplan.impl.ModPlanTestFlowUi;
import com.intellij.refactoring.modplan.ModPlanRequest;
import com.intellij.refactoring.modplan.safeDelete.ModPlanSafeDeleteOptions;
import com.intellij.refactoring.modplan.safeDelete.ModPlanSafeDeleteRefactoring;
import com.intellij.refactoring.modplan.safeDelete.impl.ModPlanSafeDeleteFlow;
import com.intellij.refactoring.safeDelete.ModPlanJavaSafeDeleteOptions;
import com.intellij.refactoring.safeDelete.SafeDeleteHandler;
import com.intellij.testFramework.ModPlanTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * The steps of the interactive safe delete on the ModPlan engine that the unit-test mode of the legacy flow does not cover.
 */
public class InteractiveModPlanSafeDeleteTest extends LightJavaCodeInsightFixtureTestCase {
  /** The comment is not next to the method: a comment right before a member is deleted with it. */
  private static final String WITH_NON_CODE_USAGES = """
    class A {
      void <caret>foo() {}

      void bar() { /* foo is old */ }
    }
    """;

  private boolean myOldSearchInComments;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    Registry.get("refactoring.safeDelete.modPlan").setValue(true, getTestRootDisposable());
    RefactoringSettings settings = RefactoringSettings.getInstance();
    myOldSearchInComments = settings.SAFE_DELETE_SEARCH_IN_COMMENTS;
    settings.SAFE_DELETE_SEARCH_IN_COMMENTS = true;
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      RefactoringSettings.getInstance().SAFE_DELETE_SEARCH_IN_COMMENTS = myOldSearchInComments;
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  /** The legacy flow shows no conflicts for a non-code usage, and previews it, which a unit test skips: the deletion goes through. */
  public void testNonCodeUsagesAloneDoNotBlockTheDeletion() {
    myFixture.configureByText("A.java", WITH_NON_CODE_USAGES);

    SafeDeleteHandler.invoke(getProject(), new PsiElement[]{myFixture.getElementAtCaret()}, true);

    String text = myFixture.getFile().getText();
    assertFalse(text, text.contains("void foo"));
    assertTrue("The non-code usage is left as it is: " + text, text.contains("/* foo is old */"));
  }

  /** The non-code usages are shown in the preview, each with its range in the file. */
  public void testNonCodeUsagesAreShownInThePreview() {
    myFixture.configureByText("A.java", WITH_NON_CODE_USAGES);
    PsiElement method = myFixture.getElementAtCaret();

    List<UsageInfo> usages = inBackground(() -> {
      ModPlanOptionValues optionValues = ModPlanOptionValues.EMPTY.with(ModPlanSafeDeleteOptions.SEARCH_IN_COMMENTS, true);
      ModPlan.AcceptedPlan acceptedPlan = (ModPlan.AcceptedPlan)ModPlanSafeDeleteRefactoring.getInstance().plan(
        new ModPlanRequest(getProject(), List.of(SymbolPointer.of(method)), optionValues));
      return List.of(ModPlanFlow.INSTANCE.nonCodeUsages(acceptedPlan));
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
  public void testNonCodeUsagesAreListedWithTheConflicts() {
    myFixture.configureByText("A.java", """
      class A {
        void <caret>foo() {}

        void use() { foo(); }

        void bar() { /* foo is old */ }
      }
      """);

    try {
      SafeDeleteHandler.invoke(getProject(), new PsiElement[]{myFixture.getElementAtCaret()}, true);
      fail("The usage of the method is a conflict");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      List<String> messages = new ArrayList<>(e.getMessages());
      assertEquals(messages.toString(), 2, messages.size());
    }
  }

  /** As in the legacy dialog, the options of one element are kept by the delegate of its language, not by the common settings. */
  public void testSearchOptionsOfOneElementAreKeptByItsDelegate() {
    myFixture.configureByText("A.java", WITH_NON_CODE_USAGES);
    ModPlanQuestion.Storage storage = searchStorageOf(List.of(myFixture.getElementAtCaret()));
    JavaRefactoringSettings javaSettings = JavaRefactoringSettings.getInstance();
    RefactoringSettings commonSettings = RefactoringSettings.getInstance();
    boolean oldJava = javaSettings.RENAME_SEARCH_IN_COMMENTS_FOR_METHOD;
    boolean oldNonJava = commonSettings.SAFE_DELETE_SEARCH_IN_NON_JAVA;
    try {
      javaSettings.RENAME_SEARCH_IN_COMMENTS_FOR_METHOD = false;
      assertFalse(storage.load(ModPlanOptionValues.EMPTY).get(ModPlanSafeDeleteOptions.SEARCH_IN_COMMENTS));

      storage.store(
        ModPlanOptionValues.EMPTY.with(ModPlanSafeDeleteOptions.SEARCH_IN_COMMENTS, true).with(ModPlanSafeDeleteOptions.SEARCH_IN_NON_CODE, !oldNonJava));

      assertTrue(javaSettings.RENAME_SEARCH_IN_COMMENTS_FOR_METHOD);
      assertEquals(oldNonJava, commonSettings.SAFE_DELETE_SEARCH_IN_NON_JAVA);
    }
    finally {
      javaSettings.RENAME_SEARCH_IN_COMMENTS_FOR_METHOD = oldJava;
      commonSettings.SAFE_DELETE_SEARCH_IN_NON_JAVA = oldNonJava;
    }
  }

  /** Unlike the legacy dialog, which uses the common settings for several elements, the first element keeps the options. */
  public void testSearchOptionsOfSeveralElementsAreKeptByTheFirstElement() {
    myFixture.configureByText("A.java", WITH_NON_CODE_USAGES);
    ModPlanQuestion.Storage storage = searchStorageOf(List.of(myFixture.getElementAtCaret(), ((PsiJavaFile)myFixture.getFile()).getClasses()[0]));
    JavaRefactoringSettings javaSettings = JavaRefactoringSettings.getInstance();
    RefactoringSettings commonSettings = RefactoringSettings.getInstance();
    boolean oldJava = javaSettings.RENAME_SEARCH_IN_COMMENTS_FOR_METHOD;
    try {
      javaSettings.RENAME_SEARCH_IN_COMMENTS_FOR_METHOD = true;
      commonSettings.SAFE_DELETE_SEARCH_IN_COMMENTS = false;
      assertTrue(storage.load(ModPlanOptionValues.EMPTY).get(ModPlanSafeDeleteOptions.SEARCH_IN_COMMENTS));

      javaSettings.RENAME_SEARCH_IN_COMMENTS_FOR_METHOD = false;
      storage.store(ModPlanOptionValues.EMPTY.with(ModPlanSafeDeleteOptions.SEARCH_IN_COMMENTS, true));

      assertTrue(javaSettings.RENAME_SEARCH_IN_COMMENTS_FOR_METHOD);
      assertFalse(commonSettings.SAFE_DELETE_SEARCH_IN_COMMENTS);
    }
    finally {
      javaSettings.RENAME_SEARCH_IN_COMMENTS_FOR_METHOD = oldJava;
    }
  }

  /** A file that is deleted with its class asks nothing about the search: the class does, so its delegate keeps the options. */
  public void testSearchOptionsOfAFileAndItsClassAreKeptByTheDelegateOfTheClass() {
    myFixture.configureByText("A.java", WITH_NON_CODE_USAGES);
    PsiJavaFile file = (PsiJavaFile)myFixture.getFile();
    ModPlanQuestion.Storage storage = searchStorageOf(List.of(file, file.getClasses()[0]));
    JavaRefactoringSettings javaSettings = JavaRefactoringSettings.getInstance();
    boolean oldJava = javaSettings.RENAME_SEARCH_IN_COMMENTS_FOR_CLASS;
    try {
      storage.store(ModPlanOptionValues.EMPTY.with(ModPlanSafeDeleteOptions.SEARCH_IN_COMMENTS, !oldJava));

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
  public void testElementInJarIsRefusedBeforeTheSearch() {
    myFixture.configureByText("A.java", "class A { String s; }");
    PsiClass string = myFixture.findClass("java.lang.String");

    try {
      SafeDeleteHandler.invoke(getProject(), new PsiElement[]{string}, true);
      fail("The class is in a jar file");
    }
    catch (RuntimeException e) {
      assertTrue(e.getMessage(), e.getMessage().contains("String.class") && e.getMessage().contains("read-only"));
    }
  }

  /** The members around the deleted one keep the indent that the file has, whatever the style of the project is. */
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
    assertTrue(modPlanText, modPlanText.contains("\n  private int field;"));
  }

  private String deleteFooWithTheFlow(String text, boolean onModPlan) {
    Registry.get("refactoring.safeDelete.modPlan").setValue(onModPlan, getTestRootDisposable());
    myFixture.configureByText("A.java", text);
    // The indent of the file that the editor detected: the unit-test mode detects nothing, so the test sets what the detection finds.
    CommonCodeStyleSettings.IndentOptions detectedIndentOptions = new CommonCodeStyleSettings.IndentOptions();
    detectedIndentOptions.INDENT_SIZE = 2;
    detectedIndentOptions.TAB_SIZE = 2;
    detectedIndentOptions.CONTINUATION_INDENT_SIZE = 4;
    detectedIndentOptions.associateWithDocument(myFixture.getEditor().getDocument());
    SafeDeleteHandler.invoke(getProject(), new PsiElement[]{myFixture.getElementAtCaret()}, true);
    return myFixture.getFile().getText();
  }

  /**
   * With the base method chosen, the overriding methods of each method are asked one method at a time, after the question of the base
   * method, and the answers delete all four methods. The legacy flow asks one question for the overriding methods of both.
   */
  public void testOverridersOfTheMethodAndOfTheBaseAreAskedOneMethodAtATime() {
    PsiMethod impl = addClassesWithOverridersOfTheMethodAndOfTheBase();
    List<ModPlanQuestion> askedQuestions = askedQuestionsOf(List.of(impl), (askedQuestion, currentValues) ->
      askedQuestion instanceof ModPlanQuestion.Custom custom && custom.options().contains(ModPlanJavaSafeDeleteOptions.DELETE_SUPER_METHODS)
      ? currentValues.with(ModPlanJavaSafeDeleteOptions.DELETE_SUPER_METHODS, true)
      : currentValues);

    assertEquals(1, customQuestionsOf(askedQuestions, ModPlanJavaSafeDeleteOptions.DELETE_SUPER_METHODS));
    assertTrue(askedQuestions.toString(), customQuestionsOf(askedQuestions, ModPlanJavaSafeDeleteOptions.DELETE_OVERRIDERS) >= 1);
    ModPlanRequest request = ReadAction.computeBlocking(() -> new ModPlanRequest(
      getProject(), List.of(SymbolPointer.of(impl)), ModPlanOptionValues.EMPTY.with(ModPlanJavaSafeDeleteOptions.DELETE_SUPER_METHODS, true)));
    ModPlan plan = inBackground(() -> ModPlanSafeDeleteRefactoring.getInstance().plan(request));
    assertInstanceOf(ModPlanExecutor.execute(((ModPlan.AcceptedPlan)plan).getExecution(), true),
                     ModPlanExecutionResult.Applied.class);
    for (String name : List.of("Base", "Impl", "Sub", "Other")) {
      assertEmpty(name, List.of(myFixture.findClass(name).findMethodsByName("a", false)));
    }
  }

  /** The same case through the flow: the UI is asked about the base method, then about the overriders, one method at a time. */
  public void testTheFlowAsksAboutTheBaseMethodThenAboutTheOverriders() {
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
    }, getTestRootDisposable());

    assertTrue(ModPlanSafeDeleteFlow.INSTANCE.start(getProject(), new PsiElement[]{impl}, true, true, null, null));

    assertTrue(askedQuestions.toString(), askedQuestions.size() >= 2);
    assertTrue(askedQuestions.getFirst().options().contains(ModPlanJavaSafeDeleteOptions.DELETE_SUPER_METHODS));
    for (ModPlanQuestion.Custom question : askedQuestions.subList(1, askedQuestions.size())) {
      assertTrue(askedQuestions.toString(), question.options().contains(ModPlanJavaSafeDeleteOptions.DELETE_OVERRIDERS));
    }
    for (String name : List.of("Base", "Impl", "Sub", "Other")) {
      assertEmpty(name, List.of(myFixture.findClass(name).findMethodsByName("a", false)));
    }
  }

  private @NotNull PsiMethod addClassesWithOverridersOfTheMethodAndOfTheBase() {
    myFixture.addClass("public class Base { public void a() {} }");
    myFixture.addClass("public class Impl extends Base { @Override public void a() {} }");
    myFixture.addClass("public class Sub extends Impl { @Override public void a() {} }");
    myFixture.addClass("public class Other extends Base { @Override public void a() {} }");
    return myFixture.findClass("Impl").getMethods()[0];
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
    List<ModPlanQuestion> askedQuestions = new ArrayList<>();
    inBackground(() -> {
      ModPlanRequest request = new ModPlanRequest(getProject(), ContainerUtil.map(elements, SymbolPointer::of), ModPlanOptionValues.EMPTY);
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
