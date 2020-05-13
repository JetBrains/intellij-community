// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.codeInspection.ui.InspectionRootNode;
import com.intellij.codeInspection.ui.InspectionTree;
import com.intellij.codeInspection.ui.ProblemDescriptionNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.TestSourceBasedTestCase;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class OfflineInspectionResultViewTest extends TestSourceBasedTestCase {
  private InspectionResultsView myView;
  private InspectionToolWrapper<?, ?> myUnusedToolWrapper;
  private InspectionToolWrapper<?, ?> myDataFlowToolWrapper;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    InspectionProfileImpl.INIT_INSPECTIONS = true;

    HighlightDisplayKey.findOrRegister(UnusedDeclarationInspectionBase.SHORT_NAME, UnusedDeclarationInspectionBase.getDisplayNameText(), UnusedDeclarationInspectionBase.SHORT_NAME);

    final InspectionProfileImpl profile = new InspectionProfileImpl("test") {
      @Override
      public boolean isToolEnabled(final @Nullable HighlightDisplayKey key, PsiElement element) {
        return key != null && Comparing.strEqual(key.toString(), UnusedDeclarationInspectionBase.SHORT_NAME);
      }

      @Override
      public @NotNull List<InspectionToolWrapper<?, ?>> getInspectionTools(PsiElement element) {
        return Collections.singletonList(myUnusedToolWrapper);
      }

      @Override
      public @NotNull InspectionProfileModifiableModel getModifiableModel() {
        return new InspectionProfileModifiableModel(this) {
          @Override
          public @NotNull List<InspectionToolWrapper<?, ?>> getInspectionTools(PsiElement element) {
            return Collections.singletonList(myUnusedToolWrapper);
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

    for (InspectionToolWrapper<?, ?> tool : ContainerUtil.ar(myUnusedToolWrapper, myDataFlowToolWrapper)) {
      tool.initialize(myView.getGlobalInspectionContext());
    }
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      Disposer.dispose(myView);
    }
    catch (Throwable e) {
      addSuppressedException(e);
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
      final Map<String, Set<OfflineProblemDescriptor>> descriptors = OfflineViewParseUtil.parse(file);
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
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
    PlatformTestUtil.assertTreeEqual(tree, "-InspectionViewTree\n" +
                                           " -Java\n" +
                                           "  -Declaration redundancy\n" +
                                           "   -Unused declaration\n" +
                                           "    Entry Points\n" +
                                           "    -testOfflineWithInvalid\n" +
                                           "     -<default>\n" +
                                           "      -Test\n" +
                                           "       -f()\n" +
                                           "        -D\n" +
                                           "         -b()\n" +
                                           "          Variable 'r' is never used.\n" +
                                           "          -anonymous (Runnable)\n" +
                                           "           -run()\n" +
                                           "            Variable 'i' is never used.\n" +
                                           "       -ff()\n" +
                                           "        Variable 'a' is never used.\n" +
                                           "        Variable 'd' is never used.\n" +
                                           "       -foo()\n" +
                                           "        Variable 'j' is never used.\n" +
                                           "       -main(String[])\n" +
                                           "        Variable 'test' is never used.\n" +
                                           "  -Probable bugs\n" +
                                           "   -'equals()' called on itself\n" +
                                           "    -testOfflineWithInvalid\n" +
                                           "     -<default>\n" +
                                           "      -Test\n" +
                                           "       -m()\n" +
                                           "        'equals()' called on itself\n" +
                                           "      -element no longer exists\n" +
                                           "       '()' called on itself\n"
                                          );
    tree.setSelectionRow(29);
    final ProblemDescriptionNode node = (ProblemDescriptionNode)tree.getSelectionModel().getSelectionPath().getLastPathComponent();
    assertFalse(node.isValid());
  }

  public void testOfflineView() throws InterruptedException {
    myView.getGlobalInspectionContext().getUIOptions().SHOW_STRUCTURE = true;
    InspectionTree tree = updateTree();
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
    TreeUtil.expandAll(tree);
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
    PlatformTestUtil.assertTreeEqual(tree, "-InspectionViewTree\n" +
                                           " -Java\n" +
                                           "  -Declaration redundancy\n" +
                                           "   -Unused declaration\n" +
                                           "    Entry Points\n" +
                                           "    -testOfflineView\n" +
                                           "     -<default>\n" +
                                           "      -Test\n" +
                                           "       -f()\n" +
                                           "        -D\n" +
                                           "         -b()\n" +
                                           "          Variable 'r' is never used.\n" +
                                           "          -anonymous (Runnable)\n" +
                                           "           -run()\n" +
                                           "            Variable 'i' is never used.\n" +
                                           "       -ff()\n" +
                                           "        Variable 'a' is never used.\n" +
                                           "        Variable 'd' is never used.\n" +
                                           "       -foo()\n" +
                                           "        Variable 'j' is never used.\n" +
                                           "       -main(String[])\n" +
                                           "        Variable 'test' is never used.\n" +
                                           "  -Probable bugs\n" +
                                           "   -'equals()' called on itself\n" +
                                           "    -testOfflineView\n" +
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
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
    PlatformTestUtil.assertTreeEqual(tree, "-InspectionViewTree\n" +
                                           " -Java\n" +
                                           "  -Declaration redundancy\n" +
                                           "   -Unused declaration\n" +
                                           "    Entry Points\n" +
                                           "    -Test\n" +
                                           "     -f()\n" +
                                           "      -D\n" +
                                           "       -b()\n" +
                                           "        Variable 'r' is never used.\n" +
                                           "        -anonymous (Runnable)\n" +
                                           "         -run()\n" +
                                           "          Variable 'i' is never used.\n" +
                                           "     -ff()\n" +
                                           "      Variable 'a' is never used.\n" +
                                           "      Variable 'd' is never used.\n" +
                                           "     -foo()\n" +
                                           "      Variable 'j' is never used.\n" +
                                           "     -main(String[])\n" +
                                           "      Variable 'test' is never used.\n" +
                                           "  -Probable bugs\n" +
                                           "   -'equals()' called on itself\n" +
                                           "    -Test\n" +
                                           "     'equals()' called on itself\n" +
                                           "    -Test2\n" +
                                           "     'equals()' called on itself\n"
                                         );
    InspectionRootNode root = tree.getInspectionTreeModel().getRoot();
    root.excludeElement();
    tree.getInspectionTreeModel().traverse(root).forEach(node -> assertTrue("node = " + node, node.isExcluded()));
    myView.getGlobalInspectionContext().getUIOptions().FILTER_RESOLVED_ITEMS = false;
    tree = updateTree();
    PlatformTestUtil.assertTreeEqual(tree, "-InspectionViewTree\n"
                                           + " -Java\n" +
                                           "  -Declaration redundancy\n" +
                                           "   -Unused declaration\n" +
                                           "    Entry Points\n" +
                                           "    -Test\n" +
                                           "     -f()\n" +
                                           "      -D\n" +
                                           "       -b()\n" +
                                           "        Variable 'r' is never used.\n" +
                                           "        -anonymous (Runnable)\n" +
                                           "         -run()\n" +
                                           "          Variable 'i' is never used.\n" +
                                           "     -ff()\n" +
                                           "      Variable 'a' is never used.\n" +
                                           "      Variable 'd' is never used.\n" +
                                           "     -foo()\n" +
                                           "      Variable 'j' is never used.\n" +
                                           "     -main(String[])\n" +
                                           "      Variable 'test' is never used.\n" +
                                           "  -Probable bugs\n" +
                                           "   -'equals()' called on itself\n" +
                                           "    -Test\n" +
                                           "     'equals()' called on itself\n" +
                                           "    -Test2\n" +
                                           "     'equals()' called on itself\n"
                                          );
  }

  private InspectionTree updateTree() throws InterruptedException {
    myView.update();
    final InspectionTree tree = myView.getTree();
    TreeUtil.expandAll(tree);
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
    return tree;
  }

  @Override
  protected String getTestPath() {
    return "inspection/offline";
  }

  @Override
  protected @NotNull String getTestDirectoryName() {
    return "project";
  }
}
