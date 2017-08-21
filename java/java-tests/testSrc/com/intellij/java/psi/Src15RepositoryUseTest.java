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
package com.intellij.java.psi;

import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.AnnotatedMembersSearch;
import com.intellij.psi.search.searches.AnnotatedPackagesSearch;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.PsiTestCase;
import com.intellij.testFramework.PsiTestUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * @author dsl
 */
@PlatformTestCase.WrapInCommand
public class Src15RepositoryUseTest extends PsiTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();

    LanguageLevelProjectExtension.getInstance(myProject).setLanguageLevel(LanguageLevel.JDK_1_5);
    String root = PathManagerEx.getTestDataPath() + "/psi/repositoryUse/src15";
    PsiTestUtil.removeAllRoots(myModule, IdeaTestUtil.getMockJdk17("mock 1.5"));
    PsiTestUtil.createTestProjectStructure(myProject, myModule, root, myFilesToDelete);
  }

  @Override
  protected void tearDown() throws Exception {
    LanguageLevelProjectExtension.getInstance(myProject).setLanguageLevel(LanguageLevel.JDK_1_5);
    super.tearDown();
  }


  public void testStaticImports() {
    setupLoadingFilter();
    final PsiClass aClass = findClass("staticImports.StaticImports");
    final PsiJavaFile javaFile = (PsiJavaFile)aClass.getContainingFile();
    doTestStaticImports(javaFile, false);
    tearDownLoadingFilter();
    doTestStaticImports(javaFile, true);
  }

  public void testDeprecatedAnnotation() {
    setupLoadingFilter();

    final PsiClass aClass = findClass("annotations.DeprecatedAnnotation");
    assertTrue(aClass.isDeprecated());
    PsiMethod method = aClass.getMethods()[0];
    assertTrue(method.isDeprecated());
    PsiField field = aClass.getFields()[0];
    assertTrue(field.isDeprecated());
    tearDownLoadingFilter();
  }

  public void testEnumImplements() {
    setupLoadingFilter();

    final PsiClass aClass = findClass("enumImplements.MyEnum");
    final PsiClassType[] implementsListTypes = aClass.getImplementsListTypes();
    assertEquals(1, implementsListTypes.length);

    final PsiClass baseInterface = implementsListTypes[0].resolve();
    assertNotNull(baseInterface);
    assertEquals("I", baseInterface.getName());
    tearDownLoadingFilter();
  }

  private static void doTestStaticImports(final PsiJavaFile javaFile, boolean okToLoadTree) {
    final PsiImportList importList = javaFile.getImportList();
    assertNotNull(importList);
    final PsiImportStatementBase[] allImportStatements = importList.getAllImportStatements();
    assertEquals(6, allImportStatements.length);
    final PsiImportStatement[] importStatements = importList.getImportStatements();
    assertEquals(2, importStatements.length);
    final PsiImportStaticStatement[] importStaticStatements = importList.getImportStaticStatements();
    assertEquals(4, importStaticStatements.length);
    assertFalse(importStatements[0].isOnDemand());
    assertTrue(importStaticStatements[0].isOnDemand());
    assertFalse(importStatements[0].isOnDemand());
    assertTrue(importStaticStatements[1].isOnDemand());
    assertFalse(importStaticStatements[2].isOnDemand());
    assertFalse(importStaticStatements[3].isOnDemand());

    assertEquals("sort", importStaticStatements[2].getReferenceName());
    assertEquals("sort", importStaticStatements[3].getReferenceName());

    final PsiImportStaticStatement classReference1 = importStaticStatements[1];
    final PsiElement element1 = classReference1.resolveTargetClass();
    assertNotNull(element1);
    assertTrue(element1 instanceof PsiClass);
    assertEquals("java.util.Arrays", ((PsiClass)element1).getQualifiedName());

    final PsiImportStaticStatement classReference3 = importStaticStatements[3];
    final PsiElement element3 = classReference3.resolveTargetClass();
    assertNotNull(element3);
    assertTrue(element3 instanceof PsiClass);
    assertEquals("java.util.Arrays", ((PsiClass)element3).getQualifiedName());

    if (okToLoadTree) {
      assertEquals("java.util.Collections", getText(importStaticStatements[0]));
      assertEquals("java.util.Arrays", getText(importStaticStatements[1]));
      assertEquals("java.util.Collections.sort", getText(importStaticStatements[2]));
      assertEquals("java.util.Arrays.sort", getText(importStaticStatements[3]));
    }
  }

  private static String getText(PsiImportStaticStatement statement) {
    final PsiJavaCodeReferenceElement reference = statement.getImportReference();
    return reference != null ? reference.getText() : "(null ref)";
  }

  public void testEnum() {
    setupLoadingFilter();
    final PsiClass enumClass = findClass("enums.OurEnum");
    assertTrue(enumClass.isEnum());
    final PsiClass superClass = enumClass.getSuperClass();
    assertNotNull(superClass);
    assertEquals("java.lang.Enum", superClass.getQualifiedName());
    assertTrue(enumClass.isInheritor(superClass, false));
    final PsiClassType[] superTypes = enumClass.getSuperTypes();
    assertEquals(1, superTypes.length);
    assertEquals("java.lang.Enum<enums.OurEnum>", superTypes[0].getCanonicalText());
    final PsiClass[] supers = enumClass.getSupers();
    assertEquals(1, supers.length);
    assertEquals("java.lang.Enum", supers[0].getQualifiedName());
    final PsiClassType[] extendsListTypes = enumClass.getExtendsListTypes();
    assertEquals(1, extendsListTypes.length);
    assertEquals("java.lang.Enum<enums.OurEnum>", extendsListTypes[0].getCanonicalText());
    final PsiSubstitutor superClassSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(superClass, enumClass, PsiSubstitutor.EMPTY);
    assertEquals("java.lang.Enum<enums.OurEnum>", myJavaFacade.getElementFactory().createType(superClass, superClassSubstitutor).getCanonicalText());
    tearDownLoadingFilter();
  }

  public void testEnumWithConstants() {
    setupLoadingFilter();
    PsiClass enumClass = findClass("enums.OurEnumWithConstants");
    assertTrue(enumClass.isEnum());
    checkEnumWithConstants(enumClass, false);
    tearDownLoadingFilter();
    checkEnumWithConstants(enumClass, true);
  }

  public void testEnumWithInitializedConstants() {
    setupLoadingFilter();
    final GlobalSearchScope moduleScope = GlobalSearchScope.moduleScope(myModule);

    PsiClass enumClass = findClass("enums.OurEnumWithInitializedConstants");
    assertTrue(enumClass.isEnum());
    PsiField[] fields = enumClass.getFields();
    assertEquals(3, fields.length);
    assertTrue(fields[0] instanceof PsiEnumConstant);
    assertTrue(fields[1] instanceof PsiEnumConstant);
    assertTrue(fields[2] instanceof PsiEnumConstant);
    PsiAnonymousClass initializingClass0 = ((PsiEnumConstant)fields[0]).getInitializingClass();
    assertNotNull(initializingClass0);
    PsiClass baseClass0 = initializingClass0.getBaseClassType().resolve();
    assertTrue(baseClass0 == enumClass);

    PsiAnonymousClass initializingClass1 = ((PsiEnumConstant)fields[1]).getInitializingClass();
    assertNotNull(initializingClass1);
    PsiClass baseClass1 = initializingClass1.getBaseClassType().resolve();
    assertTrue(baseClass1 == enumClass);

    PsiAnonymousClass initializingClass2 = ((PsiEnumConstant)fields[1]).getInitializingClass();
    assertNotNull(initializingClass2);
    PsiClass baseClass2 = initializingClass2.getBaseClassType().resolve();
    assertTrue(baseClass2 == enumClass);

    assertTrue(initializingClass0.isInheritor(enumClass, false));
    assertTrue(initializingClass1.isInheritor(enumClass, false));
    assertTrue(initializingClass2.isInheritor(enumClass, false));

    final PsiClass[] enumInheritors = ClassInheritorsSearch.search(enumClass, moduleScope, false).toArray(PsiClass.EMPTY_ARRAY);
    assertEquals(3, enumInheritors.length);
    assertTrue(Arrays.asList(enumInheritors).contains(initializingClass0));
    assertTrue(Arrays.asList(enumInheritors).contains(initializingClass1));
    assertTrue(Arrays.asList(enumInheritors).contains(initializingClass2));


    PsiMethod[] methods1 = initializingClass2.getMethods();
    assertEquals(1, methods1.length);
    assertEquals("foo", methods1[0].getName());

    final PsiClass baseInterfaceClass = findClass("enums.OurBaseInterface");

    final PsiClass[] inheritors = ClassInheritorsSearch.search(baseInterfaceClass, moduleScope, false).toArray(PsiClass.EMPTY_ARRAY);
    assertEquals(1, inheritors.length);
    assertTrue(inheritors[0] instanceof PsiAnonymousClass);

    tearDownLoadingFilter();

    assertTrue(inheritors[0].getParent().getParent() instanceof PsiExpressionList);
    assertTrue(inheritors[0].getParent().getParent().getParent() == fields[2]);

    final PsiExpressionList argumentList = ((PsiEnumConstant)fields[2]).getArgumentList();
    assertNotNull(argumentList);
    final PsiExpression[] expressions2 = argumentList.getExpressions();
    assertEquals(1, expressions2.length);
    assertTrue(expressions2[0] instanceof PsiNewExpression);
    final PsiAnonymousClass anonymousClass2 = ((PsiNewExpression)expressions2[0]).getAnonymousClass();
    assertTrue(anonymousClass2 != null);
    assertTrue(anonymousClass2.isInheritor(baseInterfaceClass, false));
  }

  private static void checkEnumWithConstants(PsiClass enumClass, boolean okToLoadTree) {
    PsiField[] fields = enumClass.getFields();
    assertEquals(3, fields.length);
    checkEnumConstant("A", fields[0]);
    checkEnumConstant("B", fields[1]);
    checkEnumConstant("C", fields[2]);
    if (okToLoadTree) {
      assertEquals("A", fields[0].getText());
      assertEquals("B", fields[1].getText());
      assertEquals("C", fields[2].getText());
    }
  }

  private static void checkEnumConstant(String expectedName, PsiField field) {
    assertTrue(field instanceof PsiEnumConstant);
    assertEquals(expectedName, field.getName());
    assertTrue(field.hasModifierProperty(PsiModifier.PUBLIC));
    assertTrue(field.hasModifierProperty(PsiModifier.FINAL));
    assertTrue(field.hasModifierProperty(PsiModifier.STATIC));
  }

  public void testEnumWithConstantsAndStaticFields() {
    setupLoadingFilter();
    PsiClass enumClass = findClass("enums.OurEnumWithConstantsAndStaticFields");
    PsiField[] fields = enumClass.getFields();
    assertTrue(fields[0] instanceof PsiEnumConstant);
    assertTrue(fields[1] instanceof PsiEnumConstant);
    assertTrue(fields[2] instanceof PsiEnumConstant);
    assertFalse(fields[3] instanceof PsiEnumConstant);

    tearDownLoadingFilter();

    assertEquals("public static final int A1 = 1;", fields[3].getText());
  }

  public void testEnumWithConstantsAndStaticFields2() {
    setupLoadingFilter();
    PsiClass enumClass = findClass("enums.OurEnumWithConstantsAndStaticFields2");
    PsiField[] fields = enumClass.getFields();
    assertTrue(fields[0] instanceof PsiEnumConstant);
    assertTrue(fields[1] instanceof PsiEnumConstant);
    assertTrue(fields[2] instanceof PsiEnumConstant);
    assertFalse(fields[3] instanceof PsiEnumConstant);

    tearDownLoadingFilter();

    assertEquals("public static final int A1 = 10;", fields[3].getText());
    enumClass.accept(new JavaRecursiveElementWalkingVisitor(){
      @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
        visitExpression(expression);
      }
    });
    enumClass.getText();
  }

  public void testAnnotationType() {
    setupLoadingFilter();
    final PsiClass annotationTypeClass = findClass("annotations.AnnotationType");
    assertTrue(annotationTypeClass.isAnnotationType());
    tearDownLoadingFilter();
  }

  public void testAnnotationIndex() {
    PsiManagerEx.getInstanceEx(getProject()).setAssertOnFileLoadingFilter(new VirtualFileFilter() {
      @Override
      public boolean accept(final VirtualFile file) {
        return !"package-info.java".equals(file.getName());
      }
    }, getTestRootDisposable());

    final PsiClass annotationTypeClass = findClass("annotations.AnnotationType");
    assertTrue(annotationTypeClass.isAnnotationType());

    final Collection<PsiMember> all = AnnotatedMembersSearch.search(annotationTypeClass, GlobalSearchScope.moduleScope(myModule)).findAll();

    assertEquals(2, all.size());
    Set<String> correctNames = new HashSet<>(Arrays.asList("AnnotatedClass", "correctMethod"));
    for (PsiMember member : all) {
      assertTrue(correctNames.contains(member.getName()));
    }

    final Collection<PsiPackage> packages =
      AnnotatedPackagesSearch.search(annotationTypeClass, GlobalSearchScope.moduleScope(myModule)).findAll();
    assertEquals(1, packages.size());
    assertEquals("annotated", packages.iterator().next().getQualifiedName());

    tearDownLoadingFilter();
  }

  private void setupLoadingFilter() {
    PsiManagerEx.getInstanceEx(getProject()).setAssertOnFileLoadingFilter(VirtualFileFilter.ALL, getTestRootDisposable());
  }

  private void tearDownLoadingFilter() {
    PsiManagerEx.getInstanceEx(getProject()).setAssertOnFileLoadingFilter(VirtualFileFilter.NONE, getTestRootDisposable());
  }

  @NotNull
  private PsiClass findClass(final String name) {
    PsiClass aClass = myJavaFacade.findClass(name, GlobalSearchScope.moduleScope(myModule));
    assertNotNull(name, aClass);
    return aClass;
  }
}
