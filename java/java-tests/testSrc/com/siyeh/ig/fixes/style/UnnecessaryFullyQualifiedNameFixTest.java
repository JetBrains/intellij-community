// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.style;

import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesFixture;
import com.siyeh.ig.style.UnnecessaryFullyQualifiedNameInspection;
import org.intellij.lang.annotations.Language;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
public class UnnecessaryFullyQualifiedNameFixTest {
  private final TestFixture<IGQuickFixesFixture> ig =
    igQuickFixesFixture(() -> List.of(new UnnecessaryFullyQualifiedNameInspection()), null, null, null, getEnvironmentClasses());

  @Test
  public void testLeaveFQNamesInJavadoc() {
    doTest(
      """
        /**
         * @see java.util.List
         */
        class X {  /**/java.util.List l;}""",

      """
        import java.util.List;

        /**
         * @see java.util.List
         */
        class X {  List l;}""",
      JavaCodeStyleSettings.FULLY_QUALIFY_NAMES_ALWAYS
    );
  }

  @Test
  public void testReplaceFQNamesInJavadoc() {
    doTest(
      """
        /**
         * @see java.util.List
         */
        class X {  /**/java.util.List l;}""",

      """
        import java.util.List;

        /**
         * @see List
         */
        class X {  List l;}""",
      JavaCodeStyleSettings.FULLY_QUALIFY_NAMES_IF_NOT_IMPORTED
    );
  }

  @Test
  public void testReplaceFQNamesInJavadoc2() {
    doTest(
      """
        /**
         * @see java.util.List
         */
        class X {  /**/java.util.List l;}""",

      """
        import java.util.List;

        /**
         * @see List
         */
        class X {  List l;}""",
      JavaCodeStyleSettings.SHORTEN_NAMES_ALWAYS_AND_ADD_IMPORT
    );
  }

  @Test
  public void testPackageInfo() {
    doTest(
      """
        /**
         * @see javax.annotation.Nonnull
         */
        @/**/javax.annotation.Nonnull
        package p;
        """,

      """
        /**
         * @see javax.annotation.Nonnull
         */
        @Nonnull
        package p;

        import javax.annotation.Nonnull;""",
      JavaCodeStyleSettings.SHORTEN_NAMES_ALWAYS_AND_ADD_IMPORT, "package-info.java");
  }

  @Test
  public void testAlreadyImported() {
    ig.get().doTest(InspectionGadgetsBundle.message("unnecessary.fully.qualified.name.remove.quickfix"),
           "import java.util.List;" +
           "class X {" +
           "  /**/java.util.List l;" +
           "}",

           "import java.util.List;" +
           "class X {" +
           "  List l;" +
           "}");
  }

  @Test
  public void testJavaLang() {
    ig.get().doTest(InspectionGadgetsBundle.message("unnecessary.fully.qualified.name.remove.quickfix"),
           "class X {" +
           "  /**/java.lang.String s;" +
           "}",

           "class X {" +
           "  String s;" +
           "}");
  }

  @Test
  public void testCaretOnClassName() {
    ig.get().doTest(InspectionGadgetsBundle.message("unnecessary.fully.qualified.name.remove.quickfix"),
           "class X {" +
           "  java.lang.St/**/ring s;" +
           "}",

           "class X {" +
           "  String s;" +
           "}");
  }

  private void doTest(@Language("JAVA") @NotNull @NonNls String before, @Language("JAVA") @NotNull @NonNls String after,
                      @MagicConstant(intValues = {
                        JavaCodeStyleSettings.FULLY_QUALIFY_NAMES_ALWAYS,
                        JavaCodeStyleSettings.FULLY_QUALIFY_NAMES_IF_NOT_IMPORTED,
                        JavaCodeStyleSettings.SHORTEN_NAMES_ALWAYS_AND_ADD_IMPORT
                      }) int classNamesInJavadoc) {
    doTest(before, after, classNamesInJavadoc, getClass().getName() + "_" + ig.get().getTestName(false) + ".java");
  }

  private void doTest(@Language("JAVA") @NotNull @NonNls String before,
                      @Language("JAVA") @NotNull @NonNls String after,
                      @MagicConstant(intValues = {
                        JavaCodeStyleSettings.FULLY_QUALIFY_NAMES_ALWAYS,
                        JavaCodeStyleSettings.FULLY_QUALIFY_NAMES_IF_NOT_IMPORTED,
                        JavaCodeStyleSettings.SHORTEN_NAMES_ALWAYS_AND_ADD_IMPORT
                      }) int classNamesInJavadoc,
                      String fileName) {
    JavaCodeInsightTestFixture myFixture = ig.get().getFixture();
    final JavaCodeStyleSettings javaSettings = JavaCodeStyleSettings.getInstance(myFixture.getProject());
    javaSettings.CLASS_NAMES_IN_JAVADOC = classNamesInJavadoc;
    ig.get().doTest(InspectionGadgetsBundle.message("unnecessary.fully.qualified.name.replace.quickfix"), before, after, fileName);
  }

  private static String[] getEnvironmentClasses() {
    return new String[] {
      "package javax.annotation; public @interface Nonnull {}"
    };
  }
}
