// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.json;

import com.intellij.icons.AllIcons;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.newStructureView.StructureViewComponent;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.lang.LanguageStructureViewBuilder;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.Consumer;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;

import static com.intellij.testFramework.PlatformTestUtil.assertTreeEqual;
import static com.intellij.testFramework.PlatformTestUtil.expandAll;

/**
 * @author Mikhail Golubev
 */
public class JsonStructureViewTest extends JsonTestCase {

  private void doTest(final String expected) {
    myFixture.configureByFile(getTestName(false) + ".json");
    myFixture.testStructureView(svc -> {
      expandAll(svc.getTree());
      assertTreeEqual(svc.getTree(), expected);
    });
  }

  private void doTestTreeStructure(@NotNull Consumer<StructureViewModel> consumer) {
    myFixture.configureByFile(getTestName(false) + ".json");
    final StructureViewBuilder builder = LanguageStructureViewBuilder.INSTANCE.getStructureViewBuilder(myFixture.getFile());
    assertNotNull(builder);
    StructureViewComponent component = null;
    try {
      final FileEditor editor = FileEditorManager.getInstance(getProject()).getSelectedEditor(myFixture.getFile().getVirtualFile());
      component = (StructureViewComponent)builder.createStructureView(editor, myFixture.getProject());
      final StructureViewModel model = component.getTreeModel();
      consumer.consume(model);
    }
    finally {
      if (component != null) {
        Disposer.dispose(component);
      }
    }
  }

  public void testPropertyOrderPreserved() {
    doTest("-PropertyOrderPreserved.json\n" +
           " ccc\n" +
           " bbb\n" +
           " -aaa\n" +
           "  eee\n" +
           "  ddd\n");
  }

  // IDEA-127119
  public void testObjectsInsideArraysAreShown() {
    doTest("-ObjectsInsideArraysAreShown.json\n" +
           " aProp\n" +
           " -node1\n" +
           "  anotherProp\n" +
           "  subNode1\n" +
           "  subNode2\n" +
           " -node2\n" +
           "  -object\n" +
           "   -subNode2\n" +
           "    -object\n" +
           "     someNode\n" +
           " -node3\n" +
           "  -object\n" +
           "   prop1\n" +
           "   prop2\n" +
           "   someFlag\n" +
           "   -array\n" +
           "    -object\n" +
           "     arrProp1\n" +
           "     -array2\n" +
           "      -object\n" +
           "       arr2Prop1\n" +
           "       arr2Prop2\n" +
           "       -array3\n" +
           "        -object\n" +
           "         prop1\n" +
           "         prop2\n");
  }

  // IDEA-131502
  public void testArrayNodesAreShownIfNecessary() {
    doTest("-ArrayNodesAreShownIfNecessary.json\n" +
           " -array\n" +
           "  -object\n" +
           "   nestedObject\n" +
           " -array\n" +
           "  -array\n" +
           "   -object\n" +
           "    deepNestedObject\n" +
           " -object\n" +
           "  siblingObject\n");
  }

  // IDEA-167017
  public void testValuesOfScalarPropertiesAreShown() {
    doTestTreeStructure(model -> {
      final TreeElement[] children = model.getRoot().getChildren();
      assertSize(6, children);
      final ItemPresentation booleanNode = children[0].getPresentation();
      assertEquals("boolean", booleanNode.getPresentableText());
      assertEquals("true", booleanNode.getLocationString());
      
      final ItemPresentation nullNode = children[1].getPresentation();
      assertEquals("nullable", nullNode.getPresentableText());
      assertEquals("null", nullNode.getLocationString());
      
      final ItemPresentation numNode = children[2].getPresentation();
      assertEquals("number", numNode.getPresentableText());
      assertEquals("42", numNode.getLocationString());
      
      final ItemPresentation stringNode = children[3].getPresentation();
      assertEquals("string", stringNode.getPresentableText());
      assertEquals("\"foo\"", stringNode.getLocationString());
      
      final ItemPresentation arrayNode = children[4].getPresentation();
      assertEquals("array", arrayNode.getPresentableText());
      assertNull(arrayNode.getLocationString());

      final ItemPresentation objectNode = children[5].getPresentation();
      assertEquals("object", objectNode.getPresentableText());
      assertNull(objectNode.getLocationString());

      final TreeElement[] nestedChildren = children[5].getChildren();
      assertSize(1, nestedChildren);
      final ItemPresentation subStringNode = nestedChildren[0].getPresentation();
      assertEquals("foo", subStringNode.getPresentableText());
      assertEquals("\"bar\"", subStringNode.getLocationString());
    });
  }

  // Moved from JavaScript

  public void testSimpleStructure() {
    doTestTreeStructure(model -> {
      TreeElement[] children = model.getRoot().getChildren();
      assertEquals(2, children.length);
      assertEquals("aaa", children[0].getPresentation().getPresentableText());
      assertEquals(PlatformIcons.PROPERTY_ICON, children[0].getPresentation().getIcon(false));
      assertEquals("bbb", children[1].getPresentation().getPresentableText());
      assertEquals(AllIcons.Json.Object, children[1].getPresentation().getIcon(false));

      children = children[1].getChildren();
      assertEquals(1, children.length);
      assertEquals("ccc", children[0].getPresentation().getPresentableText());
      assertEquals(PlatformIcons.PROPERTY_ICON, children[0].getPresentation().getIcon(false));
    });
  }

  @NotNull
  @Override
  public String getBasePath() {
    return super.getBasePath() + "/structureView";
  }
}
