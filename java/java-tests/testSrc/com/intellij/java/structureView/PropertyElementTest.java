// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.structureView;

import com.intellij.ide.structureView.impl.java.FieldsFilter;
import com.intellij.ide.structureView.impl.java.PropertiesGrouper;
import com.intellij.ide.structureView.impl.java.PropertyGroup;
import com.intellij.ide.structureView.impl.java.PublicElementsFilter;
import com.intellij.ide.structureView.newStructureView.StructureViewComponent;
import com.intellij.ide.util.InheritedMembersNodeProvider;
import com.intellij.ide.util.treeView.smartTree.GroupWrapper;
import com.intellij.ide.util.treeView.smartTree.Sorter;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.TreeModel;
import java.util.List;

import static com.intellij.ide.structureView.impl.java.PropertyGroup.*;
import static com.intellij.psi.util.PsiUtil.ACCESS_LEVEL_PROTECTED;
import static com.intellij.psi.util.PsiUtil.ACCESS_LEVEL_PUBLIC;
import static org.junit.Assert.assertArrayEquals;

public class PropertyElementTest extends LightJavaStructureViewTestCaseBase {
  @Language("JAVA")
  public static final String INTERFACE = "interface Interface {" + "\n" +
                                         " void setII(int ii);" + "\n" +
                                         " int getII();" + "\n" +
                                         " void setI(int i);" + "\n" +
                                         " int getI();" + "\n" +
                                         "}";

  @Language("JAVA")
  public static final String BASE = "class Base {" + "\n" +
                                    " public void setO(int o) {}" + "\n" +
                                    " public int getO() {return 0;}" + "\n" +
                                    " public void setB(int b) {}" + "\n" +
                                    " public int getB() {return 0;}" + "\n" +
                                    "}";

  @Language("JAVA")
  public static final String SAMPLE = "abstract class Sample extends Base implements Interface {" + "\n" +
                                      " public void setII(int ii) {}" + "\n" +
                                      " public int getII() {return 0;}" + "\n" +
                                      " public void setO(int o) {}" + "\n" +
                                      " public int getO() {return 0;}" + "\n" +
                                      " protected void setRW(int x) {}" + "\n" +
                                      " public int getRW() {return 0;}" + "\n" +
                                      " public void setW(int w) {}" + "\n" +
                                      " public int getR() {return 0;}" + "\n" +
                                      " protected static void setSRW(int i) {}" + "\n" +
                                      " private static int getSRW() {return 0;}" + "\n" +
                                      " public static void setSW(int sw) {}" + "\n" +
                                      " public static int getSR() {return 0;}" + "\n" +
                                      " private int RW;" + "\n" +
                                      " private static int SRW;" + "\n" +
                                      "}";
  @Language("JAVA")
  public static final String NOT_STATIC_GETTERS = "class A {" +
                                                  " private static int aaa;" +
                                                  " public int getAaa() {return aaa;}" +
                                                  " public void setAaa(int v) { aaa = v; }" +
                                                  "}";

  private static List<PropertyGroup> getFeatures(@NotNull StructureViewComponent svc) {
    TreeModel model = svc.getTree().getModel();
    Object first = TreeUtil.getFirstNodePath(svc.getTree()).getLastPathComponent();
    return TreeUtil.nodeChildren(first, model).map(TreeUtil::getUserObject)
      .filter(GroupWrapper.class).map(o -> o.getValue()).filter(PropertyGroup.class)
      .toList();
  }


  public void testPropertyElement() {
    init();

    myFixture.testStructureView(component -> {
      component.setActionActive(PropertiesGrouper.ID, true);
      component.setActionActive(Sorter.ALPHA_SORTER_ID, true);

      PlatformTestUtil.assertTreeEqual(component.getTree(),
                                       "-Sample.java\n" +
                                       " -Sample\n" +
                                       "  +II: int\n" +
                                       "  +o: int\n" +
                                       "  +r: int\n" +
                                       "  +RW: int\n" +
                                       "  +SR: int\n" +
                                       "  +SRW: int\n" +
                                       "  +SW: int\n" +
                                       "  +w: int");

      List<PropertyGroup> properties = getFeatures(component);

      assertArrayEquals(properties.stream().map(o -> o.getPresentation().getIcon(true)).toArray(),
                        new Object[]{
                          PROPERTY_READ_WRITE_ICON
                          , PROPERTY_READ_WRITE_ICON
                          , PROPERTY_READ_ICON
                          , PROPERTY_READ_WRITE_ICON
                          , PROPERTY_READ_STATIC_ICON
                          , PROPERTY_READ_WRITE_STATIC_ICON
                          , PROPERTY_WRITE_STATIC_ICON
                          , PROPERTY_WRITE_ICON
                        });

      assertArrayEquals(properties.stream().map(o -> o.getAccessLevel()).toArray(),
                        new Object[]{
                          ACCESS_LEVEL_PUBLIC
                          , ACCESS_LEVEL_PUBLIC
                          , ACCESS_LEVEL_PUBLIC
                          , ACCESS_LEVEL_PUBLIC
                          , ACCESS_LEVEL_PUBLIC
                          , ACCESS_LEVEL_PROTECTED
                          , ACCESS_LEVEL_PUBLIC
                          , ACCESS_LEVEL_PUBLIC
                        });
    });
  }

  public void testStaticProperties() {
    init();

    myFixture.testStructureView(component -> {
      component.setActionActive(PropertiesGrouper.ID, true);
      component.setActionActive(Sorter.ALPHA_SORTER_ID, true);
      JTree tree = component.getTree();
      PlatformTestUtil.expandAll(tree);
      PlatformTestUtil.assertTreeEqual(tree,
                                       "-Sample.java\n" +
                                       " -Sample\n" +
                                       "  -II: int\n" +
                                       "   getII(): int\n" +
                                       "   setII(int): void\n" +
                                       "  -o: int\n" +
                                       "   getO(): int\n" +
                                       "   setO(int): void\n" +
                                       "  -r: int\n" +
                                       "   getR(): int\n" +
                                       "  -RW: int\n" +
                                       "   getRW(): int\n" +
                                       "   setRW(int): void\n" +
                                       "   RW: int\n" +
                                       "  -SR: int\n" +
                                       "   getSR(): int\n" +
                                       "  -SRW: int\n" +
                                       "   getSRW(): int\n" +
                                       "   setSRW(int): void\n" +
                                       "   SRW: int\n" +
                                       "  -SW: int\n" +
                                       "   setSW(int): void\n" +
                                       "  -w: int\n" +
                                       "   setW(int): void");
    });
  }

  public void test14827() {
    myFixture.configureByText("Derived.java", "class Base {int x;}\n" +
                                              "class Derived extends Base {int getX() {return 0;}}");
    myFixture.testStructureView(component -> {
      component.setActionActive(FieldsFilter.ID, false);
      component.setActionActive(PropertiesGrouper.ID, true);
      PlatformTestUtil.assertTreeEqual(component.getTree(),
                                       "-Derived.java\n" +
                                       " -Base\n" +
                                       "  x: int\n" +
                                       " -Derived\n" +
                                       "  +x: int");
    });
  }

  public void testNotStaticAccessors() {
    myFixture.configureByText("A.java", NOT_STATIC_GETTERS);

    myFixture.testStructureView(component -> {
      component.setActionActive(PropertiesGrouper.ID, true);
      component.setActionActive(Sorter.ALPHA_SORTER_ID, true);
      component.setActionActive(InheritedMembersNodeProvider.ID, false);
      component.setActionActive(PublicElementsFilter.ID, false);

      JTree tree = component.getTree();
      PlatformTestUtil.expandAll(tree);
      PlatformTestUtil.assertTreeEqual(tree,
                                       "-A.java\n" +
                                       " -A\n" +
                                       "  -aaa: int\n" +
                                       "   getAaa(): int\n" +
                                       "   setAaa(int): void\n" +
                                       "   aaa: int");
    });
  }

  @Override
  public void init() {
    myFixture.addClass(INTERFACE);
    myFixture.addClass(BASE);
    myFixture.configureByText("Sample.java", SAMPLE);
  }
}
