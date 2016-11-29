/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.dependencies;

import com.intellij.JavaTestUtil;
import com.intellij.analysis.AnalysisScope;
import com.intellij.analysis.JavaAnalysisScope;
import com.intellij.cyclicDependencies.CyclicDependenciesBuilder;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiPackage;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PsiTestCase;
import com.intellij.testFramework.PsiTestUtil;

import java.util.*;

/**
 * User: anna
 * Date: Feb 2, 2005
 */
public class CyclicDependenciesTest extends PsiTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();

    String root = JavaTestUtil.getJavaTestDataPath() + "/dependencies/cycle/" + getTestName(true);
    PsiTestUtil.removeAllRoots(myModule, IdeaTestUtil.getMockJdk17());
    PsiTestUtil.createTestProjectStructure(myProject, myModule, root, myFilesToDelete);
  }

  public void testT1() {
    // com.a<->com.b
    final CyclicDependenciesBuilder builder = new CyclicDependenciesBuilder(myProject,
                                                                            new AnalysisScope(myProject));
    builder.analyze();
    final HashMap<PsiPackage, Set<List<PsiPackage>>> cyclicDependencies = builder.getCyclicDependencies();
    HashMap<String, String[][]> expected = new HashMap<>();
    expected.put("com.b", new String[][]{{"com.a", "com.b"}});
    expected.put("com.a", new String[][]{{"com.b", "com.a"}});
    checkResult(expected, cyclicDependencies);
  }

  public void testPackageScope1(){
    // com.a<->com.b
    final CyclicDependenciesBuilder builder = new CyclicDependenciesBuilder(myProject,
                                                                            new JavaAnalysisScope(JavaPsiFacade
                                                                              .getInstance(myPsiManager.getProject()).findPackage("com"), null));
    builder.analyze();
    final HashMap<PsiPackage, Set<List<PsiPackage>>> cyclicDependencies = builder.getCyclicDependencies();
    HashMap<String, String[][]> expected = new HashMap<>();
    expected.put("com.b", new String[][]{{"com.a", "com.b"}});
    expected.put("com.a", new String[][]{{"com.b", "com.a"}});
    checkResult(expected, cyclicDependencies);
  }

  public void testT2() {
    //com.b<->com.a
    //com.c<->com.d
    final CyclicDependenciesBuilder builder = new CyclicDependenciesBuilder(myProject,
                                                                            new AnalysisScope(myProject));
    builder.analyze();
    final HashMap<PsiPackage, Set<List<PsiPackage>>> cyclicDependencies = builder.getCyclicDependencies();
    HashMap<String, String[][]> expected = new HashMap<>();
    expected.put("com.b", new String[][]{{"com.a", "com.b"}});
    expected.put("com.d", new String[][]{{"com.c", "com.d"}});
    expected.put("com.c", new String[][]{{"com.d", "com.c"}});
    expected.put("com.a", new String[][]{{"com.b", "com.a"}});
    checkResult(expected, cyclicDependencies);
  }

  public void testPackageScope2() {
    //com.b<->com.a  - find
    //com.c<->com.d  - not in scope
    final CyclicDependenciesBuilder builder = new CyclicDependenciesBuilder(myProject,
                                                                            new JavaAnalysisScope(JavaPsiFacade
                                                                              .getInstance(myPsiManager.getProject()).findPackage(
                                                                              "com.subscope1"), null));
    builder.analyze();
    final HashMap<PsiPackage, Set<List<PsiPackage>>> cyclicDependencies = builder.getCyclicDependencies();
    HashMap<String, String[][]> expected = new HashMap<>();
    expected.put("com.subscope1.b", new String[][]{{"com.subscope1.a", "com.subscope1.b"}});
    expected.put("com.subscope1.a", new String[][]{{"com.subscope1.b", "com.subscope1.a"}});
    checkResult(expected, cyclicDependencies);
  }

  public void testT3() {
    //com.b<->com.d
    //com.b->com.a->com.c->com.b
    final CyclicDependenciesBuilder builder = new CyclicDependenciesBuilder(myProject,
                                                                            new AnalysisScope(myProject));
    builder.analyze();
    final HashMap<PsiPackage, Set<List<PsiPackage>>> cyclicDependencies = builder.getCyclicDependencies();
    HashMap<String, String[][]> expected = new HashMap<>();
    expected.put("com.b", new String[][]{{"com.c", "com.a", "com.b"}, {"com.d", "com.b"}});
    expected.put("com.d", new String[][]{{"com.b", "com.d"}});
    expected.put("com.c", new String[][]{{"com.a", "com.b", "com.c"}});
    expected.put("com.a", new String[][]{{"com.b", "com.c", "com.a"}});
    checkResult(expected, cyclicDependencies, true);
  }

  public void testT4() {
    //com.a<->com.b
    //com.a->com.c->com.d->com.a
    final CyclicDependenciesBuilder builder = new CyclicDependenciesBuilder(myProject,
                                                                            new AnalysisScope(myProject));
    builder.analyze();
    final HashMap<PsiPackage, Set<List<PsiPackage>>> cyclicDependencies = builder.getCyclicDependencies();
    HashMap<String, String[][]> expected = new HashMap<>();
    expected.put("com.b", new String[][]{{"com.a", "com.b" }});
    expected.put("com.d", new String[][]{{"com.c", "com.a", "com.d"}});
    expected.put("com.c", new String[][]{{"com.a", "com.d", "com.c"}});
    expected.put("com.a", new String[][]{{"com.d", "com.c","com.a"}, {"com.b", "com.a"}});
    checkResult(expected, cyclicDependencies, true);
  }

  public void testT5() {
    //com.b<->com.d
    //com.b->com.a->com.c->com.b
    final CyclicDependenciesBuilder builder = new CyclicDependenciesBuilder(myProject,
                                                                            new AnalysisScope(myProject));
    builder.analyze();
    final HashMap<PsiPackage, Set<List<PsiPackage>>> cyclicDependencies = builder.getCyclicDependencies();
    HashMap<String, String[][]> expected = new HashMap<>();
    expected.put("com.b", new String[][]{{"com.d", "com.b"}, {"com.c", "com.a", "com.b"}});
    expected.put("com.d", new String[][]{{"com.b", "com.d"}});
    expected.put("com.c", new String[][]{{"com.a", "com.b", "com.c"}});
    expected.put("com.a", new String[][]{{"com.b", "com.c", "com.a"}});
    checkResult(expected, cyclicDependencies, true);
  }

  public void testT6() {
    //A->B1
    //B2->C
    //C->A
    final CyclicDependenciesBuilder builder = new CyclicDependenciesBuilder(myProject,
                                                                            new AnalysisScope(myProject));
    builder.analyze();
    final HashMap<PsiPackage, Set<List<PsiPackage>>> cyclicDependencies = builder.getCyclicDependencies();
    HashMap<String, String[][]> expected = new HashMap<>();
    expected.put("com.b", new String[][]{{"com.a", "com.c", "com.b"}});
    expected.put("com.c", new String[][]{{"com.b", "com.a", "com.c"}});
    expected.put("com.a", new String[][]{{"com.c", "com.b", "com.a"}});
    checkResult(expected, cyclicDependencies, true);
  }

  public void testNoCycle(){
    //B->A
    final CyclicDependenciesBuilder builder = new CyclicDependenciesBuilder(myProject,
                                                                            new AnalysisScope(myProject));
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
