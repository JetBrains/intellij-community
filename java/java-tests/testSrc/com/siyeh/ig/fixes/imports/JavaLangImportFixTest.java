// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes.imports;

import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesFixture;
import com.siyeh.ig.imports.JavaLangImportInspection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

/**
 * @author Fabrice TIERCELIN
 */
@TestApplication
public class JavaLangImportFixTest {
  private final TestFixture<IGQuickFixesFixture> ig =
    igQuickFixesFixture(() -> List.of(new JavaLangImportInspection()));

  @Test
  public void testRemoveJavaLangImport() {
    ig.get().doTest(InspectionGadgetsBundle.message("delete.import.quickfix"),
           """
             import/**/ java.lang.String;

             class X {
               public void printName(String[] names) {
               }
             }
             """,
           """
             class X {
               public void printName(String[] names) {
               }
             }
             """
    );
  }

  @Test
  public void testRemoveJavaLangImportOnDemand() {
    ig.get().doTest(InspectionGadgetsBundle.message("delete.import.quickfix"),
           """
             import/**/ java.lang.*;

             class X {
               public void printName(String[] names) {
               }
             }
             """,
           """
             class X {
               public void printName(String[] names) {
               }
             }
             """
    );
  }

  @Test
  public void testRemoveJavaLangImportAmongOther() {
    ig.get().doTest(InspectionGadgetsBundle.message("delete.import.quickfix"),
           """
             import/**/ java.lang.String;
             import java.util.Date;

             class X {
               public String printDate(Date date) {
                 return date.toString();
               }
             }
             """,
           """
             import java.util.Date;

             class X {
               public String printDate(Date date) {
                 return date.toString();
               }
             }
             """
    );
  }
}
