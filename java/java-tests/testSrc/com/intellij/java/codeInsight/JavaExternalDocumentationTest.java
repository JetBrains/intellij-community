// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.ide.BuiltInServerManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class JavaExternalDocumentationTest {
  private static final LightProjectDescriptor MY_DESCRIPTOR = new DefaultLightProjectDescriptor() {
    @Override
    public void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry) {
      super.configureModule(module, model, contentEntry);
      final VirtualFile libClasses = JavaDocumentationTestUtil.getJarFile("library.jar");
      final VirtualFile libJavadocJar = JavaDocumentationTestUtil.getJarFile("library-javadoc.jar");
      PsiTestUtil.newLibrary("myLib").classesRoot(libClasses).javaDocRoot(libJavadocJar).addTo(model);
    }
  };

  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(MY_DESCRIPTOR);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  public static final Pattern BASE_URL_PATTERN = Pattern.compile("<base href=\"([^\"]*)");
  public static final Pattern LOCALHOST_URL_PATTERN = Pattern.compile("https?://localhost:\\d+/([^\"']*)");
  public static final Pattern IMG_URL_PATTERN = Pattern.compile("<img src=\"([^\"]*)");

  @BeforeEach
  void setUp() {
    BuiltInServerManager.getInstance().waitForStart();
  }

  @Test
  public void testImagesInsideJavadocJar() throws Exception {
    Project project = codeInsightFixture.get().getProject();
    String text = EdtTestUtil.runInEdtAndGet(
      () -> JavaDocumentationTestUtil.getDocumentationText(project, "class Foo { com.jetbrains.<caret>Test field; }"));
    Matcher baseUrlMatcher = BASE_URL_PATTERN.matcher(text);
    assertTrue(baseUrlMatcher.find());
    String baseUrl = baseUrlMatcher.group(1);
    Matcher imgMatcher = IMG_URL_PATTERN.matcher(text);
    assertTrue(imgMatcher.find());
    String relativeUrl = imgMatcher.group(1);

    URL imageUrl = new URL(new URL(baseUrl), relativeUrl);
    URLConnection connection = imageUrl.openConnection();
    BuiltInServerManager.getInstance().configureRequestToWebServer(connection);
    try (InputStream stream = connection.getInputStream()) {
      assertEquals(228, FileUtil.loadBytes(stream).length);
    }
  }

  // We're guessing style of references in javadoc by bytecode version of library class file
  // but displaying quick doc should work even if javadoc was generated using a JDK not corresponding to bytecode version
  @Test
  public void testReferenceStyleDoesntMatchBytecodeVersion() {
    doTest("@com.jetbrains.TestAnnotation(<caret>param = \"foo\") class Foo {}");
  }

  @Test
  public void testLinkWithReference() {
    doTest("class Foo { com.jetbrains.<caret>ClassWithRefLink field;}");
  }

  @Test
  public void testLinkToPackageSummaryWithReference() {
    doTest("class Foo implements com.jetbrains.<caret>SimpleInterface {}");
  }

  @Test
  public void testLinkBetweenMethods() {
    doTest("class Foo {{ new com.jetbrains.LinkBetweenMethods().<caret>m1(); }}");
  }

  @Test
  public void testEscapingLink() {
    doTest("class Foo {{ new com.jetbrains.GenericClass().<caret>genericMethod(null); }}");
  }

  private void doTest(String text) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    String actualText = EdtTestUtil.runInEdtAndGet(() -> JavaDocumentationTestUtil.getDocumentationText(myFixture.getProject(), text));
    PlatformTestUtil.assertSameLinesWithFile(getDataFile(myFixture.getTestName(false) + ".html").toString(), 
                                             replaceLocalHostUrlsWithPlaceholder(actualText));
  }

  private static String replaceLocalHostUrlsWithPlaceholder(String actualText) {
    return LOCALHOST_URL_PATTERN.matcher(actualText).replaceAll("placeholder");
  }



  private static File getDataFile(String name) {
    return new File(JavaTestUtil.getJavaTestDataPath() + "/codeInsight/documentation/" + name);
  }

}
