// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight;

import com.intellij.JavaTestUtil;
import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInsight.generation.ClassMember;
import com.intellij.codeInsight.generation.GenerateConstructorHandler;
import com.intellij.codeInsight.generation.RecordConstructorMember;
import com.intellij.java.codeInspection.DataFlowInspectionTest;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiClass;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.util.ArrayUtil;
import com.intellij.util.VisibilityUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;

public class GenerateConstructorTest extends LightJavaCodeInsightFixtureTestCase {

  @Override
  public void setUp() throws Exception {
    super.setUp();
    JavaCodeStyleSettings.getInstance(getProject()).VISIBILITY = VisibilityUtil.ESCALATE_VISIBILITY;
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_LATEST_WITH_LATEST_JDK;
  }

  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/generateConstructor";
  }

  public void testAbstractClass() { doTest(); }
  public void testNewLine() { doTest(); }
  public void testPackageLocalClass() { doTest(); }
  public void testPrivateClass() { doTest(); }
  public void testBoundComments() { doTest(); }
  public void testSameNamedFields() { doTest(); }
  public void testEnumWithAbstractMethod() { doTest(); }
  public void testNoMoreConstructorsCanBeGenerated() { doTest(); }
  public void testBaseVarargs() { doTest(); }
  public void testFinalFieldPreselection() { doTest(true); }
  public void testSubstitution() { doTest(true); }

  public void testImmediatelyAfterRBrace() {    // IDEADEV-28811
    CodeStyle.getSettings(getProject()).getCommonSettings(JavaLanguage.INSTANCE).CLASS_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE;
    doTest();
  }

  public void testBoundCommentsKeepsBlankLine() {
    CodeStyle.getSettings(getProject()).getCommonSettings(JavaLanguage.INSTANCE).BLANK_LINES_AFTER_CLASS_HEADER = 1;
    doTest();
  }

  public void testFieldPrefixCoincidence() {
    getJavaSettings().FIELD_NAME_PREFIX = "m";
    doTest();
  }

  @NotNull
  private JavaCodeStyleSettings getJavaSettings() {
    return JavaCodeStyleSettings.getInstance(getProject());
  }

  public void testFieldPrefixCoincidence1() {
    getJavaSettings().FIELD_NAME_PREFIX = "_";
    doTest();
  }

  public void testTypeAnnotatedField() {
    myFixture.addClass("package foo;\n\nimport java.lang.annotation.*;\n\n@Target(ElementType.TYPE_USE) public @interface TestNotNull { }");
    NullableNotNullManager manager = NullableNotNullManager.getInstance(getProject());
    manager.setNotNulls("foo.TestNotNull");
    Disposer.register(myFixture.getTestRootDisposable(), manager::setNotNulls);
    doTest();
  }

  public void testParametersForFieldsNotNullByDefaultShouldNotGetExplicitAnnotation() {
    DataFlowInspectionTest.addJavaxNullabilityAnnotations(myFixture);
    myFixture.addClass("package foo;" +
                       "import static java.lang.annotation.ElementType.*;" +
                       "@javax.annotation.meta.TypeQualifierDefault({PARAMETER, FIELD, METHOD, LOCAL_VARIABLE}) " +
                       "@javax.annotation.Nonnull " +
                       "public @interface NonnullByDefault {}");
    doTest();
  }

  public void testNullableField() { doTest(); }

  public void testRecordCompactConstructor() {
    doTestRecordConstructor((aClass, m) -> new ClassMember[]{new RecordConstructorMember(aClass, true)});
  }

  public void testRecordCanonicalConstructor() {
    doTestRecordConstructor((aClass, m) -> new ClassMember[]{new RecordConstructorMember(aClass, false)});
  }

  public void testRecordCustomConstructor() {
    doTestRecordConstructor((aClass, m) -> m);
  }

  public void testRecordCustomConstructor2() {
    doTestRecordConstructor((aClass, m) -> Arrays.copyOf(m, 2));
  }
  
  public void testRecordCustomConstructor3() {
    doTestRecordConstructor((aClass, m) -> Arrays.copyOf(m, 2));
  }

  public void testRecordCustomConstructorOrder() {
    doTestRecordConstructor((aClass, m) -> ArrayUtil.reverseArray(m));
  }

  private void doTest() {
    doTest(false);
  }

  private void doTestRecordConstructor(BiFunction<PsiClass, ClassMember[], ClassMember[]> chooser) {
    String name = getTestName(false);
    myFixture.configureByFile("before" + name + ".java");
    new GenerateConstructorHandler() {
      @Override
      protected ClassMember @Nullable [] chooseOriginalMembers(PsiClass aClass, Project project) {
        return chooser.apply(aClass, getAllOriginalMembers(aClass));
      }
    }.invoke(getProject(), getEditor(), getFile());
    myFixture.checkResultByFile("after" + name + ".java");
  }

  private void doTest(boolean preSelect) {
    String name = getTestName(false);
    myFixture.configureByFile("before" + name + ".java");
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
    }.invoke(getProject(), getEditor(), getFile());
    myFixture.checkResultByFile("after" + name + ".java");
  }
}