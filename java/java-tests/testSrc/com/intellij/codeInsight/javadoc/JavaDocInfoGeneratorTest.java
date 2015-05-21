/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.codeInsight.javadoc;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.CodeInsightTestCase;
import com.intellij.lang.java.JavaDocumentationProvider;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PsiTestUtil;

import java.io.File;
import java.io.IOException;

/**
 * @author yole
 */
public class JavaDocInfoGeneratorTest extends CodeInsightTestCase {
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
    final File htmlPath = new File(JavaTestUtil.getJavaTestDataPath() + "/codeInsight/javadocIG/" + name + ".html");
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
    configureByFile("/codeInsight/javadocIG/" + getTestName(true) + ".java");
    return ((PsiJavaFile)myFile).getClasses() [0];
  }

  private void verifyJavaDoc(final PsiElement field) throws IOException {
    String docInfo = new JavaDocInfoGenerator(getProject(), field).generateDocInfo(null);
    assertNotNull(docInfo);
    assertEquals(exampleHtmlFileText(getTestName(true)), replaceEnvironmentDependentContent(docInfo));
  }

  public void testPackageInfo() throws Exception {
    final String path = JavaTestUtil.getJavaTestDataPath() + "/codeInsight/javadocIG/";
    final String packageInfo = path + getTestName(true);
    PsiTestUtil.createTestProjectStructure(myProject, myModule, path, myFilesToDelete);
    final String info =
      new JavaDocInfoGenerator(getProject(), JavaPsiFacade.getInstance(getProject()).findPackage(getTestName(true))).generateDocInfo(null);
    String htmlText = FileUtil.loadFile(new File(packageInfo + File.separator + "packageInfo.html"));
    assertNotNull(info);
    assertEquals(StringUtil.convertLineSeparators(htmlText.trim()), replaceEnvironmentDependentContent(info));
  }

  public void testInheritedParameter() throws Exception {
    configureByFile("/codeInsight/javadocIG/" + getTestName(true) + ".java");
    PsiClass outerClass = ((PsiJavaFile) myFile).getClasses()[0];
    PsiClass innerClass = outerClass.findInnerClassByName("Impl", false);
    assertNotNull(innerClass);
    PsiParameter parameter = innerClass.getMethods()[0].getParameterList().getParameters()[0];
    verifyJavaDoc(parameter);
  }
  
  public void testHtmlLink() throws Exception {
    PsiTestUtil.createTestProjectStructure(myProject, myModule, 
                                           getTestDataPath() + "/codeInsight/javadocIG/htmlLinkProject", myFilesToDelete);
    verifyJavadocFor("htmlLink");
    verifyJavadocFor("pack.htmlLinkDeep");
  }

  private void verifyJavadocFor(String className) throws IOException {
    PsiClass psiClass = JavaPsiFacade.getInstance(myProject).findClass(className, GlobalSearchScope.allScope(myProject));
    assertNotNull(psiClass);
    String doc = new JavaDocInfoGenerator(myProject, psiClass).generateDocInfo(null);
    assertNotNull(doc);
    PsiDirectory dir = (PsiDirectory)psiClass.getParent().getParent();
    PsiFile htmlFile = dir.findFile(psiClass.getName() + ".html");
    assertNotNull(htmlFile);
    assertEquals(StringUtil.convertLineSeparators(new String(htmlFile.getVirtualFile().contentsToByteArray()).trim()), 
                 replaceEnvironmentDependentContent(doc));
  }
  
  public void testHtmlLinkWithRef() throws Exception {
    verifyJavaDoc(getTestClass());
  }
  
  public void testMultipleSpacesInLiteral() throws Exception {
    useJava8();
    verifyJavaDoc(getTestClass());
  }

  public void testLegacySpacesInLiteral() throws Exception {
    useJava7();
    verifyJavaDoc(getTestClass());
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
    return StringUtil.convertLineSeparators(html.trim()).replaceAll("<base href=\"[^\"]*\">", "<base href=\"placeholder\">");
  }
}
