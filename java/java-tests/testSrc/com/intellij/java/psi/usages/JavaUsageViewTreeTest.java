// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.psi.usages;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.UsageViewSettings;
import com.intellij.util.xmlb.XmlSerializerUtil;

import java.util.Collections;

public class JavaUsageViewTreeTest extends LightJavaCodeInsightFixtureTestCase {

  @Override
  public void setUp() throws Exception {
    super.setUp();
    UsageViewSettings oldSettingsState = new UsageViewSettings();
    UsageViewSettings settings = UsageViewSettings.getInstance();
    XmlSerializerUtil.copyBean(settings.getState(), oldSettingsState);
    disposeOnTearDown(() -> settings.loadState(oldSettingsState));

    settings.setGroupByModule(false);
    settings.setGroupByFileStructure(true);
    settings.setGroupByUsageType(false);
    settings.setGroupByPackage(false);
  }

  public void testSimpleModule() {
    PsiClass aClass = myFixture.addClass("""
                                           class A {  void foo(){}
                                             void bar()
                                            {    foo();
                                             } }
                                           """);

    PsiMethod[] foos = aClass.findMethodsByName("foo", false);
    assertEquals(1, foos.length);
    PsiMethod foo = foos[0];
    PsiReference ref = ReferencesSearch.search(foo).findFirst();
    assertNotNull(ref);
    assertEquals("""
                   <root> (1)
                    Usages in (1)
                     A (1)
                      bar() (1)
                       3{    foo();
                   """, myFixture.getUsageViewTreeTextRepresentation(Collections.singleton(new UsageInfo(ref))));
  }
}
