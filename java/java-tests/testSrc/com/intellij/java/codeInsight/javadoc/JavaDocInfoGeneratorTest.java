/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.java.codeInsight.javadoc;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.CodeInsightTestCase;
import com.intellij.java.codeInsight.JavaExternalDocumentationTest;
import com.intellij.lang.java.JavaDocumentationProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.DumbServiceImpl;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.lang.annotations.Flow;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
public class JavaDocInfoGeneratorTest extends CodeInsightTestCase {
  private static final String TEST_DATA_FOLDER = "/codeInsight/javadocIG/";

  private boolean myUseJava8Sdk;
  
  public void testSimpleField() throws Exception {
    doTestField();
  }

  public void testFieldValue() throws Exception {
    doTestField();
  }

  public void testValueInMethod() throws Exception {
    doTestMethod();
  }

  public void testValueInMethodNoHash() throws Exception {
    doTestMethod();
  }

  public void testEscapingStringValue() throws Exception {
    doTestMethod();
  }

  public void testIdeadev2326() throws Exception {
    doTestMethod();
  }

  public void testMethodTypeParameter() throws Exception {
    doTestMethod();
  }

  public void testAnonymousAndSuperJavadoc() throws Exception {
    PsiClass psiClass = getTestClass();
    assertNotNull(psiClass);
    psiClass = PsiTreeUtil.findChildOfType(psiClass, PsiAnonymousClass.class);
    assertNotNull(psiClass);
    PsiMethod method = psiClass.getMethods() [0];
    verifyJavaDoc(method);
  }
  
  public void testInheritedDocInThrows() throws Exception {
    doTestMethod();
  }
  
  public void testInheritedDocInThrows1() throws Exception {
    doTestMethod();
  }
  
  public void testEscapeValues() throws Exception {
    PsiClass psiClass = getTestClass();
    verifyJavaDoc(psiClass);
  }

  public void testClassTypeParameter() throws Exception {
    verifyJavaDoc(getTestClass());
  }
  
  public void testUnicodeEscapes() throws Exception {
    verifyJavaDoc(getTestClass());
  }
  
  public void testEnumValueOf() throws Exception {
    doTestMethod();
  }

  public void testMethodFormatting() throws Exception {
    doTestMethod();
  }

  public void testConstantFieldInitializer() throws Exception {
    doTestField();
  }

  public void testInitializerWithNew() throws Exception {
    doTestField();
  }

  public void testInitializerWithLiteral() throws Exception {
    doTestField();
  }

  public void testMethodExpressionWithLiteral() throws Exception {
    doTestField();
  }

  public void testInitializerWithReference() throws Exception {
    doTestField();
  }

  public void testAnnotations() throws Exception {
    doTestField();
  }

  public void testAnnotationsInParams() throws Exception {
    doTestMethod();
  }

  public void testApiNotes() throws Exception {
    doTestMethod();
  }

  public void testLiteral() throws Exception {
    doTestField();
  }

  public void testEscapingInLiteral() throws Exception {
    doTestField();
  }

  public void testCode() throws Exception {
    useJava8();
    doTestField();
  }

  public void testPInsidePre() throws Exception {
    doTestField();
  }

  public void testCommaInsideArgsList() throws Exception {
    doTestField();
  }

  public void testFieldInitializedWithLambda() throws Exception {
    doTestField();
  }

  public void testFieldInitializedWithArray() throws Exception {
    doTestField();
  }

  public void testFieldInitializedWithSizedArray() throws Exception {
    doTestField();
  }

  public void testDoubleLt() throws Exception {
    doTestClass();
  }

  public void testEnumConstantOrdinal() throws Exception {
    PsiClass psiClass = getTestClass();
    PsiField field = psiClass.getFields() [0];
    String docInfo = new JavaDocumentationProvider().generateDoc(field, field);
    assertNotNull(docInfo);
    assertEquals(exampleHtmlFileText(getTestName(true)), replaceEnvironmentDependentContent(docInfo));

    docInfo = new JavaDocumentationProvider().getQuickNavigateInfo(field, field);
    assertNotNull(docInfo);
    assertEquals(exampleHtmlFileText(getTestName(true) + "_quick"), replaceEnvironmentDependentContent(docInfo));
  }

  public void testClickableFieldReference() throws Exception {
    PsiClass aClass = getTestClass();
    PsiTypeElement element = aClass.getFields()[0].getTypeElement();
    assertNotNull(element);
    PsiJavaCodeReferenceElement innermostComponentReferenceElement = element.getInnermostComponentReferenceElement();
    assertNotNull(innermostComponentReferenceElement);
    String docInfo = new JavaDocumentationProvider().generateDoc(innermostComponentReferenceElement.resolve(), element);
    assertNotNull(docInfo);
    assertEquals(exampleHtmlFileText(getTestName(true)), replaceEnvironmentDependentContent(docInfo));
  }
  
  public void testNoSpaceAfterTagName() throws Exception {
    verifyJavaDoc(getTestClass());
  }

  private static String exampleHtmlFileText(String name) throws IOException {
    final File htmlPath = new File(JavaTestUtil.getJavaTestDataPath() + TEST_DATA_FOLDER + name + ".html");
    return StringUtil.convertLineSeparators(FileUtil.loadFile(htmlPath).trim());
  }

  public void testClassTypeParamsPresentation() throws Exception {
    PsiClass psiClass = getTestClass();
    final PsiReferenceList extendsList = psiClass.getExtendsList();
    assertNotNull(extendsList);
    final PsiJavaCodeReferenceElement referenceElement = extendsList.getReferenceElements()[0];
    final PsiClass superClass = extendsList.getReferencedTypes()[0].resolve();

    String docInfo = new JavaDocumentationProvider().getQuickNavigateInfo(superClass, referenceElement);
    assertNotNull(docInfo);
    assertEquals(exampleHtmlFileText(getTestName(true)), replaceEnvironmentDependentContent(docInfo));
  }

  public void testLambdaParameter() throws Exception {
    doTestLambdaParameter();
  }

  private void doTestClass() throws Exception {
    PsiClass psiClass = getTestClass();
    verifyJavaDoc(psiClass);
  }

  private void doTestField() throws Exception {
    PsiClass psiClass = getTestClass();
    PsiField field = psiClass.getFields() [0];
    verifyJavaDoc(field);
  }

  private void doTestMethod() throws Exception {
    PsiClass psiClass = getTestClass();
    PsiMethod method = psiClass.getMethods() [0];
    verifyJavaDoc(method);
  }

  private void doTestLambdaParameter() throws Exception {
    PsiClass psiClass = getTestClass();
    final PsiLambdaExpression lambdaExpression = PsiTreeUtil.findChildOfType(psiClass, PsiLambdaExpression.class);
    assertNotNull(lambdaExpression);
    verifyJavaDoc(lambdaExpression.getParameterList().getParameters()[0]);
  }

  private PsiClass getTestClass() throws Exception{
    configureByFile();
    return ((PsiJavaFile)myFile).getClasses() [0];
  }

  private void verifyJavaDoc(final PsiElement field) throws IOException {
    verifyJavaDoc(field, null);
  }

  private void verifyJavaDoc(final PsiElement field, List<String> docUrls) throws IOException {
    String docInfo = JavaDocumentationProvider.generateExternalJavadoc(field, docUrls);
    assertNotNull(docInfo);
    assertEquals(exampleHtmlFileText(getTestName(true)), replaceEnvironmentDependentContent(docInfo));
  }

  public void testPackageInfo() throws Exception {
    final String path = JavaTestUtil.getJavaTestDataPath() + TEST_DATA_FOLDER;
    final String packageInfo = path + getTestName(true);
    PsiTestUtil.createTestProjectStructure(myProject, myModule, path, myFilesToDelete);
    PsiPackage psiPackage = JavaPsiFacade.getInstance(getProject()).findPackage(getTestName(true));
    final String info = JavaDocumentationProvider.generateExternalJavadoc(psiPackage, (List<String>)null);
    String htmlText = FileUtil.loadFile(new File(packageInfo + File.separator + "packageInfo.html"));
    assertNotNull(info);
    assertEquals(StringUtil.convertLineSeparators(htmlText.trim()), replaceEnvironmentDependentContent(info));
  }
  
  public void testPackageInfoFromComment() throws Exception {
    doTestPackageInfo("some");
  }

  public void testPackageInfoWithCopyright() throws Exception {
    doTestPackageInfo("packageInfoWithCopyright");
  }
  
  private void doTestPackageInfo(String caretPositionedAt) throws Exception {
    final String rootPath = getTestDataPath() + TEST_DATA_FOLDER;
    VirtualFile root = PsiTestUtil.createTestProjectStructure(myProject, myModule, rootPath, myFilesToDelete);
    VirtualFile piFile = root.findFileByRelativePath(getTestName(true) + "/package-info.java");
    assertNotNull(piFile);
    PsiFile psiFile = PsiManager.getInstance(myProject).findFile(piFile);
    assertNotNull(psiFile);
    final String info = JavaExternalDocumentationTest.getDocumentationText(psiFile, psiFile.getText().indexOf(caretPositionedAt));
    String htmlText = FileUtil.loadFile(new File(rootPath + getTestName(true) + File.separator + "packageInfo.html"));
    assertEquals(StringUtil.convertLineSeparators(htmlText.trim()), replaceEnvironmentDependentContent(info));
  }

  public void testInheritedParameter() throws Exception {
    configureByFile();
    PsiClass outerClass = ((PsiJavaFile) myFile).getClasses()[0];
    PsiClass innerClass = outerClass.findInnerClassByName("Impl", false);
    assertNotNull(innerClass);
    PsiParameter parameter = innerClass.getMethods()[0].getParameterList().getParameters()[0];
    verifyJavaDoc(parameter);
  }
  
  public void testHtmlLink() throws Exception {
    PsiTestUtil.createTestProjectStructure(myProject, myModule, 
                                           getTestDataPath() + TEST_DATA_FOLDER + "htmlLinkProject", myFilesToDelete);
    verifyJavadocFor("htmlLink");
    verifyJavadocFor("pack.htmlLinkDeep");
  }

  private void verifyJavadocFor(String className) throws IOException {
    PsiClass psiClass = JavaPsiFacade.getInstance(myProject).findClass(className, GlobalSearchScope.allScope(myProject));
    assertNotNull(psiClass);
    String doc = JavaDocumentationProvider.generateExternalJavadoc(psiClass, (List<String>)null);
    assertNotNull(doc);
    PsiDirectory dir = (PsiDirectory)psiClass.getParent().getParent();
    PsiFile htmlFile = dir.findFile(psiClass.getName() + ".html");
    assertNotNull(htmlFile);
    assertEquals(StringUtil.convertLineSeparators(new String(htmlFile.getVirtualFile().contentsToByteArray(), "UTF-8").trim()), 
                 replaceEnvironmentDependentContent(doc));
  }
  
  public void testHtmlLinkWithRef() throws Exception {
    verifyJavaDoc(getTestClass());
  }
  
  public void testHtmlLinkToPackageInfo() throws Exception {
    PsiTestUtil.createTestProjectStructure(myProject, myModule,
                                           getTestDataPath() + TEST_DATA_FOLDER + "htmlLinkToPackageInfo", myFilesToDelete);
    verifyJavadocFor("pack.A");
  }
  
  public void testMultipleSpacesInLiteral() throws Exception {
    useJava8();
    verifyJavaDoc(getTestClass());
  }

  public void testLegacySpacesInLiteral() throws Exception {
    useJava7();
    verifyJavaDoc(getTestClass());
  }

  public void testHideNonDocumentedFlowAnnotations() {
    ModuleRootModificationUtil.setModuleSdk(myModule, removeAnnotationsJar(PsiTestUtil.addJdkAnnotations(IdeaTestUtil.getMockJdk17())));

    PsiMethod mapPut = myJavaFacade.findClass(CommonClassNames.JAVA_UTIL_MAP, GlobalSearchScope.allScope(myProject))
      .findMethodsByName("put", false)[0];

    PsiAnnotation annotation = AnnotationUtil.findAnnotation(mapPut, Flow.class.getName());
    assertNotNull(annotation);
    assertNull(annotation.getNameReferenceElement().resolve());

    String doc = JavaDocumentationProvider.generateExternalJavadoc(mapPut);
    assertFalse(doc, doc.contains("Flow"));
  }

  private static Sdk removeAnnotationsJar(Sdk sdk) {
    SdkModificator modificator = sdk.getSdkModificator();
    VirtualFile annotationsJar = ContainerUtil.find(modificator.getRoots(OrderRootType.CLASSES), r -> r.getName().contains("annotations"));
    modificator.removeRoot(annotationsJar, OrderRootType.CLASSES);
    modificator.commitChanges();
    return sdk;
  }

  public void testMatchingParameterNameFromParent() throws Exception {
    configureByFile();
    PsiClass psiClass = ((PsiJavaFile)myFile).getClasses()[1];
    PsiMethod method = psiClass.getMethods()[0];
    verifyJavaDoc(method);
  }

  public void testMatchingTypeParameterNameFromParent() throws Exception {
    configureByFile();
    PsiClass psiClass = ((PsiJavaFile)myFile).getClasses()[1];
    PsiMethod method = psiClass.getMethods()[0];
    verifyJavaDoc(method);
  }
  
  public void testDocumentationForJdkClassWhenExternalDocIsNotAvailable() throws Exception {
    PsiClass aClass = JavaPsiFacade.getInstance(myProject).findClass("java.lang.String", GlobalSearchScope.allScope(myProject));
    assertNotNull(aClass);
    verifyJavaDoc(aClass, Collections.singletonList("dummyUrl"));
  }

  public void testDocumentationForJdkClassWithReferencesToClassesFromJavaLang() throws Exception {
    doTestAtCaret();
  }

  public void testDocumentationForUncheckedExceptionsInSupers() throws Exception {
    doTestAtCaret();
  }

  public void testDocumentationForGetterByField() throws Exception {
    doTestAtCaret();
  }

  public void testDumbMode() throws Exception {
    DumbServiceImpl.getInstance(myProject).setDumb(true);
    try {
      doTestAtCaret();
    }
    finally {
      DumbServiceImpl.getInstance(myProject).setDumb(false);
    }
   }

  private void doTestAtCaret() throws Exception {
    configureByFile();
    String docInfo = JavaExternalDocumentationTest.getDocumentationText(myFile, myEditor.getCaretModel().getOffset());
    assertNotNull(docInfo);
    assertEquals(exampleHtmlFileText(getTestName(true)), replaceEnvironmentDependentContent(docInfo));
  }

  private void configureByFile() throws Exception {
    configureByFile(TEST_DATA_FOLDER + getTestName(true) + ".java");
  }

  public void testLibraryPackageDocumentation() throws Exception {
    final VirtualFile libClasses = JavaExternalDocumentationTest.getJarFile("library.jar");
    final VirtualFile libSources = JavaExternalDocumentationTest.getJarFile("library-src.jar");

    ApplicationManager.getApplication().runWriteAction(() -> {
      final Library library = LibraryTablesRegistrar.getInstance().getLibraryTable(myProject).createLibrary("myLib");
      final Library.ModifiableModel model = library.getModifiableModel();
      model.addRoot(libClasses, OrderRootType.CLASSES);
      model.addRoot(libSources, OrderRootType.SOURCES);
      model.commit();

      Module[] modules = ModuleManager.getInstance(myProject).getModules();
      assertSize(1, modules);
      ModuleRootModificationUtil.addDependency(modules[0], library);
    });
    
    PsiPackage aPackage = JavaPsiFacade.getInstance(myProject).findPackage("com.jetbrains");
    assertNotNull(aPackage);
    verifyJavaDoc(aPackage);
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  @Override
  protected Sdk getTestProjectJdk() {
    return myUseJava8Sdk ? IdeaTestUtil.getMockJdk18() : IdeaTestUtil.getMockJdk17();
  }
  
  private void useJava8() {
    myUseJava8Sdk = true;
    setUpJdk();
  }
  
  private void useJava7() {
    myUseJava8Sdk = false;
    setUpJdk();
  }

  private static String replaceEnvironmentDependentContent(String html) {
    return html == null ? null : StringUtil.convertLineSeparators(html.trim()).replaceAll("<base href=\"[^\"]*\">", 
                                                                                          "<base href=\"placeholder\">");
  }
}
