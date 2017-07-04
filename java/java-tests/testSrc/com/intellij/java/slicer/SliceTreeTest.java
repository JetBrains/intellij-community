/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.slicer;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.impl.ToolWindowHeadlessManagerImpl;
import com.intellij.psi.*;
import com.intellij.slicer.*;
import com.intellij.util.ArrayUtil;
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

    SliceUsage usage = LanguageSlicing.getProvider(element).createRootUsage(element, params);


    ToolWindowHeadlessManagerImpl.MockToolWindow toolWindow = new ToolWindowHeadlessManagerImpl.MockToolWindow(myProject);
    SlicePanel panel = new SlicePanel(getProject(), true, new SliceRootNode(getProject(), new DuplicateMap(), usage), false, toolWindow) {
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
    Collection<SliceNode> nodes = node.getChildren();
    for (SliceNode child : nodes) {
      expandNodesTo(child, to);
    }
  }

  public void testTypingDoesNotInterfereWithDuplicates() throws Exception {
    SliceTreeStructure treeStructure = configureTree("DupSlice");
    SliceNode root = (SliceNode)treeStructure.getRootElement();
    List<SliceNode> nodes = new ArrayList<>();
    expandNodesTo(root, nodes);

    TIntArrayList hasDups = new TIntArrayList();
    for (SliceNode node : nodes) {
      if (node.getDuplicate() != null) {
        PsiElement element = node.getValue().getElement();
        hasDups.add(element.getTextRange().getStartOffset());
        assertTrue(element instanceof PsiParameter && "i".equals(((PsiParameter)element).getName()) || element instanceof PsiLiteralExpression);
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
        assertTrue(element instanceof PsiParameter && "i".equals(((PsiParameter)element).getName()) || element instanceof PsiLiteralExpression);
      }
    }
    assertTrue(hasDups.isEmpty());
  }

  public void testLeafExpressionsAreEmptyInCaseOfInfinitelyExpandingTreeWithDuplicateNodes() throws Exception {
    SliceTreeStructure treeStructure = configureTree("Tuple");
    SliceNode root = (SliceNode)treeStructure.getRootElement();
    SliceLeafAnalyzer analyzer = JavaSlicerAnalysisUtil.createLeafAnalyzer();
    Collection<PsiElement> leaves = analyzer.calcLeafExpressions(root, treeStructure, analyzer.createMap());
    assertNotNull(leaves);
    assertEmpty(leaves);
  }

  public void testLeafExpressionsSimple() throws Exception {
    SliceTreeStructure treeStructure = configureTree("DupSlice");
    SliceNode root = (SliceNode)treeStructure.getRootElement();
    SliceLeafAnalyzer analyzer = JavaSlicerAnalysisUtil.createLeafAnalyzer();
    Collection<PsiElement> leaves = analyzer.calcLeafExpressions(root, treeStructure, analyzer.createMap());
    assertNotNull(leaves);
    PsiElement element = assertOneElement(leaves);
    assertTrue(element instanceof PsiLiteralExpression);
    assertEquals(1111111111, ((PsiLiteral)element).getValue());
  }
  public void testLeafExpressionsMoreComplex() throws Exception {
    SliceTreeStructure treeStructure = configureTree("Duplicate");
    SliceNode root = (SliceNode)treeStructure.getRootElement();
    SliceLeafAnalyzer analyzer = JavaSlicerAnalysisUtil.createLeafAnalyzer();
    Map<SliceNode, Collection<PsiElement>> map = analyzer.createMap();
    Collection<PsiElement> leaves = analyzer.calcLeafExpressions(root, treeStructure, map);
    assertNotNull(leaves);
    List<PsiElement> list = new ArrayList<>(leaves);
    String message = ContainerUtil.map(list, element -> element.getClass() + ": '" + element.getText() + "' (" + JavaSlicerAnalysisUtil.LEAF_ELEMENT_EQUALITY.computeHashCode(element) + ") ").toString();
    assertEquals(map.entrySet()+"\n"+message, 2, leaves.size());
    Collections.sort(list, (o1, o2) -> o1.getText().compareTo(o2.getText()));
    assertTrue(list.get(0) instanceof PsiLiteralExpression);
    assertEquals(false, ((PsiLiteral)list.get(0)).getValue());
    assertTrue(list.get(1) instanceof PsiLiteralExpression);
    assertEquals(true, ((PsiLiteral)list.get(1)).getValue());
  }

  @SuppressWarnings("ConstantConditions")
  public void testGroupByValuesCorrectLeaves() throws Exception {
    SliceTreeStructure treeStructure = configureTree("DuplicateLeaves");
    SliceRootNode root = (SliceRootNode)treeStructure.getRootElement();
    SliceLeafAnalyzer analyzer = JavaSlicerAnalysisUtil.createLeafAnalyzer();
    Map<SliceNode, Collection<PsiElement>> map = analyzer.createMap();
    Collection<PsiElement> leaves = analyzer.calcLeafExpressions(root, treeStructure, map);
    assertNotNull(leaves);
    assertEquals(1, leaves.size());
    PsiElement leaf = leaves.iterator().next();
    assertTrue(leaf instanceof PsiLiteralExpression);
    assertEquals("\"oo\"", leaf.getText());

    SliceRootNode newRoot = analyzer.createTreeGroupedByValues(leaves, root, map);
    Collection<SliceNode> children = newRoot.getChildren();
    assertEquals(1, children.size());
    SliceNode child = children.iterator().next();
    assertTrue(child instanceof SliceLeafValueRootNode);

    children = child.getChildren();
    assertEquals(1, children.size());
    child = children.iterator().next();
    assertTrue(child.getValue().getElement() instanceof PsiField);

    children = child.getChildren();
    child = assertOneElement(children);
    assertTrue(child.getValue().getElement() instanceof PsiReferenceExpression);

    children = child.getChildren();
    child = assertOneElement(children);
    assertTrue(child.getValue().getElement() instanceof PsiParameter);

    children = child.getChildren();
    child = assertOneElement(children);
    assertTrue(child.getValue().getElement() instanceof PsiReferenceExpression);

    children = child.getChildren();
    child = assertOneElement(children);
    assertTrue(child.getValue().getElement() instanceof PsiParameter);

    children = child.getChildren();
    child = assertOneElement(children);
    assertTrue(child.getValue().getElement() instanceof PsiLiteralExpression);
    assertEquals(child.getValue().getElement(), leaf);
  }

  public void testNullness() throws Exception {
    SliceTreeStructure treeStructure = configureTree("Nulls");
    final SliceRootNode root = (SliceRootNode)treeStructure.getRootElement();
    Map<SliceNode, JavaSliceNullnessAnalyzer.NullAnalysisResult> map = JavaSliceNullnessAnalyzer.createMap();
    JavaSliceNullnessAnalyzer analyzer = new JavaSliceNullnessAnalyzer();
    JavaSliceNullnessAnalyzer.NullAnalysisResult leaves = analyzer.calcNullableLeaves(root, treeStructure, map);

    SliceRootNode newRoot = analyzer.createNewTree(leaves, root, map);

    checkStructure(newRoot, "Null Values\n" +
                            "  Value: o\n" +
                            "    6|String| |l|;\n" +
                            "      52|l| |=| |d|;\n" +
                            "        51|void| |set|(|String| |d|)| |{\n" +
                            "          15|set|(|o|)|;\n" +
                            "  Value: nu()\n" +
                            "    6|String| |l|;\n" +
                            "      52|l| |=| |d|;\n" +
                            "        51|void| |set|(|String| |d|)| |{\n" +
                            "          29|set|(|nu|(|)|)|;\n" +
                            "  Value: t\n" +
                            "    6|String| |l|;\n" +
                            "      52|l| |=| |d|;\n" +
                            "        51|void| |set|(|String| |d|)| |{\n" +
                            "          46|x|.|set|(|t|)|;\n" +
                            "NotNull Values\n" +
                            "  Value: \"\"\n" +
                            "    6|String| |l|;\n" +
                            "      52|l| |=| |d|;\n" +
                            "        51|void| |set|(|String| |d|)| |{\n" +
                            "          19|set|(|CON|)|;\n" +
                            "            5|private| |final| |static| |String| |CON| |=| |\"\"|;\n" +
                            "  Value: \"xxx\"\n" +
                            "    6|String| |l|;\n" +
                            "      52|l| |=| |d|;\n" +
                            "        51|void| |set|(|String| |d|)| |{\n" +
                            "          10|set|(|\"xxx\"|)|;\n" +
                            "  Value: new String()\n" +
                            "    6|String| |l|;\n" +
                            "      52|l| |=| |d|;\n" +
                            "        51|void| |set|(|String| |d|)| |{\n" +
                            "          17|set|(|new| |String|(|)|)|;\n" +
                            "  Value: nn()\n" +
                            "    6|String| |l|;\n" +
                            "      52|l| |=| |d|;\n" +
                            "        51|void| |set|(|String| |d|)| |{\n" +
                            "          18|set|(|nn|(|)|)|;\n" +
                            "  Value: nn\n" +
                            "    6|String| |l|;\n" +
                            "      52|l| |=| |d|;\n" +
                            "        51|void| |set|(|String| |d|)| |{\n" +
                            "          21|set|(|nn|)|;\n" +
                            "  Value: g\n" +
                            "    6|String| |l|;\n" +
                            "      52|l| |=| |d|;\n" +
                            "        51|void| |set|(|String| |d|)| |{\n" +
                            "          27|set|(|g|)|;\n" +
                            "  Value: \"null\"\n" +
                            "    6|String| |l|;\n" +
                            "      52|l| |=| |d|;\n" +
                            "        51|void| |set|(|String| |d|)| |{\n" +
                            "          48|x|.|set|(|t| |==| |null| |?| |\"null\"| |:| |t|)|;\n" +
                            "            48|x|.|set|(|t| |==| |null| |?| |\"null\"| |:| |t|)|;\n" +
                            "  Value: t\n" +
                            "    6|String| |l|;\n" +
                            "      52|l| |=| |d|;\n" +
                            "        51|void| |set|(|String| |d|)| |{\n" +
                            "          48|x|.|set|(|t| |==| |null| |?| |\"null\"| |:| |t|)|;\n" +
                            "            48|x|.|set|(|t| |==| |null| |?| |\"null\"| |:| |t|)|;\n" +
                            "  Value: d\n" +
                            "    6|String| |l|;\n" +
                            "      55|l| |=| |d|;\n" +
                            "Other Values\n" +
                            "  Value: private String d;\n" +
                            "    6|String| |l|;\n" +
                            "      52|l| |=| |d|;\n" +
                            "        51|void| |set|(|String| |d|)| |{\n" +
                            "          30|set|(|hz|(|)|)|;\n" +
                            "            42|return| |d|;\n" +
                            "              7|private| |String| |d|;\n" +
                            "  Value: String g\n" +
                            "    6|String| |l|;\n" +
                            "      52|l| |=| |d|;\n" +
                            "        51|void| |set|(|String| |d|)| |{\n" +
                            "          11|set|(|g|)|;\n" +
                            "            9|public| |X|(|String| |g|)| |{\n" +
                            "");
  }

  private static void checkStructure(final SliceNode root, @NonNls String dataExpected) {
    List<SliceNode> actualNodes = new ArrayList<>(root.getChildren());
    Collections.sort(actualNodes, SliceTreeBuilder.SLICE_NODE_COMPARATOR);

    Object[] actualStrings = ContainerUtil.map2Array(actualNodes, SliceNode::toString);

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
    Map<SliceNode, JavaSliceNullnessAnalyzer.NullAnalysisResult> map = JavaSliceNullnessAnalyzer.createMap();
    JavaSliceNullnessAnalyzer analyzer = new JavaSliceNullnessAnalyzer();

    JavaSliceNullnessAnalyzer.NullAnalysisResult leaves = analyzer.calcNullableLeaves(root, treeStructure, map);

    SliceRootNode newRoot = analyzer.createNewTree(leaves, root, map);
    checkStructure(newRoot,
        "Null Values\n" +
        "  Value: null\n" +
        "    2|String| |l|;\n" +
        "      4|l| |=| |null|;\n" +
        "      7|l| |=| |null|;\n" +
        ""
                   );
  }

  public void testGroupByLeavesWithLists() throws Exception {
    SliceTreeStructure treeStructure = configureTree(getTestName(false));
    final SliceRootNode root = (SliceRootNode)treeStructure.getRootElement();
    SliceLeafAnalyzer analyzer = JavaSlicerAnalysisUtil.createLeafAnalyzer();
    Map<SliceNode, Collection<PsiElement>> map = analyzer.createMap();
    Collection<PsiElement> leaves = analyzer.calcLeafExpressions(root, treeStructure, map);
    assertEquals(2, leaves.size());
    Set<String> names = ContainerUtil.map2Set(leaves, PsiElement::getText);
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
    SliceLeafAnalyzer analyzer = JavaSlicerAnalysisUtil.createLeafAnalyzer();
    Map<SliceNode, Collection<PsiElement>> map = analyzer.createMap();
    Collection<PsiElement> leaves = analyzer.calcLeafExpressions(root, treeStructure, map);
    return ContainerUtil.map2Set(leaves, PsiElement::getText);
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
