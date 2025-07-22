// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.slicer;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.slicer.*;
import com.intellij.toolWindow.ToolWindowHeadlessManagerImpl;
import com.intellij.util.FontUtil;
import com.intellij.util.containers.ContainerUtil;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import one.util.streamex.EntryStream;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NonNls;

import java.util.*;

public class SliceTreeTest extends SliceTestCase {
  @Override
  protected String getBasePath() {
    return "/java/java-tests/testData/codeInsight/slice/backward/";
  }

  private SliceTreeStructure configureTree(@NonNls String name) {
    myFixture.configureByFile(name + ".java");
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    PsiElement element = SliceHandler.create(true).getExpressionAtCaret(getEditor(), getFile());
    assertNotNull(element);
    Collection<HighlightInfo> errors = myFixture.doHighlighting(HighlightSeverity.ERROR);
    assertEmpty(errors);

    SliceAnalysisParams params = new SliceAnalysisParams();
    params.scope = new AnalysisScope(getProject());
    params.dataFlowToThis = true;

    SliceUsage usage = LanguageSlicing.getProvider(element).createRootUsage(element, params);


    ToolWindowHeadlessManagerImpl.MockToolWindow toolWindow = new ToolWindowHeadlessManagerImpl.MockToolWindow(getProject());
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

  public void testTypingDoesNotInterfereWithDuplicates() {
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

    myFixture.type("   xx");
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

  private void backspace() {
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_BACKSPACE);
  }

  public void testLeafExpressionsAreEmptyInCaseOfInfinitelyExpandingTreeWithDuplicateNodes() {
    SliceTreeStructure treeStructure = configureTree("Tuple");
    SliceNode root = treeStructure.getRootElement();
    SliceLeafAnalyzer analyzer = JavaSlicerAnalysisUtil.createLeafAnalyzer();
    Collection<PsiElement> leaves = analyzer.calcLeafExpressions(root, treeStructure, analyzer.createMap());
    assertNotNull(leaves);
    assertEmpty(leaves);
  }

  public void testLeafExpressionsSimple() {
    SliceTreeStructure treeStructure = configureTree("DupSlice");
    SliceNode root = treeStructure.getRootElement();
    SliceLeafAnalyzer analyzer = JavaSlicerAnalysisUtil.createLeafAnalyzer();
    Collection<PsiElement> leaves = analyzer.calcLeafExpressions(root, treeStructure, analyzer.createMap());
    assertNotNull(leaves);
    PsiElement element = assertOneElement(leaves);
    assertTrue(element instanceof PsiLiteralExpression);
    assertEquals(1111111111, ((PsiLiteral)element).getValue());
  }

  public void testLeafExpressionsMoreComplex() {
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
  public void testGroupByValuesCorrectLeaves() {
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

  public void testNullness() {
    SliceTreeStructure treeStructure = configureTree("Nulls");
    SliceRootNode root = treeStructure.getRootElement();
    Map<SliceNode, JavaSliceNullnessAnalyzer.NullAnalysisResult> map = SliceNullnessAnalyzerBase.createMap();
    JavaSliceNullnessAnalyzer analyzer = new JavaSliceNullnessAnalyzer();
    JavaSliceNullnessAnalyzer.NullAnalysisResult leaves = analyzer.calcNullableLeaves(root, treeStructure, map);

    SliceRootNode newRoot = analyzer.createNewTree(leaves, root, map);

    checkStructure(newRoot, """
      Null Values
        Value: o
          6| |String |l;| in X
            52| |l = |d|;| in X.set(String)
              51| |void| set(String |d|) {| in X.set(String)
                15| |set(|o|);| in X.X(String) (filter: null)
        Value: other
          6| |String |l;| in X
            52| |l = |d|;| in X.set(String)
              51| |void| set(String |d|) {| in X.set(String)
                24| |set(|other|);| in X.X(String)
        Value: nu()
          6| |String |l;| in X
            52| |l = |d|;| in X.set(String)
              51| |void| set(String |d|) {| in X.set(String)
                29| |set(|nu()|);| in X.X(String)
        Value: t
          6| |String |l;| in X
            52| |l = |d|;| in X.set(String)
              51| |void| set(String |d|) {| in X.set(String)
                46| |x.set(|t|);| in X.fs(String, X)
      NotNull Values
        Value: "xxx"
          6| |String |l;| in X
            52| |l = |d|;| in X.set(String)
              51| |void| set(String |d|) {| in X.set(String)
                10| |set(|"xxx"|);| in X.X(String)
        Value: new String()
          6| |String |l;| in X
            52| |l = |d|;| in X.set(String)
              51| |void| set(String |d|) {| in X.set(String)
                17| |set(|new| String()|);| in X.X(String) (filter: non-null)
        Value: nn()
          6| |String |l;| in X
            52| |l = |d|;| in X.set(String)
              51| |void| set(String |d|) {| in X.set(String)
                18| |set(|nn()|);| in X.X(String) (filter: non-null)
        Value: CON
          6| |String |l;| in X
            52| |l = |d|;| in X.set(String)
              51| |void| set(String |d|) {| in X.set(String)
                19| |set(|CON|);| in X.X(String) (filter: "")
        Value: nn
          6| |String |l;| in X
            52| |l = |d|;| in X.set(String)
              51| |void| set(String |d|) {| in X.set(String)
                21| |set(|nn|);| in X.X(String) (filter: non-null)
        Value: g
          6| |String |l;| in X
            52| |l = |d|;| in X.set(String)
              51| |void| set(String |d|) {| in X.set(String)
                27| |set(|g|);| in X.X(String) (filter: non-null)
        Value: t == null ? "null" : t
          6| |String |l;| in X
            52| |l = |d|;| in X.set(String)
              51| |void| set(String |d|) {| in X.set(String)
                48| |x.set(|t == |null| ? |"null"| : t|);| in X.fs(String, X) (filter: non-null)
        Value: d
          6| |String |l;| in X
            55| |l = |d|;| in X.setFromNN(String) (filter: non-null)
      Other Values
        Value: private String d;
          6| |String |l;| in X
            52| |l = |d|;| in X.set(String)
              51| |void| set(String |d|) {| in X.set(String)
                30| |set(|hz()|);| in X.X(String)
                  42| |return| |d|;| in X.hz()
                    7| |private| String |d;| in X
        Value: String g
          6| |String |l;| in X
            52| |l = |d|;| in X.set(String)
              51| |void| set(String |d|) {| in X.set(String)
                11| |set(|g|);| in X.X(String)
                  9| |public| X(String |g|) {| in X.X(String)
      """);
  }

  private static void checkStructure(SliceNode root, @NonNls String dataExpected) {
    String dataActual =
      EntryStream.ofTree(root, (depth, node) -> StreamEx.of(node.getChildren()).sorted(SliceTreeBuilder.SLICE_NODE_COMPARATOR))
        .skip(1)
        .mapKeyValue((depth, node) -> {
          node.getValue().updateCachedPresentation();
          String s = StringUtil.repeat("  ", depth - 1) + node + "\n";
          return s.replace(FontUtil.thinSpace(), "");
        })
        .joining();
    assertEquals(dataExpected, dataActual);
  }

  public void testDoubleNullness() {
    SliceTreeStructure treeStructure = configureTree("DoubleNulls");
    SliceRootNode root = treeStructure.getRootElement();
    Map<SliceNode, JavaSliceNullnessAnalyzer.NullAnalysisResult> map = SliceNullnessAnalyzerBase.createMap();
    JavaSliceNullnessAnalyzer analyzer = new JavaSliceNullnessAnalyzer();

    JavaSliceNullnessAnalyzer.NullAnalysisResult leaves = analyzer.calcNullableLeaves(root, treeStructure, map);

    SliceRootNode newRoot = analyzer.createNewTree(leaves, root, map);
    checkStructure(newRoot, """
      Null Values
        Value: null
          2| |String |l;| in X
            4| |l = |null|;| in X
            7| |l = |null|;| in X
      """);
  }

  public void testGroupByLeavesWithLists() {
    SliceTreeStructure treeStructure = configureTree(getTestName(false));
    SliceRootNode root = treeStructure.getRootElement();
    SliceLeafAnalyzer analyzer = JavaSlicerAnalysisUtil.createLeafAnalyzer();
    Map<SliceNode, Collection<PsiElement>> map = analyzer.createMap();
    Collection<PsiElement> leaves = analyzer.calcLeafExpressions(root, treeStructure, map);
    assertEquals(2, leaves.size());
    Set<String> names = ContainerUtil.map2Set(leaves, PsiElement::getText);
    assertEquals(ContainerUtil.newHashSet("\"uuu\"", "\"xxx\""), names);
  }

  public void testCollectionTrack() {
    Set<String> names = groupByLeaves();
    assertEquals(3, names.size());
    assertEquals(ContainerUtil.newHashSet("\"uuu\"", "\"x\"", "\"y\""), names);
  }

  private Set<String> groupByLeaves() {
    SliceTreeStructure treeStructure = configureTree(getTestName(false));
    SliceRootNode root = treeStructure.getRootElement();
    SliceLeafAnalyzer analyzer = JavaSlicerAnalysisUtil.createLeafAnalyzer();
    Map<SliceNode, Collection<PsiElement>> map = analyzer.createMap();
    Collection<PsiElement> leaves = analyzer.calcLeafExpressions(root, treeStructure, map);
    return ContainerUtil.map2Set(leaves, PsiElement::getText);
  }

  public void testArrayCopyTrack() {
    Set<String> names = groupByLeaves();
    assertOrderedEquals(Collections.singletonList("\"x\""), assertOneElement(names));
  }

  public void testMapValuesTrack() {
    Set<String> names = groupByLeaves();
    assertOrderedEquals(Collections.singletonList("\"y\""), assertOneElement(names));
  }

  public void testMapKeysTrack() {
    Set<String> names = groupByLeaves();
    assertOrderedEquals(Collections.singletonList("\"x\""), assertOneElement(names));
  }
}
