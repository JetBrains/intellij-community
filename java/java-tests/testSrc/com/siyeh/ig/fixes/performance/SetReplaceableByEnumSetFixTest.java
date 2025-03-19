// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.performance;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.performance.SetReplaceableByEnumSetInspection;

public class SetReplaceableByEnumSetFixTest extends IGQuickFixesTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new SetReplaceableByEnumSetInspection());
    myRelativePath = "performance/set_replaceable_with_enum_set";
    myDefaultHint = CommonQuickFixBundle.message("fix.replace.with.x", "EnumSet");
    myFixture.addClass("""
                         package java.util;

                         public abstract class EnumSet<E extends Enum<E>> extends AbstractSet<E>
                           implements Cloneable, java.io.Serializable
                         {
                           EnumSet(Class<E>elementType, Enum<?>[] universe) {
                            \s
                           }
                         }""");
    myFixture.addClass("""
                         import java.util.AbstractSet;
                         import java.util.Set;

                         public class HashSet<E>
                           extends AbstractSet<E>
                           implements Set<E>, Cloneable, java.io.Serializable {
                           public HashSet() {
                           }
                         }""");

  }


  public void testSimple() {
    doTest();
  }


}