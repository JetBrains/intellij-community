// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.*;
import com.intellij.codeInspection.dataFlow.DfaPsiUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.MavenDependencyUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.junit.Assume;

import java.util.List;

public class NullableNotNullManagerTest extends LightJavaCodeInsightFixtureTestCase {
  private NullableNotNullManagerImpl myManager;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    NullableNotNullManager manager = NullableNotNullManager.getInstance(getProject());
    Assume.assumeTrue(manager instanceof NullableNotNullManagerImpl);
    myManager = (NullableNotNullManagerImpl)manager;
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return new DefaultLightProjectDescriptor() {
      @Override
      public void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry) {
        super.configureModule(module, model, contentEntry);
        VirtualFile file =
          LocalFileSystem.getInstance().refreshAndFindFileByPath(JavaTestUtil.getJavaTestDataPath() + "/nullableAnnotations/");
        MavenDependencyUtil.addFromMaven(model, "com.google.code.findbugs:jsr305:3.0.2");
        // Library order is important
        PsiTestUtil.newLibrary("lib_2.0").classesRoot(file.findChild("lib_2.0")).addTo(model);
        PsiTestUtil.newLibrary("lib_1.0").classesRoot(file.findChild("lib_1.0")).addTo(model);
      }
    };
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      myManager.loadState(new Element("x"));
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }
  
  public void testSplitPackageContainerAnnotation() {
    // Check that annotations work normally
    NullabilityAnnotationInfo fooNullability = getMethodReturnNullability("pkg.Test", "foo");
    assertNotNull(fooNullability);
    assertEquals(Nullability.NOT_NULL, fooNullability.getNullability());
    assertTrue(fooNullability.isContainer());

    // Check the conflicting case
    NullabilityAnnotationInfo nullability = getMethodReturnNullability("pkg.Conflict", "getNullableSomething");
    assertNull(nullability);
  }

  private NullabilityAnnotationInfo getMethodReturnNullability(String className, String methodName) {
    JavaPsiFacade facade = JavaPsiFacade.getInstance(getProject());
    GlobalSearchScope scope = GlobalSearchScope.allScope(getProject());
    NullableNotNullManager manager = NullableNotNullManager.getInstance(getProject());
    PsiClass testClass = facade.findClass(className, scope);
    assertNotNull(testClass);
    PsiMethod[] fooMethods = testClass.findMethodsByName(methodName, false);
    assertSize(1, fooMethods);
    return manager.findEffectiveNullabilityInfo(fooMethods[0]);
  }

  public void testCannotAddNotNullToNullable() {
    assertNotNull(myManager);
    checkAnnotations();
    myManager.setNotNulls("foo.NotNull");
    checkAnnotations();
    assertTrue(myManager.getNotNulls().contains("foo.NotNull"));
    myManager.setNotNulls(AnnotationUtil.NULLABLE);
    myManager.setNullables(AnnotationUtil.NOT_NULL);
    checkAnnotations();
  }

  public void testTypeAnnotationNullabilityOnStubs() {
    PsiClass clazz = myFixture.addClass("import org.jetbrains.annotations.*;" +
                                        "interface Foo {" +
                                        "  String @NotNull [] read();" +
                                        "}");
    assertEquals(Nullability.NOT_NULL, DfaPsiUtil.getTypeNullability(clazz.getMethods()[0].getReturnType()));
    assertFalse(((PsiFileImpl)clazz.getContainingFile()).isContentsLoaded());
  }

  private void checkAnnotations() {
    List<String> notNulls = myManager.getNotNulls();
    assertTrue(notNulls.contains(AnnotationUtil.NOT_NULL));
    assertFalse(notNulls.contains(AnnotationUtil.NULLABLE));
    List<String> nullables = myManager.getNullables();
    assertTrue(nullables.contains(AnnotationUtil.NULLABLE));
    assertFalse(nullables.contains(AnnotationUtil.NOT_NULL));
  }
}
