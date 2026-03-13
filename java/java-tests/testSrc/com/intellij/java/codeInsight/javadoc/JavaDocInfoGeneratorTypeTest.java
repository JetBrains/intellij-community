// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.javadoc;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.ExternalAnnotationLineMarkerProvider;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.javadoc.AnnotationDocGenerator;
import com.intellij.codeInsight.javadoc.NonCodeAnnotationGenerator;
import com.intellij.openapi.fileTypes.BinaryFileTypeDecompilers;
import com.intellij.openapi.project.Project;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.impl.compiled.ClsClassImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.java.codeInsight.javadoc.JavaDocInfoGeneratorTest.assertEqualsFileText;

@TestDataPath("$CONTENT_ROOT/testData/codeInsight/javadocIG/")
public class JavaDocInfoGeneratorTypeTest extends LightJavaCodeInsightFixtureTestCase {

  private static final String TEST_DATA_FOLDER = "/codeInsight/javadocIG/generatedTooltip/";

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_26;
  }

  public void testOptionalCountClassTypeParameter() {
    Project project = getProject();
    JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    PsiClass optionalClass = facade.findClass(CommonClassNames.JAVA_UTIL_OPTIONAL, GlobalSearchScope.allScope(project));
    PsiFile containingFile = optionalClass.getContainingFile();
    myFixture.configureFromExistingVirtualFile(containingFile.getVirtualFile());
    MultiMap<PsiElement, AnnotationDocGenerator> annotations = NonCodeAnnotationGenerator.getSignatureNonCodeAnnotations(optionalClass);
    assertEquals(1, annotations.size());
  }

  public void testOptionalOrTypeParameter() {
    Project project = getProject();
    JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    PsiClass optionalClass = facade.findClass(CommonClassNames.JAVA_UTIL_OPTIONAL, GlobalSearchScope.allScope(project));
    PsiFile containingFile = optionalClass.getContainingFile();
    myFixture.configureFromExistingVirtualFile(containingFile.getVirtualFile());

    PsiMethod[] ors = optionalClass.findMethodsByName("or", false);
    assertEquals(1, ors.length);

    MultiMap<PsiElement, AnnotationDocGenerator> annotations = NonCodeAnnotationGenerator.getSignatureNonCodeAnnotations(ors[0]);
    assertEquals(3, annotations.size());
  }

  public void testComparatorNaturalOrderTypeParameter() {
    Project project = getProject();
    JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    PsiClass comparatorClass = facade.findClass(CommonClassNames.JAVA_UTIL_COMPARATOR, GlobalSearchScope.allScope(project));
    PsiFile containingFile = comparatorClass.getContainingFile();
    myFixture.configureFromExistingVirtualFile(containingFile.getVirtualFile());

    PsiMethod[] ors = comparatorClass.findMethodsByName("naturalOrder", false);
    assertEquals(1, ors.length);

    MultiMap<PsiElement, AnnotationDocGenerator> annotations = NonCodeAnnotationGenerator.getSignatureNonCodeAnnotations(ors[0]);
    assertEquals(2, annotations.size());
  }

  public void testComparatorNaturalOrderText() {
    Project project = getProject();
    JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    ClsClassImpl comparatorClass =
      (ClsClassImpl)facade.findClass(CommonClassNames.JAVA_UTIL_COMPARATOR, GlobalSearchScope.allScope(project));
    PsiClass mirrorClass = BinaryFileTypeDecompilers.getInstance().allowDecompileOnEDT(() -> ((PsiClass)comparatorClass.getMirror()));

    PsiFile containingFile = comparatorClass.getContainingFile();
    myFixture.configureFromExistingVirtualFile(containingFile.getVirtualFile());

    PsiMethod[] mirrorMethods = mirrorClass.findMethodsByName("naturalOrder", false);
    assertEquals(1, mirrorMethods.length);

    List<LineMarkerInfo> infos = new ArrayList<>();
    new ExternalAnnotationLineMarkerProvider().collectSlowLineMarkers(List.of(mirrorMethods[0].getNameIdentifier()), infos);

    assertEquals(1, infos.size());

    String tooltip = infos.getFirst().getLineMarkerTooltip();
    assertFileTextEquals(tooltip);
  }

  public void testOptionalClassText() {
    Project project = getProject();
    JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    ClsClassImpl optionalClass = (ClsClassImpl)facade.findClass(CommonClassNames.JAVA_UTIL_OPTIONAL, GlobalSearchScope.allScope(project));
    PsiClass mirrorClass = BinaryFileTypeDecompilers.getInstance().allowDecompileOnEDT(() -> ((PsiClass)optionalClass.getMirror()));

    List<LineMarkerInfo> infos = new ArrayList<>();
    new ExternalAnnotationLineMarkerProvider().collectSlowLineMarkers(List.of(mirrorClass.getNameIdentifier()), infos);

    assertEquals(1, infos.size());

    String tooltip = infos.getFirst().getLineMarkerTooltip();
    assertFileTextEquals(tooltip);
  }

  public void testOptionalOrClassText() {
    Project project = getProject();
    JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    ClsClassImpl optionalClass = (ClsClassImpl)facade.findClass(CommonClassNames.JAVA_UTIL_OPTIONAL, GlobalSearchScope.allScope(project));
    PsiClass mirrorClass = BinaryFileTypeDecompilers.getInstance().allowDecompileOnEDT(() -> ((PsiClass)optionalClass.getMirror()));

    PsiMethod[] mirrorMethods = mirrorClass.findMethodsByName("or", false);
    assertEquals(1, mirrorMethods.length);

    List<LineMarkerInfo> infos = new ArrayList<>();
    new ExternalAnnotationLineMarkerProvider().collectSlowLineMarkers(List.of(mirrorMethods[0].getNameIdentifier()), infos);

    assertEquals(1, infos.size());

    String tooltip = infos.getFirst().getLineMarkerTooltip();
    assertFileTextEquals(tooltip);
  }


  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  private void assertFileTextEquals(String docInfo) {
    assertFileTextEquals(docInfo, getTestName(true) + ".html");
  }

  private void assertFileTextEquals(String docInfo, String expectedFile) {
    assertEqualsFileText(getTestDataPath() + TEST_DATA_FOLDER + expectedFile, docInfo);
  }
}