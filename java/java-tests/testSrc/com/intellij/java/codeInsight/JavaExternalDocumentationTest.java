// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.ide.BuiltInServerManager;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JavaExternalDocumentationTest extends LightPlatformTestCase {
  private static final LightProjectDescriptor MY_DESCRIPTOR = new DefaultLightProjectDescriptor() {
    @Override
    public void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry) {
      super.configureModule(module, model, contentEntry);
      final VirtualFile libClasses = JavaDocumentationTestUtil.getJarFile("library.jar");
      final VirtualFile libJavadocJar = JavaDocumentationTestUtil.getJarFile("library-javadoc.jar");
      PsiTestUtil.newLibrary("myLib").classesRoot(libClasses).javaDocRoot(libJavadocJar).addTo(model);
    }
  };

  public static final Pattern BASE_URL_PATTERN = Pattern.compile("<base href=\"([^\"]*)");
  public static final Pattern LOCALHOST_URL_PATTERN = Pattern.compile("https?://localhost:\\d+/([^\"']*)");
  public static final Pattern IMG_URL_PATTERN = Pattern.compile("<img src=\"([^\"]*)");

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    BuiltInServerManager.getInstance().waitForStart();
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return MY_DESCRIPTOR;
  }

  public void testImagesInsideJavadocJar() throws Exception {
    String text = JavaDocumentationTestUtil.getDocumentationText(getProject(), "class Foo { com.jetbrains.<caret>Test field; }");
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
  public void testReferenceStyleDoesntMatchBytecodeVersion() {
    doTest("@com.jetbrains.TestAnnotation(<caret>param = \"foo\") class Foo {}");
  }

  public void testLinkWithReference() {
    doTest("class Foo { com.jetbrains.<caret>ClassWithRefLink field;}");
  }

  public void testLinkToPackageSummaryWithReference() {
    doTest("class Foo implements com.jetbrains.<caret>SimpleInterface {}");
  }

  public void testLinkBetweenMethods() {
    doTest("class Foo {{ new com.jetbrains.LinkBetweenMethods().<caret>m1(); }}");
  }

  public void testEscapingLink() {
    doTest("class Foo {{ new com.jetbrains.GenericClass().<caret>genericMethod(null); }}");
  }

  private void doTest(String text) {
    String actualText = JavaDocumentationTestUtil.getDocumentationText(getProject(), text);
    assertSameLinesWithFile(getDataFile(getTestName(false) + ".html").toString(), 
                            replaceLocalHostUrlsWithPlaceholder(actualText));
  }

  private static String replaceLocalHostUrlsWithPlaceholder(String actualText) {
    return LOCALHOST_URL_PATTERN.matcher(actualText).replaceAll("placeholder");
  }



  private static File getDataFile(String name) {
    return new File(JavaTestUtil.getJavaTestDataPath() + "/codeInsight/documentation/" + name);
  }

}
