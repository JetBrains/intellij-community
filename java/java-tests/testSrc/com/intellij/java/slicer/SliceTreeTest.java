// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.slicer;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.slicer.*;
import com.intellij.toolWindow.ToolWindowHeadlessManagerImpl;
import com.intellij.util.containers.ContainerUtil;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import one.util.streamex.EntryStream;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NonNls;

import java.util.*;

public class SliceTreeTest extends SliceTestCase {
  private SliceTreeStructure configureTree(@NonNls String name) throws Exception {
    configureByFile("/codeInsight/slice/backward/"+ name +".java");
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    PsiElement element = SliceHandler.create(true).getExpressionAtCaret(getEditor(), getFile());
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
    return panel.getBuilder().getTreeStructure();
  }

  private static void expandNodesTo(SliceNode node, List<? super SliceNode> to) {
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
    SliceNode root = treeStructure.getRootElement();
    List<SliceNode> nodes = new ArrayList<>();
    expandNodesTo(root, nodes);

    IntList hasDups = new IntArrayList();
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
        hasDups.removeInt(i);
        assertTrue(element instanceof PsiParameter && "i".equals(((PsiParameter)element).getName()) || element instanceof PsiLiteralExpression);
      }
    }
    assertTrue(hasDups.isEmpty());
  }

  public void testLeafExpressionsAreEmptyInCaseOfInfinitelyExpandingTreeWithDuplicateNodes() throws Exception {
    SliceTreeStructure treeStructure = configureTree("Tuple");
    SliceNode root = treeStructure.getRootElement();
    SliceLeafAnalyzer analyzer = JavaSlicerAnalysisUtil.createLeafAnalyzer();
    Collection<PsiElement> leaves = analyzer.calcLeafExpressions(root, treeStructure, analyzer.createMap());
    assertNotNull(leaves);
    assertEmpty(leaves);
  }

  public void testLeafExpressionsSimple() throws Exception {
    SliceTreeStructure treeStructure = configureTree("DupSlice");
    SliceNode root = treeStructure.getRootElement();
    SliceLeafAnalyzer analyzer = JavaSlicerAnalysisUtil.createLeafAnalyzer();
    Collection<PsiElement> leaves = analyzer.calcLeafExpressions(root, treeStructure, analyzer.createMap());
    assertNotNull(leaves);
    PsiElement element = assertOneElement(leaves);
    assertTrue(element instanceof PsiLiteralExpression);
    assertEquals(1111111111, ((PsiLiteral)element).getValue());
  }

  public void testLeafExpressionsMoreComplex() throws Exception {
    SliceTreeStructure treeStructure = configureTree("Duplicate");
    SliceNode root = treeStructure.getRootElement();
    SliceLeafAnalyzer analyzer = JavaSlicerAnalysisUtil.createLeafAnalyzer();
    Map<SliceNode, Collection<PsiElement>> map = analyzer.createMap();
    Collection<PsiElement> leaves = analyzer.calcLeafExpressions(root, treeStructure, map);
    assertNotNull(leaves);
    List<PsiElement> list = new ArrayList<>(leaves);
    String message = ContainerUtil.map(list, element -> element.getClass() + ": '" + element.getText() + "' (" + JavaSlicerAnalysisUtil.LEAF_ELEMENT_EQUALITY.hashCode(element) + ") ").toString();
    assertEquals(map.entrySet()+"\n"+message, 2, leaves.size());
    list.sort(Comparator.comparing(PsiElement::getText));
    assertTrue(list.get(0) instanceof PsiLiteralExpression);
    assertEquals(false, ((PsiLiteral)list.get(0)).getValue());
    assertTrue(list.get(1) instanceof PsiLiteralExpression);
    assertEquals(true, ((PsiLiteral)list.get(1)).getValue());
  }

  @SuppressWarnings("ConstantConditions")
  public void testGroupByValuesCorrectLeaves() throws Exception {
    SliceTreeStructure treeStructure = configureTree("DuplicateLeaves");
    SliceRootNode root = treeStructure.getRootElement();
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
    SliceRootNode root = treeStructure.getRootElement();
    Map<SliceNode, JavaSliceNullnessAnalyzer.NullAnalysisResult> map = SliceNullnessAnalyzerBase.createMap();
    JavaSliceNullnessAnalyzer analyzer = new JavaSliceNullnessAnalyzer();
    JavaSliceNullnessAnalyzer.NullAnalysisResult leaves = analyzer.calcNullableLeaves(root, treeStructure, map);

    SliceRootNode newRoot = analyzer.createNewTree(leaves, root, map);

    checkStructure(newRoot, """
      Null Values
        Value: o
          6|String |l;
            52|l = |d|;
              51|void| set(String |d|) {
                15|set(|o|);
        Value: other
          6|String |l;
            52|l = |d|;
              51|void| set(String |d|) {
                24|set(|other|);
        Value: nu()
          6|String |l;
            52|l = |d|;
              51|void| set(String |d|) {
                29|set(|nu()|);
        Value: t
          6|String |l;
            52|l = |d|;
              51|void| set(String |d|) {
                46|x.set(|t|);
      NotNull Values
        Value: "xxx"
          6|String |l;
            52|l = |d|;
              51|void| set(String |d|) {
                10|set(|"xxx"|);
        Value: new String()
          6|String |l;
            52|l = |d|;
              51|void| set(String |d|) {
                17|set(|new| String()|);
        Value: nn()
          6|String |l;
            52|l = |d|;
              51|void| set(String |d|) {
                18|set(|nn()|);
        Value: CON
          6|String |l;
            52|l = |d|;
              51|void| set(String |d|) {
                19|set(|CON|);
        Value: nn
          6|String |l;
            52|l = |d|;
              51|void| set(String |d|) {
                21|set(|nn|);
        Value: g
          6|String |l;
            52|l = |d|;
              51|void| set(String |d|) {
                27|set(|g|);
        Value: t == null ? "null" : t
          6|String |l;
            52|l = |d|;
              51|void| set(String |d|) {
                48|x.set(|t == |null| ? |"null"| : t|);
        Value: d
          6|String |l;
            55|l = |d|;
      Other Values
        Value: private String d;
          6|String |l;
            52|l = |d|;
              51|void| set(String |d|) {
                30|set(|hz()|);
                  42|return| |d|;
                    7|private| String |d;
        Value: String g
          6|String |l;
            52|l = |d|;
              51|void| set(String |d|) {
                11|set(|g|);
                  9|public| X(String |g|) {
      """);
  }

  private static void checkStructure(SliceNode root, @NonNls String dataExpected) {
    String dataActual =
      EntryStream.ofTree(root, (depth, node) -> StreamEx.of(node.getChildren()).sorted(SliceTreeBuilder.SLICE_NODE_COMPARATOR))
        .skip(1)
        .mapKeyValue((depth, node) -> StringUtil.repeat("  ", depth - 1) + node + "\n")
        .joining();
    assertEquals(dataExpected, dataActual);
  }

  public void testDoubleNullness() throws Exception {
    SliceTreeStructure treeStructure = configureTree("DoubleNulls");
    SliceRootNode root = treeStructure.getRootElement();
    Map<SliceNode, JavaSliceNullnessAnalyzer.NullAnalysisResult> map = SliceNullnessAnalyzerBase.createMap();
    JavaSliceNullnessAnalyzer analyzer = new JavaSliceNullnessAnalyzer();

    JavaSliceNullnessAnalyzer.NullAnalysisResult leaves = analyzer.calcNullableLeaves(root, treeStructure, map);

    SliceRootNode newRoot = analyzer.createNewTree(leaves, root, map);
    checkStructure(newRoot, """
      Null Values
        Value: null
          2|String |l;
            4|l = |null|;
            7|l = |null|;
      """);
  }

  public void testGroupByLeavesWithLists() throws Exception {
    SliceTreeStructure treeStructure = configureTree(getTestName(false));
    SliceRootNode root = treeStructure.getRootElement();
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
    SliceRootNode root = treeStructure.getRootElement();
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
