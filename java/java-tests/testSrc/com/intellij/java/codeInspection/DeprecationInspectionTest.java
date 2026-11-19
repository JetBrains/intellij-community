// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.deprecation.DeprecationInspection;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.JavaModuleExternalPaths;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.JavaInspectionFixture;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.impl.JavaCodeInsightTestFixtureImpl;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.JavaInspectionFixtureKt.javaInspectionFixtureWithProjectPerTest;
import static org.assertj.core.api.Assertions.assertThat;

@TestApplication
public class DeprecationInspectionTest {
  private static final String TEST_DATA_PATH = JavaTestUtil.getJavaTestDataPath() + "/inspection";
  private final TestFixture<JavaInspectionFixture> javaInspection = javaInspectionFixtureWithProjectPerTest(
    testName -> new ExternalAnnotationsDescriptor(PlatformTestUtil.lowercaseFirstLetter(testName, true)), TEST_DATA_PATH);

  /**
   * The mock JDK 11 and the external annotations in the test data directory of the test.
   */
  private static final class ExternalAnnotationsDescriptor extends DefaultLightProjectDescriptor {
    private final String myTestName;

    private ExternalAnnotationsDescriptor(String testName) {
      myTestName = testName;
    }

    @Override
    public void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry) {
      super.configureModule(module, model, contentEntry);
      model.getModuleExtension(JavaModuleExternalPaths.class)
        .setExternalAnnotationUrls(new String[]{VfsUtilCore.pathToUrl(TEST_DATA_PATH + "/deprecation/" + myTestName + "/extAnnotations")});
    }

    @Override
    public Sdk getSdk() {
      return IdeaTestUtil.getMockJdk11();
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof ExternalAnnotationsDescriptor descriptor && myTestName.equals(descriptor.myTestName);
    }

    @Override
    public int hashCode() {
      return myTestName.hashCode();
    }
  }

  private void doTest() {
    DeprecationInspection tool = new DeprecationInspection();
    tool.IGNORE_IN_SAME_OUTERMOST_CLASS = false;
    javaInspection.get().doTest("deprecation/" + javaInspection.get().getTestName(true), tool);
  }

  @Test
  public void testDeprecatedMethod() {
    doTest();
  }

  @Test
  public void testDeprecatedInImport() {
    doTest();
  }

  @Test
  public void testDeprecatedInStaticImport() {
    doTest();
  }

  @Test
  public void testDeprecatedInner() {
    doTest();
  }

  @Test
  public void testDeprecatedField() {
    doTest();
  }

  @Test
  public void testDeprecatedDefaultConstructorInSuper() {
    doTest();
  }

  @Test
  public void testDeprecatedDefaultConstructorInSuperNotCalled() {
    doTest();
  }

  @Test
  public void testDeprecatedDefaultConstructorTypeParameter() {
    doTest();
  }

  @Test
  public void testDeprecationOnVariableWithAnonymousClass() {
    doTest();
  }

  @Test
  public void testDeprecatedAnnotationProperty() {
    doTest();
  }

  @Test
  public void testMethodsOfDeprecatedClass() {
    final DeprecationInspection tool = new DeprecationInspection();
    tool.IGNORE_METHODS_OF_DEPRECATED = false;
    javaInspection.get().doTest("deprecation/" + javaInspection.get().getTestName(true), tool);
  }

  @Test
  public void testIgnoreInSameOutermostClass() {
    final DeprecationInspection tool = new DeprecationInspection();
    javaInspection.get().doTest("deprecation/" + javaInspection.get().getTestName(true), tool);
  }
  
  @Test
  public void testLanguageLevel8() {
    IdeaTestUtil.withLevel(javaInspection.get().getFixture().getModule(), LanguageLevel.JDK_1_8, () -> doTest());
  }
  
  @Test
  public void testLanguageLevel9() {
    IdeaTestUtil.withLevel(javaInspection.get().getFixture().getModule(), LanguageLevel.JDK_1_9, () -> doTest());
  }

  @Test
  public void testDeprecatedUsageInJavadoc() {
    doTest();
  }

  @Test
  public void testDeprecatedDefaultConstructor() {
    JavaCodeInsightTestFixture myFixture = javaInspection.get().getFixture();
    myFixture.enableInspections(new DeprecationInspection());
    myFixture.configureByText("B.java", """
      class B extends A {
          B() { this(0); }
          B(int i) { super(i); }
      }
      class A {
          @Deprecated A() {}
          A(int i) {}
      }""");
    assertThat(myFixture.doHighlighting(HighlightSeverity.WARNING)).isEmpty();
  }
  
  @Test
  public void testDeprecatedPackage() {
    JavaCodeInsightTestFixture myFixture = javaInspection.get().getFixture();
    DeprecationInspection inspection = new DeprecationInspection();
    inspection.IGNORE_IMPORT_STATEMENTS = false;
    myFixture.enableInspections(inspection);
    myFixture.addFileToProject("pkg/package-info.java", "@Deprecated package pkg;");
    myFixture.addFileToProject("pkg/A.java", "package pkg; public class A {public static void b() {}}");
    myFixture.configureByText("Test.java", """
      import <warning descr="'pkg' is deprecated">pkg</warning>.A;

      public class Test {
        void foo() {
          A.b();
        }
      }""");
    ((JavaCodeInsightTestFixtureImpl)myFixture)
      .setVirtualFileFilter(file -> !file.getName().equals("Test.java"));
    myFixture.testHighlighting();
  }

  @Test
  public void testExternallyDeprecatedDefaultConstructor() {
    doTest();
  }
}
