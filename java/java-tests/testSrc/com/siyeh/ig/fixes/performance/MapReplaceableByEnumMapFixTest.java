// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.performance;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.performance.MapReplaceableByEnumMapInspection;

public class MapReplaceableByEnumMapFixTest extends IGQuickFixesTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new MapReplaceableByEnumMapInspection());
    myRelativePath = "performance/map_replaceable_with_enum_map";
    myDefaultHint = CommonQuickFixBundle.message("fix.replace.with.x", "EnumMap");
    myFixture.addClass("""
                         package java.util;

                         public class EnumMap<K extends Enum<K>, V> extends AbstractMap<K, V>
                           implements java.io.Serializable, Cloneable
                         {
                           public EnumMap(Class<K> keyType) {
                            \s
                           }
                         }""");

  }


  public void testSimple() {
    doTest();
  }
  public void testParentheses() {
    doTest();
  }


}