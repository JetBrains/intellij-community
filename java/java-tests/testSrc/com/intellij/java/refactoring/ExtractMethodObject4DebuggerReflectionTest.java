// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring;

import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.JavaCodeFragment;
import com.intellij.psi.JavaCodeFragmentFactory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.refactoring.extractMethod.PrepareFailedException;
import com.intellij.refactoring.extractMethodObject.ExtractLightMethodObjectHandler;
import com.intellij.refactoring.extractMethodObject.LightMethodObjectExtractedData;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightJavaCodeInsightTestCase;
import com.intellij.testFramework.UsefulTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vitaliy.Bibaev
 */
public class ExtractMethodObject4DebuggerReflectionTest extends LightJavaCodeInsightTestCase {

  @Override
  protected Sdk getProjectJDK() {
    return IdeaTestUtil.getMockJdk21();
  }

  public void testAccessField() {
    doTest("System.out.println(instance.field)");
  }

  public void testUpdateField() {
    doTest("instance.field = 50");
  }

  public void testAccessAndUpdateField() {
    doTest("instance.field = instance.field + 1");
  }

  public void testAccessConstructor() {
    doTest("new WithReflectionAccess(50)");
  }

  public void testAccessMethod() {
    doTest("method()");
  }

  public void testAccessMethodReference() {
    doTest("apply(WithReflectionAccess::method)");
  }

  public void testTwiceAccessToTheSameField() {
    doTest("instance.field + instance.field");
  }

  public void testMethodWithParameter() {
    doTest("instance.method(instance)");
  }

  public void testMethodWithPrimitiveParameter() {
    doTest("instance.method(42)");
  }

  public void testPublicMethodInPrivateClass() {
    doTest("privateList.size()", "/PublicMethodInPrivateClass.java");
  }

  public void testStaticMethodInPrivateClass() {
    doTest("privateThread.activeCount()", "/PublicMethodInPrivateClass.java");
  }

  public void testOverloadedMethodInPrivateClass() {
    doTest("privateList.remove(Integer.valueOf(1))", "/PublicMethodInPrivateClass.java");
  }

  public void testOverriddenMethodWithNarrowerThrows() {
    doTest("privateInputStream.read()", "/PublicMethodInPrivateClass.java");
  }

  public void testCallDefaultConstructor() {
    doTest("new Inner()");
  }

  public void testPrivateTypeParameterBound() {
    doTest("value", "/PrivateTypeParameter.java");
  }

  public void testPrivateGenericTypeParameterBound() {
    doTest("value", "/PrivateGenericTypeParameter.java");
  }

  public void testPrivateWildcardTypeParameterBound() {
    doTest("value", "/PrivateWildcardTypeParameter.java");
  }

  public void testDefaultPackageTypeParameterBound() {
    doTest("value", "/DefaultPackageTypeParameter.java");
  }

  public void testLanguageLevelImplicitClasses() {
    IdeaTestUtil.withLevel(getModule(), JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.getStandardLevel(), () -> {
      String testName = getTestName(false);
      String pathToSource = "/" + testName + ".java";
      try {
        doTest("reader", pathToSource);
      }
      catch (PrepareFailedException e) {
        throw new RuntimeException(e);
      }
    });
  }

  public void testStaticMethodImport() {
    doTest("emptyList()", "/StaticImports.java", true);
  }

  public void testStaticClassImport() {
    doTest("Entry.comparingByKey()", "/StaticImports.java", true);
  }

  public void testStaticWildcardMethodImport() {
    doTest("emptyList()", "/StaticWildcardImports.java", true);
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return PathManagerEx.getCommunityHomePath() + "/java/java-tests/testData/refactoring/extractMethodObject4Debugger";
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
    String testName = getTestName(true);
    configureByFile(pathToSource);
    final int offset = getEditor().getCaretModel().getOffset();
    final PsiElement context = getFile().findElementAt(offset);
    final JavaCodeFragmentFactory fragmentFactory = JavaCodeFragmentFactory.getInstance(getProject());
    final JavaCodeFragment fragment = fragmentFactory.createExpressionCodeFragment(evaluatedText, context, null, false);
    final LightMethodObjectExtractedData extractedData =
      ExtractLightMethodObjectHandler.extractLightMethodObject(getProject(), context, fragment, "test", JavaSdkVersion.JDK_1_9);
    assertNotNull(extractedData);
    assertFalse(extractedData.useMagicAccessor());
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
    UsefulTestCase.assertSameLinesWithFile(getTestDataPath() + "/outs/" + testName + ".out", actualText, true);
  }
}
