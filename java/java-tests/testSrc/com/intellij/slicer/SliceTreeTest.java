/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.slicer;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.impl.ToolWindowHeadlessManagerImpl;
import com.intellij.psi.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NonNls;

import java.util.*;

/**
 * @author cdr
 */
public class SliceTreeTest extends SliceTestCase {
  private SliceTreeStructure configureTree(@NonNls final String name) throws Exception {
    configureByFile("/codeInsight/slice/backward/"+ name +".java");
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    PsiElement element = new SliceHandler(true).getExpressionAtCaret(getEditor(), getFile());
    assertNotNull(element);
    Collection<HighlightInfo> errors = highlightErrors();
    assertEmpty(errors);

    SliceAnalysisParams params = new SliceAnalysisParams();
    params.scope = new AnalysisScope(getProject());
    params.dataFlowToThis = true;

    SliceUsage usage = SliceUsage.createRootUsage(element, params);


    SlicePanel panel = new SlicePanel(getProject(), true, new SliceRootNode(getProject(), new DuplicateMap(), usage), false, ToolWindowHeadlessManagerImpl.HEADLESS_WINDOW) {
      @Override
      protected void close() {
      }

      @Override
      public boolean isAutoScroll() {
        return false;
      }

      @Override
      public void setAutoScroll(boolean autoScroll) {
      }

      @Override
      public boolean isPreview() {
        return false;
      }

      @Override
      public void setPreview(boolean preview) {
      }
    };
    Disposer.register(getProject(), panel);
    return (SliceTreeStructure)panel.getBuilder().getTreeStructure();
  }

  private static void expandNodesTo(final SliceNode node, List<SliceNode> to) {
    node.update();
    node.calculateDupNode();
    to.add(node);
    Collection<? extends AbstractTreeNode> nodes = node.getChildren();
    for (AbstractTreeNode child : nodes) {
      expandNodesTo((SliceNode)child, to);
    }
  }

  public void testTypingDoesNotInterfereWithDuplicates() throws Exception {
    SliceTreeStructure treeStructure = configureTree("DupSlice");
    SliceNode root = (SliceNode)treeStructure.getRootElement();
    List<SliceNode> nodes = new ArrayList<SliceNode>();
    expandNodesTo(root, nodes);

    TIntArrayList hasDups = new TIntArrayList();
    for (SliceNode node : nodes) {
      if (node.getDuplicate() != null) {
        PsiElement element = node.getValue().getElement();
        hasDups.add(element.getTextRange().getStartOffset());
        assertTrue(element instanceof PsiParameter && ((PsiParameter)element).getName().equals("i") || element instanceof PsiLiteralExpression);
      }
    }

    type("   xx");
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    backspace();
    backspace();
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    backspace();
    backspace();
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    backspace();
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();

    nodes.clear();
    expandNodesTo(root, nodes);
    for (SliceNode node : nodes) {
      if (node.getDuplicate() != null) {
        PsiElement element = node.getValue().getElement();
        int offset = element.getTextRange().getStartOffset();
        int i = hasDups.indexOf(offset);
        assertTrue(i != -1);
        hasDups.remove(i);
        assertTrue(element instanceof PsiParameter && ((PsiParameter)element).getName().equals("i") || element instanceof PsiLiteralExpression);
      }
    }
    assertTrue(hasDups.isEmpty());
  }

  public void testLeafExpressionsAreEmptyInCaseOfInfinitelyExpandingTreeWithDuplicateNodes() throws Exception {
    SliceTreeStructure treeStructure = configureTree("Tuple");
    SliceNode root = (SliceNode)treeStructure.getRootElement();
    Collection<PsiElement> leaves = SliceLeafAnalyzer.calcLeafExpressions(root, treeStructure, SliceLeafAnalyzer.createMap());
    assertNotNull(leaves);
    assertEmpty(leaves);
  }
  public void testLeafExpressionsSimple() throws Exception {
    SliceTreeStructure treeStructure = configureTree("DupSlice");
    SliceNode root = (SliceNode)treeStructure.getRootElement();
    Collection<PsiElement> leaves = SliceLeafAnalyzer.calcLeafExpressions(root, treeStructure, SliceLeafAnalyzer.createMap());
    assertNotNull(leaves);
    PsiElement element = assertOneElement(leaves);
    assertTrue(element instanceof PsiLiteralExpression);
    assertEquals(1111111111, ((PsiLiteral)element).getValue());
  }
  public void testLeafExpressionsMoreComplex() throws Exception {
    SliceTreeStructure treeStructure = configureTree("Duplicate");
    SliceNode root = (SliceNode)treeStructure.getRootElement();
    Collection<PsiElement> leaves = SliceLeafAnalyzer.calcLeafExpressions(root, treeStructure, SliceLeafAnalyzer.createMap());
    assertNotNull(leaves);
    assertEquals(2, leaves.size());
    List<PsiElement> list = new ArrayList<PsiElement>(leaves);
    Collections.sort(list, new Comparator<PsiElement>() {
      @Override
      public int compare(PsiElement o1, PsiElement o2) {
        return o1.getText().compareTo(o2.getText());
      }
    });
    assertTrue(list.get(0) instanceof PsiLiteralExpression);
    assertEquals(false, ((PsiLiteral)list.get(0)).getValue());
    assertTrue(list.get(1) instanceof PsiLiteralExpression);
    assertEquals(true, ((PsiLiteral)list.get(1)).getValue());
  }

  public void testGroupByValuesCorrectLeaves() throws Exception {
    SliceTreeStructure treeStructure = configureTree("DuplicateLeaves");
    SliceRootNode root = (SliceRootNode)treeStructure.getRootElement();
    Map<SliceNode, Collection<PsiElement>> map = SliceLeafAnalyzer.createMap();
    Collection<PsiElement> leaves = SliceLeafAnalyzer.calcLeafExpressions(root, treeStructure, map);
    assertNotNull(leaves);
    assertEquals(1, leaves.size());
    PsiElement leaf = leaves.iterator().next();
    assertTrue(leaf instanceof PsiLiteralExpression);
    assertEquals("\"oo\"", leaf.getText());

    SliceRootNode newRoot = SliceLeafAnalyzer.createTreeGroupedByValues(leaves, root, map);
    Collection<? extends AbstractTreeNode> children = newRoot.getChildren();
    assertEquals(1, children.size());
    SliceNode child = (SliceNode)children.iterator().next();
    assertTrue(child instanceof SliceLeafValueRootNode);

    children = child.getChildren();
    assertEquals(1, children.size());
    child = (SliceNode)children.iterator().next();
    assertTrue(child.getValue().getElement() instanceof PsiField);

    children = child.getChildren();
    assertEquals(1, children.size());
    child = (SliceNode)children.iterator().next();
    assertTrue(child.getValue().getElement() instanceof PsiReferenceExpression);

    children = child.getChildren();
    assertEquals(1, children.size());
    child = (SliceNode)children.iterator().next();
    assertTrue(child.getValue().getElement() instanceof PsiParameter);

    children = child.getChildren();
    assertEquals(1, children.size());
    child = (SliceNode)children.iterator().next();
    assertTrue(child.getValue().getElement() instanceof PsiReferenceExpression);

    children = child.getChildren();
    assertEquals(1, children.size());
    child = (SliceNode)children.iterator().next();
    assertTrue(child.getValue().getElement() instanceof PsiParameter);

    children = child.getChildren();
    assertEquals(1, children.size());
    child = (SliceNode)children.iterator().next();
    assertTrue(child.getValue().getElement() instanceof PsiLiteralExpression);
    assertEquals(child.getValue().getElement(), leaf);
  }

  public void testNullness() throws Exception {
    SliceTreeStructure treeStructure = configureTree("Nulls");
    final SliceRootNode root = (SliceRootNode)treeStructure.getRootElement();
    Map<SliceNode, SliceNullnessAnalyzer.NullAnalysisResult> map = SliceNullnessAnalyzer.createMap();
    SliceNullnessAnalyzer.NullAnalysisResult leaves = SliceNullnessAnalyzer.calcNullableLeaves(root, treeStructure, map);

    SliceRootNode newRoot = SliceNullnessAnalyzer.createNewTree(leaves, root, map);

    checkStructure(newRoot, "Null Values\n" +
                            "  Value: o\n" +
                            "    (6: 12) |String| |l|;\n" +
                            "      (52: 13) |l| |=| |d|;\n" +
                            "        (51: 21) |void| |set|(|String| |d|)| |{\n" +
                            "          (15: 13) |set|(|o|)|;\n" +
                            "  Value: nu()\n" +
                            "    (6: 12) |String| |l|;\n" +
                            "      (52: 13) |l| |=| |d|;\n" +
                            "        (51: 21) |void| |set|(|String| |d|)| |{\n" +
                            "          (29: 13) |set|(|nu|(|)|)|;\n" +
                            "  Value: t\n" +
                            "    (6: 12) |String| |l|;\n" +
                            "      (52: 13) |l| |=| |d|;\n" +
                            "        (51: 21) |void| |set|(|String| |d|)| |{\n" +
                            "          (46: 15) |x|.|set|(|t|)|;\n" +
                            "NotNull Values\n" +
                            "  Value: \"\"\n" +
                            "    (6: 12) |String| |l|;\n" +
                            "      (52: 13) |l| |=| |d|;\n" +
                            "        (51: 21) |void| |set|(|String| |d|)| |{\n" +
                            "          (19: 13) |set|(|CON|)|;\n" +
                            "            (5: 39) |private| |final| |static| |String| |CON| |=| |\"\"|;\n" +
                            "  Value: \"xxx\"\n" +
                            "    (6: 12) |String| |l|;\n" +
                            "      (52: 13) |l| |=| |d|;\n" +
                            "        (51: 21) |void| |set|(|String| |d|)| |{\n" +
                            "          (10: 13) |set|(|\"xxx\"|)|;\n" +
                            "  Value: new String()\n" +
                            "    (6: 12) |String| |l|;\n" +
                            "      (52: 13) |l| |=| |d|;\n" +
                            "        (51: 21) |void| |set|(|String| |d|)| |{\n" +
                            "          (17: 13) |set|(|new| |String|(|)|)|;\n" +
                            "  Value: nn()\n" +
                            "    (6: 12) |String| |l|;\n" +
                            "      (52: 13) |l| |=| |d|;\n" +
                            "        (51: 21) |void| |set|(|String| |d|)| |{\n" +
                            "          (18: 13) |set|(|nn|(|)|)|;\n" +
                            "  Value: nn\n" +
                            "    (6: 12) |String| |l|;\n" +
                            "      (52: 13) |l| |=| |d|;\n" +
                            "        (51: 21) |void| |set|(|String| |d|)| |{\n" +
                            "          (21: 13) |set|(|nn|)|;\n" +
                            "  Value: g\n" +
                            "    (6: 12) |String| |l|;\n" +
                            "      (52: 13) |l| |=| |d|;\n" +
                            "        (51: 21) |void| |set|(|String| |d|)| |{\n" +
                            "          (27: 13) |set|(|g|)|;\n" +
                            "  Value: \"null\"\n" +
                            "    (6: 12) |String| |l|;\n" +
                            "      (52: 13) |l| |=| |d|;\n" +
                            "        (51: 21) |void| |set|(|String| |d|)| |{\n" +
                            "          (48: 15) |x|.|set|(|t| |==| |null| |?| |\"null\"| |:| |t|)|;\n" +
                            "            (48: 27) |x|.|set|(|t| |==| |null| |?| |\"null\"| |:| |t|)|;\n" +
                            "  Value: t\n" +
                            "    (6: 12) |String| |l|;\n" +
                            "      (52: 13) |l| |=| |d|;\n" +
                            "        (51: 21) |void| |set|(|String| |d|)| |{\n" +
                            "          (48: 15) |x|.|set|(|t| |==| |null| |?| |\"null\"| |:| |t|)|;\n" +
                            "            (48: 36) |x|.|set|(|t| |==| |null| |?| |\"null\"| |:| |t|)|;\n" +
                            "  Value: d\n" +
                            "    (6: 12) |String| |l|;\n" +
                            "      (55: 13) |l| |=| |d|;\n" +
                            "Other Values\n" +
                            "  Value: private String d;\n" +
                            "    (6: 12) |String| |l|;\n" +
                            "      (52: 13) |l| |=| |d|;\n" +
                            "        (51: 21) |void| |set|(|String| |d|)| |{\n" +
                            "          (30: 13) |set|(|hz|(|)|)|;\n" +
                            "            (42: 16) |return| |d|;\n" +
                            "              (7: 20) |private| |String| |d|;\n" +
                            "  Value: String g\n" +
                            "    (6: 12) |String| |l|;\n" +
                            "      (52: 13) |l| |=| |d|;\n" +
                            "        (51: 21) |void| |set|(|String| |d|)| |{\n" +
                            "          (11: 13) |set|(|g|)|;\n" +
                            "            (9: 21) |public| |X|(|String| |g|)| |{\n" +
                            "");
  }

  private static void checkStructure(final SliceNode root, @NonNls String dataExpected) {
    List<SliceNode> actualNodes = new ArrayList<SliceNode>((Collection)root.getChildren());
    Collections.sort(actualNodes, SliceTreeBuilder.SLICE_NODE_COMPARATOR);

    Object[] actualStrings = ContainerUtil.map2Array(actualNodes, new Function<SliceNode, Object>() {
      @Override
      public Object fun(SliceNode node) {
        return node.toString();
      }
    });

    String[] childrenExpected = dataExpected.isEmpty() ? ArrayUtil.EMPTY_STRING_ARRAY : dataExpected.split("\n");
    String curChildren = "";
    String curNode = null;
    int iactual = 0;
    for (int iexp = 0; iexp <= childrenExpected.length; iexp++) {
      String e = iexp == childrenExpected.length ? null : childrenExpected[iexp];
      boolean isTopLevel = e == null || e.charAt(0) != ' ';
      if (isTopLevel) {
        if (curNode != null) {
          assertTrue(iactual < actualStrings.length);
          Object actual = actualStrings[iactual];
          assertEquals(curNode, actual);
          checkStructure(actualNodes.get(iactual), curChildren);
          iactual++;
        }

        curNode = e;
        curChildren = "";
      }
      else {
        curChildren += StringUtil.trimStart(e, "  ") + "\n";
      }
    }
    assertEquals(dataExpected, actualNodes.size(), iactual);
  }

  public void testDoubleNullness() throws Exception {
    SliceTreeStructure treeStructure = configureTree("DoubleNulls");
    final SliceRootNode root = (SliceRootNode)treeStructure.getRootElement();
    Map<SliceNode, SliceNullnessAnalyzer.NullAnalysisResult> map = SliceNullnessAnalyzer.createMap();
    SliceNullnessAnalyzer.NullAnalysisResult leaves = SliceNullnessAnalyzer.calcNullableLeaves(root, treeStructure, map);

    SliceRootNode newRoot = SliceNullnessAnalyzer.createNewTree(leaves, root, map);
    checkStructure(newRoot,
        "Null Values\n" +
        "  Value: null\n" +
        "    (2: 10) |String| |l|;\n" +
        "      (4: 9) |l| |=| |null|;\n" +
        "      (7: 9) |l| |=| |null|;\n" +
        ""
                   );
  }

  public void testGroupByLeavesWithLists() throws Exception {
    SliceTreeStructure treeStructure = configureTree(getTestName(false));
    final SliceRootNode root = (SliceRootNode)treeStructure.getRootElement();
    Map<SliceNode, Collection<PsiElement>> map = SliceLeafAnalyzer.createMap();
    Collection<PsiElement> leaves = SliceLeafAnalyzer.calcLeafExpressions(root, treeStructure, map);
    assertEquals(2, leaves.size());
    Set<String> names = ContainerUtil.map2Set(leaves, new Function<PsiElement, String>() {
      @Override
      public String fun(PsiElement element) {
        return element.getText();
      }
    });
    assertEquals(ContainerUtil.newHashSet("\"uuu\"", "\"xxx\""), names);
  }

  public void testCollectionTrack() throws Exception {
    Set<String> names = groupByLeaves();
    assertEquals(3, names.size());
    assertEquals(ContainerUtil.newHashSet("\"uuu\"", "\"x\"", "\"y\""), names);
  }

  private Set<String> groupByLeaves() throws Exception {
    SliceTreeStructure treeStructure = configureTree(getTestName(false));
    final SliceRootNode root = (SliceRootNode)treeStructure.getRootElement();
    Map<SliceNode, Collection<PsiElement>> map = SliceLeafAnalyzer.createMap();
    Collection<PsiElement> leaves = SliceLeafAnalyzer.calcLeafExpressions(root, treeStructure, map);
    return ContainerUtil.map2Set(leaves, new Function<PsiElement, String>() {
      @Override
      public String fun(PsiElement element) {
        return element.getText();
      }
    });
  }

  public void testArrayCopyTrack() throws Exception {
    Set<String> names = groupByLeaves();
    assertOrderedEquals(Collections.singletonList("\"x\""), assertOneElement(names));
  }
  public void testMapValuesTrack() throws Exception {
    Set<String> names = groupByLeaves();
    assertOrderedEquals(Collections.singletonList("\"y\""), assertOneElement(names));
  }
  public void testMapKeysTrack() throws Exception {
    Set<String> names = groupByLeaves();
    assertOrderedEquals(Collections.singletonList("\"x\""), assertOneElement(names));
  }
}
