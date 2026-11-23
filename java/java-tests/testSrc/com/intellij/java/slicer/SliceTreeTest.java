// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.slicer;

import com.intellij.JavaTestUtil;
import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiLiteral;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.slicer.DuplicateMap;
import com.intellij.slicer.JavaSliceNullnessAnalyzer;
import com.intellij.slicer.JavaSlicerAnalysisUtil;
import com.intellij.slicer.LanguageSlicing;
import com.intellij.slicer.SliceAnalysisParams;
import com.intellij.slicer.SliceHandler;
import com.intellij.slicer.SliceLeafAnalyzer;
import com.intellij.slicer.SliceLeafValueRootNode;
import com.intellij.slicer.SliceNode;
import com.intellij.slicer.SliceNullnessAnalyzerBase;
import com.intellij.slicer.SlicePanel;
import com.intellij.slicer.SliceRootNode;
import com.intellij.slicer.SliceTreeBuilder;
import com.intellij.slicer.SliceTreeStructure;
import com.intellij.slicer.SliceUsage;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.toolWindow.ToolWindowHeadlessManagerImpl;
import com.intellij.util.FontUtil;
import com.intellij.util.containers.ContainerUtil;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import one.util.streamex.EntryStream;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NonNls;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class SliceTreeTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_1_7_ANNOTATED);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/codeInsight/slice/backward/");
  }

  private SliceTreeStructure configureTree(@NonNls String name) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(name + ".java");
    return EdtTestUtil.runInEdtAndGet(() -> {
      SliceRootNode root = createRoot(myFixture);
      ToolWindowHeadlessManagerImpl.MockToolWindow toolWindow = new ToolWindowHeadlessManagerImpl.MockToolWindow(myFixture.getProject());
      SlicePanel panel = new SlicePanel(myFixture.getProject(), true, root, false, toolWindow) {
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
      Disposer.register(myFixture.getProject(), panel);
      return panel.getBuilder().getTreeStructure();
    });
  }

  private static SliceRootNode createRoot(LightJavaCodeInsightTestFixture fixture) {
    PsiDocumentManager.getInstance(fixture.getProject()).commitAllDocuments();
    PsiElement element = SliceHandler.create(true).getExpressionAtCaret(fixture.getEditor(), fixture.getFile());
    assertNotNull(element);
    Collection<HighlightInfo> errors = fixture.doHighlighting(HighlightSeverity.ERROR);
    assertThat(errors).isNullOrEmpty();

    SliceAnalysisParams params = new SliceAnalysisParams();
    params.scope = new AnalysisScope(fixture.getProject());
    params.dataFlowToThis = true;

    SliceUsage usage = LanguageSlicing.getProvider(element).createRootUsage(element, params);
    return new SliceRootNode(fixture.getProject(), new DuplicateMap(), usage);
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

  @Test
  public void testTypingDoesNotInterfereWithDuplicates() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile("DupSlice.java");
    EdtTestUtil.runInEdtAndWait(() -> {
      SliceRootNode root = createRoot(myFixture);
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
      assertFalse(hasDups.isEmpty(), "The slice must contain duplicates before editing");

      Document document = myFixture.getEditor().getDocument();
      String originalText = document.getText();
      myFixture.type("   xx");
      PsiDocumentManager.getInstance(myFixture.getProject()).commitAllDocuments();
      backspace();
      backspace();
      PsiDocumentManager.getInstance(myFixture.getProject()).commitAllDocuments();
      backspace();
      backspace();
      PsiDocumentManager.getInstance(myFixture.getProject()).commitAllDocuments();
      backspace();
      PsiDocumentManager.getInstance(myFixture.getProject()).commitAllDocuments();
      assertEquals(originalText, document.getText());

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
    });
  }

  private void backspace() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_BACKSPACE);
  }

  @Test
  public void testLeafExpressionsAreEmptyInCaseOfInfinitelyExpandingTreeWithDuplicateNodes() {
    SliceTreeStructure treeStructure = configureTree("Tuple");
    EdtTestUtil.runInEdtAndWait(() -> {
      SliceNode root = treeStructure.getRootElement();
      SliceLeafAnalyzer analyzer = JavaSlicerAnalysisUtil.createLeafAnalyzer();
      Collection<PsiElement> leaves = analyzer.calcLeafExpressions(root, treeStructure, analyzer.createMap());
      assertNotNull(leaves);
      assertThat(leaves).isNullOrEmpty();
    });
  }

  @Test
  public void testLeafExpressionsSimple() {
    SliceTreeStructure treeStructure = configureTree("DupSlice");
    EdtTestUtil.runInEdtAndWait(() -> {
      SliceNode root = treeStructure.getRootElement();
      SliceLeafAnalyzer analyzer = JavaSlicerAnalysisUtil.createLeafAnalyzer();
      Collection<PsiElement> leaves = analyzer.calcLeafExpressions(root, treeStructure, analyzer.createMap());
      assertNotNull(leaves);
      PsiElement element = assertThat(leaves).singleElement().actual();
      assertTrue(element instanceof PsiLiteralExpression);
      assertEquals(1111111111, ((PsiLiteral)element).getValue());
    });
  }

  @Test
  public void testLeafExpressionsMoreComplex() {
    SliceTreeStructure treeStructure = configureTree("Duplicate");
    EdtTestUtil.runInEdtAndWait(() -> {
      SliceNode root = treeStructure.getRootElement();
      SliceLeafAnalyzer analyzer = JavaSlicerAnalysisUtil.createLeafAnalyzer();
      Map<SliceNode, Collection<PsiElement>> map = analyzer.createMap();
      Collection<PsiElement> leaves = analyzer.calcLeafExpressions(root, treeStructure, map);
      assertNotNull(leaves);
      List<PsiElement> list = new ArrayList<>(leaves);
      String message = ContainerUtil.map(list, element -> element.getClass() + ": '" + element.getText() + "' (" + JavaSlicerAnalysisUtil.LEAF_ELEMENT_EQUALITY.hashCode(element) + ") ").toString();
      assertEquals(2, leaves.size(), map.entrySet()+"\n"+message);
      list.sort(Comparator.comparing(PsiElement::getText));
      assertTrue(list.get(0) instanceof PsiLiteralExpression);
      assertEquals(false, ((PsiLiteral)list.get(0)).getValue());
      assertTrue(list.get(1) instanceof PsiLiteralExpression);
      assertEquals(true, ((PsiLiteral)list.get(1)).getValue());
    });
  }

  @Test
  @SuppressWarnings("ConstantConditions")
  public void testGroupByValuesCorrectLeaves() {
    SliceTreeStructure treeStructure = configureTree("DuplicateLeaves");
    EdtTestUtil.runInEdtAndWait(() -> {
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
      child = assertThat(children).singleElement().actual();
      assertTrue(child.getValue().getElement() instanceof PsiReferenceExpression);

      children = child.getChildren();
      child = assertThat(children).singleElement().actual();
      assertTrue(child.getValue().getElement() instanceof PsiParameter);

      children = child.getChildren();
      child = assertThat(children).singleElement().actual();
      assertTrue(child.getValue().getElement() instanceof PsiReferenceExpression);

      children = child.getChildren();
      child = assertThat(children).singleElement().actual();
      assertTrue(child.getValue().getElement() instanceof PsiParameter);

      children = child.getChildren();
      child = assertThat(children).singleElement().actual();
      assertTrue(child.getValue().getElement() instanceof PsiLiteralExpression);
      assertEquals(child.getValue().getElement(), leaf);
    });
  }

  @Test
  public void testNullness() {
    SliceTreeStructure treeStructure = configureTree("Nulls");
    EdtTestUtil.runInEdtAndWait(() -> {
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
    });
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

  @Test
  public void testDoubleNullness() {
    SliceTreeStructure treeStructure = configureTree("DoubleNulls");
    EdtTestUtil.runInEdtAndWait(() -> {
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
    });
  }

  @Test
  public void testGroupByLeavesWithLists() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    SliceTreeStructure treeStructure = configureTree(myFixture.getTestName(false));
    EdtTestUtil.runInEdtAndWait(() -> {
      SliceRootNode root = treeStructure.getRootElement();
      SliceLeafAnalyzer analyzer = JavaSlicerAnalysisUtil.createLeafAnalyzer();
      Map<SliceNode, Collection<PsiElement>> map = analyzer.createMap();
      Collection<PsiElement> leaves = analyzer.calcLeafExpressions(root, treeStructure, map);
      assertEquals(2, leaves.size());
      Set<String> names = ContainerUtil.map2Set(leaves, PsiElement::getText);
      assertEquals(ContainerUtil.newHashSet("\"uuu\"", "\"xxx\""), names);
    });
  }

  @Test
  public void testCollectionTrack() {
    Set<String> names = groupByLeaves();
    assertEquals(3, names.size());
    assertEquals(ContainerUtil.newHashSet("\"uuu\"", "\"x\"", "\"y\""), names);
  }

  private Set<String> groupByLeaves() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    SliceTreeStructure treeStructure = configureTree(myFixture.getTestName(false));
    return EdtTestUtil.runInEdtAndGet(() -> {
      SliceRootNode root = treeStructure.getRootElement();
      SliceLeafAnalyzer analyzer = JavaSlicerAnalysisUtil.createLeafAnalyzer();
      Map<SliceNode, Collection<PsiElement>> map = analyzer.createMap();
      Collection<PsiElement> leaves = analyzer.calcLeafExpressions(root, treeStructure, map);
      return ContainerUtil.map2Set(leaves, PsiElement::getText);
    });
  }

  @Test
  public void testArrayCopyTrack() {
    Set<String> names = groupByLeaves();
    assertEquals("\"x\"", assertThat(names).singleElement().actual());
  }

  @Test
  public void testMapValuesTrack() {
    Set<String> names = groupByLeaves();
    assertEquals("\"y\"", assertThat(names).singleElement().actual());
  }

  @Test
  public void testMapKeysTrack() {
    Set<String> names = groupByLeaves();
    assertEquals("\"x\"", assertThat(names).singleElement().actual());
  }
}
