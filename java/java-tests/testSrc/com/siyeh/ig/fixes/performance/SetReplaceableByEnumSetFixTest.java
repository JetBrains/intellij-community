// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.performance;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.IGQuickFixesFixture;
import com.siyeh.ig.performance.SetReplaceableByEnumSetInspection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

@TestApplication
public class SetReplaceableByEnumSetFixTest {
  private final TestFixture<IGQuickFixesFixture> ig =
    igQuickFixesFixture(() -> List.of(new SetReplaceableByEnumSetInspection()), "performance/set_replaceable_with_enum_set",
                        CommonQuickFixBundle.message("fix.replace.with.x", "EnumSet"));

  @BeforeEach
  void setUp() {
    JavaCodeInsightTestFixture myFixture = ig.get().getFixture();
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


  @Test
  public void testSimple() {
    ig.get().doTest();
  }
}