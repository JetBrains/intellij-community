// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.javadoc;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.JavaCodeInsightTestCase;
import com.intellij.codeInsight.daemon.impl.quickfix.JetBrainsAnnotationsExternalLibraryResolver;
import com.intellij.codeInsight.javadoc.JavaDocInfoGenerator;
import com.intellij.java.codeInsight.JavaExternalDocumentationTest;
import com.intellij.lang.java.JavaDocumentationProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.testFramework.core.FileComparisonFailedError;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.DumbModeTestUtils;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.MavenDependencyUtil;
import com.intellij.util.lang.JavaVersion;
import com.intellij.util.ui.UIUtil;
import org.intellij.lang.annotations.Flow;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class JavaDocInfoGeneratorTest extends JavaCodeInsightTestCase {
  private static final String TEST_DATA_FOLDER = "/codeInsight/javadocIG/";

  private int myJdkVersion = 7;

  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  @Override
  protected Sdk getTestProjectJdk() {
    return IdeaTestUtil.getMockJdk(JavaVersion.compose(myJdkVersion));
  }

  @Override
  protected void setUpModule() {
    super.setUpModule();
    if (!getTestName(false).equals("HideNonDocumentedFlowAnnotations")) {
      ModuleRootModificationUtil.updateModel(
        myModule, model -> MavenDependencyUtil.addFromMaven(
          model, "org.jetbrains:annotations:" + JetBrainsAnnotationsExternalLibraryResolver.getVersion()));
    }
  }

  @Override
  protected @NotNull LanguageLevel getProjectLanguageLevel() {
    return LanguageLevel.HIGHEST;
  }

  public void testSimpleField() { doTestField(); }
  public void testFieldValue() { doTestField(); }
  public void testValueInMethod() { doTestMethod(); }
  public void testValueInMethodNoHash() { doTestMethod(); }
  public void testEscapingStringValue() { doTestMethod(); }
  public void testIdeadev2326() { doTestMethod(); }
  public void testMethodTypeParameter() { doTestMethod(); }
  public void testMethodTypeParameter2() { doTestAtCaret(); }
  public void testMethodTypeParameter3() { doTestAtCaret(); }
  public void testInheritedDocInThrows() { doTestMethod(); }
  public void testInheritedDocInThrows1() { doTestMethod(); }
  public void testEscapeValues() { doTestClass(); }
  public void testClassTypeParameter() { doTestClass(); }
  public void testClassTypeParameter1() { doTestClass(); }
  public void testClassTypeParameter2() { doTestAtCaret(); }
  public void testUnicodeEscapes() { doTestClass(); }
  public void testEnumValueOf() { doTestMethod(); }
  public void testMethodFormatting() { doTestMethod(); }
  public void testConstantFieldInitializer() { doTestField(); }
  public void testInitializerWithNew() { doTestField(); }
  public void testInitializerWithLiteral() { doTestField(); }
  public void testMethodExpressionWithLiteral() { doTestField(); }
  public void testInitializerWithReference() { doTestField(); }
  public void testAnnotations() { doTestField(); }
  public void testAnnotationsInParams() { doTestMethod(); }
  public void testInferredAnnotationsOnArray() { doTestMethod(); }
  public void testInferredAnnotationsOnArrayMethod() { doTestMethod(); }
  public void testInferredAnnotationsOnArray2d() { doTestMethod(); }
  public void testTypeAnnoMultiDimArray() { doTestMethod(); }
  public void testApiNotes() { doTestMethod(); }
  public void testLiteral() { doTestField(); }
  public void testEscapingInLiteral() { doTestField(); }
  public void testCode() { useJava8(); doTestField(); }
  public void testPInsidePre() { doTestField(); }
  public void testCommaInsideArgsList() { doTestField(); }
  public void testFieldInitializedWithLambda() { doTestField(); }
  public void testFieldInitializedWithArray() { doTestField(); }
  public void testFieldInitializedWithSizedArray() { doTestField(); }
  public void testDoubleLt() { doTestClass(); }
  public void testNoSpaceAfterTagName() { doTestClass(); }
  public void testRecordParameters() { doTestClass(); } //j.l.Record is unresolved as there is no mock jdk 14 yet
  public void testLambdaParameter() { doTestLambdaParameter(); }
  public void testLocalClassInsideAnonymous() { doTestAtCaret(); }
  public void testPackageInfoFromComment() { doTestPackageInfo("some"); }
  public void testPackageInfoWithCopyright() { doTestPackageInfo("packageInfoWithCopyright"); }
  public void testHtmlLinkWithRef() { verifyJavaDoc(getTestClass()); }
  public void testMultipleSpacesInLiteral() { useJava8(); verifyJavaDoc(getTestClass()); }
  public void testLegacySpacesInLiteral() { useJava7(); verifyJavaDoc(getTestClass()); }
  public void testDocumentationForJdkClassWithReferencesToClassesFromJavaLang() { doTestAtCaret(); }
  public void testDocumentationForUncheckedExceptionsInSupers() { doTestAtCaret(); }
  public void testDocumentationForGetterByField() { doTestAtCaret(); }
  public void testParamInJavadoc() { doTestAtCaret(); }
  public void testParamInMethod() { doTestAtCaret(); }
  public void testExternalLinksInJavadoc() { doTestAtCaret(); }
  public void testLiteralInsideCode() { useJava8(); doTestClass(); }
  public void testSuperJavadocExactResolve() { doTestAtCaret(); }
  public void testSuperJavadocErasureResolve() { doTestAtCaret(); }
  public void testPackageInfo() { doTestPackageInfo(); }
  public void testPackageWithoutPackageInfo() { doTestPackageInfo(); }
  public void testPackageHtml() { doTestPackageInfo(); }
  public void testSyntheticEnumValues() { doTestAtCaret(); }
  public void testVariableDoc() { doTestAtCaret(); }
  public void testTypeAnnotation() { useJava8(); doTestAtCaret(); }
  public void testTypeAnnotationArray() { useJava8(); doTestAtCaret(); }
  public void testTypeAnnotationClass() { useJava8(); doTestClass(); }
  public void testInlineTagIndex() { useJava9(); doTestClass(); }
  public void testInlineTagSummary() { useJava10(); doTestClass(); }
  public void testLeadingSpacesInPre() { doTestClass(); }
  public void testBlockquotePre() { doTestAtCaret(); }
  public void testPreInDeprecated() { doTestClass(); }
  public void testEscapeHtmlInCode() { doTestClass(); }
  public void testEscapeAngleBracketsInCode() { doTestClass(); }
  public void testInlineTagSnippet() { doTestClass(); }
  public void testInlineTagSnippetNoMarkup() { doTestClass(); }
  public void testInlineTagSnippetWithoutBody() { doTestClass(); }
  public void testInlineTagSnippetHighlightSeveralLines() { doTestClass(); }
  public void testExternalSnippetRegion() {
    createProjectStructure(getTestDataPath() + TEST_DATA_FOLDER + "externalSnippet");
    verifyJavadocFor("Region");
  }
  public void testExternalSnippetRegionNoMarkup() {
    createProjectStructure(getTestDataPath() + TEST_DATA_FOLDER + "externalSnippet");
    verifyJavadocFor("RegionNoMarkup");
  }
  public void testExternalSnippetNoRegion() {
    createProjectStructure(getTestDataPath() + TEST_DATA_FOLDER + "externalSnippet");
    verifyJavadocFor("NoRegion");
  }
  public void testExternalSnippetMain() {
    createProjectStructure(getTestDataPath() + TEST_DATA_FOLDER + "externalSnippet");
    verifyJavadocFor("Main");
  }
  public void testExternalSnippetMultiTag() {
    createProjectStructure(getTestDataPath() + TEST_DATA_FOLDER + "externalSnippet");
    verifyJavadocFor("MultiTag");
  }
  public void testExternalSnippetTextFile() {
      createProjectStructure(getTestDataPath() + TEST_DATA_FOLDER + "externalSnippet");
      verifyJavadocFor("TextFile");
  }
  public void testExternalSnippetUnresolved() {
    createProjectStructure(getTestDataPath() + TEST_DATA_FOLDER + "externalSnippet");
    verifyJavadocFor("Unresolved");
  }
  public void testExternalSnippetMalformed() {
    createProjectStructure(getTestDataPath() + TEST_DATA_FOLDER + "externalSnippet");
    verifyJavadocFor("Malformed");
  }
  public void testUnknownInlineTag() { doTestClass(); }
  public void testUnknownInlineMultilineTag() { doTestClass(); }
  public void testUnknownTag() { doTestMethod(); }
  public void testUnknownClassTag() { doTestClass(); }
  public void testReflectConstructor() { useJava10(); doTestAtCaret(); }
  public void testMarkdownGeneralFeatures() { doTestClass(); }
  public void testMarkdownCodeBlock(){ doTestClass(); }
  public void testMarkdownReferenceLink(){ doTestClass(); }
  public void testMarkdownInheritDoc() {
    configureByFile();
    PsiClass outerClass = ((PsiJavaFile) myFile).getClasses()[1];
    verifyJavaDoc(outerClass.getMethods()[0]);
  }
  public void testMarkdownInlineWithTags(){
    configureByFile();
    PsiClass outerClass = ((PsiJavaFile) myFile).getClasses()[0];
    verifyJavaDoc(outerClass.getMethods()[0]);
  }

  public void testRepeatableAnnotations() {
    useJava8();
    assertEquals(
      """
        <span style="color:#808000;">@</span><a href="psi_element://R"><code><span style="color:#808000;">R</span></code></a><span style="">(</span><span style="color:#008000;font-weight:bold;">"a"</span><span style="">)</span>&nbsp;
        <span style="color:#808000;">@</span><a href="psi_element://R"><code><span style="color:#808000;">R</span></code></a><span style="">(</span><span style="color:#008000;font-weight:bold;">"b"</span><span style="">)</span>&nbsp;
        <span style="color:#000080;font-weight:bold;">class</span> <span style="color:#000000;">repeatableAnnotations</span>""",
      new JavaDocInfoGenerator(getProject(), getTestClass()).generateSignature(getTestClass()));
  }

  public void testAnonymousAndSuperJavadoc() {
    PsiClass psiClass = PsiTreeUtil.findChildOfType(getTestClass(), PsiAnonymousClass.class);
    assertNotNull(psiClass);
    PsiMethod method = psiClass.getMethods()[0];
    verifyJavaDoc(method);
  }

  public void testEnumConstant1() {
    doTestEnumConstant();
  }

  public void testEnumConstant2() {
    doTestEnumConstant();
  }

  public void testEnumConstant3() {
    doTestEnumConstant();
  }

  public void testClickableFieldReference() {
    PsiClass aClass = getTestClass();
    PsiTypeElement element = aClass.getFields()[0].getTypeElement();
    assertNotNull(element);
    PsiJavaCodeReferenceElement innermostComponentReferenceElement = element.getInnermostComponentReferenceElement();
    assertNotNull(innermostComponentReferenceElement);
    String docInfo = new JavaDocumentationProvider().generateDoc(innermostComponentReferenceElement.resolve(), element);
    assertNotNull(docInfo);
    assertFileTextEquals(docInfo);
  }

  public void testClassTypeParamsPresentation() {
    PsiClass psiClass = getTestClass();
    PsiReferenceList extendsList = psiClass.getExtendsList();
    assertNotNull(extendsList);
    PsiJavaCodeReferenceElement referenceElement = extendsList.getReferenceElements()[0];
    PsiClass superClass = extendsList.getReferencedTypes()[0].resolve();
    String docInfo = new JavaDocumentationProvider().getQuickNavigateInfo(superClass, referenceElement);
    assertNotNull(docInfo);
    assertFileTextEquals(UIUtil.getHtmlBody(docInfo));
  }

  public void testInheritedParameter() {
    configureByFile();
    PsiClass outerClass = ((PsiJavaFile) myFile).getClasses()[0];
    PsiClass innerClass = outerClass.findInnerClassByName("Impl", false);
    assertNotNull(innerClass);
    PsiParameter parameter = innerClass.getMethods()[0].getParameterList().getParameters()[0];
    verifyJavaDoc(parameter);
  }

  public void testHtmlLink() {
    createProjectStructure(getTestDataPath() + TEST_DATA_FOLDER + "htmlLinkProject");
    verifyJavadocFor("htmlLink");
    verifyJavadocFor("pack.htmlLinkDeep");
  }

  public void testHtmlLinkToPackageInfo() {
    createProjectStructure(getTestDataPath() + TEST_DATA_FOLDER + "htmlLinkToPackageInfo");
    verifyJavadocFor("pack.A");
  }

  public void testHideNonDocumentedFlowAnnotations() {
    Sdk sdk = PsiTestUtil.addJdkAnnotations(IdeaTestUtil.getMockJdk17());
    WriteAction.runAndWait(() -> ProjectJdkTable.getInstance().addJdk(sdk, getTestRootDisposable()));
    ModuleRootModificationUtil.setModuleSdk(myModule, sdk);

    PsiClass mapClass = myJavaFacade.findClass(CommonClassNames.JAVA_UTIL_MAP);
    PsiMethod mapPut = mapClass.findMethodsByName("put", false)[0];

    PsiAnnotation annotation = AnnotationUtil.findAnnotation(mapPut, Flow.class.getName());
    assertNotNull(annotation);
    assertNull(annotation.getNameReferenceElement().resolve());

    String doc = JavaDocumentationProvider.generateExternalJavadoc(mapPut);
    assertFalse(doc, doc.contains("Flow"));
  }

  public void testMatchingParameterNameFromParent() {
    configureByFile();
    PsiClass psiClass = ((PsiJavaFile)myFile).getClasses()[1];
    PsiMethod method = psiClass.getMethods()[0];
    verifyJavaDoc(method);
  }

  public void testMatchingTypeParameterNameFromParent() {
    configureByFile();
    PsiClass psiClass = ((PsiJavaFile)myFile).getClasses()[1];
    PsiMethod method = psiClass.getMethods()[0];
    verifyJavaDoc(method);
  }

  public void testDocumentationForJdkClassWhenExternalDocIsNotAvailable() {
    PsiClass aClass = myJavaFacade.findClass("java.lang.String");
    assertNotNull(aClass);
    verifyJavaDoc(aClass, Collections.singletonList("dummyUrl"));
  }

  public void testDumbMode() {
    DumbModeTestUtils.runInDumbModeSynchronously(myProject, () -> {
      doTestAtCaret();
    });
  }

  public void testLibraryPackageDocumentation() {
    VirtualFile libClasses = JavaExternalDocumentationTest.getJarFile("library.jar");
    VirtualFile libSources = JavaExternalDocumentationTest.getJarFile("library-src.jar");

    ApplicationManager.getApplication().runWriteAction(() -> {
      Library library = LibraryTablesRegistrar.getInstance().getLibraryTable(myProject).createLibrary("myLib");
      Library.ModifiableModel model = library.getModifiableModel();
      model.addRoot(libClasses, OrderRootType.CLASSES);
      model.addRoot(libSources, OrderRootType.SOURCES);
      model.commit();

      Module[] modules = ModuleManager.getInstance(myProject).getModules();
      assertSize(1, modules);
      ModuleRootModificationUtil.addDependency(modules[0], library);
    });

    PsiPackage aPackage = myJavaFacade.findPackage("com.jetbrains");
    assertNotNull(aPackage);
    verifyJavaDoc(aPackage);
  }

  private void doTestClass() {
    PsiClass psiClass = getTestClass();
    verifyJavaDoc(psiClass);
  }

  private void doTestEnumConstant() {
    PsiClass psiClass = getTestClass();
    PsiField field = psiClass.getFields()[0];
    String docInfo = new JavaDocumentationProvider().generateDoc(field, field);
    assertNotNull(docInfo);
    assertFileTextEquals(docInfo);

    docInfo = new JavaDocumentationProvider().getQuickNavigateInfo(field, field);
    assertNotNull(docInfo);
    String htmlText = loadFile(new File(getTestDataPath() + TEST_DATA_FOLDER + getTestName(true) + "_quick.html"));
    assertEquals(htmlText, replaceEnvironmentDependentContent(UIUtil.getHtmlBody(docInfo)));
  }

  private void doTestField() {
    PsiClass psiClass = getTestClass();
    PsiField field = psiClass.getFields()[0];
    verifyJavaDoc(field);
  }

  private void doTestMethod() {
    PsiClass psiClass = getTestClass();
    PsiMethod method = psiClass.getMethods()[0];
    verifyJavaDoc(method);
  }

  private void doTestLambdaParameter() {
    PsiClass psiClass = getTestClass();
    final PsiLambdaExpression lambdaExpression = PsiTreeUtil.findChildOfType(psiClass, PsiLambdaExpression.class);
    assertNotNull(lambdaExpression);
    verifyJavaDoc(lambdaExpression.getParameterList().getParameters()[0]);
  }

  private PsiClass getTestClass() {
    configureByFile();
    return ((PsiJavaFile)myFile).getClasses()[0];
  }

  private void verifyJavaDoc(PsiElement element) {
    verifyJavaDoc(element, null);
  }

  private void verifyJavaDoc(PsiElement element, List<String> docUrls) {
    String docInfo = JavaDocumentationProvider.generateExternalJavadoc(element, docUrls);
    assertNotNull(docInfo);
    assertFileTextEquals(docInfo);
  }

  private void verifyJavadocFor(String className) {
    PsiClass psiClass = myJavaFacade.findClass(className);
    assertNotNull(psiClass);
    String doc = JavaDocumentationProvider.generateExternalJavadoc(psiClass, (List<String>)null);
    assertNotNull(doc);
    PsiDirectory dir = (PsiDirectory)psiClass.getParent().getParent();
    PsiFile htmlFile = dir.findFile(psiClass.getName() + ".html");
    assertNotNull(htmlFile);
    assertEquals(StringUtil.convertLineSeparators(htmlFile.getText().trim()), replaceEnvironmentDependentContent(doc));
  }

  private void doTestPackageInfo() {
    createProjectStructure(getTestDataPath() + TEST_DATA_FOLDER);
    PsiPackage psiPackage = myJavaFacade.findPackage(getTestName(true));
    assertNotNull(psiPackage);
    String info = JavaDocumentationProvider.generateExternalJavadoc(psiPackage, (List<String>)null);
    assertFileTextEquals(info, getTestName(true) + "/packageInfo.html");
  }

  private void doTestPackageInfo(String caretPositionedAt) {
    VirtualFile root = createProjectStructure(getTestDataPath() + TEST_DATA_FOLDER);
    VirtualFile file = root.findFileByRelativePath(getTestName(true) + "/package-info.java");
    assertNotNull(file);
    PsiFile psiFile = PsiManager.getInstance(myProject).findFile(file);
    assertNotNull(psiFile);
    String info = JavaExternalDocumentationTest.getDocumentationText(psiFile, psiFile.getText().indexOf(caretPositionedAt));
    assertFileTextEquals(info, getTestName(true) + "/packageInfo.html");
  }

  private VirtualFile createProjectStructure(String rootPath) {
    try {
      return createTestProjectStructure(rootPath);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private void doTestAtCaret() {
    configureByFile();
    String docInfo = JavaExternalDocumentationTest.getDocumentationText(myFile, myEditor.getCaretModel().getOffset());
    assertNotNull(docInfo);
    assertFileTextEquals(docInfo);
  }

  private void configureByFile() {
    try {
      configureByFile(TEST_DATA_FOLDER + getTestName(true) + ".java");
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private void assertFileTextEquals(String docInfo) {
    assertFileTextEquals(docInfo, getTestName(true) + ".html");
  }

  private void assertFileTextEquals(String docInfo, String expectedFile) {
    assertEqualsFileText(getTestDataPath() + TEST_DATA_FOLDER + expectedFile, docInfo);
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

  private void useJava7() {
    myJdkVersion = 7;
    setUpJdk();
  }

  private void useJava8() {
    myJdkVersion = 8;
    setUpJdk();
  }

  private void useJava9() {
    myJdkVersion = 9;
    setUpJdk();
  }

  private void useJava10() {
    myJdkVersion = 10;
    setUpJdk();
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