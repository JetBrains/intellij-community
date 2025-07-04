package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.dataFlow.ConstantValueInspection;
import com.intellij.codeInspection.dataFlow.DataFlowInspection;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;

import java.io.IOException;

public class DataFlowInspectionHeavyTest extends JavaCodeInsightFixtureTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection/dataFlow/fixture/";
  }

  public void testDifferentAnnotationsWithDifferentLanguageLevels() throws IOException {
    Module module6 =
      PsiTestUtil.addModule(getProject(), JavaModuleType.getModuleType(), "mod6", myFixture.getTempDirFixture().findOrCreateDir("mod6"));
    IdeaTestUtil.setModuleLanguageLevel(module6, LanguageLevel.JDK_1_6);
    IdeaTestUtil.setModuleLanguageLevel(getModule(), LanguageLevel.JDK_1_8);
    ModuleRootModificationUtil.addDependency(getModule(), module6);
    ModuleRootModificationUtil.setModuleSdk(module6, ModuleRootManager.getInstance(getModule()).getSdk());

    myFixture.addFileToProject("mod6/annos/annos.java",
                               annotationsText("ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER, ElementType.LOCAL_VARIABLE"));
    myFixture.addFileToProject("mod6/foo/ObjectUtils.java", """
       package foo;
       public class ObjectUtils {
         @annos.NotNull
         public static native <T> T notNull(@annos.Nullable T value);
       }""");

    myFixture.addFileToProject("annos/annos.java", annotationsText("ElementType.TYPE_USE"));
    DataFlowInspectionTestCase.setCustomAnnotations(getProject(), myFixture.getTestRootDisposable(), "annos.NotNull", "annos.Nullable");

    PsiFile testFile = myFixture.addFileToProject("test.java", """
       class Zoo {
         @annos.Nullable String a = null;
         @annos.NotNull String f = foo.ObjectUtils.notNull(<weak_warning descr="Value 'a' is always 'null'">a</weak_warning>);

         void bar(@annos.NotNull String param) { }
         void goo(@annos.Nullable String param) {
           String p1 = foo.ObjectUtils.notNull(param);
           bar(p1);
         }
       }""");
    myFixture.configureFromExistingVirtualFile(testFile.getVirtualFile());
    myFixture.enableInspections(new ConstantValueInspection());
    myFixture.checkHighlighting();
  }

  private static String annotationsText(String targets) {
    return """
          package annos;
          import java.lang.annotation.*;

          @Target({$TARGETS$})
          public @interface NotNull {}

          @Target({$TARGETS$})
          public @interface Nullable {}""".replace("$TARGETS$", targets);
  }

  public void test_no_always_failing_calls_in_tests() throws IOException {
    PsiTestUtil.addSourceRoot(getModule(), myFixture.getTempDirFixture().findOrCreateDir("test"), true);

    myFixture.addFileToProject("test/org/junit/Test.java", """
      package org.junit;

      public @interface Test {
        Class<? extends Throwable> expected();
      }
      """);
    myFixture.configureFromExistingVirtualFile(myFixture.addFileToProject("test/Foo.java", """
      class Foo {
        @org.junit.Test(expected=RuntimeException.class)
        void foo() {
          assertTrue(false);
        }
        private void assertTrue(boolean b) {
          if (!b) throw new RuntimeException();
        }
      }
      """).getVirtualFile());
    myFixture.enableInspections(new DataFlowInspection());
    myFixture.checkHighlighting();
  }

  public void testTypeQualifierNicknameWithoutDeclarations() throws IOException {
    myFixture.addClass("package javax.annotation.meta; public @interface TypeQualifierNickname {}");
    DataFlowInspectionTest.addJavaxNullabilityAnnotations(myFixture);

    String noJsr305dep = "noJsr305dep";
    Module anotherModule =
      PsiTestUtil.addModule(getProject(), JavaModuleType.getModuleType(), noJsr305dep, myFixture.getTempDirFixture().findOrCreateDir(noJsr305dep));
    ModuleRootModificationUtil.setModuleSdk(anotherModule, ModuleRootManager.getInstance(getModule()).getSdk());

    PsiFile nullableNick = myFixture.addFileToProject(noJsr305dep + "/bar/NullableNick.java", DataFlowInspectionTest.barNullableNick());

    // We load AST for anno attribute. In cls usages, this isn't an issue, but for simplicity we're testing with red Java source here
    myFixture.allowTreeAccessForFile(nullableNick.getVirtualFile());

    myFixture.enableInspections(new DataFlowInspection());
    myFixture.configureFromExistingVirtualFile(myFixture.copyFileToProject("TypeQualifierNickname.java", noJsr305dep + "/a.java"));
    myFixture.checkHighlighting(true, false, false);
  }
}
