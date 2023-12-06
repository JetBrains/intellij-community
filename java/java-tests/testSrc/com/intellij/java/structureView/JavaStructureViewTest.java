// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.structureView;

import com.intellij.ide.structureView.impl.java.*;
import com.intellij.ide.structureView.newStructureView.StructureViewComponent;
import com.intellij.ide.util.InheritedMembersNodeProvider;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.smartTree.Sorter;
import com.intellij.ide.util.treeView.smartTree.TreeElementWrapper;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.containers.JBTreeTraverser;
import com.intellij.util.ui.tree.TreeUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.TreeModel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class JavaStructureViewTest extends LightJavaStructureViewTestCaseBase {
  @Language("JAVA")
  private static final String CLASS_WITH_ANONYMOUS = """
    class Foo {
      Object field;
      Object o1 = new Object(){};
      Object o2 = new Object(){};
      Object o3 = new Object(){};
      Object o4 = new Object(){};
      Object o5 = new Object(){};
      Object o6 = new Object(){};
      Object o7 = new Object(){};
      Object o8 = new Object(){};
      Object o9 = new Object(){};
      Object o10 = new Object(){};
      Object o11 = new Object(){};
      Object o12 = new Object(){};
      Object o13 = new Object(){
        int num = 1;
        Object o1 = new Object(){};
        Object o2 = new Object(){};
      }; \s
    }""";
  private static final int ANNO_FIELD_COUNT = 13;
  private static final int FIELD_COUNT = 1;

  public void testAnonymousNotShown() {
    assertEquals(getFieldsCount(), getElements(false, true).length);
  }

  public void testAnonymousShown() {
    final Object[] elements = getElements();
    assertEquals(getFieldsCount(), elements.length);
    assertEquals(ANNO_FIELD_COUNT + 2, getAllAnonymous().size());
  }

  private List<PsiAnonymousClass> getAllAnonymous() {
    JBTreeTraverser<AbstractTreeNode<?>> traverser = JBTreeTraverser.from(AbstractTreeNode::getChildren);
    List<AbstractTreeNode<?>> roots = new ArrayList<>();
    for (Object element : getElements()) {
      if (element instanceof AbstractTreeNode) {
        roots.add((AbstractTreeNode<?>)element);
      }
    }
    return traverser.withRoots(roots)
      .traverse()
      .map(StructureViewComponent::unwrapValue)
      .filter(PsiAnonymousClass.class)
      .toList();
  }

  public void testSorting() {
    int i = 0;
    for (Object element : getElements(true, false)) {
      assertEquals("$" + ++i, element.toString());
    }
  }

  public void testAnonymousInsideAnonymous() {
    Object[] elements = getElements();
    TreeElementWrapper last = (TreeElementWrapper)elements[elements.length - 1];
    Collection<AbstractTreeNode<?>> children = last.getChildren();
    assertEquals(1, children.size());
    assertEquals(3, ((AbstractTreeNode<?>)((List<?>)children).get(0)).getChildren().size());
  }

  private Object[] getElements() {
    return getElements(true, true);
  }

  private Object[] getElements(boolean showAnonymous, boolean showFields) {
    myFixture.configureByText("Foo.java", CLASS_WITH_ANONYMOUS);
    Ref<Object[]> ref = Ref.create();
    myFixture.testStructureView(svc -> {
      svc.setActionActive(JavaAnonymousClassesNodeProvider.ID, showAnonymous);
      svc.setActionActive(FieldsFilter.ID, !showFields);
      ref.set(getElements(svc));
    });
    return ref.get();
  }

  private static Object @NotNull [] getElements(@NotNull StructureViewComponent svc) {
    TreeModel model = svc.getTree().getModel();
    Object first = TreeUtil.getFirstNodePath(svc.getTree()).getLastPathComponent();
    return TreeUtil.nodeChildren(first, model).map(TreeUtil::getUserObject).toList().toArray();
  }

  private static int getFieldsCount() {
    return ANNO_FIELD_COUNT + FIELD_COUNT;
  }

  public void testSuperTypeGrouping() {
    doTest("""
             abstract class Abstract {
             abstract void toImplement();
             void toOverride(){}}
             class aClass extends Abstract {
             void toImplement(){};
             void toOverride(){};}""",

           """
             -Test.java
              -Abstract
               toImplement(): void
               toOverride(): void
              -aClass
               -Abstract
                toImplement(): void
               -Abstract
                toOverride(): void"""
      , true, false);
  }

  public void testPropertiesGrouping1() {
    doPropertiesTest("""
                       class Foo {\s
                         int i;
                         void setI(int i){}
                         int getI(){} }""",

                     """
                       -Test.java
                        -Foo
                         -i: int
                          setI(int): void
                          getI(): int
                          i: int
                       """);
  }

  public void testPropertiesGrouping2() {
    doPropertiesTest("""
                       class Foo {\s
                         void setI(int i){}
                         int getI(){} }""",

                     """
                       -Test.java
                        -Foo
                         -i: int
                          setI(int): void
                          getI(): int
                       """);
  }

  public void testPropertiesGrouping3() {
    doPropertiesTest("""
                       class Foo {\s
                         String i;
                         void setI(int i){}
                         int getI(){} }""",

                     """
                       -Test.java
                        -Foo
                         -i: int
                          setI(int): void
                          getI(): int
                         i: String
                       """);
  }

  public void testPropertiesGrouping4() {
    doPropertiesTest("""
                       class Foo {\s
                         int i;
                         int getI(){} }""",

                     """
                       -Test.java
                        -Foo
                         -i: int
                          getI(): int
                          i: int
                       """);
  }

  public void testPropertiesGrouping5() {
    doPropertiesTest("""
                       class Foo {\s
                         void setI(int i){}
                        }""",

                     """
                       -Test.java
                        -Foo
                         -i: int
                          setI(int): void
                       """);
  }

  public void testPropertiesGrouping6() {
    doPropertiesTest("""
                       class Foo {\s
                         void setI(String i){}
                         int getI(){} }""",

                     """
                       -Test.java
                        -Foo
                         -i: String
                          setI(String): void
                         -i: int
                          getI(): int
                       """);
  }

  public void testPropertiesGrouping7() {
    doPropertiesTest("""
                       class Foo {\s
                         int i:\s
                        }""",

                     """
                       -Test.java
                        -Foo
                         i: int
                       """);
  }

  public void testPropertiesGrouping8() {
    doPropertiesTest("""
                       class Foo {\s
                         static void setI(int i){}
                         int getI(){} }""",

                     """
                       -Test.java
                        -Foo
                         -i: int
                          setI(int): void
                          getI(): int
                       """);
  }

  public void testInnerMethodClasses() {
    doTest("""
             class Foo {
               void foo(){
                 class Inner implements Runnable {
                   public void run(){}
                 }
                 new Runnable(){
                   public void run(){
                     class Inner2{}     \s
             }    };
               }
             }""",

           """
             -Test.java
              -Foo
               -foo(): void
                -Inner
                 run(): void
                -$1
                 -run(): void
                  Inner2""");
  }

  public void testCustomRegionsIdea179610() {
    doTest(
      """
        public class Main {

            //region with empty row

            private static String filter(String in) {
                return in.toLowerCase();
            }

            //endregion


            //region without empty row \s
            public static void foo(String p) {

                System.out.println(p);
                System.out.println("heelp");

            }
            //endregion
        }""",

      """
        -Test.java
         -Main
          -with empty row
           filter(String): String
          -without empty row
           foo(String): void"""
    );
  }

  public void testCustomRegionsIdea205350() {
    doTest(
      """
        // region Test
        package com.company;

        class CustRegionTest {
           // region Another
           void foo () { }
           // endregion
        }
        //endregion""",

      """
        -Test.java
         -Test
          -CustRegionTest
           -Another
            foo(): void"""
    );
  }

  public void testRecordComponents() {
    doTest(
      """
        // region Test
        package com.company;

        record R(String s, int i) {}""",

      """
        -Test.java
         -Test
          -R
           s: String
           i: int"""
    );
  }

  public void testImplicitClass() {
    doTest(
      """
        void foo() {
        }
        """,

      """
        -Test.java
         foo(): void"""
    );
  }

  public void testRecursive() {
    myFixture.configureByText("I.java", "interface I {" +
                                        "  class Impl implements I {" +
                                        "  }" +
                                        "};");
    myFixture.testStructureView(component -> {
      component.setActionActive(InheritedMembersNodeProvider.ID, true);
      PlatformTestUtil.assertTreeEqual(component.getTree(),
                                       """
                                         -I.java
                                          -I
                                           +Impl""");
    });
  }

  public void testVisibilitySorterComparingEqualKnown() {
    init();
    myFixture.testStructureView(svc -> {
      svc.setActionActive(InheritedMembersNodeProvider.ID, true);
      PlatformTestUtil.assertTreeEqual(svc.getTree(),
                                       """
                                         -Derived.java
                                          -Derived
                                           +Inner
                                           f(): void
                                           g(): void
                                           setX(int): void
                                           getX(): int
                                           setY(int): void
                                           getY(): int
                                           setI(int): void
                                           getI(): int
                                           toString(): String
                                           getZ(): int
                                           setZ(int): void
                                           getClass(): Class<?>
                                           hashCode(): int
                                           equals(Object): boolean
                                           clone(): Object
                                           notify(): void
                                           notifyAll(): void
                                           wait(long): void
                                           wait(long, int): void
                                           wait(): void
                                           finalize(): void
                                           x: int
                                           i: int""");
      svc.setActionActive(VisibilitySorter.ID, true);
      PlatformTestUtil.assertTreeEqual(svc.getTree(),
                                       """
                                         -Derived.java
                                          -Derived
                                           +Inner
                                           f(): void
                                           g(): void
                                           setX(int): void
                                           getX(): int
                                           setY(int): void
                                           getY(): int
                                           setI(int): void
                                           getI(): int
                                           toString(): String
                                           getClass(): Class<?>
                                           hashCode(): int
                                           equals(Object): boolean
                                           notify(): void
                                           notifyAll(): void
                                           wait(long): void
                                           wait(long, int): void
                                           wait(): void
                                           getZ(): int
                                           clone(): Object
                                           finalize(): void
                                           setZ(int): void
                                           x: int
                                           i: int""");
    });
  }

  public void testVisibilitySorterCompareInheritanceGroups() {
    init();
    myFixture.testStructureView(svc -> {
      svc.setActionActive(PropertiesGrouper.ID, true);
      svc.setActionActive(SuperTypesGrouper.ID, true);
      svc.setActionActive(InheritedMembersNodeProvider.ID, true);

      PlatformTestUtil.assertTreeEqual(svc.getTree(),
                                       """
                                         -Derived.java
                                          -Derived
                                           +Inner
                                           +Base
                                           +Interface
                                           +Base
                                           +Object
                                           +y: int
                                           x: int
                                           i: int""");

      svc.setActionActive(VisibilitySorter.ID, true);
      PlatformTestUtil.assertTreeEqual(svc.getTree(),
                                       """
                                         -Derived.java
                                          -Derived
                                           +Inner
                                           +Base
                                           +Base
                                           +Object
                                           +Interface
                                           +y: int
                                           x: int
                                           i: int"""
      );
    });
  }

  public void testHidingFieldsAndMethods() {
    myFixture.configureByText("Derived.java", DERIVED);

    myFixture.testStructureView(svc -> {
      svc.setActionActive(FieldsFilter.ID, true);
      svc.setActionActive(Sorter.ALPHA_SORTER_ID, true);

      svc.setActionActive(InheritedMembersNodeProvider.ID, false);

      JTree tree = svc.getTree();
      PlatformTestUtil.expandAll(tree);
      PlatformTestUtil.assertTreeEqual(tree,
                                       """
                                         -Derived.java
                                          -Derived
                                           Inner
                                           f(): void
                                           g(): void
                                           getI(): int
                                           getX(): int
                                           getY(): int
                                           setI(int): void
                                           setX(int): void
                                           setY(int): void""");
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
                                       """
                                         -Derived.java
                                          -Derived
                                           +Inner
                                           +Base
                                           +Base
                                           +Interface
                                           +Object
                                           +y: int
                                           i: int
                                           x: int""");
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
                                       """
                                         -Derived.java
                                          -Derived
                                           -Inner
                                            -Object
                                             clone(): Object
                                             equals(Object): boolean
                                             finalize(): void
                                             getClass(): Class<?>
                                             hashCode(): int
                                             notify(): void
                                             notifyAll(): void
                                             toString(): String
                                             wait(): void
                                             wait(long): void
                                             wait(long, int): void
                                           -Base
                                            f(): void
                                            getX(): int
                                            setX(int): void
                                           -Base
                                            getZ(): int
                                            setZ(int): void
                                            toString(): String
                                           -Interface
                                            g(): void
                                            getI(): int
                                            setI(int): void
                                           -Object
                                            clone(): Object
                                            equals(Object): boolean
                                            finalize(): void
                                            getClass(): Class<?>
                                            hashCode(): int
                                            notify(): void
                                            notifyAll(): void
                                            wait(): void
                                            wait(long): void
                                            wait(long, int): void
                                           getY(): int
                                           setY(int): void
                                           i: int
                                           x: int""");
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
                                       """
                                         -Derived.java
                                          -Derived
                                           -Inner
                                            -Object
                                             equals(Object): boolean
                                             getClass(): Class<?>
                                             hashCode(): int
                                             notify(): void
                                             notifyAll(): void
                                             toString(): String
                                             wait(): void
                                             wait(long): void
                                             wait(long, int): void
                                           -Base
                                            f(): void
                                            getX(): int
                                            setX(int): void
                                           -Base
                                            toString(): String
                                           -Interface
                                            g(): void
                                            getI(): int
                                            setI(int): void
                                           -Object
                                            equals(Object): boolean
                                            getClass(): Class<?>
                                            hashCode(): int
                                            notify(): void
                                            notifyAll(): void
                                            wait(): void
                                            wait(long): void
                                            wait(long, int): void
                                           getY(): int
                                           setY(int): void""");
    });
  }

  public void testInheritedEnumMembers() {
    myFixture.configureByText("a.java", "enum F { A,B,C}");

    myFixture.testStructureView(svc -> {
      svc.setActionActive(InheritedMembersNodeProvider.ID, true);
      svc.setActionActive(PropertiesGrouper.ID, false);
      svc.setActionActive(SuperTypesGrouper.ID, true);
      svc.setActionActive(Sorter.ALPHA_SORTER_ID, true);

      JTree tree = svc.getTree();
      PlatformTestUtil.expandAll(tree);
      PlatformTestUtil.assertTreeEqual(tree,
                                       """
                                         -a.java
                                          -F
                                           -Comparable
                                            compareTo(T): int
                                           -Enum
                                            clone(): Object
                                            compareTo(E): int
                                            equals(Object): boolean
                                            finalize(): void
                                            getDeclaringClass(): Class<E>
                                            hashCode(): int
                                            name(): String
                                            ordinal(): int
                                            toString(): String
                                            valueOf(Class<T>, String): T
                                           -F
                                            valueOf(String): F
                                            values(): F[]
                                           -Object
                                            getClass(): Class<?>
                                            notify(): void
                                            notifyAll(): void
                                            wait(): void
                                            wait(long): void
                                            wait(long, int): void
                                           A: F
                                           B: F
                                           C: F""");
    });
  }

  private void doTest(String classText, @Language("TEXT") String expected) {
    doTest(classText, expected, false, false);
  }

  private void doPropertiesTest(String classText, String expected) {
    doTest(classText, expected, false, true);
  }

  private void doTest(String classText,
                      String expected,
                      boolean showInterfaces,
                      boolean showProperties) {
    myFixture.configureByText("Test.java", classText);
    myFixture.testStructureView(svc -> {
      svc.setActionActive(SuperTypesGrouper.ID, showInterfaces);
      svc.setActionActive(PropertiesGrouper.ID, showProperties);
      svc.setActionActive(JavaAnonymousClassesNodeProvider.ID, true);
      JTree tree = svc.getTree();
      PlatformTestUtil.waitWhileBusy(tree);
      PlatformTestUtil.expandAll(tree);
      PlatformTestUtil.assertTreeEqual(tree, expected);
    });
  }
}