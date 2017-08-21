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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.projectRoots.impl.ProjectRootUtil;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.text.BlockSupport;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.PsiTestCase;
import com.intellij.testFramework.PsiTestUtil;

import java.io.File;
import java.io.IOException;

@SuppressWarnings("ConstantConditions")
@PlatformTestCase.WrapInCommand
public class SrcRepositoryUseTest extends PsiTestCase{
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.SrcRepositoryUseTest");
  private static final Key<String> TEST_KEY = Key.create("TEST");

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    LanguageLevelProjectExtension.getInstance(myProject).setLanguageLevel(LanguageLevel.JDK_1_5);
    String root = PathManagerEx.getTestDataPath() + "/psi/repositoryUse/src";
    PsiTestUtil.removeAllRoots(myModule, IdeaTestUtil.getMockJdk17());
    PsiTestUtil.createTestProjectStructure(myProject, myModule, root, myFilesToDelete);
  }

  public void testGetClasses(){
    setupLoadingFilter();

    PsiDirectory root = ProjectRootUtil.getAllContentRoots(myProject) [0];
    VirtualFile child = root.getVirtualFile().findChild("MyClass1.java");
    assertNotNull(child);
    PsiJavaFile file = (PsiJavaFile)myPsiManager.findFile(child);
    PsiClass[] classes = file.getClasses();
    assertEquals(2, classes.length);
    assertEquals(file, classes[0].getParent());

    teardownLoadingFilter();
  }

  public void testGetClassName(){
    setupLoadingFilter();

    PsiDirectory root = ProjectRootUtil.getAllContentRoots(myProject) [0];
    VirtualFile child = root.getVirtualFile().findChild("MyClass1.java");
    assertNotNull(child);
    PsiJavaFile file = (PsiJavaFile)myPsiManager.findFile(child);
    PsiClass[] classes = file.getClasses();
    assertEquals(2, classes.length);

    assertEquals("MyClass1", classes[0].getName());
    assertEquals("Class2", classes[1].getName());

    teardownLoadingFilter();
  }

  public void testClassParameter() {
    setupLoadingFilter();
    PsiClass psiClass = myJavaFacade.findClass("Class2", GlobalSearchScope.allScope(myProject));
    assertNotNull(psiClass);

    PsiTypeParameterList parameterList = psiClass.getTypeParameterList();
    assertNotNull(parameterList);
    PsiTypeParameter[] params = parameterList.getTypeParameters();
    assertEquals(params.length, 2);

    assertEquals(params[0].getName(), "A");
    assertEquals(params[1].getName(), "B");

    assertEquals("java.lang.String", params[0].getSupers()[0].getQualifiedName());
    assertEquals(CommonClassNames.JAVA_LANG_OBJECT, params[1].getSupers()[0].getQualifiedName());

    teardownLoadingFilter();
  }

  public void testGetClassQName(){
    setupLoadingFilter();

    PsiDirectory root = ProjectRootUtil.getAllContentRoots(myProject) [0];
    VirtualFile child = root.getVirtualFile().findChild("pack").findChild("MyClass2.java");
    assertNotNull(child);
    PsiJavaFile file = (PsiJavaFile)myPsiManager.findFile(child);
    PsiClass[] classes = file.getClasses();
    assertEquals(1, classes.length);

    assertEquals("pack.MyClass2", classes[0].getQualifiedName());

    teardownLoadingFilter();
  }

  public void testGetFields(){
    setupLoadingFilter();

    PsiDirectory root = ProjectRootUtil.getAllContentRoots(myProject) [0];
    VirtualFile child = root.getVirtualFile().findChild("pack").findChild("MyClass2.java");
    assertNotNull(child);
    PsiJavaFile file = (PsiJavaFile)myPsiManager.findFile(child);
    PsiClass[] classes = file.getClasses();
    assertEquals(1, classes.length);

    PsiClass aClass = classes[0];
    PsiField[] fields = aClass.getFields();
    assertEquals(3, fields.length);
    assertEquals(aClass, fields[0].getParent());

    teardownLoadingFilter();
  }

  public void testGetMethods(){
    setupLoadingFilter();

    PsiDirectory root = ProjectRootUtil.getAllContentRoots(myProject) [0];
    VirtualFile child = root.getVirtualFile().findChild("pack").findChild("MyClass2.java");
    assertNotNull(child);
    PsiJavaFile file = (PsiJavaFile)myPsiManager.findFile(child);
    PsiClass[] classes = file.getClasses();
    assertEquals(1, classes.length);

    PsiClass aClass = classes[0];
    PsiMethod[] methods = aClass.getMethods();
    assertEquals(3, methods.length);
    assertEquals(aClass, methods[0].getParent());

    teardownLoadingFilter();
  }

  public void testGetInnerClasses(){
    setupLoadingFilter();

    PsiDirectory root = ProjectRootUtil.getAllContentRoots(myProject) [0];
    VirtualFile child = root.getVirtualFile().findChild("pack").findChild("MyClass2.java");
    assertNotNull(child);
    PsiJavaFile file = (PsiJavaFile)myPsiManager.findFile(child);
    PsiClass[] classes = file.getClasses();
    assertEquals(1, classes.length);

    PsiClass aClass = classes[0];
    PsiClass[] inners = aClass.getInnerClasses();
    assertEquals(1, inners.length);
    assertEquals(aClass, inners[0].getParent());

    teardownLoadingFilter();
  }
  public void testResolveSuperClass(){
    setupLoadingFilter();

    PsiDirectory root = ProjectRootUtil.getAllContentRoots(myProject) [0];
    VirtualFile child = root.getVirtualFile().findChild("pack").findChild("MyClass2.java");
    assertNotNull(child);
    PsiJavaFile file = (PsiJavaFile)myPsiManager.findFile(child);
    PsiClass[] classes = file.getClasses();
    assertEquals(1, classes.length);

    PsiClass aClass = classes[0];
    PsiClass[] superTypes = aClass.getSupers();
    LOG.assertTrue(superTypes.length == 2);
    LOG.assertTrue(superTypes[0].getQualifiedName().equals(CommonClassNames.JAVA_LANG_STRING));
    LOG.assertTrue(superTypes[1].getQualifiedName().equals(CommonClassNames.JAVA_LANG_RUNNABLE));

    teardownLoadingFilter();
  }

  public void testGetContainingFile(){
    setupLoadingFilter();

    PsiDirectory root = ProjectRootUtil.getAllContentRoots(myProject) [0];
    VirtualFile child = root.getVirtualFile().findChild("pack").findChild("MyClass2.java");
    assertNotNull(child);
    PsiJavaFile file = (PsiJavaFile)myPsiManager.findFile(child);
    PsiClass[] classes = file.getClasses();
    assertEquals(1, classes.length);

    assertEquals(file, classes[0].getContainingFile());

    teardownLoadingFilter();
  }

  public void testFindClass(){
    setupLoadingFilter();

    PsiClass aClass = myJavaFacade.findClass("pack.MyClass2", GlobalSearchScope.allScope(myProject));
    assertNotNull(aClass);

    teardownLoadingFilter();
  }

  public void testGetFieldName(){
    setupLoadingFilter();

    PsiClass aClass = myJavaFacade.findClass("pack.MyClass2", GlobalSearchScope.allScope(myProject));
    assertEquals("field1", aClass.getFields()[0].getName());

    teardownLoadingFilter();
  }

  public void testGetMethodName(){
    setupLoadingFilter();

    PsiClass aClass = myJavaFacade.findClass("pack.MyClass2", GlobalSearchScope.allScope(myProject));
    assertEquals("method1", aClass.getMethods()[0].getName());

    teardownLoadingFilter();
  }

  public void testModifierList() throws Exception {
    setupLoadingFilter();

    PsiModifierList modifierList;
    {
      PsiClass aClass = myJavaFacade.findClass("pack.MyClass2", GlobalSearchScope.allScope(myProject));

      modifierList = aClass.getModifierList();
      assertTrue(modifierList.hasModifierProperty(PsiModifier.PUBLIC));
      assertTrue(!modifierList.hasModifierProperty(PsiModifier.STATIC));
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
      method = null;
    }


    for (int i = 0; i < 2; i++) {
      System.gc();
      Thread.sleep(200);
    }

    modifierList.getChildren();
  }

  public void testIsInterface(){
    setupLoadingFilter();

    PsiClass aClass = myJavaFacade.findClass("pack.MyClass2", GlobalSearchScope.allScope(myProject));
    assertTrue(!aClass.isInterface());

    PsiClass anInterface = myJavaFacade.findClass("pack.MyInterface1", GlobalSearchScope.allScope(myProject));
    assertTrue(anInterface.isInterface());

    teardownLoadingFilter();
  }

  public void testFindFieldByName(){
    setupLoadingFilter();

    PsiClass aClass = myJavaFacade.findClass("pack.MyClass2", GlobalSearchScope.allScope(myProject));
    PsiField field = aClass.findFieldByName("field1", false);
    assertNotNull(field);

    teardownLoadingFilter();
  }

  public void testIsDeprecated(){
    setupLoadingFilter();

    PsiClass aClass = myJavaFacade.findClass("pack.MyClass2", GlobalSearchScope.allScope(myProject));
    assertTrue(!aClass.isDeprecated());

    PsiField field = aClass.findFieldByName("field1", false);
    assertTrue(field.isDeprecated());

    teardownLoadingFilter();
  }

  public void testPackageName(){
    setupLoadingFilter();

    PsiClass aClass = myJavaFacade.findClass("pack.MyClass2", GlobalSearchScope.allScope(myProject));
    String packageName = ((PsiJavaFile)aClass.getContainingFile()).getPackageName();
    assertEquals("pack", packageName);

    teardownLoadingFilter();
  }

  public void testFieldType() {
    setupLoadingFilter();

    PsiClass aClass = myJavaFacade.findClass("pack.MyClass2", GlobalSearchScope.allScope(myProject));

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

    assertTrue(!(type1 instanceof PsiArrayType));
    assertTrue(type1 instanceof PsiPrimitiveType);
    assertTrue(!(type3 instanceof PsiPrimitiveType));
    assertTrue(type3 instanceof PsiArrayType);

    teardownLoadingFilter();
  }

  public void testHasInitializer() {
    setupLoadingFilter();

    PsiClass aClass = myJavaFacade.findClass("pack.MyClass2", GlobalSearchScope.allScope(myProject));

    assertTrue(aClass.getFields()[0].hasInitializer());
    assertFalse(aClass.getFields()[1].hasInitializer());
    assertFalse(aClass.getFields()[2].hasInitializer());

    teardownLoadingFilter();
  }

  public void testMethodType() {
    setupLoadingFilter();

    PsiClass aClass = myJavaFacade.findClass("pack.MyClass2", GlobalSearchScope.allScope(myProject));

    PsiMethod method1 = aClass.getMethods()[0];
    PsiType type1 = method1.getReturnType();

    assertTrue(type1.equalsToText("void"));
    assertTrue(!(type1 instanceof PsiArrayType));
    assertTrue(type1 instanceof PsiPrimitiveType);

    PsiMethod method3 = aClass.getMethods()[2];
    assertNull(method3.getReturnType());

    teardownLoadingFilter();
  }

  public void testIsConstructor() {
    setupLoadingFilter();

    PsiClass aClass = myJavaFacade.findClass("pack.MyClass2", GlobalSearchScope.allScope(myProject));
    PsiMethod method1 = aClass.getMethods()[0];
    assertFalse(method1.isConstructor());

    teardownLoadingFilter();
  }

  public void testComponentType() {
    setupLoadingFilter();

    PsiClass aClass = myJavaFacade.findClass("pack.MyClass2", GlobalSearchScope.allScope(myProject));

    PsiField field = aClass.getFields()[2];
    PsiType type = field.getType();
    LOG.assertTrue(type instanceof PsiArrayType);
    PsiType componentType = ((PsiArrayType) type).getComponentType();

    assertTrue(componentType.equalsToText(CommonClassNames.JAVA_LANG_OBJECT));
    assertFalse(componentType instanceof PsiPrimitiveType);
    assertFalse(componentType instanceof PsiArrayType);

    teardownLoadingFilter();
  }

  public void testTypeReference() {
    setupLoadingFilter();

    PsiClass aClass = myJavaFacade.findClass("pack.MyClass2", GlobalSearchScope.allScope(myProject));

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
  }

  public void testImportList(){
    setupLoadingFilter();

    PsiDirectory root = ProjectRootUtil.getAllContentRoots(myProject) [0];
    VirtualFile child = root.getVirtualFile().findChild("MyClass1.java");
    assertNotNull(child);
    PsiJavaFile file = (PsiJavaFile)myPsiManager.findFile(child);
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
  }

  public void testResolveTypeReference() {
    setupLoadingFilter();

    PsiClass aClass = myJavaFacade.findClass("pack.MyClass2", GlobalSearchScope.allScope(myProject));

    PsiType type1 = aClass.getFields()[1].getType();
    PsiElement target1 = PsiUtil.resolveClassInType(type1);
    assertNotNull(target1);
    PsiClass objectClass = myJavaFacade.findClass(CommonClassNames.JAVA_LANG_OBJECT, GlobalSearchScope.allScope(myProject));
    assertEquals(objectClass, target1);

    PsiType type2 = aClass.getFields()[1].getType();
    PsiElement target2 = PsiUtil.resolveClassInType(type2);
    assertNotNull(target2);
    assertEquals(objectClass, target2);

    teardownLoadingFilter();
  }

  public void testExtendsList() {
    setupLoadingFilter();

    PsiClass aClass = myJavaFacade.findClass("pack.MyClass2", GlobalSearchScope.allScope(myProject));

    PsiReferenceList list = aClass.getExtendsList();

    PsiClassType[] refs = list.getReferencedTypes();
    assertEquals(1, refs.length);
    assertEquals("String", refs[0].getPresentableText());

    teardownLoadingFilter();
  }

  public void testImplementsList() {
    setupLoadingFilter();

    PsiClass aClass = myJavaFacade.findClass("pack.MyClass2", GlobalSearchScope.allScope(myProject));

    PsiReferenceList list = aClass.getImplementsList();

    PsiClassType[] refs = list.getReferencedTypes();
    assertEquals(1, refs.length);
    assertEquals("Runnable", refs[0].getPresentableText());

    teardownLoadingFilter();
  }

  public void testThrowsList() {
    setupLoadingFilter();

    PsiClass aClass = myJavaFacade.findClass("pack.MyClass2", GlobalSearchScope.allScope(myProject));

    PsiReferenceList list = aClass.getMethods()[0].getThrowsList();

    PsiClassType[] refs = list.getReferencedTypes();
    assertEquals(2, refs.length);
    assertEquals("Exception", refs[0].getPresentableText());
    assertEquals("java.io.IOException", refs[1].getCanonicalText());

    teardownLoadingFilter();
  }

  public void testParameters() {
    setupLoadingFilter();

    PsiClass aClass = myJavaFacade.findClass("pack.MyClass2", GlobalSearchScope.allScope(myProject));
    assertNotNull(aClass);
    PsiMethod[] methods = aClass.getMethods();
    PsiParameterList list = methods[0].getParameterList();

    PsiParameter[] parms = list.getParameters();
    assertEquals(5, parms.length);

    assertEquals("p1", parms[0].getName());
    assertEquals("p2", parms[1].getName());
    assertEquals("p3", parms[2].getName());
    assertEquals("p4", parms[3].getName());
    assertEquals("p5", parms[4].getName());

    PsiType type1 = parms[0].getType();
    assertEquals("int[]", type1.getPresentableText());
    assertFalse(type1 instanceof PsiPrimitiveType);
    assertTrue(type1 instanceof PsiArrayType);
    assertNull(PsiUtil.resolveClassInType(type1));

    PsiType type2 = parms[1].getType();
    assertEquals("Object", type2.getPresentableText());
    assertFalse(type2 instanceof PsiArrayType);
    assertFalse(type2 instanceof PsiPrimitiveType);
    PsiClass target2 = PsiUtil.resolveClassInType(type2);
    assertNotNull(target2);
    PsiClass objectClass = myJavaFacade.findClass(CommonClassNames.JAVA_LANG_OBJECT, GlobalSearchScope.allScope(myProject));
    assertEquals(objectClass, target2);

    checkPackAAA(parms[2].getType());
    checkPackAAA(parms[3].getType());
    checkPackAAA(parms[4].getType());

    teardownLoadingFilter();

    parms[0].getModifierList();
  }

  private static void checkPackAAA(PsiType type) {
    assertEquals("AAA", type.getPresentableText());
    assertFalse(type instanceof PsiArrayType);
    assertFalse(type instanceof PsiPrimitiveType);
    PsiClass target3 = PsiUtil.resolveClassInType(type);
    assertNull(target3);
    assertEquals("pack.AAA", type.getCanonicalText());
  }

  public void testAnonymousClass() {
    setupLoadingFilter();

    PsiClass cloneableClass = myJavaFacade.findClass("java.lang.Cloneable", GlobalSearchScope.allScope(myProject));
    PsiClass[] inheritors = ClassInheritorsSearch.search(cloneableClass, GlobalSearchScope.projectScope(myProject), true).toArray(PsiClass.EMPTY_ARRAY);
    assertEquals(2, inheritors.length);
    assertTrue(inheritors[0] instanceof PsiAnonymousClass || inheritors[1] instanceof PsiAnonymousClass);
    PsiAnonymousClass anonClass = (PsiAnonymousClass)(inheritors[0] instanceof PsiAnonymousClass ? inheritors[0] : inheritors[1]);

    PsiClassType baseClassRef = anonClass.getBaseClassType();
    assertEquals("Cloneable", baseClassRef.getPresentableText());
    assertEquals(cloneableClass, baseClassRef.resolve());
    assertEquals("java.lang.Cloneable", baseClassRef.getCanonicalText());

    teardownLoadingFilter();

    assertTrue(anonClass.getParent() instanceof PsiNewExpression);
  }

  private void teardownLoadingFilter() {
    PsiManagerEx.getInstanceEx(getProject()).setAssertOnFileLoadingFilter(VirtualFileFilter.NONE, getTestRootDisposable());
  }

  private void setupLoadingFilter() {
    PsiManagerEx.getInstanceEx(getProject()).setAssertOnFileLoadingFilter(VirtualFileFilter.ALL, getTestRootDisposable());
  }

  public void testAnonymousClass2() {
    setupLoadingFilter();

    PsiClass throwable = myJavaFacade.findClass("java.lang.Throwable", GlobalSearchScope.allScope(myProject));
    PsiClass[] inheritors = ClassInheritorsSearch.search(throwable, GlobalSearchScope.projectScope(myProject), true).toArray(PsiClass.EMPTY_ARRAY);
    assertEquals(1, inheritors.length);
    assertTrue(inheritors[0] instanceof PsiAnonymousClass);
    PsiAnonymousClass anonClass = (PsiAnonymousClass)inheritors[0];

    PsiClassType baseClassRef = anonClass.getBaseClassType();
    assertEquals("Throwable", baseClassRef.getPresentableText());
    assertEquals(throwable, baseClassRef.resolve());
    assertEquals("java.lang.Throwable", baseClassRef.getCanonicalText());

    teardownLoadingFilter();

    assertTrue(anonClass.getParent() instanceof PsiNewExpression);
  }

  public void testLocalClass() {
    setupLoadingFilter();

    PsiClass cloneableClass = myJavaFacade.findClass("java.lang.Cloneable", GlobalSearchScope.allScope(myProject));
    PsiClass[] inheritors = ClassInheritorsSearch.search(cloneableClass, GlobalSearchScope.projectScope(myProject), true).toArray(PsiClass.EMPTY_ARRAY);
    assertEquals(2, inheritors.length);
    assertTrue(inheritors[0] instanceof PsiAnonymousClass || inheritors[1] instanceof PsiAnonymousClass);
    PsiClass localClass = inheritors[0] instanceof PsiAnonymousClass ? inheritors[1] : inheritors[0];

    teardownLoadingFilter();

    assertTrue(localClass.getParent() instanceof PsiDeclarationStatement);
  }

  public void testClassNameModification() {
    PsiClass aClass = myJavaFacade.findClass("pack.MyClass2", GlobalSearchScope.allScope(myProject));
    assertNotNull(aClass);

    ApplicationManager.getApplication().runWriteAction(() -> {
      aClass.getNameIdentifier().replace(myJavaFacade.getElementFactory().createIdentifier("NewName"));
    });

    assertEquals("pack.NewName", aClass.getQualifiedName());
  }

  public void testModification1() {
    PsiClass aClass = myJavaFacade.findClass("pack.MyClass2", GlobalSearchScope.allScope(myProject));
    assertNotNull(aClass);

    PsiField field = aClass.getFields()[0];
    ApplicationManager.getApplication().runWriteAction(() -> {
      aClass.getNameIdentifier().replace(myJavaFacade.getElementFactory().createIdentifier("NewName"));
    });

    assertTrue(field.isValid());
  }

  public void testModification2() {
    PsiClass aClass = myJavaFacade.findClass("pack.MyClass2", GlobalSearchScope.allScope(myProject));
    ApplicationManager.getApplication().runWriteAction(() -> PsiUtil.setModifierProperty(aClass, PsiModifier.FINAL, true));


    PsiClass aClass2 = myJavaFacade.findClass("pack.MyClass2", GlobalSearchScope.allScope(myProject));
    assertEquals(aClass, aClass2);
  }

  public void testModification3() {
    PsiClass aClass = myJavaFacade.findClass("pack.MyInterface1", GlobalSearchScope.allScope(myProject));
    TextRange classRange = aClass.getTextRange();
    String text = aClass.getText();

    BlockSupport blockSupport = ServiceManager.getService(myProject, BlockSupport.class);
    final PsiFile psiFile = aClass.getContainingFile();
    ApplicationManager.getApplication().runWriteAction(() -> blockSupport.reparseRange(psiFile, classRange.getStartOffset(), classRange.getEndOffset(), ""));

    LOG.assertTrue(!aClass.isValid());
    ApplicationManager.getApplication().runWriteAction(() -> blockSupport.reparseRange(psiFile, classRange.getStartOffset(), classRange.getStartOffset(), text));


    aClass = myJavaFacade.findClass("pack.MyInterface1", GlobalSearchScope.allScope(myProject));
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
  }

  public void testRenamePackage() {
    renamePackage("pack", "renamedPack");

    PsiClass psiClass = myJavaFacade.findClass("renamedPack.MyInterface1", GlobalSearchScope.allScope(myProject));
    assertNotNull(psiClass);
  }

  public void testReplaceRootWithSubRoot1() {
    final PsiClass aClass = myJavaFacade.findClass("pack.MyInterface1", GlobalSearchScope.allScope(myProject));
    final PsiFile psiFile = aClass.getContainingFile();
    ((PsiJavaFile) psiFile).getClasses();
    psiFile.getText();
    assertNotNull(aClass);

    final VirtualFile newSourceRoot = psiFile.getVirtualFile().getParent();
    replaceSourceRoot(newSourceRoot);

    assertEquals("MyInterface1", aClass.getName());
  }

  public void testReplaceRootWithSubRoot2() {
    final PsiClass aClass = myJavaFacade.findClass("pack.MyInterface1", GlobalSearchScope.allScope(myProject));
    assertNotNull(aClass);

    final VirtualFile newSourceRoot = aClass.getContainingFile().getVirtualFile().getParent();
    replaceSourceRoot(newSourceRoot);

    assertEquals("MyInterface1", aClass.getName());
  }

  private void replaceSourceRoot(final VirtualFile newSourceRoot) {
    ApplicationManager.getApplication().runWriteAction(
      () -> {
        final ModifiableRootModel rootModel = ModuleRootManager.getInstance(myModule).getModifiableModel();
        final ContentEntry[] content = rootModel.getContentEntries();
        boolean contentToChangeFound = false;
        for (ContentEntry contentEntry : content) {
          final SourceFolder[] sourceFolders = contentEntry.getSourceFolders();
          for (SourceFolder sourceFolder : sourceFolders) {
            contentEntry.removeSourceFolder(sourceFolder);
          }
          final VirtualFile contentRoot = contentEntry.getFile();
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

  public void testParentIdAssert() {
    PsiClass jpanelClass = myJavaFacade.findClass("javax.swing.JPanel", GlobalSearchScope.allScope(myProject));
    PsiClass[] inheritors = ClassInheritorsSearch.search(jpanelClass, GlobalSearchScope.projectScope(myProject), true).toArray(PsiClass.EMPTY_ARRAY);
    assertEquals(2, inheritors.length);
    assertTrue(inheritors[0] instanceof PsiAnonymousClass || inheritors[1] instanceof PsiAnonymousClass);
    PsiClass nonAnonClass = inheritors[0] instanceof PsiAnonymousClass ? inheritors[1] : inheritors[0];

    PsiMethod[] methods = nonAnonClass.getMethods();
    assertEquals(1, methods.length);
    PsiTypeElement newType = myJavaFacade.getElementFactory().createTypeElement(PsiType.FLOAT);
    ApplicationManager.getApplication().runWriteAction(() -> {
      methods[0].getReturnTypeElement().replace(newType);
    });
  }

  public void testParentIdAssertOnExternalChange() throws Exception {
    PsiDirectory root = ProjectRootUtil.getAllContentRoots(myProject) [0];
    VirtualFile vFile = root.getVirtualFile().findChild("MyClass1.java");

    PsiJavaFile psiFile = (PsiJavaFile) myPsiManager.findFile(vFile);
    psiFile.getClasses();

    rewriteFileExternally(vFile, "import a . b;\n" +
                                 "import c.*;\n" +
                                 "import\n" +
                                 "\n" +
                                 "class MyClass1{\n" +
                                 "  {\n" +
                                 "    class Local{\n" +
                                 "      public void foo(){\n" +
                                 "        new Runnable(){\n" +
                                 "          public void run(){\n" +
                                 "            new Throwable(){\n" +
                                 "            };\n" +
                                 "          };\n" +
                                 "        }\n" +
                                 "      }\n" +
                                 "    };\n" +
                                 "  }\n" +
                                 "}\n" +
                                 "\n" +
                                 "class Class2{\n" +
                                 "}\n");

    PsiClass myClass = myJavaFacade.findClass("MyClass1", GlobalSearchScope.allScope(myProject));
    myClass.getChildren();
  }

  public void testCopyableUserDataChild() {
    final PsiClass aClass = myJavaFacade.findClass("pack.MyInterface1", GlobalSearchScope.allScope(myProject));
    assertNotNull(aClass);
    final PsiFile containingFile = aClass.getContainingFile();
    final CompositeElement element = ((PsiFileImpl)containingFile).calcTreeElement();
    aClass.putCopyableUserData(TEST_KEY, "TEST");
    final PsiJavaFile fileCopy = (PsiJavaFile)containingFile.copy();
    final PsiClass[] classesCopy = fileCopy.getClasses();
    assertEquals(1, classesCopy.length);
    assertNotNull(element);
    assertEquals("TEST", classesCopy[0].getCopyableUserData(TEST_KEY));
  }

  private static void rewriteFileExternally(VirtualFile vFile, String text) throws IOException {
    FileUtil.writeToFile(new File(vFile.getPath()), text.getBytes(CharsetToolkit.UTF8_CHARSET));
    vFile.refresh(false, false);
  }

  private void renamePackage(String packageName, String newPackageName) {
    PsiPackage aPackage = JavaPsiFacade.getInstance(myPsiManager.getProject()).findPackage(packageName);
    assertNotNull("Package " + packageName + " not found", aPackage);

    //PsiDirectory dir = aPackage.getDirectories()[0];
    //to rename dir with classes move is used
    new RenameProcessor(myProject, aPackage, newPackageName, true, true).run();
    FileDocumentManager.getInstance().saveAllDocuments();
  }


}
