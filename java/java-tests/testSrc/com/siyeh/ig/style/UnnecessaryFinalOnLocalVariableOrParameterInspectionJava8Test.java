// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.style;

import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

@TestApplication
public class UnnecessaryFinalOnLocalVariableOrParameterInspectionJava8Test {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new UnnecessaryFinalOnLocalVariableOrParameterInspection());

  @Test
  public void testFinalWithoutInnerClass() {
    inspectionFixture.get().doTest("""
             class Issue {
                 public static void main(String[] args) {
                     /*Unnecessary 'final' on variable 's'*/final/**/ int s;
                     if (args.length == 0) {
                         s = 1;
                     } else {
                         s = 2;
                     }
                     new Runnable() {
                         @Override
                         public void run() {
                             System.out.println(s);
                         }
                     };        System.out.println(s);
                 }
             }""");
  }

  @Test
  public void testInterfaceMethods() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    final UnnecessaryFinalOnLocalVariableOrParameterInspection inspection = new UnnecessaryFinalOnLocalVariableOrParameterInspection();
    inspection.onlyWarnOnAbstractMethods = true;
    myFixture.enableInspections(inspection);
    inspectionFixture.get().doTest("interface X {" +
           "  default void m(final String s) {}" +
           "  static void n(final String s) {}" +
           "  void o(/*Unnecessary 'final' on parameter 's'*/final/**/ String s);" +
           "}");
  }
  
  @Test
  public void testTryWithResources() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    final UnnecessaryFinalOnLocalVariableOrParameterInspection inspection = new UnnecessaryFinalOnLocalVariableOrParameterInspection();
    inspection.reportLocalVariables = false;
    myFixture.enableInspections(inspection);
    inspectionFixture.get().doTest("""
             import java.io.*;
             import java.util.*;

             class FinalTest {
               public List<String> foobar(/*Unnecessary 'final' on parameter 'shouldBeNonFinal'*/final/**/ String shouldBeNonFinal) throws IOException {
                 List<String> finalVar = new ArrayList<>();

                 try (final BufferedReader reader = new BufferedReader(new FileReader(""))) {
                   for (String nonFinalVar = reader.readLine(); nonFinalVar != null; nonFinalVar = reader.readLine()) {
                     finalVar.add(nonFinalVar);
                   }
                 }

                 for (/*Unnecessary 'final' on parameter 's'*/final/**/ String s : finalVar) {

                 }
                 for (final Iterator<String> it = finalVar.iterator(); it.hasNext(); ) {
                   if (it.next() == null) {
                     System.out.println("deleting");
                     it.remove();
                   }
                 }
                 return finalVar;
               }
             }""");
  }

  @Test
  public void testForeachParametersDisabled() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    final UnnecessaryFinalOnLocalVariableOrParameterInspection inspection = new UnnecessaryFinalOnLocalVariableOrParameterInspection();
    inspection.reportForeachParameters = false;
    myFixture.enableInspections(inspection);
    inspectionFixture.get().doTest("""
                      import java.util.Arrays;
             
                      class FinalTest {
                        public void foobar(/*Unnecessary 'final' on parameter 'boo'*/final/**/ String boo) {
                          for (final Object e : Arrays.asList(1, 2, 3)) {
                            System.out.println(e);
                          }
                        }
                      }
             """);
  }

  @Test
  public void testForeachParametersEnabled() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    final UnnecessaryFinalOnLocalVariableOrParameterInspection inspection = new UnnecessaryFinalOnLocalVariableOrParameterInspection();
    inspection.reportForeachParameters = true;
    myFixture.enableInspections(inspection);
    inspectionFixture.get().doTest("""
                      import java.util.Arrays;
             
                      class FinalTest {
                        public void foobar(/*Unnecessary 'final' on parameter 'boo'*/final/**/ String boo) {
                          for (<warning descr="Unnecessary 'final' on parameter 'e'">final</warning> Object e : Arrays.asList(1, 2, 3)) {
                            System.out.println(e);
                          }
                        }
                      }
             """);
  }

  @Test
  public void testCatchParametersDisabled() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    final UnnecessaryFinalOnLocalVariableOrParameterInspection inspection = new UnnecessaryFinalOnLocalVariableOrParameterInspection();
    inspection.reportCatchParameters = false;
    myFixture.enableInspections(inspection);
    inspectionFixture.get().doTest("""
                      import java.io.*;
             
                      class FinalTest {
                        public void foobar(/*Unnecessary 'final' on parameter 'boo'*/final/**/ String boo) {
                          try (/*Unnecessary 'final' on variable 'reader'*/final/**/ BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
                            reader.readLine();
                          }
                          catch (final IOException e) {
                            throw new RuntimeException(e);
                          }
                        }
                      }
             """);
  }

  @Test
  public void testCatchParametersEnabled() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    final UnnecessaryFinalOnLocalVariableOrParameterInspection inspection = new UnnecessaryFinalOnLocalVariableOrParameterInspection();
    inspection.reportCatchParameters = true;
    myFixture.enableInspections(inspection);
    inspectionFixture.get().doTest("""
                      import java.io.*;
             
                      class FinalTest {
                        public void foobar(/*Unnecessary 'final' on parameter 'boo'*/final/**/ String boo) {
                          try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
                            reader.readLine();
                          } catch (/*Unnecessary 'final' on parameter 'e'*/final/**/ IOException e) {
                            throw new RuntimeException(e);
                          }
                        }
                      }
             """);
  }
}
