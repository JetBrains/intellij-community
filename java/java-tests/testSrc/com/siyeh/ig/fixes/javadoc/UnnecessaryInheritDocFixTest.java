// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes.javadoc;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.javadoc.UnnecessaryInheritDocInspection;

/**
 * @author Fabrice TIERCELIN
 */
public class UnnecessaryInheritDocFixTest extends IGQuickFixesTestCase {
  @Override
  protected void tuneFixture(final JavaModuleFixtureBuilder builder) throws Exception {
    builder.setLanguageLevel(LanguageLevel.JDK_1_9);
  }

  @Override
  protected BaseInspection getInspection() {
    return new UnnecessaryInheritDocInspection();
  }

  public void testRemoveJavaLangImport() {
    doTest(InspectionGadgetsBundle.message("unnecessary.inherit.doc.quickfix"),
           """
               class Example implements Comparable<Example> {
                 /**
                  * {@inheritDoc}<caret>
                  */
                 @Override
                 public int compareTo(Example o) {
                   return 0;
                 }
               }
             """,

           """
               class Example implements Comparable<Example> {
                 @Override
                 public int compareTo(Example o) {
                   return 0;
                 }
               }
             """
    );
  }

  public void testDoNotFixCompletedDoc() {
    assertQuickfixNotAvailable(InspectionGadgetsBundle.message("unnecessary.inherit.doc.quickfix"),
                               """
                                   class Example implements Comparable<Example> {
                                     /**
                                      * {@inheritDoc}<caret>
                                      * This implementation always returns zero.
                                      */
                                     @Override
                                     public int compareTo(Example o) {
                                       return 0;
                                     }
                                   }
                                 """
    );
  }
}
