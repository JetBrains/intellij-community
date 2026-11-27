// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.performance;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.IGQuickFixesFixture;
import com.siyeh.ig.performance.MapReplaceableByEnumMapInspection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

@TestApplication
public class MapReplaceableByEnumMapFixTest {
  private final TestFixture<IGQuickFixesFixture> ig =
    igQuickFixesFixture(() -> List.of(new MapReplaceableByEnumMapInspection()), "performance/map_replaceable_with_enum_map",
                        CommonQuickFixBundle.message("fix.replace.with.x", "EnumMap"));

  @BeforeEach
  void setUp() {
    JavaCodeInsightTestFixture myFixture = ig.get().getFixture();
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


  @Test
  public void testSimple() {
    ig.get().doTest();
  }
  @Test
  public void testParentheses() {
    ig.get().doTest();
  }
}