// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.HeavyJavaCodeInsightFixture;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.impl.ProjectRootUtil;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiDeclarationStatement;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiImportList;
import com.intellij.psi.PsiImportStatement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.PsiTypeParameterList;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.text.BlockSupport;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static com.intellij.codeInsight.HeavyJavaCodeInsightFixture.heavyJavaCodeInsightFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class SrcRepositoryUseTest {
  private static final Logger LOG = Logger.getInstance(SrcRepositoryUseTest.class);
  private static final Key<String> TEST_KEY = Key.create("TEST");

  private final TestFixture<HeavyJavaCodeInsightFixture> heavyFixture = heavyJavaCodeInsightFixture(JavaTestUtil.getJavaTestDataPath());

  @BeforeEach
  void setUp() {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    IdeaTestUtil.setProjectLanguageLevel(fixture.getProject(), LanguageLevel.JDK_1_5);
    String root = JavaTestUtil.getJavaTestDataPath() + "/psi/repositoryUse/src";
    PsiTestUtil.removeAllRoots(fixture.getModule(), IdeaTestUtil.getMockJdk17());
    fixture.createTestProjectStructure( root);
  }

  @Test
  public void testGetClasses(){
    ReadAction.runBlocking(() -> {
      setupLoadingFilter();

      PsiDirectory root = ProjectRootUtil.getAllContentRoots(heavyFixture.get().getProject()) [0];
      VirtualFile child = root.getVirtualFile().findChild("MyClass1.java");
      assertNotNull(child);
      PsiJavaFile file = (PsiJavaFile)heavyFixture.get().getPsiManager().findFile(child);
      PsiClass[] classes = file.getClasses();
      assertEquals(2, classes.length);
      assertEquals(file, classes[0].getParent());

      teardownLoadingFilter();
    });
  }

  @Test
  public void testGetClassName(){
    ReadAction.runBlocking(() -> {
      setupLoadingFilter();

      PsiDirectory root = ProjectRootUtil.getAllContentRoots(heavyFixture.get().getProject()) [0];
      VirtualFile child = root.getVirtualFile().findChild("MyClass1.java");
      assertNotNull(child);
      PsiJavaFile file = (PsiJavaFile)heavyFixture.get().getPsiManager().findFile(child);
      PsiClass[] classes = file.getClasses();
      assertEquals(2, classes.length);

      assertEquals("MyClass1", classes[0].getName());
      assertEquals("Class2", classes[1].getName());

      teardownLoadingFilter();
    });
  }

  @Test
  public void testClassParameter() {
    ReadAction.runBlocking(() -> {
      setupLoadingFilter();
      PsiClass psiClass = heavyFixture.get().getJavaFacade().findClass("Class2", GlobalSearchScope.allScope(heavyFixture.get().getProject()));
      assertNotNull(psiClass);

      PsiTypeParameterList parameterList = psiClass.getTypeParameterList();
      assertNotNull(parameterList);
      PsiTypeParameter[] params = parameterList.getTypeParameters();
      assertEquals(2, params.length);

      assertEquals("A", params[0].getName());
      assertEquals("B", params[1].getName());

      assertEquals("java.lang.String", params[0].getSupers()[0].getQualifiedName());
      assertEquals(CommonClassNames.JAVA_LANG_OBJECT, params[1].getSupers()[0].getQualifiedName());

      teardownLoadingFilter();
    });
  }

  @Test
  public void testGetClassQName(){
    ReadAction.runBlocking(() -> {
      setupLoadingFilter();

      PsiDirectory root = ProjectRootUtil.getAllContentRoots(heavyFixture.get().getProject()) [0];
      VirtualFile child = root.getVirtualFile().findChild("pack").findChild("MyClass2.java");
      assertNotNull(child);
      PsiJavaFile file = (PsiJavaFile)heavyFixture.get().getPsiManager().findFile(child);
      PsiClass[] classes = file.getClasses();
      assertEquals(1, classes.length);

      assertEquals("pack.MyClass2", classes[0].getQualifiedName());

      teardownLoadingFilter();
    });
  }

  @Test
  public void testGetFields(){
    ReadAction.runBlocking(() -> {
      setupLoadingFilter();

      PsiDirectory root = ProjectRootUtil.getAllContentRoots(heavyFixture.get().getProject()) [0];
      VirtualFile child = root.getVirtualFile().findChild("pack").findChild("MyClass2.java");
      assertNotNull(child);
      PsiJavaFile file = (PsiJavaFile)heavyFixture.get().getPsiManager().findFile(child);
      PsiClass[] classes = file.getClasses();
      assertEquals(1, classes.length);

      PsiClass aClass = classes[0];
      PsiField[] fields = aClass.getFields();
      assertEquals(3, fields.length);
      assertEquals(aClass, fields[0].getParent());

      teardownLoadingFilter();
    });
  }

  @Test
  public void testGetMethods(){
    ReadAction.runBlocking(() -> {
      setupLoadingFilter();

      PsiDirectory root = ProjectRootUtil.getAllContentRoots(heavyFixture.get().getProject()) [0];
      VirtualFile child = root.getVirtualFile().findChild("pack").findChild("MyClass2.java");
      assertNotNull(child);
      PsiJavaFile file = (PsiJavaFile)heavyFixture.get().getPsiManager().findFile(child);
      PsiClass[] classes = file.getClasses();
      assertEquals(1, classes.length);

      PsiClass aClass = classes[0];
      PsiMethod[] methods = aClass.getMethods();
      assertEquals(3, methods.length);
      assertEquals(aClass, methods[0].getParent());

      teardownLoadingFilter();
    });
  }

  @Test
  public void testGetInnerClasses(){
    ReadAction.runBlocking(() -> {
      setupLoadingFilter();

      PsiDirectory root = ProjectRootUtil.getAllContentRoots(heavyFixture.get().getProject()) [0];
      VirtualFile child = root.getVirtualFile().findChild("pack").findChild("MyClass2.java");
      assertNotNull(child);
      PsiJavaFile file = (PsiJavaFile)heavyFixture.get().getPsiManager().findFile(child);
      PsiClass[] classes = file.getClasses();
      assertEquals(1, classes.length);

      PsiClass aClass = classes[0];
      PsiClass[] inners = aClass.getInnerClasses();
      assertEquals(1, inners.length);
      assertEquals(aClass, inners[0].getParent());

      teardownLoadingFilter();
    });
  }
  @Test
  public void testResolveSuperClass(){
    ReadAction.runBlocking(() -> {
      setupLoadingFilter();

      PsiDirectory root = ProjectRootUtil.getAllContentRoots(heavyFixture.get().getProject()) [0];
      VirtualFile child = root.getVirtualFile().findChild("pack").findChild("MyClass2.java");
      assertNotNull(child);
      PsiJavaFile file = (PsiJavaFile)heavyFixture.get().getPsiManager().findFile(child);
      PsiClass[] classes = file.getClasses();
      assertEquals(1, classes.length);

      PsiClass aClass = classes[0];
      PsiClass[] superTypes = aClass.getSupers();
      LOG.assertTrue(superTypes.length == 2);
      LOG.assertTrue(superTypes[0].getQualifiedName().equals(CommonClassNames.JAVA_LANG_STRING));
      LOG.assertTrue(superTypes[1].getQualifiedName().equals(CommonClassNames.JAVA_LANG_RUNNABLE));

      teardownLoadingFilter();
    });
  }

  @Test
  public void testGetContainingFile(){
    ReadAction.runBlocking(() -> {
      setupLoadingFilter();

      PsiDirectory root = ProjectRootUtil.getAllContentRoots(heavyFixture.get().getProject()) [0];
      VirtualFile child = root.getVirtualFile().findChild("pack").findChild("MyClass2.java");
      assertNotNull(child);
      PsiJavaFile file = (PsiJavaFile)heavyFixture.get().getPsiManager().findFile(child);
      PsiClass[] classes = file.getClasses();
      assertEquals(1, classes.length);

      assertEquals(file, classes[0].getContainingFile());

      teardownLoadingFilter();
    });
  }

  @Test
  public void testFindClass(){
    ReadAction.runBlocking(() -> {
      setupLoadingFilter();

      PsiClass aClass = heavyFixture.get().getJavaFacade().findClass("pack.MyClass2", GlobalSearchScope.allScope(heavyFixture.get().getProject()));
      assertNotNull(aClass);

      teardownLoadingFilter();
    });
  }

  @Test
  public void testGetFieldName(){
    ReadAction.runBlocking(() -> {
      setupLoadingFilter();

      PsiClass aClass = heavyFixture.get().getJavaFacade().findClass("pack.MyClass2", GlobalSearchScope.allScope(heavyFixture.get().getProject()));
      assertEquals("field1", aClass.getFields()[0].getName());

      teardownLoadingFilter();
    });
  }

  @Test
  public void testGetMethodName(){
    ReadAction.runBlocking(() -> {
      setupLoadingFilter();

      PsiClass aClass = heavyFixture.get().getJavaFacade().findClass("pack.MyClass2", GlobalSearchScope.allScope(heavyFixture.get().getProject()));
      assertEquals("method1", aClass.getMethods()[0].getName());

      teardownLoadingFilter();
    });
  }

  @Test
  public void testModifierList() {
    ReadAction.runBlocking(() -> {
      setupLoadingFilter();

      PsiClass aClass = heavyFixture.get().getJavaFacade().findClass("pack.MyClass2", GlobalSearchScope.allScope(heavyFixture.get().getProject()));

      PsiModifierList modifierList = aClass.getModifierList();
      assertTrue(modifierList.hasModifierProperty(PsiModifier.PUBLIC));
      assertFalse(modifierList.hasModifierProperty(PsiModifier.STATIC));
      assertEquals(modifierList.getParent(), aClass);

      PsiField field = aClass.getFields()[0];
      modifierList = field.getModifierList();
      assertTrue(modifierList.hasModifierProperty(PsiModifier.PACKAGE_LOCAL));
      assertEquals(modifierList.getParent(), field);

      PsiMethod method = aClass.getMethods()[0];
      modifierList = method.getModifierList();
      assertTrue(modifierList.hasModifierProperty(PsiModifier.PACKAGE_LOCAL));
      assertEquals(modifierList.getParent(), method);

      teardownLoadingFilter();
    });
  }

  @Test
  public void testIsInterface(){
    ReadAction.runBlocking(() -> {
      setupLoadingFilter();

      PsiClass aClass = heavyFixture.get().getJavaFacade().findClass("pack.MyClass2", GlobalSearchScope.allScope(heavyFixture.get().getProject()));
      assertFalse(aClass.isInterface());

      PsiClass anInterface = heavyFixture.get().getJavaFacade().findClass("pack.MyInterface1", GlobalSearchScope.allScope(heavyFixture.get().getProject()));
      assertTrue(anInterface.isInterface());

      teardownLoadingFilter();
    });
  }

  @Test
  public void testFindFieldByName(){
    ReadAction.runBlocking(() -> {
      setupLoadingFilter();

      PsiClass aClass = heavyFixture.get().getJavaFacade().findClass("pack.MyClass2", GlobalSearchScope.allScope(heavyFixture.get().getProject()));
      PsiField field = aClass.findFieldByName("field1", false);
      assertNotNull(field);

      teardownLoadingFilter();
    });
  }

  @Test
  public void testIsDeprecated(){
    ReadAction.runBlocking(() -> {
      setupLoadingFilter();

      PsiClass aClass = heavyFixture.get().getJavaFacade().findClass("pack.MyClass2", GlobalSearchScope.allScope(heavyFixture.get().getProject()));
      assertFalse(aClass.isDeprecated());

      PsiField field = aClass.findFieldByName("field1", false);
      assertTrue(field.isDeprecated());
    
      PsiField field2 = aClass.findFieldByName("field2", false);
      assertFalse(field2.isDeprecated());

      teardownLoadingFilter();
    });
  }

  @Test
  public void testPackageName(){
    ReadAction.runBlocking(() -> {
      setupLoadingFilter();

      PsiClass aClass = heavyFixture.get().getJavaFacade().findClass("pack.MyClass2", GlobalSearchScope.allScope(heavyFixture.get().getProject()));
      String packageName = ((PsiJavaFile)aClass.getContainingFile()).getPackageName();
      assertEquals("pack", packageName);

      teardownLoadingFilter();
    });
  }

  @Test
  public void testFieldType() {
    ReadAction.runBlocking(() -> {
      setupLoadingFilter();

      PsiClass aClass = heavyFixture.get().getJavaFacade().findClass("pack.MyClass2", GlobalSearchScope.allScope(heavyFixture.get().getProject()));

      PsiField field1 = aClass.getFields()[0];
      PsiType type1 = field1.getType();

      PsiField field2 = aClass.getFields()[1];
      PsiType type2 = field2.getType();

      PsiField field3 = aClass.getFields()[2];
      PsiType type3 = field3.getType();

      assertEquals("int", type1.getPresentableText());
      assertEquals("Object[]", type2.getPresentableText());
      assertEquals("Object[]", type3.getPresentableText());

      assertTrue(type1.equalsToText("int"));
      assertTrue(type2.equalsToText("java.lang.Object[]"));
      assertTrue(type3.equalsToText("java.lang.Object[]"));

      assertFalse(type1 instanceof PsiArrayType);
      assertTrue(type1 instanceof PsiPrimitiveType);
      assertFalse(type3 instanceof PsiPrimitiveType);
      assertTrue(type3 instanceof PsiArrayType);

      teardownLoadingFilter();
    });
  }

  @Test
  public void testHasInitializer() {
    ReadAction.runBlocking(() -> {
      setupLoadingFilter();

      PsiClass aClass = heavyFixture.get().getJavaFacade().findClass("pack.MyClass2", GlobalSearchScope.allScope(heavyFixture.get().getProject()));

      assertTrue(aClass.getFields()[0].hasInitializer());
      assertFalse(aClass.getFields()[1].hasInitializer());
      assertFalse(aClass.getFields()[2].hasInitializer());

      teardownLoadingFilter();
    });
  }

  @Test
  public void testMethodType() {
    ReadAction.runBlocking(() -> {
      setupLoadingFilter();

      PsiClass aClass = heavyFixture.get().getJavaFacade().findClass("pack.MyClass2", GlobalSearchScope.allScope(heavyFixture.get().getProject()));

      PsiMethod method1 = aClass.getMethods()[0];
      PsiType type1 = method1.getReturnType();

      assertTrue(type1.equalsToText("void"));
      assertFalse(type1 instanceof PsiArrayType);
      assertTrue(type1 instanceof PsiPrimitiveType);

      PsiMethod method3 = aClass.getMethods()[2];
      assertNull(method3.getReturnType());

      teardownLoadingFilter();
    });
  }

  @Test
  public void testIsConstructor() {
    ReadAction.runBlocking(() -> {
      setupLoadingFilter();

      PsiClass aClass = heavyFixture.get().getJavaFacade().findClass("pack.MyClass2", GlobalSearchScope.allScope(heavyFixture.get().getProject()));
      PsiMethod method1 = aClass.getMethods()[0];
      assertFalse(method1.isConstructor());

      teardownLoadingFilter();
    });
  }

  @Test
  public void testComponentType() {
    ReadAction.runBlocking(() -> {
      setupLoadingFilter();

      PsiClass aClass = heavyFixture.get().getJavaFacade().findClass("pack.MyClass2", GlobalSearchScope.allScope(heavyFixture.get().getProject()));

      PsiField field = aClass.getFields()[2];
      PsiType type = field.getType();
      LOG.assertTrue(type instanceof PsiArrayType);
      PsiType componentType = ((PsiArrayType) type).getComponentType();

      assertTrue(componentType.equalsToText(CommonClassNames.JAVA_LANG_OBJECT));
      assertFalse(componentType instanceof PsiPrimitiveType);
      assertFalse(componentType instanceof PsiArrayType);

      teardownLoadingFilter();
    });
  }

  @Test
  public void testTypeReference() {
    ReadAction.runBlocking(() -> {
      setupLoadingFilter();

      PsiClass aClass = heavyFixture.get().getJavaFacade().findClass("pack.MyClass2", GlobalSearchScope.allScope(heavyFixture.get().getProject()));

      PsiField field1 = aClass.getFields()[0];
      PsiType type1 = field1.getType();
      assertNull(PsiUtil.resolveClassInType(type1));

      PsiField field2 = aClass.getFields()[1];
      PsiType type2 = ((PsiArrayType) field2.getType()).getComponentType();
      assertEquals("Object", type2.getPresentableText());

      PsiField field3 = aClass.getFields()[2];
      PsiType type3 = ((PsiArrayType) field3.getType()).getComponentType();
      assertTrue(type3.equalsToText(CommonClassNames.JAVA_LANG_OBJECT));

      teardownLoadingFilter();
    });
  }

  @Test
  public void testImportList(){
    ReadAction.runBlocking(() -> {
      setupLoadingFilter();

      PsiDirectory root = ProjectRootUtil.getAllContentRoots(heavyFixture.get().getProject()) [0];
      VirtualFile child = root.getVirtualFile().findChild("MyClass1.java");
      assertNotNull(child);
      PsiJavaFile file = (PsiJavaFile)heavyFixture.get().getPsiManager().findFile(child);
      PsiImportList list = file.getImportList();
      assertEquals(file, list.getParent());

      PsiImportStatement[] imports = list.getImportStatements();
      assertEquals(3, imports.length);
      assertFalse(imports[0].isOnDemand());
      assertTrue(imports[1].isOnDemand());
      imports[2].isOnDemand();

      String ref1 = imports[0].getQualifiedName();
      String ref2 = imports[1].getQualifiedName();
      String ref3 = imports[2].getQualifiedName();

      assertEquals("a.b", ref1);
      assertEquals("c", ref2);
      assertNull(ref3);

      teardownLoadingFilter();
    });
  }

  @Test
  public void testResolveTypeReference() {
    ReadAction.runBlocking(() -> {
      setupLoadingFilter();

      PsiClass aClass = heavyFixture.get().getJavaFacade().findClass("pack.MyClass2", GlobalSearchScope.allScope(heavyFixture.get().getProject()));

      PsiType type1 = aClass.getFields()[1].getType();
      PsiElement target1 = PsiUtil.resolveClassInType(type1);
      assertNotNull(target1);
      PsiClass objectClass = heavyFixture.get().getJavaFacade().findClass(CommonClassNames.JAVA_LANG_OBJECT, GlobalSearchScope.allScope(heavyFixture.get().getProject()));
      assertEquals(objectClass, target1);

      PsiType type2 = aClass.getFields()[1].getType();
      PsiElement target2 = PsiUtil.resolveClassInType(type2);
      assertNotNull(target2);
      assertEquals(objectClass, target2);

      teardownLoadingFilter();
    });
  }

  @Test
  public void testExtendsList() {
    ReadAction.runBlocking(() -> {
      setupLoadingFilter();

      PsiClass aClass = heavyFixture.get().getJavaFacade().findClass("pack.MyClass2", GlobalSearchScope.allScope(heavyFixture.get().getProject()));

      PsiReferenceList list = aClass.getExtendsList();

      PsiClassType[] refs = list.getReferencedTypes();
      assertEquals(1, refs.length);
      assertEquals("String", refs[0].getPresentableText());

      teardownLoadingFilter();
    });
  }

  @Test
  public void testImplementsList() {
    ReadAction.runBlocking(() -> {
      setupLoadingFilter();

      PsiClass aClass = heavyFixture.get().getJavaFacade().findClass("pack.MyClass2", GlobalSearchScope.allScope(heavyFixture.get().getProject()));

      PsiReferenceList list = aClass.getImplementsList();

      PsiClassType[] refs = list.getReferencedTypes();
      assertEquals(1, refs.length);
      assertEquals("Runnable", refs[0].getPresentableText());

      teardownLoadingFilter();
    });
  }

  @Test
  public void testThrowsList() {
    ReadAction.runBlocking(() -> {
      setupLoadingFilter();

      PsiClass aClass = heavyFixture.get().getJavaFacade().findClass("pack.MyClass2", GlobalSearchScope.allScope(heavyFixture.get().getProject()));

      PsiReferenceList list = aClass.getMethods()[0].getThrowsList();

      PsiClassType[] refs = list.getReferencedTypes();
      assertEquals(2, refs.length);
      assertEquals("Exception", refs[0].getPresentableText());
      assertEquals("java.io.IOException", refs[1].getCanonicalText());

      teardownLoadingFilter();
    });
  }

  @Test
  public void testParameters() {
    ReadAction.runBlocking(() -> {
      setupLoadingFilter();

      PsiClass aClass = heavyFixture.get().getJavaFacade().findClass("pack.MyClass2", GlobalSearchScope.allScope(heavyFixture.get().getProject()));
      assertNotNull(aClass);
      PsiMethod[] methods = aClass.getMethods();
      PsiParameterList list = methods[0].getParameterList();

      PsiParameter[] parameters = list.getParameters();
      assertEquals(5, parameters.length);

      assertEquals("p1", parameters[0].getName());
      assertEquals("p2", parameters[1].getName());
      assertEquals("p3", parameters[2].getName());
      assertEquals("p4", parameters[3].getName());
      assertEquals("p5", parameters[4].getName());

      PsiType type1 = parameters[0].getType();
      assertEquals("int[]", type1.getPresentableText());
      assertFalse(type1 instanceof PsiPrimitiveType);
      assertTrue(type1 instanceof PsiArrayType);
      assertNull(PsiUtil.resolveClassInType(type1));

      PsiType type2 = parameters[1].getType();
      assertEquals("Object", type2.getPresentableText());
      assertFalse(type2 instanceof PsiArrayType);
      assertFalse(type2 instanceof PsiPrimitiveType);
      PsiClass target2 = PsiUtil.resolveClassInType(type2);
      assertNotNull(target2);
      PsiClass objectClass = heavyFixture.get().getJavaFacade().findClass(CommonClassNames.JAVA_LANG_OBJECT, GlobalSearchScope.allScope(heavyFixture.get().getProject()));
      assertEquals(objectClass, target2);

      checkPackAAA(parameters[2].getType());
      checkPackAAA(parameters[3].getType());
      checkPackAAA(parameters[4].getType());

      teardownLoadingFilter();

      parameters[0].getModifierList();
    });
  }

  private static void checkPackAAA(PsiType type) {
    assertEquals("AAA", type.getPresentableText());
    assertFalse(type instanceof PsiArrayType);
    assertFalse(type instanceof PsiPrimitiveType);
    PsiClass target3 = PsiUtil.resolveClassInType(type);
    assertNull(target3);
    assertEquals("pack.AAA", type.getCanonicalText());
  }

  @Test
  public void testAnonymousClass() {
    ReadAction.runBlocking(() -> {
      setupLoadingFilter();

      PsiClass cloneableClass = heavyFixture.get().getJavaFacade().findClass("java.lang.Cloneable", GlobalSearchScope.allScope(heavyFixture.get().getProject()));
      PsiClass[] inheritors = ClassInheritorsSearch.search(cloneableClass, GlobalSearchScope.projectScope(heavyFixture.get().getProject()), true).toArray(PsiClass.EMPTY_ARRAY);
      assertEquals(2, inheritors.length);
      assertTrue(inheritors[0] instanceof PsiAnonymousClass || inheritors[1] instanceof PsiAnonymousClass);
      PsiAnonymousClass anonClass = (PsiAnonymousClass)(inheritors[0] instanceof PsiAnonymousClass ? inheritors[0] : inheritors[1]);

      PsiClassType baseClassRef = anonClass.getBaseClassType();
      assertEquals("Cloneable", baseClassRef.getPresentableText());
      assertEquals(cloneableClass, baseClassRef.resolve());
      assertEquals("java.lang.Cloneable", baseClassRef.getCanonicalText());

      teardownLoadingFilter();

      assertTrue(anonClass.getParent() instanceof PsiNewExpression);
    });
  }

  private void teardownLoadingFilter() {
    PsiManagerEx.getInstanceEx(heavyFixture.get().getProject()).setAssertOnFileLoadingFilter(VirtualFileFilter.NONE, heavyFixture.get().getTestRootDisposable());
  }

  private void setupLoadingFilter() {
    PsiManagerEx.getInstanceEx(heavyFixture.get().getProject()).setAssertOnFileLoadingFilter(VirtualFileFilter.ALL, heavyFixture.get().getTestRootDisposable());
  }

  @Test
  public void testAnonymousClass2() {
    ReadAction.runBlocking(() -> {
      setupLoadingFilter();

      PsiClass throwable = heavyFixture.get().getJavaFacade().findClass(CommonClassNames.JAVA_LANG_THROWABLE, GlobalSearchScope.allScope(heavyFixture.get().getProject()));
      PsiClass[] inheritors = ClassInheritorsSearch.search(throwable, GlobalSearchScope.projectScope(heavyFixture.get().getProject()), true).toArray(PsiClass.EMPTY_ARRAY);
      assertEquals(1, inheritors.length);
      assertTrue(inheritors[0] instanceof PsiAnonymousClass);
      PsiAnonymousClass anonClass = (PsiAnonymousClass)inheritors[0];

      PsiClassType baseClassRef = anonClass.getBaseClassType();
      assertEquals("Throwable", baseClassRef.getPresentableText());
      assertEquals(throwable, baseClassRef.resolve());
      assertEquals(CommonClassNames.JAVA_LANG_THROWABLE, baseClassRef.getCanonicalText());

      teardownLoadingFilter();

      assertTrue(anonClass.getParent() instanceof PsiNewExpression);
    });
  }

  @Test
  public void testLocalClass() {
    ReadAction.runBlocking(() -> {
      setupLoadingFilter();

      PsiClass cloneableClass = heavyFixture.get().getJavaFacade().findClass("java.lang.Cloneable", GlobalSearchScope.allScope(heavyFixture.get().getProject()));
      PsiClass[] inheritors = ClassInheritorsSearch.search(cloneableClass, GlobalSearchScope.projectScope(heavyFixture.get().getProject()), true).toArray(PsiClass.EMPTY_ARRAY);
      assertEquals(2, inheritors.length);
      assertTrue(inheritors[0] instanceof PsiAnonymousClass || inheritors[1] instanceof PsiAnonymousClass);
      PsiClass localClass = inheritors[0] instanceof PsiAnonymousClass ? inheritors[1] : inheritors[0];

      teardownLoadingFilter();

      assertTrue(localClass.getParent() instanceof PsiDeclarationStatement);
    });
  }

  @Test
  public void testClassNameModification() {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    Project project = fixture.getProject();
    PsiClass aClass = ReadAction.computeBlocking(() -> fixture.getJavaFacade().findClass("pack.MyClass2", GlobalSearchScope.allScope(project)));
    assertNotNull(aClass);

    WriteCommandAction.writeCommandAction(project).run(() -> {
      aClass.getNameIdentifier().replace(fixture.getJavaFacade().getElementFactory().createIdentifier("NewName"));
    });

    assertEquals("pack.NewName", ReadAction.computeBlocking(() -> aClass.getQualifiedName()));
  }

  @Test
  public void testModification1() {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    Project project = fixture.getProject();
    PsiClass aClass = ReadAction.computeBlocking(() -> fixture.getJavaFacade().findClass("pack.MyClass2", GlobalSearchScope.allScope(project)));
    assertNotNull(aClass);

    PsiField field = ReadAction.computeBlocking(() -> aClass.getFields()[0]);
    WriteCommandAction.writeCommandAction(project).run(() -> {
      aClass.getNameIdentifier().replace(fixture.getJavaFacade().getElementFactory().createIdentifier("NewName"));
    });

    assertTrue(ReadAction.computeBlocking(() -> field.isValid()));
  }

  @Test
  public void testModification2() {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    Project project = fixture.getProject();
    PsiClass aClass = ReadAction.computeBlocking(() -> fixture.getJavaFacade().findClass("pack.MyClass2", GlobalSearchScope.allScope(project)));
    WriteCommandAction.writeCommandAction(project).run(() -> PsiUtil.setModifierProperty(aClass, PsiModifier.FINAL, true));


    PsiClass aClass2 = ReadAction.computeBlocking(() -> fixture.getJavaFacade().findClass("pack.MyClass2", GlobalSearchScope.allScope(project)));
    assertEquals(aClass, aClass2);
  }

  @Test
  public void testModification3() {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    Project project = fixture.getProject();
    PsiClass oldClass = ReadAction.computeBlocking(() -> fixture.getJavaFacade().findClass("pack.MyInterface1", GlobalSearchScope.allScope(project)));
    TextRange classRange = ReadAction.computeBlocking(() -> oldClass.getTextRange());
    String text = ReadAction.computeBlocking(() -> oldClass.getText());

    BlockSupport blockSupport = project.getService(BlockSupport.class);
    PsiFile psiFile = ReadAction.computeBlocking(() -> oldClass.getContainingFile());
    WriteCommandAction.writeCommandAction(project).run(() -> blockSupport.reparseRange(psiFile, classRange.getStartOffset(), classRange.getEndOffset(), ""));

    LOG.assertTrue(!ReadAction.computeBlocking(() -> oldClass.isValid()));
    WriteCommandAction.writeCommandAction(project).run(() -> blockSupport.reparseRange(psiFile, classRange.getStartOffset(), classRange.getStartOffset(), text));


    ReadAction.runBlocking(() -> {
      PsiClass aClass = fixture.getJavaFacade().findClass("pack.MyInterface1", GlobalSearchScope.allScope(project));
      PsiElement[] children = aClass.getChildren();
      for (PsiElement child : children) {
        if (child instanceof PsiModifierList) {
          PsiElement parent = child.getParent();
          assertEquals(aClass, parent);
          PsiModifierList modifierList = aClass.getModifierList();
          assertEquals(modifierList, child);
        }
      }

      PsiJavaFile file = (PsiJavaFile)aClass.getContainingFile();
      PsiImportList importList = file.getImportList();
      children = importList.getChildren();
      int index = 0;
      for (PsiElement child : children) {
        if (child instanceof PsiImportStatement) {
          PsiElement parent = child.getParent();
          assertEquals(importList, parent);
          PsiImportStatement statement = importList.getImportStatements()[index++];
          assertEquals(statement, child);
        }
      }
    });
  }

  @Test
  public void testRenamePackage() {
    renamePackage("pack", "renamedPack");

    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    PsiClass psiClass = ReadAction.computeBlocking(
      () -> fixture.getJavaFacade().findClass("renamedPack.MyInterface1", GlobalSearchScope.allScope(fixture.getProject())));
    assertNotNull(psiClass);
  }

  @Test
  public void testReplaceRootWithSubRoot1() {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    PsiClass aClass = ReadAction.computeBlocking(
      () -> fixture.getJavaFacade().findClass("pack.MyInterface1", GlobalSearchScope.allScope(fixture.getProject())));
    PsiFile psiFile = ReadAction.computeBlocking(() -> aClass.getContainingFile());
    assertNotNull(aClass);

    VirtualFile newSourceRoot = psiFile.getVirtualFile().getParent();
    replaceSourceRoot(newSourceRoot);

    assertEquals("MyInterface1", ReadAction.computeBlocking(() -> aClass.getName()));
  }

  @Test
  public void testReplaceRootWithSubRoot2() {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    PsiClass aClass = ReadAction.computeBlocking(
      () -> fixture.getJavaFacade().findClass("pack.MyInterface1", GlobalSearchScope.allScope(fixture.getProject())));
    assertNotNull(aClass);

    VirtualFile newSourceRoot = ReadAction.computeBlocking(() -> aClass.getContainingFile().getVirtualFile().getParent());
    replaceSourceRoot(newSourceRoot);

    assertEquals("MyInterface1", ReadAction.computeBlocking(() -> aClass.getName()));
  }

  private void replaceSourceRoot(VirtualFile newSourceRoot) {
    Module module = heavyFixture.get().getModule();
    WriteAction.runAndWait(
      () -> {
        ModifiableRootModel rootModel = ModuleRootManager.getInstance(module).getModifiableModel();
        ContentEntry[] content = rootModel.getContentEntries();
        boolean contentToChangeFound = false;
        for (ContentEntry contentEntry : content) {
          SourceFolder[] sourceFolders = contentEntry.getSourceFolders();
          for (SourceFolder sourceFolder : sourceFolders) {
            contentEntry.removeSourceFolder(sourceFolder);
          }
          VirtualFile contentRoot = contentEntry.getFile();
          if (contentRoot != null && VfsUtilCore.isAncestor(contentRoot, newSourceRoot, false)) {
            contentEntry.addSourceFolder(newSourceRoot, false);
            contentToChangeFound = true;
          }
        }
        assertTrue(contentToChangeFound);
        rootModel.commit();
      }
    );
  }

  @Test
  public void testParentIdAssert() {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    Project project = fixture.getProject();
    PsiMethod[] methods = ReadAction.computeBlocking(() -> {
      PsiClass jPanelClass = fixture.getJavaFacade().findClass("javax.swing.JPanel", GlobalSearchScope.allScope(project));
      PsiClass[] inheritors = ClassInheritorsSearch.search(jPanelClass, GlobalSearchScope.projectScope(project), true).toArray(PsiClass.EMPTY_ARRAY);
      assertEquals(2, inheritors.length);
      assertTrue(inheritors[0] instanceof PsiAnonymousClass || inheritors[1] instanceof PsiAnonymousClass);
      PsiClass nonAnonClass = inheritors[0] instanceof PsiAnonymousClass ? inheritors[1] : inheritors[0];

      return nonAnonClass.getMethods();
    });
    assertEquals(1, methods.length);
    PsiTypeElement newType = ReadAction.computeBlocking(() -> fixture.getJavaFacade().getElementFactory().createTypeElement(PsiTypes.floatType()));
    WriteCommandAction.writeCommandAction(project).run(() -> {
      methods[0].getReturnTypeElement().replace(newType);
    });
  }

  @Test
  public void testParentIdAssertOnExternalChange() throws Exception {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    Project project = fixture.getProject();
    VirtualFile vFile = ReadAction.computeBlocking(() -> {
      PsiDirectory root = ProjectRootUtil.getAllContentRoots(project) [0];
      VirtualFile child = root.getVirtualFile().findChild("MyClass1.java");

      PsiJavaFile psiFile = (PsiJavaFile) fixture.getPsiManager().findFile(child);
      psiFile.getClasses();
      return child;
    });

    rewriteFileExternally(vFile, """
      import a . b;
      import c.*;
      import

      class MyClass1{
        {
          class Local{
            public void foo(){
              new Runnable(){
                public void run(){
                  new Throwable(){
                  };
                };
              }
            }
          };
        }
      }

      class Class2{
      }
      """);

    PsiClass myClass = ReadAction.computeBlocking(() -> fixture.getJavaFacade().findClass("MyClass1", GlobalSearchScope.allScope(project)));
    assertNotNull(myClass);
  }

  @Test
  public void testCopyableUserDataChild() {
    ReadAction.runBlocking(() -> {
      PsiClass aClass = heavyFixture.get().getJavaFacade().findClass("pack.MyInterface1", GlobalSearchScope.allScope(heavyFixture.get().getProject()));
      assertNotNull(aClass);
      PsiFile containingFile = aClass.getContainingFile();
      CompositeElement element = ((PsiFileImpl)containingFile).calcTreeElement();
      aClass.putCopyableUserData(TEST_KEY, "TEST");
      PsiJavaFile fileCopy = (PsiJavaFile)containingFile.copy();
      PsiClass[] classesCopy = fileCopy.getClasses();
      assertEquals(1, classesCopy.length);
      assertNotNull(element);
      assertEquals("TEST", classesCopy[0].getCopyableUserData(TEST_KEY));
    });
  }

  @SuppressWarnings("SameParameterValue")
  private static void rewriteFileExternally(VirtualFile vFile, String text) throws IOException {
    FileUtil.writeToFile(new File(vFile.getPath()), text.getBytes(StandardCharsets.UTF_8));
    vFile.refresh(false, false);
  }

  @SuppressWarnings("SameParameterValue")
  private void renamePackage(String packageName, String newPackageName) {
    Project project = heavyFixture.get().getProject();
    EdtTestUtil.runInEdtAndWait(() -> {
      PsiPackage aPackage = JavaPsiFacade.getInstance(project).findPackage(packageName);
      assertNotNull(aPackage, "Package " + packageName + " not found");

      //PsiDirectory dir = aPackage.getDirectories()[0];
      //to rename dir with classes move is used
      new RenameProcessor(project, aPackage, newPackageName, true, true).run();
      FileDocumentManager.getInstance().saveAllDocuments();
    });
  }
}
