// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.dependencies;

import com.intellij.JavaTestUtil;
import com.intellij.analysis.AnalysisScope;
import com.intellij.analysis.JavaAnalysisScope;
import com.intellij.cyclicDependencies.CyclicDependenciesBuilder;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiPackage;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class CyclicDependenciesTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_1_7);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    codeInsightFixture.get().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/dependencies/cycle/");
    myFixture.copyDirectoryToProject(myFixture.getTestName(true), "");
  }

  @Test
  public void testT1() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    // com.a<->com.b
    final CyclicDependenciesBuilder builder = new CyclicDependenciesBuilder(myFixture.getProject(),
                                                                            new AnalysisScope(myFixture.getProject()));
    builder.analyze();
    final HashMap<PsiPackage, Set<List<PsiPackage>>> cyclicDependencies = builder.getCyclicDependencies();
    HashMap<String, String[][]> expected = new HashMap<>();
    expected.put("com.b", new String[][]{{"com.a", "com.b"}});
    expected.put("com.a", new String[][]{{"com.b", "com.a"}});
    checkResult(expected, cyclicDependencies);
  }

  @Test
  public void testPackageScope1(){
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    // com.a<->com.b
    final CyclicDependenciesBuilder builder = new CyclicDependenciesBuilder(myFixture.getProject(),
                                                                            new JavaAnalysisScope(JavaPsiFacade
                                                                              .getInstance(myFixture.getProject()).findPackage("com"), null));
    builder.analyze();
    final HashMap<PsiPackage, Set<List<PsiPackage>>> cyclicDependencies = builder.getCyclicDependencies();
    HashMap<String, String[][]> expected = new HashMap<>();
    expected.put("com.b", new String[][]{{"com.a", "com.b"}});
    expected.put("com.a", new String[][]{{"com.b", "com.a"}});
    checkResult(expected, cyclicDependencies);
  }

  @Test
  public void testT2() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    //com.b<->com.a
    //com.c<->com.d
    final CyclicDependenciesBuilder builder = new CyclicDependenciesBuilder(myFixture.getProject(),
                                                                            new AnalysisScope(myFixture.getProject()));
    builder.analyze();
    final HashMap<PsiPackage, Set<List<PsiPackage>>> cyclicDependencies = builder.getCyclicDependencies();
    HashMap<String, String[][]> expected = new HashMap<>();
    expected.put("com.b", new String[][]{{"com.a", "com.b"}});
    expected.put("com.d", new String[][]{{"com.c", "com.d"}});
    expected.put("com.c", new String[][]{{"com.d", "com.c"}});
    expected.put("com.a", new String[][]{{"com.b", "com.a"}});
    checkResult(expected, cyclicDependencies);
  }

  @Test
  public void testPackageScope2() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    //com.b<->com.a  - find
    //com.c<->com.d  - not in scope
    final CyclicDependenciesBuilder builder = new CyclicDependenciesBuilder(myFixture.getProject(),
                                                                            new JavaAnalysisScope(JavaPsiFacade
                                                                              .getInstance(myFixture.getProject()).findPackage(
                                                                              "com.subscope1"), null));
    builder.analyze();
    final HashMap<PsiPackage, Set<List<PsiPackage>>> cyclicDependencies = builder.getCyclicDependencies();
    HashMap<String, String[][]> expected = new HashMap<>();
    expected.put("com.subscope1.b", new String[][]{{"com.subscope1.a", "com.subscope1.b"}});
    expected.put("com.subscope1.a", new String[][]{{"com.subscope1.b", "com.subscope1.a"}});
    checkResult(expected, cyclicDependencies);
  }

  @Test
  public void testT3() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    //com.b<->com.d
    //com.b->com.a->com.c->com.b
    final CyclicDependenciesBuilder builder = new CyclicDependenciesBuilder(myFixture.getProject(),
                                                                            new AnalysisScope(myFixture.getProject()));
    builder.analyze();
    final HashMap<PsiPackage, Set<List<PsiPackage>>> cyclicDependencies = builder.getCyclicDependencies();
    HashMap<String, String[][]> expected = new HashMap<>();
    expected.put("com.b", new String[][]{{"com.c", "com.a", "com.b"}, {"com.d", "com.b"}});
    expected.put("com.d", new String[][]{{"com.b", "com.d"}});
    expected.put("com.c", new String[][]{{"com.a", "com.b", "com.c"}});
    expected.put("com.a", new String[][]{{"com.b", "com.c", "com.a"}});
    checkResult(expected, cyclicDependencies, true);
  }

  @Test
  public void testT4() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    //com.a<->com.b
    //com.a->com.c->com.d->com.a
    final CyclicDependenciesBuilder builder = new CyclicDependenciesBuilder(myFixture.getProject(),
                                                                            new AnalysisScope(myFixture.getProject()));
    builder.analyze();
    final HashMap<PsiPackage, Set<List<PsiPackage>>> cyclicDependencies = builder.getCyclicDependencies();
    HashMap<String, String[][]> expected = new HashMap<>();
    expected.put("com.b", new String[][]{{"com.a", "com.b" }});
    expected.put("com.d", new String[][]{{"com.c", "com.a", "com.d"}});
    expected.put("com.c", new String[][]{{"com.a", "com.d", "com.c"}});
    expected.put("com.a", new String[][]{{"com.d", "com.c","com.a"}, {"com.b", "com.a"}});
    checkResult(expected, cyclicDependencies, true);
  }

  @Test
  public void testT5() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    //com.b<->com.d
    //com.b->com.a->com.c->com.b
    final CyclicDependenciesBuilder builder = new CyclicDependenciesBuilder(myFixture.getProject(),
                                                                            new AnalysisScope(myFixture.getProject()));
    builder.analyze();
    final HashMap<PsiPackage, Set<List<PsiPackage>>> cyclicDependencies = builder.getCyclicDependencies();
    HashMap<String, String[][]> expected = new HashMap<>();
    expected.put("com.b", new String[][]{{"com.d", "com.b"}, {"com.c", "com.a", "com.b"}});
    expected.put("com.d", new String[][]{{"com.b", "com.d"}});
    expected.put("com.c", new String[][]{{"com.a", "com.b", "com.c"}});
    expected.put("com.a", new String[][]{{"com.b", "com.c", "com.a"}});
    checkResult(expected, cyclicDependencies, true);
  }

  @Test
  public void testT6() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    //A->B1
    //B2->C
    //C->A
    final CyclicDependenciesBuilder builder = new CyclicDependenciesBuilder(myFixture.getProject(),
                                                                            new AnalysisScope(myFixture.getProject()));
    builder.analyze();
    final HashMap<PsiPackage, Set<List<PsiPackage>>> cyclicDependencies = builder.getCyclicDependencies();
    HashMap<String, String[][]> expected = new HashMap<>();
    expected.put("com.b", new String[][]{{"com.a", "com.c", "com.b"}});
    expected.put("com.c", new String[][]{{"com.b", "com.a", "com.c"}});
    expected.put("com.a", new String[][]{{"com.c", "com.b", "com.a"}});
    checkResult(expected, cyclicDependencies, true);
  }

  @Test
  public void testNoCycle(){
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    //B->A
    final CyclicDependenciesBuilder builder = new CyclicDependenciesBuilder(myFixture.getProject(),
                                                                            new AnalysisScope(myFixture.getProject()));
    builder.analyze();
    final HashMap<PsiPackage, Set<List<PsiPackage>>> cyclicDependencies = builder.getCyclicDependencies();
    HashMap<String, String[][]> expected = new HashMap<>();
    expected.put("com.b", new String[0][0]);
    expected.put("com.a", new String[0][0]);
    checkResult(expected, cyclicDependencies, true);
  }

  private static void checkResult(HashMap<String, String[][]> expected, HashMap<PsiPackage, Set<List<PsiPackage>>> cycles) {
    assertEquals(expected.size(), cycles.size());
    Iterator<PsiPackage> it = cycles.keySet().iterator();
    for (final String packs : expected.keySet()) {
      final PsiPackage psiPackage = it.next();
      assertEquals(packs, psiPackage.getQualifiedName());
      assertEquals(expected.get(packs).length, cycles.get(psiPackage).size());
      Iterator<List<PsiPackage>> iC = cycles.get(psiPackage).iterator();
      for (int i = 0; i < expected.get(packs).length; i++) {
        final String[] expectedCycle = expected.get(packs)[i];
        final List<PsiPackage> cycle = iC.next();
        assertEquals(expectedCycle.length, cycle.size());
        Iterator<PsiPackage> iCycle = cycle.iterator();
        for (final String expectedInCycle : expectedCycle) {
          final PsiPackage packageInCycle = iCycle.next();
          assertEquals(expectedInCycle, packageInCycle.getQualifiedName());
        }
      }
    }
  }

  private static void checkResult(HashMap<String, String[][]> expected, HashMap<PsiPackage, Set<List<PsiPackage>>> cycles, boolean forceContains){
    assertEquals(expected.size(), cycles.size());
    for (final PsiPackage psiPackage : cycles.keySet()) {
      assertTrue(expected.containsKey(psiPackage.getQualifiedName()));
      final String packs = psiPackage.getQualifiedName();
      if (forceContains) {
        assertEquals(expected.get(packs).length, cycles.get(psiPackage).size());
      }
      for (final List<PsiPackage> cycle : cycles.get(psiPackage)) {
        final String[][] expectedCycles = expected.get(packs);
        final String[] string = new String[cycle.size()];
        int i = 0;
        for (final PsiPackage packageInCycle : cycle) {
          string[i++] = packageInCycle.getQualifiedName();
        }
        assertTrue(findInMatrix(expectedCycles, string) > -1);
      }
    }
  }

  private static int findInMatrix(String [][] matrix, String [] string){
    for (int i = 0; i < matrix.length; i++) {
      String[] strings = matrix[i];
      if (Arrays.equals(strings, string)){
        return i;
      }
    }
    return -1;
  }
}
