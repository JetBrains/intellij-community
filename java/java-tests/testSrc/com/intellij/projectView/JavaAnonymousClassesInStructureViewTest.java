// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.projectView;

import com.intellij.ide.structureView.impl.java.FieldsFilter;
import com.intellij.ide.structureView.impl.java.JavaAnonymousClassesNodeProvider;
import com.intellij.ide.structureView.newStructureView.StructureViewComponent;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.smartTree.TreeElementWrapper;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.containers.JBTreeTraverser;
import com.intellij.util.ui.tree.TreeUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.TreeModel;
import java.util.Collection;
import java.util.List;

public class JavaAnonymousClassesInStructureViewTest extends LightCodeInsightFixtureTestCase {
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
}