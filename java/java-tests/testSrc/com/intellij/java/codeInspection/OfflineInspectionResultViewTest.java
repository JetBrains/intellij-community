/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package com.intellij.java.codeInspection;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.actions.ViewOfflineResultsAction;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspectionBase;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionProfileModifiableModel;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.offline.OfflineProblemDescriptor;
import com.intellij.codeInspection.offlineViewer.OfflineViewParseUtil;
import com.intellij.codeInspection.ui.InspectionResultsView;
import com.intellij.codeInspection.ui.InspectionTree;
import com.intellij.codeInspection.ui.InspectionTreeNode;
import com.intellij.codeInspection.ui.ProblemDescriptionNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.TestSourceBasedTestCase;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class OfflineInspectionResultViewTest extends TestSourceBasedTestCase {
  private InspectionResultsView myView;
  private InspectionToolWrapper myUnusedToolWrapper;
  private InspectionToolWrapper myDataFlowToolWrapper;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    InspectionProfileImpl.INIT_INSPECTIONS = true;

    HighlightDisplayKey key = HighlightDisplayKey.find(UnusedDeclarationInspectionBase.SHORT_NAME);
    if (key == null) {
      HighlightDisplayKey.register(UnusedDeclarationInspectionBase.SHORT_NAME);
    }

    final InspectionProfileImpl profile = new InspectionProfileImpl("test") {
      @Override
      public boolean isToolEnabled(@Nullable final HighlightDisplayKey key, PsiElement element) {
        return key != null && Comparing.strEqual(key.toString(), UnusedDeclarationInspectionBase.SHORT_NAME);
      }

      @Override
      @NotNull
      public InspectionToolWrapper[] getInspectionTools(PsiElement element) {
        return new InspectionToolWrapper[]{myUnusedToolWrapper};
      }

      @Override
      @NotNull
      public InspectionProfileModifiableModel getModifiableModel() {
        return new InspectionProfileModifiableModel(this) {
          @Override
          @NotNull
          public InspectionToolWrapper[] getInspectionTools(PsiElement element) {
            return new InspectionToolWrapper[]{myUnusedToolWrapper};
          }

          @Override
          public boolean isToolEnabled(@Nullable HighlightDisplayKey key, PsiElement element) {
            return key != null && Comparing.strEqual(key.toString(), UnusedDeclarationInspectionBase.SHORT_NAME);
          }
        };
      }
    };

    myView = ViewOfflineResultsAction.showOfflineView(getProject(), parse(), profile, "");
    profile.initInspectionTools(myProject);
    myUnusedToolWrapper = profile.getInspectionTool("unused", myProject);
    myDataFlowToolWrapper = profile.getInspectionTool("EqualsWithItself", myProject);

    for (InspectionToolWrapper tool : ContainerUtil.ar(myUnusedToolWrapper, myDataFlowToolWrapper)) {
      tool.initialize(myView.getGlobalInspectionContext());
    }
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      Disposer.dispose(myView);

    }
    finally {
      InspectionProfileImpl.INIT_INSPECTIONS = false;
      myView = null;
      myUnusedToolWrapper = null;
      myDataFlowToolWrapper = null;
      super.tearDown();
    }
  }

  private Map<String, Map<String, Set<OfflineProblemDescriptor>>> parse() throws IOException {
    final String moduleName = getModule().getName();
    final File res = new File(PathManagerEx.getTestDataPath(), getTestPath() + File.separator + "res");
    final File[] files = res.listFiles();
    assert files != null;
    final Map<String, Map<String, Set<OfflineProblemDescriptor>>> map = new HashMap<>();
    for (File file : files) {
      final String problems = FileUtil.loadFile(file);
      final Map<String, Set<OfflineProblemDescriptor>> descriptors = OfflineViewParseUtil.parse(problems);
      for (Set<OfflineProblemDescriptor> problemDescriptors : descriptors.values()) {
        for (OfflineProblemDescriptor descriptor : problemDescriptors) {
          descriptor.setModule(moduleName);
        }
      }
      final String name = file.getName();
      map.put(name.substring(0, name.lastIndexOf('.')), descriptors);
    }
    return map;
  }

  public void testOfflineWithInvalid() throws InterruptedException {
    ApplicationManager.getApplication().runWriteAction(() -> getJavaFacade().findClass("Test2").getContainingFile().delete());
    myView.getGlobalInspectionContext().getUIOptions().SHOW_STRUCTURE = true;
    InspectionTree tree = updateTree();
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
    TreeUtil.expandAll(tree);
    PlatformTestUtil.assertTreeEqual(tree, "-" + getProject() + "\n" +
                                           " -Java\n" +
                                           "  -Declaration redundancy\n" +
                                           "   -unused\n" +
                                           "    -Module: 'testOfflineWithInvalid'\n" +
                                           "     -<default>\n" +
                                           "      -Test\n" +
                                           "       -f()\n" +
                                           "        -D\n" +
                                           "         -b()\n" +
                                           "          -anonymous (Runnable)\n" +
                                           "           -run()\n" +
                                           "            Variable <code>i</code> is never used.\n" +
                                           "          Variable <code>r</code> is never used.\n" +
                                           "       -ff()\n" +
                                           "        Variable <code>a</code> is never used.\n" +
                                           "        Variable <code>d</code> is never used.\n" +
                                           "       -foo()\n" +
                                           "        Variable <code>j</code> is never used.\n" +
                                           "       -main(String[])\n" +
                                           "        Variable <code>test</code> is never used.\n" +
                                           "  -Probable bugs\n" +
                                           "   -EqualsWithItself\n" +
                                           "    -Module: 'testOfflineWithInvalid'\n" +
                                           "     -<default>\n" +
                                           "      -Test\n" +
                                           "       -m()\n" +
                                           "        'equals()' called on itself\n" +
                                           "      -null\n" +
                                           "       '()' called on itself\n"
                                          );
    tree.setSelectionRow(28);
    final ProblemDescriptionNode node = (ProblemDescriptionNode)tree.getSelectionModel().getSelectionPath().getLastPathComponent();
    assertFalse(node.isValid());
  }

  public void testOfflineView() throws InterruptedException {
    myView.getGlobalInspectionContext().getUIOptions().SHOW_STRUCTURE = true;
    InspectionTree tree = updateTree();
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
    TreeUtil.expandAll(tree);
    PlatformTestUtil.assertTreeEqual(tree, "-" + getProject() + "\n" +
                                           " -Java\n" +
                                           "  -Declaration redundancy\n" +
                                           "   -unused\n" +
                                           "    -Module: 'testOfflineView'\n" +
                                           "     -<default>\n" +
                                           "      -Test\n" +
                                           "       -f()\n" +
                                           "        -D\n" +
                                           "         -b()\n" +
                                           "          -anonymous (Runnable)\n" +
                                           "           -run()\n" +
                                           "            Variable <code>i</code> is never used.\n" +
                                           "          Variable <code>r</code> is never used.\n" +
                                           "       -ff()\n" +
                                           "        Variable <code>a</code> is never used.\n" +
                                           "        Variable <code>d</code> is never used.\n" +
                                           "       -foo()\n" +
                                           "        Variable <code>j</code> is never used.\n" +
                                           "       -main(String[])\n" +
                                           "        Variable <code>test</code> is never used.\n" +
                                           "  -Probable bugs\n" +
                                           "   -" + myDataFlowToolWrapper + "\n" +
                                           "    -Module: 'testOfflineView'\n" +
                                           "     -<default>\n" +
                                           "      -Test\n" +
                                           "       -m()\n" +
                                           "        'equals()' called on itself\n" +
                                           "      -Test2\n" +
                                           "       -m123()\n" +
                                           "        'equals()' called on itself\n"
                                         );
    myView.getGlobalInspectionContext().getUIOptions().SHOW_STRUCTURE = false;
    tree = updateTree();
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
    TreeUtil.expandAll(tree);
    PlatformTestUtil.assertTreeEqual(tree, "-" + getProject() + "\n" +
                                           " -Java\n" +
                                           "  -Declaration redundancy\n" +
                                           "   -unused\n" +
                                           "    -Test\n" +
                                           "     -f()\n" +
                                           "      -D\n" +
                                           "       -b()\n" +
                                           "        -anonymous (Runnable)\n" +
                                           "         -run()\n" +
                                           "          Variable <code>i</code> is never used.\n" +
                                           "        Variable <code>r</code> is never used.\n" +
                                           "     -ff()\n" +
                                           "      Variable <code>a</code> is never used.\n" +
                                           "      Variable <code>d</code> is never used.\n" +
                                           "     -foo()\n" +
                                           "      Variable <code>j</code> is never used.\n" +
                                           "     -main(String[])\n" +
                                           "      Variable <code>test</code> is never used.\n" +
                                           "  -Probable bugs\n" +
                                           "   -EqualsWithItself\n" +
                                           "    -Test\n" +
                                           "     'equals()' called on itself\n" +
                                           "    -Test2\n" +
                                           "     'equals()' called on itself\n"
                                         );
    TreeUtil.selectNode(tree, tree.getRoot());
    final InspectionTreeNode root = tree.getRoot();
    root.excludeElement();
    TreeUtil.treeNodeTraverser(root).traverse().processEach(node -> {
      assertTrue(((InspectionTreeNode)node).isExcluded());
      return true;
    });
    myView.getGlobalInspectionContext().getUIOptions().FILTER_RESOLVED_ITEMS = false;
    tree = updateTree();
    PlatformTestUtil.assertTreeEqual(tree, "-" + getProject() + "\n"
                                           + " -Java\n" +
                                           "  -Declaration redundancy\n" +
                                           "   -unused\n" +
                                           "    -Test\n" +
                                           "     -f()\n" +
                                           "      -D\n" +
                                           "       -b()\n" +
                                           "        -anonymous (Runnable)\n" +
                                           "         -run()\n" +
                                           "          Variable <code>i</code> is never used.\n" +
                                           "        Variable <code>r</code> is never used.\n" +
                                           "     -ff()\n" +
                                           "      Variable <code>a</code> is never used.\n" +
                                           "      Variable <code>d</code> is never used.\n" +
                                           "     -foo()\n" +
                                           "      Variable <code>j</code> is never used.\n" +
                                           "     -main(String[])\n" +
                                           "      Variable <code>test</code> is never used.\n" +
                                           "  -Probable bugs\n" +
                                           "   -EqualsWithItself\n" +
                                           "    -Test\n" +
                                           "     'equals()' called on itself\n" +
                                           "    -Test2\n" +
                                           "     'equals()' called on itself\n"
                                          );
  }

  private InspectionTree updateTree() {
    myView.update();
    final InspectionTree tree = myView.getTree();
    TreeUtil.expandAll(tree);
    return tree;
  }

  @Override
  protected String getTestPath() {
    return "inspection/offline";
  }

  @Override
  protected String getTestDirectoryName() {
    return "project";
  }
}
