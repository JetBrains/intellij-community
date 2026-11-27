// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
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
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.projectView.TestSourceBasedFixture;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.intellij.projectView.TestSourceBasedFixture.testSourceBasedFixture;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class OfflineInspectionResultViewTest {
  private final TestFixture<TestSourceBasedFixture> testSourceBasedFixture = testSourceBasedFixture("inspection/offline", "project");

  private InspectionResultsView myView;
  private InspectionToolWrapper<?, ?> myUnusedToolWrapper;
  private InspectionToolWrapper<?, ?> myDataFlowToolWrapper;

  @BeforeEach
  void setUp() throws Exception {
    TestSourceBasedFixture fixture = testSourceBasedFixture.get();
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

    Map<String, Map<String, Set<OfflineProblemDescriptor>>> map = parse();
    EdtTestUtil.runInEdtAndWait(() -> {
      myView = ViewOfflineResultsAction.showOfflineView(fixture.getProject(), map, profile, "");
      profile.initInspectionTools(fixture.getProject());
      myUnusedToolWrapper = profile.getInspectionTool("unused", fixture.getProject());
      myDataFlowToolWrapper = profile.getInspectionTool("EqualsWithItself", fixture.getProject());

      for (InspectionToolWrapper<?, ?> tool : ContainerUtil.ar(myUnusedToolWrapper, myDataFlowToolWrapper)) {
        tool.initialize(myView.getGlobalInspectionContext());
      }
    });

    Disposer.register(fixture.getTestRootDisposable(), () -> { profile.cleanup(fixture.getProject()); });
  }

  @AfterEach
  void tearDown() {
    try {
      if (myView != null) {
        EdtTestUtil.runInEdtAndWait(() -> Disposer.dispose(myView));
      }
    }
    finally {
      InspectionProfileImpl.INIT_INSPECTIONS = false;
      myView = null;
      myUnusedToolWrapper = null;
      myDataFlowToolWrapper = null;
    }
  }

  private Map<String, Map<String, Set<OfflineProblemDescriptor>>> parse() throws IOException {
    TestSourceBasedFixture fixture = testSourceBasedFixture.get();
    final String moduleName = fixture.getModule().getName();
    final File res = new File(JavaTestUtil.getJavaTestDataPath(), "inspection/offline" + File.separator + "res");
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

  @Test
  public void testOfflineWithInvalid() throws InterruptedException {
    TestSourceBasedFixture fixture = testSourceBasedFixture.get();
    WriteAction.runAndWait(() -> fixture.getJavaFacade().findClass("Test2").getContainingFile().delete());
    myView.getGlobalInspectionContext().getUIOptions().SHOW_STRUCTURE = true;
    InspectionTree tree = updateTree();
    EdtTestUtil.runInEdtAndWait(() -> {
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
    });
  }

  @Test
  public void testOfflineView() throws InterruptedException {
    myView.getGlobalInspectionContext().getUIOptions().SHOW_STRUCTURE = true;
    InspectionTree tree = updateTree();
    EdtTestUtil.runInEdtAndWait(() -> {
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
    });
    myView.getGlobalInspectionContext().getUIOptions().SHOW_STRUCTURE = false;
    InspectionTree tree2 = updateTree();
    EdtTestUtil.runInEdtAndWait(() -> {
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
      PlatformTestUtil.expandAll(tree2);
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
      PlatformTestUtil.assertTreeEqual(tree2, """
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
      InspectionRootNode root = tree2.getInspectionTreeModel().getRoot();
      root.excludeElement();
      tree2.getInspectionTreeModel().traverse(root).forEach(node -> assertTrue(node.isExcluded(), "node = " + node));
    });
    myView.getGlobalInspectionContext().getUIOptions().FILTER_RESOLVED_ITEMS = false;
    InspectionTree tree3 = updateTree();
    EdtTestUtil.runInEdtAndWait(() -> {
      PlatformTestUtil.assertTreeEqual(tree3, """
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
    });
  }

  private InspectionTree updateTree() throws InterruptedException {
    return EdtTestUtil.runInEdtAndGet(() -> {
      myView.update();
      myView.dispatchTreeUpdate();
      final InspectionTree tree = myView.getTree();
      PlatformTestUtil.expandAll(tree);
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
      return tree;
    });
  }
}
