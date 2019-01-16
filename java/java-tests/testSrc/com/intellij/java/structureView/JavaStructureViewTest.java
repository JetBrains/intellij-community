// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.util.containers.JBIterable;
import com.intellij.util.containers.JBTreeTraverser;
import com.intellij.util.ui.tree.TreeUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.TreeModel;
import java.util.Collection;
import java.util.List;

public class JavaStructureViewTest extends LightJavaStructureViewTestCaseBase {
  @Language("JAVA")
  private static final String CLASS_WITH_ANONYMOUS = "class Foo {\n" +
                                                     "  Object field;\n" +
                                                     "  Object o1 = new Object(){};\n" +
                                                     "  Object o2 = new Object(){};\n" +
                                                     "  Object o3 = new Object(){};\n" +
                                                     "  Object o4 = new Object(){};\n" +
                                                     "  Object o5 = new Object(){};\n" +
                                                     "  Object o6 = new Object(){};\n" +
                                                     "  Object o7 = new Object(){};\n" +
                                                     "  Object o8 = new Object(){};\n" +
                                                     "  Object o9 = new Object(){};\n" +
                                                     "  Object o10 = new Object(){};\n" +
                                                     "  Object o11 = new Object(){};\n" +
                                                     "  Object o12 = new Object(){};\n" +
                                                     "  Object o13 = new Object(){\n" +
                                                     "    int num = 1;\n" +
                                                     "    Object o1 = new Object(){};\n" +
                                                     "    Object o2 = new Object(){};\n" +
                                                     "  };  \n" +
                                                     "}";
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
    JBTreeTraverser<AbstractTreeNode> traverser = JBTreeTraverser.from(AbstractTreeNode::getChildren);
    return traverser.withRoots(JBIterable.of(getElements()).filter(AbstractTreeNode.class))
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
    Collection<AbstractTreeNode> children = last.getChildren();
    assertEquals(1, children.size());
    assertEquals(3, ((AbstractTreeNode)((List)children).get(0)).getChildren().size());
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

  @NotNull
  private static Object[] getElements(@NotNull StructureViewComponent svc) {
    TreeModel model = svc.getTree().getModel();
    Object first = TreeUtil.getFirstNodePath(svc.getTree()).getLastPathComponent();
    return TreeUtil.nodeChildren(first, model).map(TreeUtil::getUserObject).toList().toArray();
  }

  private static int getFieldsCount() {
    return ANNO_FIELD_COUNT + FIELD_COUNT;
  }

  public void testSuperTypeGrouping() {
    doTest("abstract class Abstract {\n" +
           "abstract void toImplement();\n" +
           "void toOverride(){}}\n" +
           "class aClass extends Abstract {\n" +
           "void toImplement(){};\n" +
           "void toOverride(){};}",

           "-Test.java\n" +
           " -Abstract\n" +
           "  toImplement(): void\n" +
           "  toOverride(): void\n" +
           " -aClass\n" +
           "  -Abstract\n" +
           "   toImplement(): void\n" +
           "  -Abstract\n" +
           "   toOverride(): void"
      , true, false);
  }

  public void testPropertiesGrouping1() {
    doPropertiesTest("class Foo { \n" +
                     "  int i;\n" +
                     "  void setI(int i){}\n" +
                     "  int getI(){}" +
                     " }",

                     "-Test.java\n" +
                     " -Foo\n" +
                     "  -i: int\n" +
                     "   setI(int): void\n" +
                     "   getI(): int\n" +
                     "   i: int\n");
  }

  public void testPropertiesGrouping2() {
    doPropertiesTest("class Foo { \n" +
                     "  void setI(int i){}\n" +
                     "  int getI(){}" +
                     " }",

                     "-Test.java\n" +
                     " -Foo\n" +
                     "  -i: int\n" +
                     "   setI(int): void\n" +
                     "   getI(): int\n");
  }

  public void testPropertiesGrouping3() {
    doPropertiesTest("class Foo { \n" +
                     "  String i;\n" +
                     "  void setI(int i){}\n" +
                     "  int getI(){}" +
                     " }",

                     "-Test.java\n" +
                     " -Foo\n" +
                     "  -i: int\n" +
                     "   setI(int): void\n" +
                     "   getI(): int\n" +
                     "  i: String\n");
  }

  public void testPropertiesGrouping4() {
    doPropertiesTest("class Foo { \n" +
                     "  int i;\n" +
                     "  int getI(){}" +
                     " }",

                     "-Test.java\n" +
                     " -Foo\n" +
                     "  -i: int\n" +
                     "   getI(): int\n" +
                     "   i: int\n");
  }

  public void testPropertiesGrouping5() {
    doPropertiesTest("class Foo { \n" +
                     "  void setI(int i){}\n" +
                     " }",

                     "-Test.java\n" +
                     " -Foo\n" +
                     "  -i: int\n" +
                     "   setI(int): void\n");
  }

  public void testPropertiesGrouping6() {
    doPropertiesTest("class Foo { \n" +
                     "  void setI(String i){}\n" +
                     "  int getI(){}" +
                     " }",

                     "-Test.java\n" +
                     " -Foo\n" +
                     "  -i: String\n" +
                     "   setI(String): void\n" +
                     "  -i: int\n" +
                     "   getI(): int\n");
  }

  public void testPropertiesGrouping7() {
    doPropertiesTest("class Foo { \n" +
                     "  int i: \n" +
                     " }",

                     "-Test.java\n" +
                     " -Foo\n" +
                     "  i: int\n");
  }

  public void testPropertiesGrouping8() {
    doPropertiesTest("class Foo { \n" +
                     "  static void setI(int i){}\n" +
                     "  int getI(){}" +
                     " }",

                     "-Test.java\n" +
                     " -Foo\n" +
                     "  -i: int\n" +
                     "   setI(int): void\n" +
                     "   getI(): int\n");
  }

  public void testInnerMethodClasses() {
    doTest("class Foo {\n" +
           "  void foo(){\n" +
           "    class Inner implements Runnable {\n" +
           "      public void run(){}\n" +
           "    }\n" +
           "    new Runnable(){\n" +
           "      public void run(){\n" +
           "        class Inner2{}" +
           "      \n}" +
           "    };\n" +
           "  }\n" +
           "}",

           "-Test.java\n" +
           " -Foo\n" +
           "  -foo(): void\n" +
           "   -Inner\n" +
           "    run(): void\n" +
           "   -$1\n" +
           "    -run(): void\n" +
           "     Inner2");
  }

  public void testCustomRegionsIdea179610() {
    doTest(
      "public class Main {\n" +
      "\n" +
      "    //region with empty row\n" +
      "\n" +
      "    private static String filter(String in) {\n" +
      "        return in.toLowerCase();\n" +
      "    }\n" +
      "\n" +
      "    //endregion\n" +
      "\n" +
      "\n" +
      "    //region without empty row  \n" +
      "    public static void foo(String p) {\n" +
      "\n" +
      "        System.out.println(p);\n" +
      "        System.out.println(\"heelp\");\n" +
      "\n" +
      "    }\n" +
      "    //endregion\n" +
      "}",

      "-Test.java\n" +
      " -Main\n" +
      "  -with empty row\n" +
      "   filter(String): String\n" +
      "  -without empty row\n" +
      "   foo(String): void"
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
                                       "-I.java\n" +
                                       " -I\n" +
                                       "  +Impl");
    });
  }

  public void testVisibilitySorterComparingEqualKnown() {
    init();
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

  public void testVisibilitySorterCompareInheritanceGroups() {
    init();
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
                                       "-a.java\n" +
                                       " -F\n" +
                                       "  -Comparable\n" +
                                       "   compareTo(T): int\n" +
                                       "  -Enum\n" +
                                       "   clone(): Object\n" +
                                       "   compareTo(E): int\n" +
                                       "   equals(Object): boolean\n" +
                                       "   finalize(): void\n" +
                                       "   getDeclaringClass(): Class<E>\n" +
                                       "   hashCode(): int\n" +
                                       "   name(): String\n" +
                                       "   ordinal(): int\n" +
                                       "   toString(): String\n" +
                                       "   valueOf(Class<T>, String): T\n" +
                                       "  -F\n" +
                                       "   valueOf(String): F\n" +
                                       "   values(): F[]\n" +
                                       "  -Object\n" +
                                       "   getClass(): Class<?>\n" +
                                       "   notify(): void\n" +
                                       "   notifyAll(): void\n" +
                                       "   wait(): void\n" +
                                       "   wait(long): void\n" +
                                       "   wait(long, int): void\n" +
                                       "  A: F\n" +
                                       "  B: F\n" +
                                       "  C: F");
    });
  }

  private void doTest(String classText, String expected) {
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