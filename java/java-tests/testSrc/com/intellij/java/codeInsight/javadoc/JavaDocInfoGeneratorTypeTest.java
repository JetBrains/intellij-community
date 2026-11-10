// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.javadoc;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.ExternalAnnotationLineMarkerProvider;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.javadoc.AnnotationDocGenerator;
import com.intellij.codeInsight.javadoc.NonCodeAnnotationGenerator;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileTypes.BinaryFileTypeDecompilers;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.platform.testFramework.core.FileComparisonFailedError;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.impl.compiled.ClsClassImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestDataPath("$CONTENT_ROOT/testData/codeInsight/javadocIG/")
@TestApplication
public class JavaDocInfoGeneratorTypeTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_26);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(JavaTestUtil.getJavaTestDataPath());
  }

  private static final String TEST_DATA_FOLDER = "/codeInsight/javadocIG/generatedTooltip/";

  @Test
  public void testOptionalCountClassTypeParameter() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    Project project = myFixture.getProject();
    JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    PsiClass optionalClass = ReadAction.computeBlocking(() -> facade.findClass(CommonClassNames.JAVA_UTIL_OPTIONAL, GlobalSearchScope.allScope(project)));
    PsiFile containingFile = optionalClass.getContainingFile();
    myFixture.configureFromExistingVirtualFile(containingFile.getVirtualFile());
    ReadAction.runBlocking(() -> {
      MultiMap<PsiElement, AnnotationDocGenerator> annotations = NonCodeAnnotationGenerator.getSignatureNonCodeAnnotations(optionalClass);
      assertEquals(1, annotations.size());
    });
  }

  @Test
  public void testOptionalOrTypeParameter() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    Project project = myFixture.getProject();
    JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    PsiClass optionalClass = ReadAction.computeBlocking(() -> facade.findClass(CommonClassNames.JAVA_UTIL_OPTIONAL, GlobalSearchScope.allScope(project)));
    PsiFile containingFile = optionalClass.getContainingFile();
    myFixture.configureFromExistingVirtualFile(containingFile.getVirtualFile());

    ReadAction.runBlocking(() -> {
      PsiMethod[] ors = optionalClass.findMethodsByName("or", false);
      assertEquals(1, ors.length);

      MultiMap<PsiElement, AnnotationDocGenerator> annotations = NonCodeAnnotationGenerator.getSignatureNonCodeAnnotations(ors[0]);
      // Method -> Contract
      // Method return type (Optional) -> NotNull
      // Parameter type (Supplier) -> NotNull
      // Parameter type argument (Optional) -> NotNull
      assertEquals(4, annotations.size());
    });
  }

  @Test
  public void testComparatorNaturalOrderTypeParameter() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    Project project = myFixture.getProject();
    JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    PsiClass comparatorClass = ReadAction.computeBlocking(() -> facade.findClass(CommonClassNames.JAVA_UTIL_COMPARATOR, GlobalSearchScope.allScope(project)));
    PsiFile containingFile = comparatorClass.getContainingFile();
    myFixture.configureFromExistingVirtualFile(containingFile.getVirtualFile());

    ReadAction.runBlocking(() -> {
      PsiMethod[] ors = comparatorClass.findMethodsByName("naturalOrder", false);
      assertEquals(1, ors.length);

      MultiMap<PsiElement, AnnotationDocGenerator> annotations = NonCodeAnnotationGenerator.getSignatureNonCodeAnnotations(ors[0]);
      // method -> Contract
      // method return type -> NotNull
      // parameter type -> NotNull
      assertEquals(3, annotations.size());
    });
  }

  @Test
  public void testComparatorNaturalOrderText() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    Project project = myFixture.getProject();
    JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    ClsClassImpl comparatorClass =
      ReadAction.computeBlocking(() -> (ClsClassImpl)facade.findClass(CommonClassNames.JAVA_UTIL_COMPARATOR, GlobalSearchScope.allScope(project)));
    PsiClass mirrorClass =
      ReadAction.computeBlocking(() -> BinaryFileTypeDecompilers.getInstance().allowDecompilerSlowOperation(() -> ((PsiClass)comparatorClass.getMirror())));

    PsiFile containingFile = comparatorClass.getContainingFile();
    myFixture.configureFromExistingVirtualFile(containingFile.getVirtualFile());

    ReadAction.runBlocking(() -> {
      PsiMethod[] mirrorMethods = mirrorClass.findMethodsByName("naturalOrder", false);
      assertEquals(1, mirrorMethods.length);

      List<LineMarkerInfo> infos = new ArrayList<>();
      new ExternalAnnotationLineMarkerProvider().collectSlowLineMarkers(List.of(mirrorMethods[0].getNameIdentifier()), infos);

      assertEquals(1, infos.size());

      String tooltip = infos.getFirst().getLineMarkerTooltip();
      assertFileTextEquals(tooltip);
    });
  }

  @Test
  public void testOptionalClassText() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    Project project = myFixture.getProject();
    JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    ReadAction.runBlocking(() -> {
      ClsClassImpl optionalClass = (ClsClassImpl)facade.findClass(CommonClassNames.JAVA_UTIL_OPTIONAL, GlobalSearchScope.allScope(project));
      PsiClass mirrorClass = BinaryFileTypeDecompilers.getInstance().allowDecompilerSlowOperation(() -> ((PsiClass)optionalClass.getMirror()));

      List<LineMarkerInfo> infos = new ArrayList<>();
      new ExternalAnnotationLineMarkerProvider().collectSlowLineMarkers(List.of(mirrorClass.getNameIdentifier()), infos);

      assertEquals(1, infos.size());

      String tooltip = infos.getFirst().getLineMarkerTooltip();
      assertFileTextEquals(tooltip);
    });
  }

  @Test
  public void testOptionalOrClassText() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    Project project = myFixture.getProject();
    JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    ReadAction.runBlocking(() -> {
      ClsClassImpl optionalClass = (ClsClassImpl)facade.findClass(CommonClassNames.JAVA_UTIL_OPTIONAL, GlobalSearchScope.allScope(project));
      PsiClass mirrorClass = BinaryFileTypeDecompilers.getInstance().allowDecompilerSlowOperation(() -> ((PsiClass)optionalClass.getMirror()));

      PsiMethod[] mirrorMethods = mirrorClass.findMethodsByName("or", false);
      assertEquals(1, mirrorMethods.length);

      List<LineMarkerInfo> infos = new ArrayList<>();
      new ExternalAnnotationLineMarkerProvider().collectSlowLineMarkers(List.of(mirrorMethods[0].getNameIdentifier()), infos);

      assertEquals(1, infos.size());

      String tooltip = infos.getFirst().getLineMarkerTooltip();
      assertFileTextEquals(tooltip);
    });
  }

  private void assertFileTextEquals(String docInfo) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    assertFileTextEquals(docInfo, myFixture.getTestName(true) + ".html");
  }

  private void assertFileTextEquals(String docInfo, String expectedFile) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    assertEqualsFileText(myFixture.getTestDataPath() + TEST_DATA_FOLDER + expectedFile, docInfo);
  }

  static void assertEqualsFileText(@NotNull String expectedFile, @NotNull String actual) {
    String actualText = replaceEnvironmentDependentContent(actual).replaceAll("[ \t]+\\n", "\n");
    File htmlPath = new File(expectedFile);
    String expectedText = loadFile(htmlPath);
    if (!StringUtil.equals(expectedText, actualText)) {
      String message = "Text mismatch in file: " + htmlPath.getName();
      throw new FileComparisonFailedError(message, expectedText, actualText, FileUtil.toSystemIndependentName(htmlPath.getPath()));
    }
  }

  private static String loadFile(File file) {
      try {
        return StringUtil.convertLineSeparators(FileUtil.loadFile(file).trim());
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

  private static String replaceEnvironmentDependentContent(String html) {
    return html != null ? StringUtil.convertLineSeparators(html.trim()).replaceAll("<base href=\"[^\"]*\">", "<base href=\"placeholder\">") : null;
  }
}