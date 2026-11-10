// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.usages;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.UsageViewSettings;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class JavaUsageViewTreeTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    UsageViewSettings oldSettingsState = new UsageViewSettings();
    UsageViewSettings settings = UsageViewSettings.getInstance();
    XmlSerializerUtil.copyBean(settings.getState(), oldSettingsState);
    Disposer.register(myFixture.getTestRootDisposable(), () -> settings.loadState(oldSettingsState));

    settings.setGroupByModule(false);
    settings.setGroupByFileStructure(true);
    settings.setGroupByUsageType(false);
    settings.setGroupByPackage(false);
  }

  @Test
  public void testSimpleModule() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass aClass = myFixture.addClass("""
                                           class A {  void foo(){}
                                             void bar()
                                            {    foo();
                                             } }
                                           """);

    UsageInfo usage = ReadAction.computeBlocking(() -> {
      PsiMethod[] foos = aClass.findMethodsByName("foo", false);
      assertEquals(1, foos.length);
      PsiMethod foo = foos[0];
      PsiReference ref = ReferencesSearch.search(foo).findFirst();
      assertNotNull(ref);
      return new UsageInfo(ref);
    });
    assertEquals("""
                   <root> (1)
                    Usages (1)
                     A (1)
                      bar() (1)
                       3{    foo();
                   """, EdtTestUtil.runInEdtAndGet(() -> myFixture.getUsageViewTreeTextRepresentation(Collections.singleton(usage))));
  }
}
