// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.Nullability;
import com.intellij.codeInsight.NullabilityAnnotationInfo;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInsight.NullableNotNullManagerImpl;
import com.intellij.codeInspection.dataFlow.DfaPsiUtil;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.fixtures.MavenDependencyUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@TestApplication
public class NullableNotNullManagerTest {
  private static final DefaultLightProjectDescriptor DESCRIPTOR = new DefaultLightProjectDescriptor() {
    @Override
    public void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry) {
      super.configureModule(module, model, contentEntry);
      VirtualFile file =
        StandardFileSystems.local().refreshAndFindFileByPath(JavaTestUtil.getJavaTestDataPath() + "/nullableAnnotations/");
      addJetBrainsAnnotations(model);
      MavenDependencyUtil.addFromMaven(model, "com.google.code.findbugs:jsr305:3.0.2");
      // Library order is important
      PsiTestUtil.newLibrary("lib_2.0").classesRoot(file.findChild("lib_2.0")).addTo(model);
      PsiTestUtil.newLibrary("lib_1.0").classesRoot(file.findChild("lib_1.0")).addTo(model);
    }
  };
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(DESCRIPTOR);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  private NullableNotNullManagerImpl myManager;

  @BeforeEach
  void setUp() {
    NullableNotNullManager manager = NullableNotNullManager.getInstance(codeInsightFixture.get().getProject());
    assumeTrue(manager instanceof NullableNotNullManagerImpl);
    myManager = (NullableNotNullManagerImpl)manager;
  }

  @AfterEach
  void tearDown() {
    if (myManager != null) {
      myManager.loadState(new Element("x"));
    }
  }

  @Test
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
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    JavaPsiFacade facade = JavaPsiFacade.getInstance(myFixture.getProject());
    GlobalSearchScope scope = GlobalSearchScope.allScope(myFixture.getProject());
    NullableNotNullManager manager = NullableNotNullManager.getInstance(myFixture.getProject());
    return ReadAction.computeBlocking(() -> {
      PsiClass testClass = facade.findClass(className, scope);
      assertNotNull(testClass);
      PsiMethod[] fooMethods = testClass.findMethodsByName(methodName, false);
      assertThat(fooMethods).hasSize(1);
      return manager.findEffectiveNullabilityInfo(fooMethods[0]);
    });
  }

  @Test
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

  @Test
  public void testTypeAnnotationNullabilityOnStubs() {
    PsiClass clazz = codeInsightFixture.get().addClass("import org.jetbrains.annotations.*;" +
                                        "interface Foo {" +
                                        "  String @NotNull [] read();" +
                                        "}");
    ReadAction.runBlocking(() -> assertEquals(Nullability.NOT_NULL, DfaPsiUtil.getTypeNullability(clazz.getMethods()[0].getReturnType())));
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
