// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi;

import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaRecursiveElementWalkingVisitor;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiEnumConstant;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiImportList;
import com.intellij.psi.PsiImportStatement;
import com.intellij.psi.PsiImportStatementBase;
import com.intellij.psi.PsiImportStaticStatement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.AnnotatedMembersSearch;
import com.intellij.psi.search.searches.AnnotatedPackagesSearch;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.JavaPsiTestCase;
import com.intellij.testFramework.PsiTestUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;

@HeavyPlatformTestCase.WrapInCommand
public class Src15RepositoryUseTest extends JavaPsiTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();

    IdeaTestUtil.setProjectLanguageLevel(myProject, LanguageLevel.JDK_1_5);
    String root = PathManagerEx.getTestDataPath() + "/psi/repositoryUse/src15";
    PsiTestUtil.removeAllRoots(myModule, IdeaTestUtil.getMockJdk17());
    createTestProjectStructure( root);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      IdeaTestUtil.setProjectLanguageLevel(myProject, LanguageLevel.JDK_1_5);
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  public void testStaticImports() {
    setupLoadingFilter();
    PsiClass aClass = findClass("staticImports.StaticImports");
    PsiJavaFile javaFile = (PsiJavaFile)aClass.getContainingFile();
    doTestStaticImports(javaFile, false);
    tearDownLoadingFilter();
    doTestStaticImports(javaFile, true);
  }

  public void testDeprecatedAnnotation() {
    setupLoadingFilter();

    PsiClass aClass = findClass("annotations.DeprecatedAnnotation");
    assertTrue(aClass.isDeprecated());
    PsiMethod method = aClass.getMethods()[0];
    assertTrue(method.isDeprecated());
    PsiField field = aClass.getFields()[0];
    assertTrue(field.isDeprecated());
    tearDownLoadingFilter();
  }

  public void testEnumImplements() {
    setupLoadingFilter();

    PsiClass aClass = findClass("enumImplements.MyEnum");
    PsiClassType[] implementsListTypes = aClass.getImplementsListTypes();
    assertEquals(1, implementsListTypes.length);

    PsiClass baseInterface = implementsListTypes[0].resolve();
    assertNotNull(baseInterface);
    assertEquals("I", baseInterface.getName());
    tearDownLoadingFilter();
  }

  private static void doTestStaticImports(PsiJavaFile javaFile, boolean okToLoadTree) {
    PsiImportList importList = javaFile.getImportList();
    assertNotNull(importList);
    PsiImportStatementBase[] allImportStatements = importList.getAllImportStatements();
    assertEquals(6, allImportStatements.length);
    PsiImportStatement[] importStatements = importList.getImportStatements();
    assertEquals(2, importStatements.length);
    PsiImportStaticStatement[] importStaticStatements = importList.getImportStaticStatements();
    assertEquals(4, importStaticStatements.length);
    assertFalse(importStatements[0].isOnDemand());
    assertTrue(importStaticStatements[0].isOnDemand());
    assertFalse(importStatements[0].isOnDemand());
    assertTrue(importStaticStatements[1].isOnDemand());
    assertFalse(importStaticStatements[2].isOnDemand());
    assertFalse(importStaticStatements[3].isOnDemand());

    assertEquals("sort", importStaticStatements[2].getReferenceName());
    assertEquals("sort", importStaticStatements[3].getReferenceName());

    PsiImportStaticStatement classReference1 = importStaticStatements[1];
    PsiClass element1 = classReference1.resolveTargetClass();
    assertNotNull(element1);

    assertEquals("java.util.Arrays", element1.getQualifiedName());

    PsiImportStaticStatement classReference3 = importStaticStatements[3];
    PsiClass element3 = classReference3.resolveTargetClass();
    assertNotNull(element3);
    assertEquals("java.util.Arrays", element3.getQualifiedName());

    if (okToLoadTree) {
      assertEquals("java.util.Collections", getText(importStaticStatements[0]));
      assertEquals("java.util.Arrays", getText(importStaticStatements[1]));
      assertEquals("java.util.Collections.sort", getText(importStaticStatements[2]));
      assertEquals("java.util.Arrays.sort", getText(importStaticStatements[3]));
    }
  }

  private static String getText(PsiImportStaticStatement statement) {
    PsiJavaCodeReferenceElement reference = statement.getImportReference();
    return reference != null ? reference.getText() : "(null ref)";
  }

  public void testEnum() {
    setupLoadingFilter();
    PsiClass enumClass = findClass("enums.OurEnum");
    assertTrue(enumClass.isEnum());
    PsiClass superClass = enumClass.getSuperClass();
    assertNotNull(superClass);
    assertEquals("java.lang.Enum", superClass.getQualifiedName());
    assertTrue(enumClass.isInheritor(superClass, false));
    PsiClassType[] superTypes = enumClass.getSuperTypes();
    assertEquals(1, superTypes.length);
    assertEquals("java.lang.Enum<enums.OurEnum>", superTypes[0].getCanonicalText());
    PsiClass[] supers = enumClass.getSupers();
    assertEquals(1, supers.length);
    assertEquals("java.lang.Enum", supers[0].getQualifiedName());
    PsiClassType[] extendsListTypes = enumClass.getExtendsListTypes();
    assertEquals(1, extendsListTypes.length);
    assertEquals("java.lang.Enum<enums.OurEnum>", extendsListTypes[0].getCanonicalText());
    PsiSubstitutor superClassSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(superClass, enumClass, PsiSubstitutor.EMPTY);
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
    GlobalSearchScope moduleScope = GlobalSearchScope.moduleScope(myModule);

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
    assertSame(baseClass0, enumClass);

    PsiAnonymousClass initializingClass1 = ((PsiEnumConstant)fields[1]).getInitializingClass();
    assertNotNull(initializingClass1);
    PsiClass baseClass1 = initializingClass1.getBaseClassType().resolve();
    assertSame(baseClass1, enumClass);

    PsiAnonymousClass initializingClass2 = ((PsiEnumConstant)fields[1]).getInitializingClass();
    assertNotNull(initializingClass2);
    PsiClass baseClass2 = initializingClass2.getBaseClassType().resolve();
    assertSame(baseClass2, enumClass);

    assertTrue(initializingClass0.isInheritor(enumClass, false));
    assertTrue(initializingClass1.isInheritor(enumClass, false));
    assertTrue(initializingClass2.isInheritor(enumClass, false));

    PsiClass[] enumInheritors = ClassInheritorsSearch.search(enumClass, moduleScope, false).toArray(PsiClass.EMPTY_ARRAY);
    assertEquals(3, enumInheritors.length);
    assertTrue(Arrays.asList(enumInheritors).contains(initializingClass0));
    assertTrue(Arrays.asList(enumInheritors).contains(initializingClass1));
    assertTrue(Arrays.asList(enumInheritors).contains(initializingClass2));


    PsiMethod[] methods1 = initializingClass2.getMethods();
    assertEquals(1, methods1.length);
    assertEquals("foo", methods1[0].getName());

    PsiClass baseInterfaceClass = findClass("enums.OurBaseInterface");

    PsiClass[] inheritors = ClassInheritorsSearch.search(baseInterfaceClass, moduleScope, false).toArray(PsiClass.EMPTY_ARRAY);
    assertEquals(1, inheritors.length);
    assertTrue(inheritors[0] instanceof PsiAnonymousClass);

    tearDownLoadingFilter();

    assertTrue(inheritors[0].getParent().getParent() instanceof PsiExpressionList);
    assertSame(inheritors[0].getParent().getParent().getParent(), fields[2]);

    PsiExpressionList argumentList = ((PsiEnumConstant)fields[2]).getArgumentList();
    assertNotNull(argumentList);
    PsiExpression[] expressions2 = argumentList.getExpressions();
    assertEquals(1, expressions2.length);
    assertTrue(expressions2[0] instanceof PsiNewExpression);
    PsiAnonymousClass anonymousClass2 = ((PsiNewExpression)expressions2[0]).getAnonymousClass();
    assertNotNull(anonymousClass2);
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
      @Override public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
        visitExpression(expression);
      }
    });
  }

  public void testAnnotationType() {
    setupLoadingFilter();
    PsiClass annotationTypeClass = findClass("annotations.AnnotationType");
    assertTrue(annotationTypeClass.isAnnotationType());
    tearDownLoadingFilter();
  }

  public void testAnnotationIndex() {
    PsiManagerEx.getInstanceEx(getProject()).setAssertOnFileLoadingFilter(file -> !"package-info.java".equals(file.getName()), getTestRootDisposable());

    PsiClass annotationTypeClass = findClass("annotations.AnnotationType");
    assertTrue(annotationTypeClass.isAnnotationType());

    Collection<PsiMember> all = AnnotatedMembersSearch.search(annotationTypeClass, GlobalSearchScope.moduleScope(myModule)).findAll();

    assertEquals(2, all.size());
    Set<String> correctNames = Set.of("AnnotatedClass", "correctMethod");
    for (PsiMember member : all) {
      assertTrue(correctNames.contains(member.getName()));
    }

    Collection<PsiPackage> packages =
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

  private PsiClass findClass(String name) {
    PsiClass aClass = myJavaFacade.findClass(name, GlobalSearchScope.moduleScope(myModule));
    assertNotNull(name, aClass);
    return aClass;
  }
}
