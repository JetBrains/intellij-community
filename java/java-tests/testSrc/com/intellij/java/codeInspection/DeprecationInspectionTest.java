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
import com.intellij.testFramework.JavaInspectionTestCase;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.impl.JavaCodeInsightTestFixtureImpl;
import org.jetbrains.annotations.NotNull;

public class DeprecationInspectionTest extends JavaInspectionTestCase {

  private final DefaultLightProjectDescriptor myProjectDescriptor = new DefaultLightProjectDescriptor() {
    @Override
    public void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry) {
      super.configureModule(module, model, contentEntry);
      model.getModuleExtension(JavaModuleExternalPaths.class)
        .setExternalAnnotationUrls(new String[]{VfsUtilCore.pathToUrl(getTestDataPath() + "/deprecation/" + getTestName(true) + "/extAnnotations")});
    }

    @Override
    public Sdk getSdk() {
      return IdeaTestUtil.getMockJdk11();
    }
  };

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection";
  }

  private void doTest() {
    DeprecationInspection tool = new DeprecationInspection();
    tool.IGNORE_IN_SAME_OUTERMOST_CLASS = false;
    doTest("deprecation/" + getTestName(true), tool);
  }

  public void testDeprecatedMethod() {
    doTest();
  }

  public void testDeprecatedInImport() {
    doTest();
  }

  public void testDeprecatedInStaticImport() {
    doTest();
  }

  public void testDeprecatedInner() {
    doTest();
  }

  public void testDeprecatedField() {
    doTest();
  }

  public void testDeprecatedDefaultConstructorInSuper() {
    doTest();
  }

  public void testDeprecatedDefaultConstructorInSuperNotCalled() {
    doTest();
  }

  public void testDeprecatedDefaultConstructorTypeParameter() {
    doTest();
  }

  public void testDeprecationOnVariableWithAnonymousClass() {
    doTest();
  }

  public void testDeprecatedAnnotationProperty() {
    doTest();
  }

  public void testMethodsOfDeprecatedClass() {
    final DeprecationInspection tool = new DeprecationInspection();
    tool.IGNORE_METHODS_OF_DEPRECATED = false;
    doTest("deprecation/" + getTestName(true), tool);
  }

  public void testIgnoreInSameOutermostClass() {
    final DeprecationInspection tool = new DeprecationInspection();
    doTest("deprecation/" + getTestName(true), tool);
  }
  
  public void testLanguageLevel8() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_1_8, () -> doTest());
  }
  
  public void testLanguageLevel9() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_1_9, () -> doTest());
  }

  public void testDeprecatedUsageInJavadoc() {
    doTest();
  }

  public void testDeprecatedDefaultConstructor() {
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
    assertEmpty(myFixture.doHighlighting(HighlightSeverity.WARNING));
  }
  
  public void testDeprecatedPackage() {
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

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return myProjectDescriptor;
  }

  public void testExternallyDeprecatedDefaultConstructor() {
    doTest();
  }
}
