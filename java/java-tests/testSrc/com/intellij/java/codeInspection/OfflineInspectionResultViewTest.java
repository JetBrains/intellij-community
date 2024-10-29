// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
        return key != null && Comparing.strEqual(key.getShortName(), UnusedDeclarationInspectionBase.SHORT_NAME);
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
            return key != null && Comparing.strEqual(key.getShortName(), UnusedDeclarationInspectionBase.SHORT_NAME);
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

    Disposer.register(getTestRootDisposable(), () -> { profile.cleanup(myProject); });
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
      Map<String, Set<OfflineProblemDescriptor>> descriptors = OfflineViewParseUtil.parse(file.toPath());
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
    PlatformTestUtil.expandAll(tree);
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
    PlatformTestUtil.assertTreeEqual(tree, """
                                       -Inspection Results
                                        -Java
                                         -Declaration redundancy
                                          -Unused declaration
                                           Entry Points
                                           -testOfflineWithInvalid
                                            -<default>
                                             -Test
                                              -f()
                                               -D
                                                -b()
                                                 Variable 'r' is never used
                                                 -anonymous (Runnable)
                                                  -run()
                                                   Variable 'i' is never used
                                              -ff()
                                               Variable 'd' is never used
                                               Variable 'a' is never used
                                              -foo()
                                               Variable 'j' is never used
                                              -main(String[])
                                               Variable 'test' is never used
                                         -Probable bugs
                                          -'equals()' called on itself
                                           -testOfflineWithInvalid
                                            -<default>
                                             -Test
                                              -m()
                                               'equals()' called on itself
                                             -element no longer exists
                                              '()' called on itself
                                       """
                                          );
    tree.setSelectionRow(30);
    final ProblemDescriptionNode node = (ProblemDescriptionNode)tree.getSelectionModel().getSelectionPath().getLastPathComponent();
    assertFalse(node.isValid());
  }

  public void testOfflineView() throws InterruptedException {
    myView.getGlobalInspectionContext().getUIOptions().SHOW_STRUCTURE = true;
    InspectionTree tree = updateTree();
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
    PlatformTestUtil.expandAll(tree);
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
    PlatformTestUtil.assertTreeEqual(tree, """
                                       -Inspection Results
                                        -Java
                                         -Declaration redundancy
                                          -Unused declaration
                                           Entry Points
                                           -testOfflineView
                                            -<default>
                                             -Test
                                              -f()
                                               -D
                                                -b()
                                                 Variable 'r' is never used
                                                 -anonymous (Runnable)
                                                  -run()
                                                   Variable 'i' is never used
                                              -ff()
                                               Variable 'd' is never used
                                               Variable 'a' is never used
                                              -foo()
                                               Variable 'j' is never used
                                              -main(String[])
                                               Variable 'test' is never used
                                         -Probable bugs
                                          -'equals()' called on itself
                                           -testOfflineView
                                            -<default>
                                             -Test
                                              -m()
                                               'equals()' called on itself
                                             -Test2
                                              -m123()
                                               'equals()' called on itself
                                       """
                                         );
    myView.getGlobalInspectionContext().getUIOptions().SHOW_STRUCTURE = false;
    tree = updateTree();
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
    PlatformTestUtil.expandAll(tree);
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
    PlatformTestUtil.assertTreeEqual(tree, """
                                       -Inspection Results
                                        -Java
                                         -Declaration redundancy
                                          -Unused declaration
                                           Entry Points
                                           -Test
                                            -f()
                                             -D
                                              -b()
                                               Variable 'r' is never used
                                               -anonymous (Runnable)
                                                -run()
                                                 Variable 'i' is never used
                                            -ff()
                                             Variable 'd' is never used
                                             Variable 'a' is never used
                                            -foo()
                                             Variable 'j' is never used
                                            -main(String[])
                                             Variable 'test' is never used
                                         -Probable bugs
                                          -'equals()' called on itself
                                           -Test
                                            'equals()' called on itself
                                           -Test2
                                            'equals()' called on itself
                                       """
                                         );
    InspectionRootNode root = tree.getInspectionTreeModel().getRoot();
    root.excludeElement();
    tree.getInspectionTreeModel().traverse(root).forEach(node -> assertTrue("node = " + node, node.isExcluded()));
    myView.getGlobalInspectionContext().getUIOptions().FILTER_RESOLVED_ITEMS = false;
    tree = updateTree();
    PlatformTestUtil.assertTreeEqual(tree, """
                                       -Inspection Results
                                        -Java
                                         -Declaration redundancy
                                          -Unused declaration
                                           Entry Points
                                           -Test
                                            -f()
                                             -D
                                              -b()
                                               Variable 'r' is never used
                                               -anonymous (Runnable)
                                                -run()
                                                 Variable 'i' is never used
                                            -ff()
                                             Variable 'd' is never used
                                             Variable 'a' is never used
                                            -foo()
                                             Variable 'j' is never used
                                            -main(String[])
                                             Variable 'test' is never used
                                         -Probable bugs
                                          -'equals()' called on itself
                                           -Test
                                            'equals()' called on itself
                                           -Test2
                                            'equals()' called on itself
                                       """
                                          );
  }

  private InspectionTree updateTree() throws InterruptedException {
    myView.update();
    myView.dispatchTreeUpdate();
    final InspectionTree tree = myView.getTree();
    PlatformTestUtil.expandAll(tree);
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
