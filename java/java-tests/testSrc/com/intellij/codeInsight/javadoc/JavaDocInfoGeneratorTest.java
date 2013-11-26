package com.intellij.codeInsight.javadoc;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.CodeInsightTestCase;
import com.intellij.lang.java.JavaDocumentationProvider;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.testFramework.PsiTestUtil;

import java.io.File;
import java.io.IOException;

/**
 * @author yole
 */
public class JavaDocInfoGeneratorTest extends CodeInsightTestCase {
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
  
  public void testEnumValueOf() throws Exception {
    doTestMethod();
  }

  public void testInitializerWithNew() throws Exception {
    doTestField();
  }

  public void testInitializerWithLiteral() throws Exception {
    doTestField();
  }

  public void testInitializerWithReference() throws Exception {
    doTestField();
  }

  public void testLiteral() throws Exception {
    doTestField();
  }

  public void testCode() throws Exception {
    doTestField();
  }

  public void testEnumConstantOrdinal() throws Exception {
    PsiClass psiClass = getTestClass();
    PsiField field = psiClass.getFields() [0];
    final File htmlPath = new File(JavaTestUtil.getJavaTestDataPath() + "/codeInsight/javadocIG/" + getTestName(true) + ".html");
    String htmlText = FileUtil.loadFile(htmlPath);
    String docInfo = new JavaDocumentationProvider().getQuickNavigateInfo(field, field);
    assertNotNull(docInfo);
    assertEquals(StringUtil.convertLineSeparators(htmlText.trim()), StringUtil.convertLineSeparators(docInfo.trim()));
  }

  public void testClassTypeParamsPresentation() throws Exception {
    PsiClass psiClass = getTestClass();
    final PsiReferenceList extendsList = psiClass.getExtendsList();
    final PsiJavaCodeReferenceElement referenceElement = extendsList.getReferenceElements()[0];
    final PsiClass superClass = extendsList.getReferencedTypes()[0].resolve();
    final File htmlPath = new File(JavaTestUtil.getJavaTestDataPath() + "/codeInsight/javadocIG/" + getTestName(true) + ".html");
    String htmlText = FileUtil.loadFile(htmlPath);
    String docInfo = new JavaDocumentationProvider().getQuickNavigateInfo(superClass, referenceElement);
    assertNotNull(docInfo);
    assertEquals(StringUtil.convertLineSeparators(htmlText.trim()), StringUtil.convertLineSeparators(docInfo.trim()));
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

  private PsiClass getTestClass() throws Exception{
    configureByFile("/codeInsight/javadocIG/" + getTestName(true) + ".java");
    return ((PsiJavaFile)myFile).getClasses() [0];
  }

  private void verifyJavaDoc(final PsiElement field) throws IOException {
    final File htmlPath = new File(JavaTestUtil.getJavaTestDataPath() + "/codeInsight/javadocIG/" + getTestName(true) + ".html");
    String htmlText = FileUtil.loadFile(htmlPath);
    String docInfo = new JavaDocInfoGenerator(getProject(), field).generateDocInfo(null);
    assertNotNull(docInfo);
    assertEquals(StringUtil.convertLineSeparators(htmlText.trim()), StringUtil.convertLineSeparators(docInfo.trim()));
  }

  public void testPackageInfo() throws Exception {
    final String path = JavaTestUtil.getJavaTestDataPath() + "/codeInsight/javadocIG/";
    final String packageInfo = path + getTestName(true);
    PsiTestUtil.createTestProjectStructure(myProject, myModule, path, myFilesToDelete);
    final String info =
      new JavaDocInfoGenerator(getProject(), JavaPsiFacade.getInstance(getProject()).findPackage(getTestName(true))).generateDocInfo(null);
    String htmlText = FileUtil.loadFile(new File(packageInfo + File.separator + "packageInfo.html"));
    assertNotNull(info);
    assertEquals(StringUtil.convertLineSeparators(htmlText.trim()), StringUtil.convertLineSeparators(info.trim()));
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }
}
