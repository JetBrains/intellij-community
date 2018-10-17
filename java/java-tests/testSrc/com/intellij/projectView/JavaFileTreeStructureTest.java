// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.projectView;

import com.intellij.ide.structureView.impl.java.FieldsFilter;
import com.intellij.ide.structureView.impl.java.PropertiesGrouper;
import com.intellij.ide.structureView.impl.java.PublicElementsFilter;
import com.intellij.ide.structureView.impl.java.SuperTypesGrouper;
import com.intellij.ide.util.InheritedMembersNodeProvider;
import com.intellij.ide.util.treeView.smartTree.Sorter;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.ui.tree.TreeUtil;

import javax.swing.*;

public class JavaFileTreeStructureTest extends JavaFileStructureTestCaseBase {
  public void testHidingFieldsAndMethods() {
    myFixture.configureByText("Derived.java", DERIVED);

    myFixture.testStructureView(svc -> {
      svc.setActionActive(FieldsFilter.ID, true);
      svc.setActionActive(Sorter.ALPHA_SORTER_ID, true);

      svc.setActionActive(InheritedMembersNodeProvider.ID, false);

      JTree tree = svc.getTree();
      PlatformTestUtil.expandAll(tree);
      PlatformTestUtil.assertTreeEqual(tree,
                                       "-Derived.java\n" +
                                       " -Derived\n" +
                                       "  Inner\n" +
                                       "  f(): void\n" +
                                       "  g(): void\n" +
                                       "  getI(): int\n" +
                                       "  getX(): int\n" +
                                       "  getY(): int\n" +
                                       "  setI(int): void\n" +
                                       "  setX(int): void\n" +
                                       "  setY(int): void");
    });
  }

  public void testGrouping() {
    init();

    myFixture.testStructureView(svc -> {
      svc.setActionActive(InheritedMembersNodeProvider.ID, true);
      svc.setActionActive(PropertiesGrouper.ID, true);
      svc.setActionActive(SuperTypesGrouper.ID, true);
      svc.setActionActive(Sorter.ALPHA_SORTER_ID, true);

      JTree tree = svc.getTree();
      PlatformTestUtil.waitForPromise(TreeUtil.promiseExpand(tree, 2));
      PlatformTestUtil.assertTreeEqual(tree,
                                       "-Derived.java\n" +
                                       " -Derived\n" +
                                       "  +Inner\n" +
                                       "  +Base\n" +
                                       "  +Base\n" +
                                       "  +Interface\n" +
                                       "  +Object\n" +
                                       "  +y: int\n" +
                                       "  i: int\n" +
                                       "  x: int");
    });
  }

  public void testInheritedMembers() {
    init();

    myFixture.testStructureView(svc -> {
      svc.setActionActive(InheritedMembersNodeProvider.ID, true);
      svc.setActionActive(PropertiesGrouper.ID, false);
      svc.setActionActive(SuperTypesGrouper.ID, true);
      svc.setActionActive(Sorter.ALPHA_SORTER_ID, true);

      JTree tree = svc.getTree();
      PlatformTestUtil.expandAll(tree);
      PlatformTestUtil.assertTreeEqual(tree,
                                       "-Derived.java\n" +
                                       " -Derived\n" +
                                       "  -Inner\n" +
                                       "   -Object\n" +
                                       "    clone(): Object\n" +
                                       "    equals(Object): boolean\n" +
                                       "    finalize(): void\n" +
                                       "    getClass(): Class<?>\n" +
                                       "    hashCode(): int\n" +
                                       "    notify(): void\n" +
                                       "    notifyAll(): void\n" +
                                       "    toString(): String\n" +
                                       "    wait(): void\n" +
                                       "    wait(long): void\n" +
                                       "    wait(long, int): void\n" +
                                       "  -Base\n" +
                                       "   f(): void\n" +
                                       "   getX(): int\n" +
                                       "   setX(int): void\n" +
                                       "  -Base\n" +
                                       "   getZ(): int\n" +
                                       "   setZ(int): void\n" +
                                       "   toString(): String\n" +
                                       "  -Interface\n" +
                                       "   g(): void\n" +
                                       "   getI(): int\n" +
                                       "   setI(int): void\n" +
                                       "  -Object\n" +
                                       "   clone(): Object\n" +
                                       "   equals(Object): boolean\n" +
                                       "   finalize(): void\n" +
                                       "   getClass(): Class<?>\n" +
                                       "   hashCode(): int\n" +
                                       "   notify(): void\n" +
                                       "   notifyAll(): void\n" +
                                       "   wait(): void\n" +
                                       "   wait(long): void\n" +
                                       "   wait(long, int): void\n" +
                                       "  getY(): int\n" +
                                       "  setY(int): void\n" +
                                       "  i: int\n" +
                                       "  x: int");
    });
  }

  public void testHideNotPublic() {
    init();

    myFixture.testStructureView(svc -> {
      svc.setActionActive(InheritedMembersNodeProvider.ID, true);
      svc.setActionActive(PropertiesGrouper.ID, false);
      svc.setActionActive(SuperTypesGrouper.ID, true);
      svc.setActionActive(Sorter.ALPHA_SORTER_ID, true);
      svc.setActionActive(PublicElementsFilter.ID, true);

      JTree tree = svc.getTree();
      PlatformTestUtil.expandAll(tree);
      PlatformTestUtil.assertTreeEqual(tree,
                                       "-Derived.java\n" +
                                       " -Derived\n" +
                                       "  -Inner\n" +
                                       "   -Object\n" +
                                       "    equals(Object): boolean\n" +
                                       "    getClass(): Class<?>\n" +
                                       "    hashCode(): int\n" +
                                       "    notify(): void\n" +
                                       "    notifyAll(): void\n" +
                                       "    toString(): String\n" +
                                       "    wait(): void\n" +
                                       "    wait(long): void\n" +
                                       "    wait(long, int): void\n" +
                                       "  -Base\n" +
                                       "   f(): void\n" +
                                       "   getX(): int\n" +
                                       "   setX(int): void\n" +
                                       "  -Base\n" +
                                       "   toString(): String\n" +
                                       "  -Interface\n" +
                                       "   g(): void\n" +
                                       "   getI(): int\n" +
                                       "   setI(int): void\n" +
                                       "  -Object\n" +
                                       "   equals(Object): boolean\n" +
                                       "   getClass(): Class<?>\n" +
                                       "   hashCode(): int\n" +
                                       "   notify(): void\n" +
                                       "   notifyAll(): void\n" +
                                       "   wait(): void\n" +
                                       "   wait(long): void\n" +
                                       "   wait(long, int): void\n" +
                                       "  getY(): int\n" +
                                       "  setY(int): void");
    });
  }
}