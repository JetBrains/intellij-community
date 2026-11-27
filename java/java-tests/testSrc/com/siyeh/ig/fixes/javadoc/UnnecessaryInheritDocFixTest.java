// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes.javadoc;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesFixture;
import com.siyeh.ig.javadoc.UnnecessaryInheritDocInspection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

/**
 * @author Fabrice TIERCELIN
 */
@TestApplication
public class UnnecessaryInheritDocFixTest {
  private final TestFixture<IGQuickFixesFixture> ig =
    igQuickFixesFixture(() -> List.of(new UnnecessaryInheritDocInspection()), null, null,
                        builder -> builder.setLanguageLevel(LanguageLevel.JDK_1_9));

  @Test
  public void testRemoveJavaLangImport() {
    ig.get().doTest(InspectionGadgetsBundle.message("unnecessary.inherit.doc.quickfix"),
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

  @Test
  public void testDoNotFixCompletedDoc() {
    ig.get().assertQuickfixNotAvailable(InspectionGadgetsBundle.message("unnecessary.inherit.doc.quickfix"),
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
