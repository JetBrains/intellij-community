// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight;

import com.intellij.JavaTestUtil;
import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInsight.generation.ClassMember;
import com.intellij.codeInsight.generation.GenerateConstructorHandler;
import com.intellij.codeInsight.generation.RecordConstructorMember;
import com.intellij.codeInsight.generation.actions.GenerateConstructorAction;
import com.intellij.java.codeInspection.DataFlowInspectionTest;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.ArrayUtil;
import com.intellij.util.VisibilityUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;

@TestApplication
public class GenerateConstructorTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_LATEST_WITH_LATEST_JDK);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    codeInsightFixture.get().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/codeInsight/generateConstructor");
    JavaCodeStyleSettings.getInstance(myFixture.getProject()).VISIBILITY = VisibilityUtil.ESCALATE_VISIBILITY;
  }

  @Test
  public void testClassNestedInImplicitClass() {
    doTest();
  }
  @Test
  public void testAbstractClass() {
    doTest();
  }
  @Test
  public void testNewLine() {
    doTest();
  }
  @Test
  public void testPackageLocalClass() {
    doTest();
  }
  @Test
  public void testPrivateClass() {
    doTest();
  }
  @Test
  public void testBoundComments() {
    doTest();
  }
  @Test
  public void testSameNamedFields() {
    doTest();
  }
  @Test
  public void testEnumWithAbstractMethod() {
    doTest();
  }
  @Test
  public void testNoMoreConstructorsCanBeGenerated() {
    doTest();
  }
  @Test
  public void testBaseVarargs() {
    doTest();
  }
  @Test
  public void testFinalFieldPreselection() {
    doTest(true);
  }
  @Test
  public void testSubstitution() {
    doTest(true);
  }

  @Test
  public void testImmediatelyAfterRBrace() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();    // IDEADEV-28811
    CodeStyle.getSettings(myFixture.getProject()).getCommonSettings(JavaLanguage.INSTANCE).CLASS_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE;
    doTest();
  }

  @Test
  public void testBoundCommentsKeepsBlankLine() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    CodeStyle.getSettings(myFixture.getProject()).getCommonSettings(JavaLanguage.INSTANCE).BLANK_LINES_AFTER_CLASS_HEADER = 1;
    doTest();
  }

  @Test
  public void testFieldPrefixCoincidence() {
    getJavaSettings().FIELD_NAME_PREFIX = "m";
    doTest();
  }

  @NotNull
  private JavaCodeStyleSettings getJavaSettings() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    return JavaCodeStyleSettings.getInstance(myFixture.getProject());
  }

  @Test
  public void testFieldPrefixCoincidence1() {
    getJavaSettings().FIELD_NAME_PREFIX = "_";
    doTest();
  }

  @Test
  public void testTypeAnnotatedField() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package foo;\n\nimport java.lang.annotation.*;\n\n@Target(ElementType.TYPE_USE) public @interface TestNotNull { }");
    NullableNotNullManager manager = NullableNotNullManager.getInstance(myFixture.getProject());
    manager.setNotNulls("foo.TestNotNull");
    Disposer.register(myFixture.getTestRootDisposable(), manager::setNotNulls);
    doTest();
  }

  @Test
  public void testParametersForFieldsNotNullByDefaultShouldNotGetExplicitAnnotation() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    DataFlowInspectionTest.addJavaxNullabilityAnnotations(myFixture);
    myFixture.addClass("package foo;" +
                       "import static java.lang.annotation.ElementType.*;" +
                       "@javax.annotation.meta.TypeQualifierDefault({PARAMETER, FIELD, METHOD, LOCAL_VARIABLE}) " +
                       "@javax.annotation.Nonnull " +
                       "public @interface NonnullByDefault {}");
    doTest();
  }

  @Test
  public void testNullableField() {
    doTest();
  }

  @Test
  public void testRecordCompactConstructor() {
    doTestRecordConstructor((aClass, m) -> new ClassMember[]{new RecordConstructorMember(aClass, true)});
  }

  @Test
  public void testRecordCanonicalConstructor() {
    doTestRecordConstructor((aClass, m) -> new ClassMember[]{new RecordConstructorMember(aClass, false)});
  }

  @Test
  public void testRecordCustomConstructor() {
    doTestRecordConstructor((aClass, m) -> m);
  }

  @Test
  public void testRecordCustomConstructor2() {
    doTestRecordConstructor((aClass, m) -> Arrays.copyOf(m, 2));
  }
  
  @Test
  public void testRecordCustomConstructor3() {
    doTestRecordConstructor((aClass, m) -> Arrays.copyOf(m, 2));
  }

  @Test
  public void testRecordCustomConstructorOrder() {
    doTestRecordConstructor((aClass, m) -> ArrayUtil.reverseArray(m));
  }

  @Test
  public void testImplicitClass() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    String name = myFixture.getTestName(false);
    PsiJavaFile file = (PsiJavaFile) myFixture.configureByFile("before" + name + ".java");
    ReadAction.runBlocking(() -> {
      PsiClass[] classes = file.getClasses();
      assertThat(classes).hasSize(1);
      PsiClass aClass = classes[0];
      GenerateConstructorAction action = new GenerateConstructorAction();
      assertFalse(action.isValidForClass(aClass));
    });
  }

  private void doTest() {
    doTest(false);
  }

  private void doTestRecordConstructor(BiFunction<PsiClass, ClassMember[], ClassMember[]> chooser) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    String name = myFixture.getTestName(false);
    myFixture.configureByFile("before" + name + ".java");
    EdtTestUtil.runInEdtAndWait(() -> {
      new GenerateConstructorHandler() {
        @Override
        protected ClassMember @Nullable [] chooseOriginalMembers(PsiClass aClass, Project project) {
          return chooser.apply(aClass, getAllOriginalMembers(aClass));
        }
      }.invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile());
      myFixture.checkResultByFile("after" + name + ".java");
    });
  }

  private void doTest(boolean preSelect) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    String name = myFixture.getTestName(false);
    myFixture.configureByFile("before" + name + ".java");
    EdtTestUtil.runInEdtAndWait(() -> {
      new GenerateConstructorHandler() {
        @Override
        protected ClassMember[] chooseMembers(ClassMember[] members, boolean allowEmpty, boolean copyJavadoc, Project project, Editor editor) {
          if (preSelect) {
            List<ClassMember> preselection = GenerateConstructorHandler.preselect(members);
            return preselection.toArray(ClassMember.EMPTY_ARRAY);
          }
          else {
            return members;
          }
        }
      }.invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile());
      myFixture.checkResultByFile("after" + name + ".java");
    });
  }
}