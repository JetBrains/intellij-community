// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.dependencies;

import com.intellij.JavaTestUtil;
import com.intellij.analysis.AnalysisScope;
import com.intellij.analysis.JavaAnalysisScope;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.packageDependencies.BackwardDependenciesBuilder;
import com.intellij.packageDependencies.DependenciesBuilder;
import com.intellij.packageDependencies.FindDependencyUtil;
import com.intellij.packageDependencies.ForwardDependenciesBuilder;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPackage;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.TextChunk;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageInfo2UsageAdapter;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public class UsagesInAnalyzingDependenciesTest extends LightCodeInsightFixtureTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/dependencies/search/";
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.copyDirectoryToProject(getTestName(true), "");
  }

  public void testForwardPackageScope() {
    PsiPackage bPackage = JavaPsiFacade.getInstance(getProject()).findPackage("com.b");
    DependenciesBuilder builder = new ForwardDependenciesBuilder(getProject(), new JavaAnalysisScope(bPackage, null));
    builder.analyze();
    Set<PsiFile> searchFor = new HashSet<>();
    searchFor.add(myFixture.findClass("com.a.A").getContainingFile());
    Set<PsiFile> searchIn = new HashSet<>();
    PsiClass bClass = myFixture.findClass("com.b.B");
    searchIn.add(bClass.getContainingFile());
    PsiClass cClass = myFixture.findClass("com.b.C");
    searchIn.add(cClass.getContainingFile());
    UsageInfo[] usagesInfos = FindDependencyUtil.findDependencies(builder, searchIn, searchFor);
    UsageInfo2UsageAdapter[] usages = UsageInfo2UsageAdapter.convert(usagesInfos);
    String[] psiUsages = new String[usagesInfos.length];
    for (int i = 0; i < usagesInfos.length; i++) {
      psiUsages[i] = toString(usages[i]);
    }
    checkResult(new String[]{
      "2 import com.a.A;",
      "4 A myA = new A();",
      "4 A myA = new A();",
      "6 myA.aa();",

      "2 import com.a.A;",
      "4 A myA = new A();",
      "4 A myA = new A();",
      "6 myA.aa();"}, psiUsages);
  }

  @NotNull
  private static String toString(@NotNull Usage usage) {
    JBIterable<TextChunk> it = JBIterable.of(usage.getPresentation().getText());
    TextChunk first = it.first();
    assert first != null;
    JBIterable<TextChunk> rest = it.skip(1);
    return first.toString() + " " + StringUtil.join(rest, Object::toString, "");
  }

  public void testBackwardPackageScope() {
    PsiPackage bPackage = JavaPsiFacade.getInstance(getProject()).findPackage("com.a");
    DependenciesBuilder builder = new BackwardDependenciesBuilder(getProject(), new JavaAnalysisScope(bPackage, null));
    builder.analyze();
    Set<PsiFile> searchFor = new HashSet<>();
    searchFor.add(myFixture.findClass("com.a.A").getContainingFile());
    Set<PsiFile> searchIn = new HashSet<>();
    PsiClass bClass = myFixture.findClass("com.b.B");
    searchIn.add(bClass.getContainingFile());
    PsiClass cClass = myFixture.findClass("com.a.C");
    searchFor.add(cClass.getContainingFile());
    UsageInfo[] usagesInfos = FindDependencyUtil.findBackwardDependencies(builder, searchIn, searchFor);
    UsageInfo2UsageAdapter[] usages = UsageInfo2UsageAdapter.convert(usagesInfos);
    String[] psiUsages = new String[usagesInfos.length];
    for (int i = 0; i < usagesInfos.length; i++) {
      psiUsages[i] = toString(usages[i]);
    }
    checkResult(new String[]{
      "4 A myA = new A();",
      "4 A myA = new A();",
      "5 C myC = new C();",
      "5 C myC = new C();",
      "7 myA.aa();",
      "8 myC.cc();"}, psiUsages);
  }

  public void testForwardSimple() {
    DependenciesBuilder builder = new ForwardDependenciesBuilder(getProject(), new AnalysisScope(getProject()));
    builder.analyze();

    Set<PsiFile> searchIn = new HashSet<>();
    PsiClass aClass = myFixture.findClass("A");
    searchIn.add(aClass.getContainingFile());
    Set<PsiFile> searchFor = new HashSet<>();
    PsiClass bClass = myFixture.findClass("B");
    searchFor.add(bClass.getContainingFile());

    UsageInfo[] usagesInfos = FindDependencyUtil.findDependencies(builder, searchIn, searchFor);
    UsageInfo2UsageAdapter[] usages = UsageInfo2UsageAdapter.convert(usagesInfos);
    String[] psiUsages = new String[usagesInfos.length];
    for (int i = 0; i < usagesInfos.length; i++) {
      psiUsages[i] = toString(usages[i]);
    }
    checkResult(new String[]{
      "2 B myB = new B();",
      "2 B myB = new B();",
      "4 myB.bb();"}, psiUsages);
  }

  public void testForwardJdkClasses() {
    DependenciesBuilder builder = new ForwardDependenciesBuilder(getProject(), new AnalysisScope(getProject()));
    builder.analyze();

    Set<PsiFile> searchIn = new HashSet<>();
    PsiClass aClass = myFixture.findClass("A");
    searchIn.add(aClass.getContainingFile());

    Set<PsiFile> searchFor = new HashSet<>();
    PsiClass stringClass = myFixture.findClass("java.lang.String");
    searchFor.add((PsiFile)stringClass.getContainingFile().getNavigationElement());

    UsageInfo[] usagesInfos = FindDependencyUtil.findDependencies(builder, searchIn, searchFor);
    UsageInfo2UsageAdapter[] usages = UsageInfo2UsageAdapter.convert(usagesInfos);
    String[] psiUsages = new String[usagesInfos.length];
    for (int i = 0; i < usagesInfos.length; i++) {
      psiUsages[i] = toString(usages[i]);
    }
    checkResult(new String[]{"2 String myName;"}, psiUsages);
  }

  private static void checkResult(@NotNull String[] usages, @NotNull String[] psiUsages) {
    assertEquals(usages.length, psiUsages.length);
    for (int i = 0; i < psiUsages.length; i++) {
      assertEquals(usages[i], psiUsages[i]);
    }
  }
}
