package com.intellij.codeInsight.slice;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase;
import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.impl.ToolWindowHeadlessManagerImpl;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.slicer.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;

import java.util.*;

/**
 * @author cdr
 */
public class SliceTreeTest extends LightDaemonAnalyzerTestCase {
  private LanguageLevel myOldLanguageLevel;

  protected void setUp() throws Exception {
    super.setUp();
    myOldLanguageLevel = LanguageLevelProjectExtension.getInstance(getJavaFacade().getProject()).getLanguageLevel();
    LanguageLevelProjectExtension.getInstance(getJavaFacade().getProject()).setLanguageLevel(LanguageLevel.JDK_1_5);
  }

  @Override protected Sdk getProjectJDK() {
    return JavaSdkImpl.getMockJdk15("java 1.5");
  }

  protected void tearDown() throws Exception {
    LanguageLevelProjectExtension.getInstance(getJavaFacade().getProject()).setLanguageLevel(myOldLanguageLevel);
    super.tearDown();
  }

  private SliceTreeStructure configureTree(@NonNls final String name) throws Exception {
    configureByFile("/codeInsight/slice/backward/"+ name +".java");
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    PsiElement element = new SliceHandler(true).getExpressionAtCaret(getEditor(), getFile());
    assertNotNull(element);
    Collection<HighlightInfo> errors = DaemonAnalyzerTestCase.filter(doHighlighting(), HighlightSeverity.ERROR);
    assertEmpty(errors);

    SliceAnalysisParams params = new SliceAnalysisParams();
    params.scope = new AnalysisScope(getProject());
    params.dataFlowToThis = true;

    SliceUsage usage = SliceManager.createRootUsage(element, params);


    SlicePanel panel = new SlicePanel(getProject(), true, new SliceRootNode(getProject(), new DuplicateMap(), usage), false, ToolWindowHeadlessManagerImpl.HEADLESS_WINDOW) {
      protected void close() {
      }

      public boolean isAutoScroll() {
        return false;
      }

      public void setAutoScroll(boolean autoScroll) {
      }

      public boolean isPreview() {
        return false;
      }

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

    for (int i = 0; i < nodes.size()-1; i++) {
      SliceNode node = nodes.get(i);
      assertNull(node.getDuplicate());
    }
    SliceNode last = nodes.get(nodes.size() - 1);
    assertNotNull(last.getDuplicate());

    type("   xx");
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    backspace();
    backspace();
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();

    nodes.clear();
    expandNodesTo(root, nodes);
    for (int i = 0; i < nodes.size()-1; i++) {
      SliceNode node = nodes.get(i);
      assertNull(node.getDuplicate());
    }
    assertNotNull(last.getDuplicate());
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
    assertTrue(child.getValue().getElement() instanceof PsiReferenceExpression);

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
                            "    (6, 12) |String| |l|;\n" +
                            "      (52, 13) |l| |=| |d|;\n" +
                            "        (15, 13) |set|(|o|)|;\n" +
                            "  Value: nu()\n" +
                            "    (6, 12) |String| |l|;\n" +
                            "      (52, 13) |l| |=| |d|;\n" +
                            "        (29, 13) |set|(|nu|(|)|)|;\n" +
                            "  Value: t\n" +
                            "    (6, 12) |String| |l|;\n" +
                            "      (52, 13) |l| |=| |d|;\n" +
                            "        (46, 15) |x|.|set|(|t|)|;\n" +
                            "NotNull Values\n" +
                            "  Value: \"\"\n" +
                            "    (6, 12) |String| |l|;\n" +
                            "      (52, 13) |l| |=| |d|;\n" +
                            "        (19, 13) |set|(|CON|)|;\n" +
                            "          (5, 39) |private| |final| |static| |String| |CON| |=| |\"\"|;\n" +
                            "  Value: \"xxx\"\n" +
                            "    (6, 12) |String| |l|;\n" +
                            "      (52, 13) |l| |=| |d|;\n" +
                            "        (10, 13) |set|(|\"xxx\"|)|;\n" +
                            "  Value: new String()\n" +
                            "    (6, 12) |String| |l|;\n" +
                            "      (52, 13) |l| |=| |d|;\n" +
                            "        (17, 13) |set|(|new| |String|(|)|)|;\n" +
                            "  Value: nn()\n" +
                            "    (6, 12) |String| |l|;\n" +
                            "      (52, 13) |l| |=| |d|;\n" +
                            "        (18, 13) |set|(|nn|(|)|)|;\n" +
                            "  Value: nn\n" +
                            "    (6, 12) |String| |l|;\n" +
                            "      (52, 13) |l| |=| |d|;\n" +
                            "        (21, 13) |set|(|nn|)|;\n" +
                            "  Value: g\n" +
                            "    (6, 12) |String| |l|;\n" +
                            "      (52, 13) |l| |=| |d|;\n" +
                            "        (27, 13) |set|(|g|)|;\n" +
                            "  Value: \"null\"\n" +
                            "    (6, 12) |String| |l|;\n" +
                            "      (52, 13) |l| |=| |d|;\n" +
                            "        (48, 15) |x|.|set|(|t| |==| |null| |?| |\"null\"| |:| |t|)|;\n" +
                            "          (48, 27) |x|.|set|(|t| |==| |null| |?| |\"null\"| |:| |t|)|;\n" +
                            "  Value: t\n" +
                            "    (6, 12) |String| |l|;\n" +
                            "      (52, 13) |l| |=| |d|;\n" +
                            "        (48, 15) |x|.|set|(|t| |==| |null| |?| |\"null\"| |:| |t|)|;\n" +
                            "          (48, 36) |x|.|set|(|t| |==| |null| |?| |\"null\"| |:| |t|)|;\n" +
                            "  Value: d\n" +
                            "    (6, 12) |String| |l|;\n" +
                            "      (55, 13) |l| |=| |d|;\n" +
                            "Other Values\n" +
                            "  Value: g\n" +
                            "    (6, 12) |String| |l|;\n" +
                            "      (52, 13) |l| |=| |d|;\n" +
                            "        (11, 13) |set|(|g|)|;\n" +
                            "        (24, 13) |set|(|other|)|;\n" +
                            "          (23, 24) |String| |other| |=| |g| |==| |\"\"| |?| |CON| |:| |g|;\n" +
                            "            (23, 40) |String| |other| |=| |g| |==| |\"\"| |?| |CON| |:| |g|;\n" +
                            "  Value: d\n" +
                            "    (6, 12) |String| |l|;\n" +
                            "      (52, 13) |l| |=| |d|;\n" +
                            "        (30, 13) |set|(|hz|(|)|)|;\n" +
                            "          (42, 16) |return| |d|;\n" +
                            "");
  }

  private static void checkStructure(final SliceNode root, @NonNls String dataExpected) {
    List<SliceNode> actualNodes = new ArrayList<SliceNode>((Collection<? extends SliceNode>)root.getChildren());
    Collections.sort(actualNodes, SliceTreeBuilder.SLICE_NODE_COMPARATOR);

    Object[] actualStrings = ContainerUtil.map2Array(actualNodes, new Function<SliceNode, Object>() {
      public Object fun(SliceNode node) {
        return node.toString();
      }
    });

    String[] childrenExpected = dataExpected.length() == 0 ? ArrayUtil.EMPTY_STRING_ARRAY : dataExpected.split("\n");
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
    assertEquals(actualNodes.size(), iactual);
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
        "    (2, 10) |String| |l|;\n" +
        "      (4, 9) |l| |=| |null|;\n" +
        "      (7, 9) |l| |=| |null|;\n" +
        ""
                   );
  }
}
