// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring;

import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.pom.java.JavaFeature;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaCodeFragment;
import com.intellij.psi.JavaCodeFragmentFactory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.refactoring.extractMethod.PrepareFailedException;
import com.intellij.refactoring.extractMethodObject.ExtractLightMethodObjectHandler;
import com.intellij.refactoring.extractMethodObject.LightMethodObjectExtractedData;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaDescriptorOfTestName;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author Vitaliy.Bibaev
 */
@TestApplication
public class ExtractMethodObject4DebuggerReflectionTest {
  private static final LightProjectDescriptor DESCRIPTOR = lightJavaTestDescriptor(LanguageLevel.HIGHEST, () -> IdeaTestUtil.getMockJdk21());
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(DESCRIPTOR);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture =
    lightJavaCodeInsightFixture(projectFixture, lightJavaDescriptorOfTestName(DESCRIPTOR),
                                "/java/java-tests/testData/refactoring/extractMethodObject4Debugger");

  @Test
  public void testAccessField() {
    doTest("System.out.println(instance.field)");
  }

  @Test
  public void testUpdateField() {
    doTest("instance.field = 50");
  }

  @Test
  public void testAccessAndUpdateField() {
    doTest("instance.field = instance.field + 1");
  }

  @Test
  public void testAccessConstructor() {
    doTest("new WithReflectionAccess(50)");
  }

  @Test
  public void testAccessMethod() {
    doTest("method()");
  }

  @Test
  public void testAccessMethodReference() {
    doTest("apply(WithReflectionAccess::method)");
  }

  @Test
  public void testTwiceAccessToTheSameField() {
    doTest("instance.field + instance.field");
  }

  @Test
  public void testMethodWithParameter() {
    doTest("instance.method(instance)");
  }

  @Test
  public void testMethodWithPrimitiveParameter() {
    doTest("instance.method(42)");
  }

  @Test
  public void testPublicMethodInPrivateClass() {
    doTest("privateList.size()", "/PublicMethodInPrivateClass.java");
  }

  @Test
  public void testStaticMethodInPrivateClass() {
    doTest("privateThread.activeCount()", "/PublicMethodInPrivateClass.java");
  }

  @Test
  public void testOverloadedMethodInPrivateClass() {
    doTest("privateList.remove(Integer.valueOf(1))", "/PublicMethodInPrivateClass.java");
  }

  @Test
  public void testOverriddenMethodWithNarrowerThrows() {
    doTest("privateInputStream.read()", "/PublicMethodInPrivateClass.java");
  }

  @Test
  public void testCallDefaultConstructor() {
    doTest("new Inner()");
  }

  @Test
  public void testPrivateTypeParameterBound() {
    doTest("value", "/PrivateTypeParameter.java");
  }

  @Test
  public void testPrivateGenericTypeParameterBound() {
    doTest("value", "/PrivateGenericTypeParameter.java");
  }

  @Test
  public void testPrivateWildcardTypeParameterBound() {
    doTest("value", "/PrivateWildcardTypeParameter.java");
  }

  @Test
  public void testThisExpressionWithPrivateType() {
    doTest("this", "/PrivateThisExpression.java");
  }

  @Test
  public void testSuperMethodWithJava6() {
    doSuperMethodTestForLegacyJavaVersions(JavaSdkVersion.JDK_1_6, false);
  }

  @Test
  public void testSuperMethodWithJava6MagicAccessor() {
    doSuperMethodTestForLegacyJavaVersions(JavaSdkVersion.JDK_1_6, true);
  }

  @Test
  public void testSuperMethodWithJava7() {
    doSuperMethodTestForLegacyJavaVersions(JavaSdkVersion.JDK_1_7, false);
  }

  /**
   * Compares the complete generated super call and class with the expected output when the reflection registry flag is enabled.
   * Java 6 keeps direct calls without Java 7 APIs. Java 7 uses method handles.
   * Checks that extraction preserves the magic accessor setting.
   */
  private void doSuperMethodTestForLegacyJavaVersions(@NotNull JavaSdkVersion javaVersion, boolean useMagicAccessor) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    Registry.get("debugger.compiling.evaluator.reflection.access.with.java8").setValue(true, myFixture.getTestRootDisposable());
    Registry.get("debugger.compiling.evaluator.magic.accessor").setValue(useMagicAccessor, myFixture.getTestRootDisposable());
    myFixture.configureByFile("/PrivateSuperMethod.java");
    myFixture.runInCommand(() -> {
      var extractedData = extract("super.hashCode()", javaVersion);
      assertEquals(useMagicAccessor, extractedData.useMagicAccessor());
      checkResult(extractedData, false);
    });
  }

  @Test
  public void testDefaultPackageTypeParameterBound() {
    doTest("value", "/DefaultPackageTypeParameter.java");
  }

  @Test
  public void testLanguageLevelImplicitClasses() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.getStandardLevel(), () -> {
      String testName = myFixture.getTestName(false);
      String pathToSource = "/" + testName + ".java";
      try {
        doTest("reader", pathToSource);
      }
      catch (PrepareFailedException e) {
        throw new RuntimeException(e);
      }
    });
  }

  @Test
  public void testStaticMethodImport() {
    doTest("emptyList()", "/StaticImports.java", true);
  }

  @Test
  public void testStaticClassImport() {
    doTest("Entry.comparingByKey()", "/StaticImports.java", true);
  }

  @Test
  public void testStaticWildcardMethodImport() {
    doTest("emptyList()", "/StaticWildcardImports.java", true);
  }

  private void doTest(@NotNull String evaluatedText) throws PrepareFailedException {
    String path = "/WithReflectionAccess.java";
    doTest(evaluatedText, path);
  }

  private void doTest(@NotNull String evaluatedText, @NotNull String pathToSource) throws PrepareFailedException {
    doTest(evaluatedText, pathToSource, false);
  }

  private void doTest(@NotNull String evaluatedText,
                      @NotNull String pathToSource,
                      boolean includeImports) throws PrepareFailedException {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(pathToSource);
    myFixture.runInCommand(() -> {
      var extractedData = extract(evaluatedText, JavaSdkVersion.JDK_1_9);
      assertFalse(extractedData.useMagicAccessor());
      checkResult(extractedData, includeImports);
    });
  }

  private void checkResult(@NotNull LightMethodObjectExtractedData extractedData, boolean includeImports) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    var testName = myFixture.getTestName(true);
    String importsText = "";
    if (includeImports) {
      PsiJavaFile generatedFile = (PsiJavaFile)extractedData.getGeneratedInnerClass().getContainingFile();
      assertNotNull(generatedFile.getImportList());
      String imports = generatedFile.getImportList().getText().trim();
      importsText = "imports: " + (imports.isEmpty() ? "<none>" : imports) + "\n";
    }
    String actualText = importsText + "call text: " + extractedData.getGeneratedCallText() + "\n" +
                        "class: " + "\n" +
                        extractedData.getGeneratedInnerClass().getText();
    PlatformTestUtil.assertSameLinesWithFile(myFixture.getTestDataPath() + "/outs/" + testName + ".out", actualText, true);
  }

  private @NotNull LightMethodObjectExtractedData extract(@NotNull String evaluatedText, @NotNull JavaSdkVersion javaVersion) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final int offset = myFixture.getEditor().getCaretModel().getOffset();
    final PsiElement context = myFixture.getFile().findElementAt(offset);
    final JavaCodeFragmentFactory fragmentFactory = JavaCodeFragmentFactory.getInstance(myFixture.getProject());
    final JavaCodeFragment fragment = fragmentFactory.createExpressionCodeFragment(evaluatedText, context, null, false);
    final LightMethodObjectExtractedData extractedData =
      ExtractLightMethodObjectHandler.extractLightMethodObject(myFixture.getProject(), context, fragment, "test", javaVersion);
    assertNotNull(extractedData);
    return extractedData;
  }
}
