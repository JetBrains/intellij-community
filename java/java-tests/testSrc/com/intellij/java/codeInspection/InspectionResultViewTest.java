// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.ex.GlobalInspectionContextImpl;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ui.InspectionResultsView;
import com.intellij.java.testFramework.fixtures.LightJava9ModulesCodeInsightFixtureTestCase;
import com.intellij.java.testFramework.fixtures.MultiModuleJava9ProjectDescriptor;
import com.intellij.testFramework.InspectionsKt;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.impl.GlobalInspectionContextForTests;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;

import java.util.Set;

public class InspectionResultViewTest extends LightJava9ModulesCodeInsightFixtureTestCase {
  private static final Set<String> ENABLED_TOOLS = ContainerUtil.set("UNUSED_IMPORT", "MarkedForRemoval", "Java9RedundantRequiresStatement");

  @Override
  public void setUp() throws Exception {
    super.setUp();
    GlobalInspectionContextImpl.CREATE_VIEW_FORCE = true;
    InspectionProfileImpl.INIT_INSPECTIONS = true;
  }

  @Override
  public void tearDown() {
    GlobalInspectionContextImpl.CREATE_VIEW_FORCE = false;
    InspectionProfileImpl.INIT_INSPECTIONS = false;
    super.tearDown();
  }

  public void testModuleInfoProblemsTree() throws Exception {
    addEnv();
    InspectionResultsView view = runInspections();

    updateTree(view);
    TreeUtil.expandAll(view.getTree());
    PlatformTestUtil.assertTreeEqual(view.getTree(), "-" + getProject() + "\n" +
                                                     " -Java\n" +
                                                     "  -Code maturity issues\n" +
                                                     "   -MarkedForRemoval\n" +
                                                     "    -some.module\n" +
                                                     "     'M2' is deprecated and marked for removal(LIKE_DEPRECATED)\n" +
                                                     "  -Declaration redundancy\n" +
                                                     "   -Java9RedundantRequiresStatement\n" +
                                                     "    -some.module\n" +
                                                     "     Redundant directive 'requires M2'\n");

    view.getGlobalInspectionContext().getUIOptions().SHOW_STRUCTURE = true;
    view.update();

    updateTree(view);
    TreeUtil.expandAll(view.getTree());
    PlatformTestUtil.assertTreeEqual(view.getTree(), "-" + getProject() + "\n" +
                                                     " -Java\n" +
                                                     "  -Code maturity issues\n" +
                                                     "   -MarkedForRemoval\n" +
                                                     "    -Module: 'light_idea_test_case'\n" +
                                                     "     -some.module\n" +
                                                     "      'M2' is deprecated and marked for removal(LIKE_DEPRECATED)\n" +
                                                     "  -Declaration redundancy\n" +
                                                     "   -Java9RedundantRequiresStatement\n" +
                                                     "    -Module: 'light_idea_test_case'\n" +
                                                     "     -some.module\n" +
                                                     "      Redundant directive 'requires M2'\n");
  }

  private InspectionResultsView runInspections() {
    AnalysisScope scope = new AnalysisScope(getProject());
    GlobalInspectionContextForTests context =
      InspectionsKt.createGlobalContextForTool(scope, getProject(), ContainerUtil.list(createTestInspectionProfile()));

    context.getCurrentProfile().initInspectionTools(getProject());
    context.doInspections(scope);
    do {
      UIUtil.dispatchAllInvocationEvents();
    }
    while (!context.isFinished());

    return context.getView();
  }

  private void addEnv() {
    addFile("module-info.java", "@Deprecated(forRemoval=true) module M2 { }", MultiModuleJava9ProjectDescriptor.ModuleDescriptor.M2);
    addFile("module-info.java", "@Deprecated\n" +
                                "module some.module {\n" +
                                "  requires M2;\n" +
                                "}", MultiModuleJava9ProjectDescriptor.ModuleDescriptor.MAIN);
  }

  private InspectionToolWrapper<?, ?>[] createTestInspectionProfile() {
    InspectionProfileImpl profile = new InspectionProfileImpl("test");
    return ENABLED_TOOLS
      .stream()
      .map(shortName -> profile.getInspectionTool(shortName, getProject()))
      .toArray(InspectionToolWrapper[]::new);
  }

  private static void updateTree(InspectionResultsView view) throws Exception {
    view.dispatchTreeUpdate();
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
  }
}
