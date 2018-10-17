// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.projectView;

import com.intellij.ide.structureView.impl.java.PropertiesGrouper;
import com.intellij.ide.structureView.impl.java.SuperTypesGrouper;
import com.intellij.ide.structureView.impl.java.VisibilitySorter;
import com.intellij.ide.util.InheritedMembersNodeProvider;
import com.intellij.testFramework.PlatformTestUtil;

public class VisibilityComparatorTest extends JavaFileStructureTestCaseBase {
  @Override
  public void setUp() throws Exception {
    super.setUp();
    init();
  }

  public void testComparingEqualKnown() {
    myFixture.testStructureView(svc -> {
      svc.setActionActive(InheritedMembersNodeProvider.ID, true);
      PlatformTestUtil.assertTreeEqual(svc.getTree(),
                                       "-Derived.java\n" +
                                       " -Derived\n" +
                                       "  +Inner\n" +
                                       "  f(): void\n" +
                                       "  g(): void\n" +
                                       "  setX(int): void\n" +
                                       "  getX(): int\n" +
                                       "  setY(int): void\n" +
                                       "  getY(): int\n" +
                                       "  setI(int): void\n" +
                                       "  getI(): int\n" +
                                       "  toString(): String\n" +
                                       "  getZ(): int\n" +
                                       "  setZ(int): void\n" +
                                       "  getClass(): Class<?>\n" +
                                       "  hashCode(): int\n" +
                                       "  equals(Object): boolean\n" +
                                       "  clone(): Object\n" +
                                       "  notify(): void\n" +
                                       "  notifyAll(): void\n" +
                                       "  wait(long): void\n" +
                                       "  wait(long, int): void\n" +
                                       "  wait(): void\n" +
                                       "  finalize(): void\n" +
                                       "  x: int\n" +
                                       "  i: int");
      svc.setActionActive(VisibilitySorter.ID, true);
      PlatformTestUtil.assertTreeEqual(svc.getTree(),
                                       "-Derived.java\n" +
                                       " -Derived\n" +
                                       "  +Inner\n" +
                                       "  f(): void\n" +
                                       "  g(): void\n" +
                                       "  setX(int): void\n" +
                                       "  getX(): int\n" +
                                       "  setY(int): void\n" +
                                       "  getY(): int\n" +
                                       "  setI(int): void\n" +
                                       "  getI(): int\n" +
                                       "  toString(): String\n" +
                                       "  getClass(): Class<?>\n" +
                                       "  hashCode(): int\n" +
                                       "  equals(Object): boolean\n" +
                                       "  notify(): void\n" +
                                       "  notifyAll(): void\n" +
                                       "  wait(long): void\n" +
                                       "  wait(long, int): void\n" +
                                       "  wait(): void\n" +
                                       "  getZ(): int\n" +
                                       "  clone(): Object\n" +
                                       "  finalize(): void\n" +
                                       "  setZ(int): void\n" +
                                       "  x: int\n" +
                                       "  i: int");
    });
  }

  public void testCompareInheritanceGroups() {
    myFixture.testStructureView(svc -> {
      svc.setActionActive(PropertiesGrouper.ID, true);
      svc.setActionActive(SuperTypesGrouper.ID, true);
      svc.setActionActive(InheritedMembersNodeProvider.ID, true);

      PlatformTestUtil.assertTreeEqual(svc.getTree(),
                                       "-Derived.java\n" +
                                       " -Derived\n" +
                                       "  +Inner\n" +
                                       "  +Base\n" +
                                       "  +Interface\n" +
                                       "  +Base\n" +
                                       "  +Object\n" +
                                       "  +y: int\n" +
                                       "  x: int\n" +
                                       "  i: int");

      svc.setActionActive(VisibilitySorter.ID, true);
      PlatformTestUtil.assertTreeEqual(svc.getTree(),
                                       "-Derived.java\n" +
                                       " -Derived\n" +
                                       "  +Inner\n" +
                                       "  +Base\n" +
                                       "  +Base\n" +
                                       "  +Object\n" +
                                       "  +Interface\n" +
                                       "  +y: int\n" +
                                       "  x: int\n" +
                                       "  i: int"
                                       );
    });
  }
}
