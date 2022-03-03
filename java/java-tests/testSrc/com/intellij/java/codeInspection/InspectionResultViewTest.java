// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection;

import com.intellij.analysis.AnalysisScope;
import com.intellij.analysis.AnalysisUIOptions;
import com.intellij.codeInspection.ex.GlobalInspectionContextImpl;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ui.InspectionResultsView;
import com.intellij.java.testFramework.fixtures.LightJava9ModulesCodeInsightFixtureTestCase;
import com.intellij.java.testFramework.fixtures.MultiModuleJava9ProjectDescriptor;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.InspectionsKt;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.impl.GlobalInspectionContextForTests;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;

import java.io.File;
import java.util.Arrays;
import java.util.Set;

public class InspectionResultViewTest extends LightJava9ModulesCodeInsightFixtureTestCase {
  private static final Set<String> ENABLED_TOOLS = ContainerUtil.set("UNUSED_IMPORT", "MarkedForRemoval", "Java9RedundantRequiresStatement", "GroovyUnusedAssignment");
  private boolean myDefaultShowStructure;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    GlobalInspectionContextImpl.TESTING_VIEW = true;
    InspectionProfileImpl.INIT_INSPECTIONS = true;
    myDefaultShowStructure = AnalysisUIOptions.getInstance(getProject()).SHOW_STRUCTURE;
  }

  @Override
  public void tearDown() {
    GlobalInspectionContextImpl.TESTING_VIEW = false;
    InspectionProfileImpl.INIT_INSPECTIONS = false;
    AnalysisUIOptions.getInstance(getProject()).SHOW_STRUCTURE = myDefaultShowStructure;
    super.tearDown();
  }

  public void testModuleInfoProblemsTree() throws Exception {
    addEnv();
    InspectionResultsView view = runInspections();

    updateTree(view);
    TreeUtil.expandAll(view.getTree());
    updateTree(view);
    PlatformTestUtil.assertTreeEqual(view.getTree(), "-Inspections Results\n" +
                                                     " -Java\n" +
                                                     "  -Code maturity\n" +
                                                     "   -Usage of API marked for removal\n" +
                                                     "    -some.module\n" +
                                                     "     'M2' is deprecated and marked for removal(LIKE_DEPRECATED)\n" +
                                                     "  -Declaration redundancy\n" +
                                                     "   -Redundant 'requires' directive in module-info\n" +
                                                     "    -some.module\n" +
                                                     "     Redundant directive 'requires M2'\n");

    view.getGlobalInspectionContext().getUIOptions().SHOW_STRUCTURE = true;
    view.update();

    updateTree(view);
    TreeUtil.expandAll(view.getTree());
    updateTree(view);
    PlatformTestUtil.assertTreeEqual(view.getTree(), "-Inspections Results\n" +
                                                     " -Java\n" +
                                                     "  -Code maturity\n" +
                                                     "   -Usage of API marked for removal\n" +
                                                     "    -light_idea_test_case\n" +
                                                     "     -some.module\n" +
                                                     "      'M2' is deprecated and marked for removal(LIKE_DEPRECATED)\n" +
                                                     "  -Declaration redundancy\n" +
                                                     "   -Redundant 'requires' directive in module-info\n" +
                                                     "    -light_idea_test_case\n" +
                                                     "     -some.module\n" +
                                                     "      Redundant directive 'requires M2'\n");
  }

  public void testNonJavaDirectoryModuleGrouping() throws Exception {
    addFile("xxx/yyy/ZZZ.groovy", "class ZZZ {void mmm() { int iii = 0; }}", MultiModuleJava9ProjectDescriptor.ModuleDescriptor.M2);
    addFile("foo/bar/Baz.groovy", "class Baz {void mmm() { int iii = 0; }}", MultiModuleJava9ProjectDescriptor.ModuleDescriptor.M3);
    InspectionResultsView view = runInspections();
    view.getGlobalInspectionContext().getUIOptions().SHOW_STRUCTURE = true;
    view.update();
    updateTree(view);
    TreeUtil.expandAll(view.getTree());
    updateTree(view);

    PlatformTestUtil.assertTreeEqual(view.getTree(), ("-Inspections Results\n" +
                                                             " -Groovy\n" +
                                                             "  -Data flow\n" +
                                                             "   -Unused assignment\n" +
                                                             "    -light_idea_test_case_m2\n" +
                                                             "     -src_m2/xxx/yyy\n" +
                                                             "      -ZZZ.groovy\n" +
                                                             "       Assignment is not used\n" +
                                                             "    -light_idea_test_case_m3\n" +
                                                             "     -src_m3/foo/bar\n" +
                                                             "      -Baz.groovy\n" +
                                                             "       Assignment is not used").replace('/', File.separatorChar));

  }

  private InspectionResultsView runInspections() {
    AnalysisScope scope = new AnalysisScope(getProject());
    GlobalInspectionContextForTests context =
      InspectionsKt.createGlobalContextForTool(scope, getProject(), Arrays.asList(createTestInspectionProfile()));

    context.getCurrentProfile().initInspectionTools(getProject());
    context.doInspections(scope);
    do {
      UIUtil.dispatchAllInvocationEvents();
    }
    while (!context.isFinished());
    Disposer.register(getTestRootDisposable(), () -> context.close(false));
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
