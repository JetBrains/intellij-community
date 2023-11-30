// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;

public class UseOfPropertiesAsHashtableInspectionTest extends LightJavaInspectionTestCase {

  public void testProperties() {
    doMemberTest("""
                   public void testThis(java.util.Properties p, java.util.Properties p2, java.util.Map m, java.util.Map<String, String> m2) {
                     p./*Call to 'Hashtable.get()' on properties object*/get/**/("foo");
                     p.getProperty("foo");
                     p./*Call to 'Hashtable.put()' on properties object*/put/**/("foo", "bar");
                     p.setProperty("foo", "bar");
                     p./*Call to 'Hashtable.putAll()' on properties object*/putAll/**/(m);
                     p.putAll(m2);
                     p.putAll(p2);
                   }
                   """);
  }

  public void testUseOfPropertiesAsHashtable() {
    doTest();
    checkQuickFixAll();
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_20;
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return new UseOfPropertiesAsHashtableInspection();
  }
}
