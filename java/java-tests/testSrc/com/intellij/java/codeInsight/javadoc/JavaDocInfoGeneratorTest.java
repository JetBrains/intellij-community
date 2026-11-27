// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.javadoc;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.HeavyJavaCodeInsightFixture;
import com.intellij.codeInsight.daemon.impl.quickfix.JetBrainsAnnotationsExternalLibraryResolver;
import com.intellij.codeInsight.documentation.DocumentationTargetFinder;
import com.intellij.codeInsight.javadoc.JavaDocExternalFilter;
import com.intellij.codeInsight.javadoc.JavaDocHighlightingManagerImpl;
import com.intellij.codeInsight.javadoc.JavaDocInfoGenerator;
import com.intellij.codeInsight.javadoc.JavaDocInfoGeneratorFactory;
import com.intellij.codeInsight.javadoc.JavaDocInfoPrinter;
import com.intellij.java.codeInsight.JavaDocumentationTestUtil;
import com.intellij.lang.java.JavaDocumentationProvider;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.testFramework.core.FileComparisonFailedError;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiLambdaExpression;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.DumbModeTestUtils;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.IndexingTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.MavenDependencyUtil;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.ArrayUtil;
import com.intellij.util.lang.JavaVersion;
import com.intellij.util.ui.UIUtil;
import org.intellij.lang.annotations.Flow;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static com.intellij.codeInsight.HeavyJavaCodeInsightFixture.heavyJavaCodeInsightSubFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestDataPath("$CONTENT_ROOT/testData/codeInsight/javadocIG/")
abstract class JavaDocInfoGeneratorTest {
  protected static final String TEST_DATA_FOLDER = "/codeInsight/javadocIG/";

  protected final TestFixture<HeavyJavaCodeInsightFixture> heavyFixture =
    heavyJavaCodeInsightSubFixture("javaDocInfoGeneratorFixture", context ->
      new HeavyJavaCodeInsightFixture(context, JavaTestUtil.getJavaTestDataPath()) {
        @Override
        protected @NotNull Sdk getTestProjectJdk() {
          return IdeaTestUtil.getMockJdk(JavaVersion.compose(21));
        }

        @Override
        protected void setUpModule(@NotNull Module module) {
          if (!getTestName(false).equals("HideNonDocumentedFlowAnnotations")) {
            ModuleRootModificationUtil.updateModel(
              module, model -> MavenDependencyUtil.addFromMaven(
                model, "org.jetbrains:annotations:" + JetBrainsAnnotationsExternalLibraryResolver.getVersion()));
          }
        }

        @Override
        protected @NotNull LanguageLevel getProjectLanguageLevel() {
          return LanguageLevel.HIGHEST;
        }
      });

  protected @NotNull String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  /// @return the printer used by the test
  abstract protected @NotNull JavaDocInfoPrinter getPrinter();
  
  /// @return the expected file extension, with a dot _(e.g. ".html")_
  abstract protected @NotNull String getExpectedFileExtension();
  
  /// @return test with "decorations" specific to where the output will be shown.
  abstract protected @NotNull String decorate(@NotNull String text);
  
  /// @return Post-processed string, before comparing to file test data
  abstract protected String replaceEnvironmentDependentContent(String html);
  
  @Test
  public void testSimpleField() { doTestField(); }
  @Test
  public void testFieldValue() { doTestField(); }
  @Test
  public void testValueInMethod() { doTestMethod(); }
  @Test
  public void testValueInMethodNoHash() { doTestMethod(); }
  @Test
  public void testValueWithFormat() { doTestMethod(); }
  @Test
  public void testEscapingStringValue() { doTestMethod(); }
  @Test
  public void testIdeadev2326() { doTestMethod(); }
  @Test
  public void testMethodTypeParameter() { doTestMethod(); }
  @Test
  public void testMethodTypeParameter2() { doTestAtCaret(); }
  @Test
  public void testMethodTypeParameter3() { doTestAtCaret(); }
  @Test
  public void testInheritedDocInThrows() { doTestMethod(); }
  @Test
  public void testInheritedDocInThrows1() { doTestMethod(); }
  @Test
  public void testMultipleThrowsSameType() { doTestMethod(); }
  @Test
  public void testEmptyThrows() { doTestMethod(); }
  @Test
  public void testEscapeValues() { doTestClass(); }
  @Test
  public void testClassTypeParameter() { doTestClass(); }
  @Test
  public void testClassTypeParameter1() { doTestClass(); }
  @Test
  public void testClassTypeParameter2() { doTestAtCaret(); }
  @Test
  public void testUnicodeEscapes() { doTestClass(); }
  @Test
  public void testEnumValueOf() { doTestMethod(); }
  @Test
  public void testMethodFormatting() { doTestMethod(); }
  @Test
  public void testConstantFieldInitializer() { doTestField(); }
  @Test
  public void testInitializerWithNew() { doTestField(); }
  @Test
  public void testInitializerWithLiteral() { doTestField(); }
  @Test
  public void testInitializerWithMultiLineLiteral() { doTestField(); }
  @Test
  public void testMethodExpressionWithLiteral() { doTestField(); }
  @Test
  public void testInitializerWithReference() { doTestField(); }
  @Test
  public void testAnnotations() { doTestField(); }
  @Test
  public void testAnnotationsInParams() { doTestMethod(); }
  @Test
  public void testInferredAnnotationsOnArray() { doTestMethod(); }
  @Test
  public void testInferredAnnotationsOnArrayMethod() { doTestMethod(); }
  @Test
  public void testInferredAnnotationsOnArray2d() { doTestMethod(); }
  @Test
  public void testTypeAnnoMultiDimArray() { doTestMethod(); }

  @Test
  public void testAnnotationsFromScope() {
    heavyFixture.get().createTestProjectStructure(getTestDataPath() + TEST_DATA_FOLDER + "indirectNullability");
    PsiClass SpecifiedClass = ReadAction.computeBlocking(() -> heavyFixture.get().getJavaFacade().findClass("GetSpecified"));

    ReadAction.runBlocking(() -> {
      verifyJavadocFor("indirectNullability", SpecifiedClass.getFields()[0], "0_field");
      verifyJavadocFor("indirectNullability", SpecifiedClass.getFields()[1], "1_field");
      verifyJavadocFor("indirectNullability", SpecifiedClass.getFields()[2], "2_field");

      // Constructors
      verifyJavadocFor("indirectNullability", SpecifiedClass.getMethods()[0], "0_method");
      verifyJavadocFor("indirectNullability", SpecifiedClass.getMethods()[1], "1_method");

      // methods
      verifyJavadocFor("indirectNullability", SpecifiedClass.getMethods()[2], "2_method");
      verifyJavadocFor("indirectNullability", SpecifiedClass.getMethods()[3], "3_method");
      verifyJavadocFor("indirectNullability", SpecifiedClass.getMethods()[4], "4_method");
      verifyJavadocFor("indirectNullability", SpecifiedClass.getMethods()[5], "5_method");
      verifyJavadocFor("indirectNullability", SpecifiedClass.getMethods()[6], "6_method");
      verifyJavadocFor("indirectNullability", SpecifiedClass.getMethods()[7], "7_method");
      verifyJavadocFor("indirectNullability", SpecifiedClass.getMethods()[8], "8_method");

      verifyJavadocFor("indirectNullability", SpecifiedClass.getInnerClasses()[0], "0_record");
      verifyJavadocFor("indirectNullability", SpecifiedClass.getInnerClasses()[1], "1_record");
      verifyJavadocFor("indirectNullability", SpecifiedClass.getInnerClasses()[2], "2_record");
    });
  }

  @Test
  public void testApiNotes() { doTestMethod(); }
  @Test
  public void testLiteral() { doTestField(); }
  @Test
  public void testEscapingInLiteral() { doTestField(); }
  @Test
  public void testCode() { useJava8(); doTestField(); }
  @Test
  public void testPInsidePre() { doTestField(); }
  @Test
  public void testHtmlCodeBlocks() { doTestClass(); }
  @Test
  public void testCommaInsideArgsList() { doTestField(); }
  @Test
  public void testFieldInitializedWithLambda() { doTestField(); }
  @Test
  public void testFieldInitializedWithArray() { doTestField(); }
  @Test
  public void testFieldInitializedWithSizedArray() { doTestField(); }
  @Test
  public void testFieldInitializedWithPartlySizedArray() { doTestField(); }
  @Test
  public void testDoubleLt() { doTestClass(); }
  @Test
  public void testNoSpaceAfterTagName() { doTestClass(); }
  @Test
  public void testRecordParameters() { doTestClass(); } //j.l.Record is unresolved as there is no mock jdk 14 yet
  @Test
  public void testLambdaParameter() { doTestLambdaParameter(); }
  @Test
  public void testLocalClassInsideAnonymous() { doTestAtCaret(); }
  @Test
  public void testPackageInfoFromComment() { doTestPackageInfo("some"); }
  @Test
  public void testPackageInfoWithCopyright() { doTestPackageInfo("packageInfoWithCopyright"); }
  @Test
  public void testHtmlLinkWithRef() { verifyJavaDoc(getTestClass()); }
  @Test
  public void testMultipleSpacesInLiteral() { useJava8(); verifyJavaDoc(getTestClass()); }
  @Test
  public void testLegacySpacesInLiteral() { useJava7(); verifyJavaDoc(getTestClass()); }
  @Test
  public void testLinkWithModule() { doTestClass(); }
  @Test
  public void testLinkToModule() { doTestClass(); }
  @Test
  public void testLinkNested() { 
    configureByFile();
    PsiClass testClass = getTestClass();
    verifyJavaDoc(ReadAction.computeBlocking(() -> testClass.getAllInnerClasses()[0].getMethods()[0]));
  }
  @Test
  public void testLinkWithWildcard() { doTestClass(); }
  @Test
  public void testLinkWithLineBreak() { doTestClass(); }
  @Test
  public void testDocumentationForJdkClassWithReferencesToClassesFromJavaLang() { useJava7(); doTestAtCaret(); }
  @Test
  public void testDocumentationForUncheckedExceptionsInSupers() { useJava7(); doTestAtCaret(); }
  @Test
  public void testDocumentationForGetterByField() { doTestAtCaret(); }
  @Test
  public void testParamInJavadoc() { doTestAtCaret(); }
  @Test
  public void testParamInMethod() { doTestAtCaret(); }
  @Test
  public void testExternalLinksInJavadoc() { doTestAtCaret(); }
  @Test
  public void testLiteralInsideCode() { useJava8(); doTestClass(); }
  @Test
  public void testSuperJavadocExactResolve() { doTestAtCaret(); }
  @Test
  public void testSuperJavadocErasureResolve() { doTestAtCaret(); }
  @Test
  public void testPackageInfo() { doTestPackageInfo(); }
  @Test
  public void testPackageRelativeFiles() {
    var root = heavyFixture.get().createTestProjectStructure(getTestDataPath() + TEST_DATA_FOLDER + "packageRelativeFiles");
    var source = root.findFileByRelativePath("p/package-info.java");
    assertNotNull(source);
    ReadAction.runBlocking(() -> {
      var psiPackage = heavyFixture.get().getJavaFacade().findPackage("p");
      assertNotNull(psiPackage);
      assertRelativeFilesDoc(psiPackage, source, "packageInfo");
      var directory = PsiManager.getInstance(heavyFixture.get().getProject()).findDirectory(source.getParent());
      assertNotNull(directory);
      assertRelativeFilesDoc(directory, source, "packageDirectory");
    });
  }

  @Test
  public void testClassAndMemberRelativeFiles() {
    var root = heavyFixture.get().createTestProjectStructure(getTestDataPath() + TEST_DATA_FOLDER + "packageRelativeFiles");
    var source = root.findFileByRelativePath("p/C.java");
    assertNotNull(source);
    ReadAction.runBlocking(() -> {
      var psiClass = heavyFixture.get().getJavaFacade().findClass("p.C");
      assertNotNull(psiClass);
      assertRelativeFilesDoc(psiClass, source, "C");
      assertRelativeFilesDoc(psiClass.getMethods()[0], source, "m");
    });
  }

  @Test
  public void testMarkdownRelativeFiles() {
    var root = heavyFixture.get().createTestProjectStructure(getTestDataPath() + TEST_DATA_FOLDER + "packageRelativeFiles");
    var source = root.findFileByRelativePath("p/Markdown.java");
    assertNotNull(source);
    ReadAction.runBlocking(() -> {
      var psiClass = heavyFixture.get().getJavaFacade().findClass("p.Markdown");
      assertNotNull(psiClass);
      assertRelativeFilesDoc(psiClass, source, "Markdown");
    });
  }

  private void assertRelativeFilesDoc(PsiElement element, VirtualFile source, String expectedFile) {
    var docInfo = generateDocInfo(element);
    assertNotNull(docInfo);
    var baseUrl = VfsUtilCore.convertToURL(source.getUrl());
    assertNotNull(baseUrl);
    var directoryUrl = baseUrl.toExternalForm().substring(0, baseUrl.toExternalForm().lastIndexOf('/') + 1);
    var expectedPath = getTestDataPath() + TEST_DATA_FOLDER + "packageRelativeFiles/" + expectedFile + getExpectedFileExtension();
    assertEqualsFileText(expectedPath, docInfo.replace(directoryUrl, "file:///placeholder/"));
  }

  @Test
  public void testPackageWithoutPackageInfo() { doTestPackageInfo(); }
  @Test
  public void testEmptyPackage() { doTestPackageInfo(); }
  @Test
  public void testPackageHtml() { doTestPackageInfo(); }
  @Test
  public void testSyntheticEnumValues() { doTestAtCaret(); }
  @Test
  public void testVariableDoc() { doTestAtCaret(); }
  @Test
  public void testTypeAnnotation() { useJava8(); doTestAtCaret(); }
  @Test
  public void testTypeAnnotationArray() { useJava8(); doTestAtCaret(); }
  @Test
  public void testTypeAnnotationClass() { useJava8(); doTestClass(); }
  @Test
  public void testInlineTagIndex() { useJava9(); doTestClass(); }
  @Test
  public void testInlineTagSummary() { doTestClass(); }
  @Test
  public void testLeadingSpacesInPre() { doTestClass(); }
  @Test
  public void testBlockquotePre() { doTestAtCaret(); }
  @Test
  public void testPreInDeprecated() { doTestClass(); }
  @Test
  public void testEscapeHtmlInCode() { doTestClass(); }
  @Test
  public void testEscapeAngleBracketsInCode() { doTestClass(); }
  @Test
  public void testInlineTagSnippet() { doTestClass(); }
  @Test
  public void testInlineTagSnippetNoMarkup() { doTestClass(); }
  @Test
  public void testInlineTagSnippetWithoutBody() { doTestClass(); }
  @Test
  public void testInlineTagSnippetHighlightSeveralLines() { doTestClass(); }
  @Test
  public void testExternalSnippetRegion() {
    createProjectStructure(getTestDataPath() + TEST_DATA_FOLDER + "externalSnippet");
    verifyJavadocFor("externalSnippet", "Region");
  }
  @Test
  public void testExternalSnippetRegionNoMarkup() {
    createProjectStructure(getTestDataPath() + TEST_DATA_FOLDER + "externalSnippet");
    verifyJavadocFor("externalSnippet", "RegionNoMarkup");
  }
  @Test
  public void testExternalSnippetNoRegion() {
    createProjectStructure(getTestDataPath() + TEST_DATA_FOLDER + "externalSnippet");
    verifyJavadocFor("externalSnippet", "NoRegion");
  }
  @Test
  public void testExternalSnippetMain() {
    createProjectStructure(getTestDataPath() + TEST_DATA_FOLDER + "externalSnippet");
    verifyJavadocFor("externalSnippet", "Main");
  }
  @Test
  public void testExternalSnippetMultiTag() {
    createProjectStructure(getTestDataPath() + TEST_DATA_FOLDER + "externalSnippet");
    verifyJavadocFor("externalSnippet", "MultiTag");
  }
  @Test
  public void testExternalSnippetTextFile() {
      createProjectStructure(getTestDataPath() + TEST_DATA_FOLDER + "externalSnippet");
      verifyJavadocFor("externalSnippet", "TextFile");
  }
  @Test
  public void testExternalSnippetUnresolved() {
    createProjectStructure(getTestDataPath() + TEST_DATA_FOLDER + "externalSnippet");
    verifyJavadocFor("externalSnippet", "Unresolved");
  }
  @Test
  public void testExternalSnippetMalformed() {
    createProjectStructure(getTestDataPath() + TEST_DATA_FOLDER + "externalSnippet");
    verifyJavadocFor("externalSnippet", "Malformed");
  }
  @Test
  public void testUnknownInlineTag() { doTestClass(); }
  @Test
  public void testUnknownInlineMultilineTag() { doTestClass(); }
  @Test
  public void testUnknownTag() { doTestMethod(); }
  @Test
  public void testUnknownClassTag() { doTestClass(); }
  @Test
  public void testReflectConstructor() { doTestAtCaret(); }
  @Test
  public void testMarkdownGeneralFeatures() { doTestClass(); }
  @Test
  public void testMarkdownCodeBlock(){ doTestClass(); }
  @Test
  public void testMarkdownReferenceLink(){ doTestClass(); }
  @Test
  public void testMarkdownInheritDoc() {
    configureByFile();
    ReadAction.runBlocking(() -> {
      PsiClass outerClass = ((PsiJavaFile)heavyFixture.get().getFile()).getClasses()[1];
      verifyJavaDoc(outerClass.getMethods()[0]);
    });
  }
  @Test
  public void testMarkdownInheritDocSingleLine() {
    configureByFile();
    ReadAction.runBlocking(() -> {
      PsiClass outerClass = ((PsiJavaFile)heavyFixture.get().getFile()).getClasses()[1];
      verifyJavaDoc(outerClass.getMethods()[0]);
    });
  }
  @Test
  public void testMarkdownInlineWithTags(){
    configureByFile();
    ReadAction.runBlocking(() -> {
      PsiClass outerClass = ((PsiJavaFile)heavyFixture.get().getFile()).getClasses()[0];
      verifyJavaDoc(outerClass.getMethods()[0]);
    });
  }
  @Test
  public void testMarkdownParagraphSeparation() { doTestClass(); }
  @Test
  public void testMarkdownJepExample(){ doTestMethod(); }
  @Test
  public void testMarkdownJepExample02(){ doTestClass(); }
  @Test
  public void testHtmlCodeInMarkdown() { doTestMethod(); }
  @Test
  public void testMarkdownInlineCodeBlock() { doTestClass(); }
  @Test
  public void testEscapeHtmlCodesInCodeBlock() { doTestClass(); }
  @Test
  public void testPreTagLeakBeforeCode() { doTestClass(); }
  @Test
  public void testPreTagStrictBeforeCode(){ doTestClass(); }
  @Test
  public void testSeeTagWithLabel() { doTestClass(); }
  @Test
  public void testSeeTagWithLabelMarkdown() { doTestClass(); }
  @Test
  public void testSeeTagWithNestedLink() { doTestClass(); }
  @Test
  public void testSeeTagWithNestedLinkMarkdown() { doTestClass(); }
  @Test
  public void testLinkInParamDescriptionMarkdown() { doTestAtCaret(); }
  @Test
  public void testSeeTagMarkdownDescription() { doTestClass(); }
  @Test
  public void testAllTags() { doTestAtCaret(); }
  @Test
  public void testAllTagsMarkdown() { doTestAtCaret(); }
  @Test
  public void testFragmentReference()  { doTestClass(); }
  @Test
  public void testPackageInfoMarkdown() { doTestPackageInfo(); }
  @Test
  public void testListInTags() { doTestMethod(); }
  @Test
  public void testParagraphInTagsMarkdown() { doTestMethod(); }
  @Test
  public void testBareMethodReferences() { doTestClass(); }
  @Test
  public void testPreTagInJavadocTag() { doTestClass();}
  @Test
  public void testLinkTagMalformed() { doTestClass(); }
  @Test
  public void testCursedCodeBlock() { doTestClass(); }
  @Test
  public void testCursedCodeBlock2() { doTestClass(); }
  @Test
  public void testSnippetMarkup() { doTestClass(); }
  @Test
  public void testSealedClass() {
    createProjectStructure(getTestDataPath() + TEST_DATA_FOLDER + "sealedClasses");
    verifyJavadocFor("sealedClasses","Sealer");
    verifyJavadocFor("sealedClasses","SecondSealer");
  }
 @Test
 public void testParameterizedLinks() { doTestClass(); }
 @Test
 public void testWrongfulInnerClassReferences() { doTestClass(); }
 @Test
 public void testImplicitConstructor() { doTestClass(); }
 @Test
 public void testCommatHtmlEntity() { doTestClass(); }
 @Test
 public void testDeprecatedTagNoAnnotationMarkdown() { doTestClass(); }
 @Test
 public void testIncreasinglyIndentedListMarkdown() { doTestClass(); }

  @Test
  public void testRepeatableAnnotations() {
    useJava8();
    assertEquals(
      """
        <span style="color:#808000;">@</span><a href="psi_element://R"><code><span style="color:#808000;">R</span></code></a><span style="">(</span><span style="color:#008000;font-weight:bold;">"a"</span><span style="">)</span>&nbsp;
        <span style="color:#808000;">@</span><a href="psi_element://R"><code><span style="color:#808000;">R</span></code></a><span style="">(</span><span style="color:#008000;font-weight:bold;">"b"</span><span style="">)</span>&nbsp;
        <span style="color:#000080;font-weight:bold;">class</span> <span style="color:#000000;">repeatableAnnotations</span>""",
      generateSignature(getTestClass(), getTestClass()));
  }

  @Test
  public void testAnonymousAndSuperJavadoc() {
    PsiClass testClass = getTestClass();
    ReadAction.runBlocking(() -> {
      PsiClass psiClass = PsiTreeUtil.findChildOfType(testClass, PsiAnonymousClass.class);
      assertNotNull(psiClass);
      PsiMethod method = psiClass.getMethods()[0];
      verifyJavaDoc(method);
    });
  }
  @Test
  public void testDefaultOverload() { doTestAtCaret(); }

  @Test
  public void testEnumConstant1() { doTestEnumConstant(); }
  @Test
  public void testEnumConstant2() { doTestEnumConstant(); }
  @Test
  public void testEnumConstant3() { doTestEnumConstant(); }

  @Test
  public void testClickableFieldReference() {
    PsiClass aClass = getTestClass();
    ReadAction.runBlocking(() -> {
      PsiTypeElement element = aClass.getFields()[0].getTypeElement();
      assertNotNull(element);
      PsiJavaCodeReferenceElement innermostComponentReferenceElement = element.getInnermostComponentReferenceElement();
      assertNotNull(innermostComponentReferenceElement);
      String docInfo = generateDocInfo(innermostComponentReferenceElement.resolve());
      assertNotNull(docInfo);
      assertFileTextEquals(docInfo);
    });
  }


  @Test
  public void testInheritedParameter() {
    configureByFile();
    ReadAction.runBlocking(() -> {
      PsiClass outerClass = ((PsiJavaFile)heavyFixture.get().getFile()).getClasses()[0];
      PsiClass innerClass = outerClass.findInnerClassByName("Impl", false);
      assertNotNull(innerClass);
      PsiParameter parameter = innerClass.getMethods()[0].getParameterList().getParameters()[0];
      verifyJavaDoc(parameter);
    });
  }

  @Test
  public void testHtmlLink() {
    createProjectStructure(getTestDataPath() + TEST_DATA_FOLDER + "htmlLinkProject");
    verifyJavadocFor("htmlLinkProject", "htmlLink");
    verifyJavadocFor("htmlLinkProject", "pack.htmlLinkDeep");
  }

  @Test
  public void testHtmlLinkToPackageInfo() {
    createProjectStructure(getTestDataPath() + TEST_DATA_FOLDER + "htmlLinkToPackageInfo");
    verifyJavadocFor("htmlLinkToPackageInfo", "pack.A");
  }

  @Test
  public void testHideNonDocumentedFlowAnnotations() {
    Sdk sdk = PsiTestUtil.addJdkAnnotations(IdeaTestUtil.getMockJdk17());
    WriteAction.runAndWait(() -> ProjectJdkTable.getInstance().addJdk(sdk, heavyFixture.get().getTestRootDisposable()));
    ModuleRootModificationUtil.setModuleSdk(heavyFixture.get().getModule(), sdk);
    IndexingTestUtil.Companion.waitUntilIndexesAreReady(heavyFixture.get().getProject());
    ReadAction.runBlocking(() -> {
      PsiClass mapClass = heavyFixture.get().getJavaFacade().findClass(CommonClassNames.JAVA_UTIL_MAP);
      PsiMethod mapPut = mapClass.findMethodsByName("put", false)[0];

      PsiAnnotation annotation = AnnotationUtil.findAnnotation(mapPut, Flow.class.getName());
      assertNotNull(annotation);
      assertNull(annotation.getNameReferenceElement().resolve());

      String doc = JavaDocumentationProvider.generateExternalJavadoc(mapPut);
      assertFalse(doc.contains("Flow"), doc);
    });
  }

  @Test
  public void testMatchingParameterNameFromParent() {
    configureByFile();
    ReadAction.runBlocking(() -> {
      PsiClass psiClass = ((PsiJavaFile)heavyFixture.get().getFile()).getClasses()[1];
      PsiMethod method = psiClass.getMethods()[0];
      verifyJavaDoc(method);
    });
  }

  @Test
  public void testMatchingTypeParameterNameFromParent() {
    configureByFile();
    ReadAction.runBlocking(() -> {
      PsiClass psiClass = ((PsiJavaFile)heavyFixture.get().getFile()).getClasses()[1];
      PsiMethod method = psiClass.getMethods()[0];
      verifyJavaDoc(method);
    });
  }

  @Test
  public void testDocumentationForJdkClassWhenExternalDocIsNotAvailable() {
    useJava7();
    PsiClass aClass = ReadAction.computeBlocking(() -> heavyFixture.get().getJavaFacade().findClass("java.lang.String"));
    assertNotNull(aClass);
    verifyJavaDoc(aClass, Collections.singletonList("dummyUrl"));
  }

  @Test
  public void testDumbMode() {
    if (!Registry.is("ide.dumb.mode.check.awareness")) return;
    DumbModeTestUtils.runInDumbModeSynchronously(heavyFixture.get().getProject(), () -> {
      doTestAtCaret();
    });
  }

  @Test
  public void testExternalTypeAnnotations() {
    ReadAction.runBlocking(() -> {
      PsiClass aClass = heavyFixture.get().getJavaFacade().findClass("java.util.concurrent.CompletableFuture");
      assertNotNull(aClass);
      PsiMethod[] whenComplete = aClass.findMethodsByName("whenComplete", false);
      assertEquals(1, whenComplete.length);
      verifyJavaDoc(whenComplete[0]);
    });
  }

  @Test
  public void testLibraryPackageDocumentation() {
    VirtualFile libClasses = JavaDocumentationTestUtil.getJarFile("library.jar");
    VirtualFile libSources = JavaDocumentationTestUtil.getJarFile("library-src.jar");

    WriteAction.runAndWait(() -> {
      Library library = LibraryTablesRegistrar.getInstance().getLibraryTable(heavyFixture.get().getProject()).createLibrary("myLib");
      Library.ModifiableModel model = library.getModifiableModel();
      model.addRoot(libClasses, OrderRootType.CLASSES);
      model.addRoot(libSources, OrderRootType.SOURCES);
      model.commit();

      Module[] modules = ModuleManager.getInstance(heavyFixture.get().getProject()).getModules();
      assertEquals(1, modules.length);
      ModuleRootModificationUtil.addDependency(modules[0], library);
    });

    IndexingTestUtil.waitUntilIndexesAreReady(heavyFixture.get().getProject());
    PsiPackage aPackage = ReadAction.computeBlocking(() -> heavyFixture.get().getJavaFacade().findPackage("com.jetbrains"));
    assertNotNull(aPackage);
    verifyJavaDoc(aPackage);
  }

  private void doTestClass() {
    PsiClass psiClass = getTestClass();
    verifyJavaDoc(psiClass);
  }

  protected void doTestEnumConstant() {
    PsiClass psiClass = getTestClass();
    ReadAction.runBlocking(() -> {
      PsiField field = psiClass.getFields()[0];
      String docInfo = generateDocInfo(field);
      assertNotNull(docInfo);
      assertFileTextEquals(docInfo);

      docInfo = new JavaDocumentationProvider().getQuickNavigateInfo(field, field);
      assertNotNull(docInfo);
      String expectedTest = loadFile(new File(getTestDataPath() + TEST_DATA_FOLDER + heavyFixture.get().getTestName(true) + "_quick" + getExpectedFileExtension()));
      assertEquals(expectedTest, replaceEnvironmentDependentContent(UIUtil.getHtmlBodyWithoutPreWrapper(docInfo)));
    });
  }

  private void doTestField() {
    PsiClass psiClass = getTestClass();
    PsiField field = ReadAction.computeBlocking(() -> psiClass.getFields()[0]);
    verifyJavaDoc(field);
  }

  private void doTestMethod() {
    PsiClass psiClass = getTestClass();
    PsiMethod method = ReadAction.computeBlocking(() -> psiClass.getMethods()[0]);
    verifyJavaDoc(method);
  }

  private void doTestLambdaParameter() {
    PsiClass psiClass = getTestClass();
    ReadAction.runBlocking(() -> {
      final PsiLambdaExpression lambdaExpression = PsiTreeUtil.findChildOfType(psiClass, PsiLambdaExpression.class);
      assertNotNull(lambdaExpression);
      verifyJavaDoc(lambdaExpression.getParameterList().getParameters()[0]);
    });
  }

  protected PsiClass getTestClass() {
    configureByFile();
    return ReadAction.computeBlocking(() -> ((PsiJavaFile)heavyFixture.get().getFile()).getClasses()[0]);
  }

  private void verifyJavaDoc(PsiElement element) {
    verifyJavaDoc(element, null);
  }

  private void verifyJavaDoc(PsiElement element, List<String> docUrls) {
    ReadAction.runBlocking(() -> {
      JavaDocInfoGenerator generator = JavaDocInfoGeneratorFactory.getBuilder(heavyFixture.get().getProject())
        .setPsiElement(element)
        .setPrinter(getPrinter())
        .create();
      String docInfo = JavaDocExternalFilter.filterInternalDocInfo(generator.generateDocInfo(docUrls));
      assertNotNull(docInfo);
      assertFileTextEquals(docInfo);
    });
  }

  private void verifyJavadocFor(String rootPath, @NotNull PsiElement element, String fileName) {
    ReadAction.runBlocking(() -> {
      assertNotNull(element);
      JavaDocInfoGenerator generator = JavaDocInfoGeneratorFactory.getBuilder(heavyFixture.get().getProject())
        .setPsiElement(element)
        .setPrinter(getPrinter())
        .create();
      String doc = JavaDocExternalFilter.filterInternalDocInfo(generator.generateDocInfo(null));
      assertNotNull(doc);
      PsiDirectory dir = element.getContainingFile().getContainingDirectory();
      PsiFile file = dir.findFile(fileName + getExpectedFileExtension());
      assertNotNull(file, "The file does not exists: " + fileName + getExpectedFileExtension());
      String expectedText = StringUtil.convertLineSeparators(file.getText().trim());
      String actualText = replaceEnvironmentDependentContent(StringUtil.convertLineSeparators(doc));

      if (!StringUtil.equals(expectedText, actualText)) {
        throw new FileComparisonFailedError(null, expectedText, actualText,
                                            getTestDataPath() +
                                            TEST_DATA_FOLDER +
                                            rootPath +
                                            "/" +
                                            fileName.replace(".", "/") +
                                            getExpectedFileExtension());
      }
    });
  }

  private void verifyJavadocFor(String rootPath, String className) {
    PsiClass psiClass = ReadAction.computeBlocking(() -> heavyFixture.get().getJavaFacade().findClass(className));
    verifyJavadocFor(rootPath, psiClass, ArrayUtil.getLastElement(className.split("\\.")));
  }

  private void doTestPackageInfo() {
    createProjectStructure(getTestDataPath() + TEST_DATA_FOLDER);
    ReadAction.runBlocking(() -> {
      PsiPackage psiPackage = heavyFixture.get().getJavaFacade().findPackage(heavyFixture.get().getTestName(true));
      assertNotNull(psiPackage);
      String info = generateDocInfo(psiPackage);
      assertFileTextEquals(info, heavyFixture.get().getTestName(true) + "/packageInfo" + getExpectedFileExtension());
    });
  }


  private void doTestPackageInfo(String caretPositionedAt) {
    VirtualFile root = createProjectStructure(getTestDataPath() + TEST_DATA_FOLDER);
    ReadAction.runBlocking(() -> {
      VirtualFile file = root.findFileByRelativePath(heavyFixture.get().getTestName(true) + "/package-info.java");
      assertNotNull(file);
      PsiFile psiFile = PsiManager.getInstance(heavyFixture.get().getProject()).findFile(file);
      assertNotNull(psiFile);
      assertTrue(psiFile.getText().contains(caretPositionedAt));
      PsiPackage psiPackage = heavyFixture.get().getJavaFacade().findPackage(heavyFixture.get().getTestName(true));
      assertNotNull(psiPackage);
      String info = generateDocInfo(psiPackage);
      assertFileTextEquals(decorate(info),
                           heavyFixture.get().getTestName(true) + "/packageInfo" + getExpectedFileExtension());
    });
  }

  private VirtualFile createProjectStructure(String rootPath) {
    return heavyFixture.get().createTestProjectStructure(rootPath);
  }

  private void doTestAtCaret() {
    configureByFile();
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    ReadAction.runBlocking(() -> {
      Editor editor = fixture.getEditor();
      DocumentationTargetFinder.TargetWithContext target = DocumentationTargetFinder.findTargetElementAndContext(
        fixture.getProject(), editor, editor.getCaretModel().getOffset(), fixture.getFile(), false);
      assertNotNull(target);
      String docInfo = generateDocInfo(target.target());
      assertNotNull(docInfo);
      assertFileTextEquals(decorate(docInfo));
    });
  }

  private @NotNull JavaDocInfoGenerator createGenerator(@NotNull PsiElement element) {
    return JavaDocInfoGeneratorFactory.getBuilder(heavyFixture.get().getProject())
      .setPsiElement(element)
      .setHighlightingManager(JavaDocHighlightingManagerImpl.getInstance())
      .setPrinter(getPrinter())
      .create();
  }

  protected @Nullable String generateDocInfo(@Nullable PsiElement element) {
    if (element == null) return null;
    return ReadAction.computeBlocking(() -> JavaDocExternalFilter.filterInternalDocInfo(createGenerator(element).generateDocInfo(null)));
  }

  private void configureByFile() {
    heavyFixture.get().configureByFile(TEST_DATA_FOLDER + heavyFixture.get().getTestName(true) + ".java");
  }

  protected void assertFileTextEquals(String docInfo) {
    assertFileTextEquals(docInfo, heavyFixture.get().getTestName(true) + getExpectedFileExtension());
  }

  private void assertFileTextEquals(String docInfo, String expectedFile) {
    assertEqualsFileText(getTestDataPath() + TEST_DATA_FOLDER + expectedFile, docInfo);
  }

  void assertEqualsFileText(@NotNull String expectedFile, @NotNull String actual) {
    String actualText = replaceEnvironmentDependentContent(actual);
    File filePath = new File(expectedFile);
    String expectedText = loadFile(filePath);
    if (!StringUtil.equals(expectedText, actualText)) {
      String message = "Text mismatch in file: " + filePath.getName();
      throw new FileComparisonFailedError(message, expectedText, actualText, FileUtil.toSystemIndependentName(filePath.getPath()));
    }
  }

  private void useJava7() {
    heavyFixture.get().setUpJdk(IdeaTestUtil.getMockJdk(JavaVersion.compose(7)));
  }

  private void useJava8() {
    heavyFixture.get().setUpJdk(IdeaTestUtil.getMockJdk(JavaVersion.compose(8)));
  }

  private void useJava9() {
    heavyFixture.get().setUpJdk(IdeaTestUtil.getMockJdk(JavaVersion.compose(9)));
  }

  private String generateSignature(PsiClass generatorElement, PsiClass psiClass) {
    return ReadAction.computeBlocking(
      () -> new JavaDocInfoGenerator(heavyFixture.get().getProject(), generatorElement).generateSignature(psiClass));
  }

  private static String loadFile(File file) {
    try {
      return StringUtil.convertLineSeparators(FileUtil.loadFile(file).trim());
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
