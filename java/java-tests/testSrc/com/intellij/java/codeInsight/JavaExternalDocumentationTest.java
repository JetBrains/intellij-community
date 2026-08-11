// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.testFramework.EditorTestUtil;
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

import static com.intellij.platform.backend.documentation.impl.ImplKt.computeHtmlDocBlocking;

public class JavaExternalDocumentationTest extends LightPlatformTestCase {
  private static final LightProjectDescriptor MY_DESCRIPTOR = new DefaultLightProjectDescriptor() {
    @Override
    public void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry) {
      super.configureModule(module, model, contentEntry);
      final VirtualFile libClasses = getJarFile("library.jar");
      final VirtualFile libJavadocJar = getJarFile("library-javadoc.jar");
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
    String text = getDocumentationText("class Foo { com.jetbrains.<caret>Test field; }");
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
    String actualText = getDocumentationText(text);
    assertSameLinesWithFile(getDataFile(getTestName(false) + ".html").toString(), 
                            replaceLocalHostUrlsWithPlaceholder(actualText));
  }

  private static String replaceLocalHostUrlsWithPlaceholder(String actualText) {
    return LOCALHOST_URL_PATTERN.matcher(actualText).replaceAll("placeholder");
  }



  private static File getDataFile(String name) {
    return new File(JavaTestUtil.getJavaTestDataPath() + "/codeInsight/documentation/" + name);
  }

  @NotNull
  public static VirtualFile getJarFile(String name) {
    VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(getDataFile(name));
    assertNotNull(file);
    VirtualFile jarFile = JarFileSystem.getInstance().getJarRootForLocalFile(file);
    assertNotNull(jarFile);
    return jarFile;
  }

  private String getDocumentationText(String sourceEditorText) {
    return getDocumentationText(getProject(), sourceEditorText);
  }

  public static String getDocumentationText(Project project, String sourceEditorText) {
    int caretPosition = sourceEditorText.indexOf(EditorTestUtil.CARET_TAG);
    if (caretPosition >= 0) {
      sourceEditorText = sourceEditorText.substring(0, caretPosition) +
                         sourceEditorText.substring(caretPosition + EditorTestUtil.CARET_TAG.length());
    }
    PsiFile psiFile = PsiFileFactory.getInstance(project).createFileFromText(JavaLanguage.INSTANCE, sourceEditorText);
    return getDocumentationText(psiFile, caretPosition);
  }

  public static String getDocumentationText(@NotNull PsiFile psiFile, int caretPosition) {
    Project project = psiFile.getProject();
    Document document = PsiDocumentManager.getInstance(project).getDocument(psiFile);
    assertNotNull(document);
    Editor editor = EditorFactory.getInstance().createEditor(document, project);
    try {
      if (caretPosition >= 0) {
        editor.getCaretModel().moveToOffset(caretPosition);
      }
      return getDocumentationText(editor, psiFile);
    }
    finally {
      EditorFactory.getInstance().releaseEditor(editor);
    }
  }

  public static String getDocumentationText(@NotNull Editor editor) {
    Project project = editor.getProject();
    PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    assertNotNull(psiFile);
    return getDocumentationText(editor, psiFile);
  }

  public static String getDocumentationText(@NotNull Editor editor, @NotNull PsiFile psiFile) {
    String html = computeHtmlDocBlocking(editor, psiFile);
    if (html == null) html = "";
    return DocumentationManager.decorate(html, null, null);
  }
}
