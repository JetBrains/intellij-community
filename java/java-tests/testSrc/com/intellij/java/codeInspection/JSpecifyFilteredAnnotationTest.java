// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.LanguageLevelModuleExtension;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.platform.testFramework.core.FileComparisonFailedError;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

/**
 * The goal of this test is to check that IDEA supports popular cases of JSpecify annotations and doesn't have regressions.
 * This test contains a set of filters, which are used to filter out some cases that are not supported by IDEA or aren't expected to be supported.
 */
@RunWith(Parameterized.class)
public class JSpecifyFilteredAnnotationTest extends LightJavaCodeInsightFixtureTestCase {

  private static final String PACKAGE_NAME = "org.jspecify.annotations";
  private static final Path PATH = Paths.get(JavaTestUtil.getJavaTestDataPath(), "/inspection/dataFlow/jspecify/");
  private static final JSpecifyCorpus.Statistic STATISTIC = new JSpecifyCorpus.Statistic(new LongAdder(), new LongAdder(), new LongAdder(), MultiMap.create());

  @Parameterized.Parameter
  public String myFileName;

  private static final List<JSpecifyCorpus.ErrorFilter> FILTERS = List.of(
    new JSpecifyCorpus.SkipErrorFilter("jspecify_nullness_not_enough_information"), //it is useless for our goals
    new JSpecifyCorpus.SkipIndividuallyFilter(JSpecifyCorpus.SKIPPED_PLACES)
  );

  private static final LightProjectDescriptor PROJECT_DESCRIPTOR = new DefaultLightProjectDescriptor() {
    @Override
    public Sdk getSdk() {
      return IdeaTestUtil.getMockJdk18();
    }

    @Override
    public void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry) {
      model.getModuleExtension(LanguageLevelModuleExtension.class).setLanguageLevel(LanguageLevel.JDK_1_8);
    }
  };

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    // Cannot share project descriptor with previous test, as NullableNotNullManager caches the supported annotations
    // so it won't be updated after Registry.setValue()
    return PROJECT_DESCRIPTOR;
  }

  @Before
  public void before() {
    mockAnnotations();
  }

  private void mockAnnotations() {
    String template = "package " + PACKAGE_NAME + ";import java.lang.annotation.*;\n\n@Target(%s)public @interface %s {}";
    myFixture.addClass(String.format(Locale.ROOT, template, "ElementType.TYPE_USE", "NonNull"));
    myFixture.addClass(String.format(Locale.ROOT, template, "ElementType.TYPE_USE", "Nullable"));
    myFixture.addClass(String.format(Locale.ROOT, template, "ElementType.TYPE_USE", "NullnessUnspecified"));
    myFixture.addClass(String.format(Locale.ROOT, template, "ElementType.TYPE, ElementType.PACKAGE", "NullMarked"));
    myFixture.addClass(String.format(Locale.ROOT, template, "ElementType.TYPE, ElementType.PACKAGE", "NullUnmarked"));
  }

  @Test
  public void test() throws IOException {
    Path path = PATH.resolve(myFileName);
    boolean dirMode = Files.isDirectory(path);
    List<Path> files = Files.walk(path)
      .filter(p -> Files.isRegularFile(p)).filter(p -> p.getFileName().toString().endsWith(".java"))
      .toList();
    if (files.isEmpty()) {
      throw new IllegalStateException("No Java files");
    }
    List<FileData> fileData = new ArrayList<>();
    for (Path file : files) {
      String fileText = Files.readString(file).replace("\r\n", "\n");
      fileText = JSpecifyCorpus.TEST_CANNOT_CONVERT.matcher(fileText).replaceAll("// jspecify_nullness_mismatch");
      String stripped = JSpecifyCorpus.JSPECIFY_PATTERN.matcher(fileText).replaceAll("");
      String relativeFile = FileUtil.toSystemIndependentName((dirMode ? path.relativize(file) : file.getFileName()).toString());
      PsiFile psiFile = myFixture.addFileToProject(relativeFile, stripped);
      fileData.add(new FileData(file, fileText, stripped, psiFile, JSpecifyCorpus.createErrorContainer(fileText)));
    }
    for (FileData data : fileData) {
      String fileText = getExpectedTest(data);
      String stripped = data.stripped;
      PsiFile file = data.psiFile;

      ReadAction.run(() -> {
        Map<PsiElement, String> actual = JSpecifyCorpus.collectMarkers(getProject(), file);
        Map<Integer, String> byOffset = new LinkedHashMap<>();
        actual.forEach((anchor, message) -> byOffset.put(anchor.getTextRange().getStartOffset(), message));
        String actualText = JSpecifyCorpus.getActualText(file.getName(), byOffset, stripped, FILTERS);
        if (!FILTERS.isEmpty()) {
          Assert.assertEquals("Messages don't match (" + data.path.getFileName().toString() + ")", fileText, actualText);
        }
        if (FILTERS.isEmpty() && !fileText.equals(actualText)) {
          throw new FileComparisonFailedError("Messages don't match (" + data.path.getFileName().toString() + ")",
                                              fileText, actualText, data.path.toString());
        }
      });
    }
  }

  @AfterClass
  public static void reportUnusedFilters() {
    FILTERS.forEach(JSpecifyCorpus.ErrorFilter::reportUnused);
  }

  @AfterClass
  public static void afterClass() {
    System.out.println(STATISTIC);
  }

  @Parameterized.Parameters(name = "{0}")
  public static List<String> getData() throws IOException {
    return Files.list(PATH).filter(f -> !f.getFileName().toString().startsWith("."))
      .filter(f -> Files.isDirectory(f) || f.toString().endsWith(".java"))
      .map(PATH::relativize).map(Path::toString).collect(Collectors.toList());
  }

  @NotNull
  private static String getExpectedTest(@NotNull FileData data) {
    String restoredText = JSpecifyCorpus.getExpectedText(data.psiFile.getName(), data.path.toString(), data.stripped, data.errorContainer,
                                          FILTERS, STATISTIC);
    if (JSpecifyFilteredAnnotationTest.FILTERS.isEmpty()) {
      Assert.assertEquals("incorrect restored file", data.fileText, restoredText);
    }
    return restoredText;
  }

  private record FileData(Path path, String fileText, String stripped, PsiFile psiFile, JSpecifyCorpus.ErrorContainer errorContainer) {
  }

}
