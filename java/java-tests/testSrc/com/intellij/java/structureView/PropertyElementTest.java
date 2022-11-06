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
  public static final String INTERFACE = """
    interface Interface {
     void setII(int ii);
     int getII();
     void setI(int i);
     int getI();
    }""";

  @Language("JAVA")
  public static final String BASE = """
    class Base {
     public void setO(int o) {}
     public int getO() {return 0;}
     public void setB(int b) {}
     public int getB() {return 0;}
    }""";

  @Language("JAVA")
  public static final String SAMPLE = """
    abstract class Sample extends Base implements Interface {
     public void setII(int ii) {}
     public int getII() {return 0;}
     public void setO(int o) {}
     public int getO() {return 0;}
     protected void setRW(int x) {}
     public int getRW() {return 0;}
     public void setW(int w) {}
     public int getR() {return 0;}
     protected static void setSRW(int i) {}
     private static int getSRW() {return 0;}
     public static void setSW(int sw) {}
     public static int getSR() {return 0;}
     private int RW;
     private static int SRW;
    }""";
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
                                       """
                                         -Sample.java
                                          -Sample
                                           +II: int
                                           +o: int
                                           +r: int
                                           +RW: int
                                           +SR: int
                                           +SRW: int
                                           +SW: int
                                           +w: int""");

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
                                       """
                                         -Sample.java
                                          -Sample
                                           -II: int
                                            getII(): int
                                            setII(int): void
                                           -o: int
                                            getO(): int
                                            setO(int): void
                                           -r: int
                                            getR(): int
                                           -RW: int
                                            getRW(): int
                                            setRW(int): void
                                            RW: int
                                           -SR: int
                                            getSR(): int
                                           -SRW: int
                                            getSRW(): int
                                            setSRW(int): void
                                            SRW: int
                                           -SW: int
                                            setSW(int): void
                                           -w: int
                                            setW(int): void""");
    });
  }

  public void test14827() {
    myFixture.configureByText("Derived.java", "class Base {int x;}\n" +
                                              "class Derived extends Base {int getX() {return 0;}}");
    myFixture.testStructureView(component -> {
      component.setActionActive(FieldsFilter.ID, false);
      component.setActionActive(PropertiesGrouper.ID, true);
      PlatformTestUtil.assertTreeEqual(component.getTree(),
                                       """
                                         -Derived.java
                                          -Base
                                           x: int
                                          -Derived
                                           +x: int""");
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
                                       """
                                         -A.java
                                          -A
                                           -aaa: int
                                            getAaa(): int
                                            setAaa(int): void
                                            aaa: int""");
    });
  }

  @Override
  public void init() {
    myFixture.addClass(INTERFACE);
    myFixture.addClass(BASE);
    myFixture.configureByText("Sample.java", SAMPLE);
  }
}
