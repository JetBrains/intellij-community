// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.AggregateResultsInspection;
import com.intellij.codeInspection.GlobalInspectionContext;
import com.intellij.codeInspection.GlobalInspectionTool;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptionsProcessor;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.ex.ExternalAnnotatorBatchInspection;
import com.intellij.codeInspection.ex.GlobalInspectionContextEx;
import com.intellij.codeInspection.ex.GlobalInspectionContextImpl;
import com.intellij.codeInspection.ex.GlobalInspectionContextUtil;
import com.intellij.codeInspection.ex.GlobalInspectionToolWrapper;
import com.intellij.codeInspection.ex.InspectListener;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ex.InspectionToolsSupplier;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.java.testFramework.fixtures.LightJava9ModulesCodeInsightFixtureTestCase;
import com.intellij.lang.annotation.ProblemGroup;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.profile.codeInspection.BaseInspectionProfileManager;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLiteralExpression;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Test for {@link AggregateResultsInspection} functionality.
 * <p>
 * Tests that when an inspection implements AggregateResultsInspection,
 * problem descriptors are exported under the inspection ID specified in their problem group,
 * rather than under the aggregator inspection's own ID.
 */
public class AggregateResultsInspectionTest extends LightJava9ModulesCodeInsightFixtureTestCase {
  
  @Override
  public void setUp() throws Exception {
    super.setUp();
    InspectionProfileImpl.INIT_INSPECTIONS = true;
  }

  @Override
  public void tearDown() {
    InspectionProfileImpl.INIT_INSPECTIONS = false;
    super.tearDown();
  }

  public void testAggregateResultsExport() throws Exception {
    myFixture.addFileToProject("Foo.java", """
      class Foo {
          void m() {
              int i = 42;           // MockInspectionA
              String s = "test";    // UnknownInspection
          }
      }""");

    InspectionManager im = InspectionManager.getInstance(getProject());
    AnalysisScope scope = new AnalysisScope(getProject());
    List<Path> resultFiles = new ArrayList<>();
    Path outputPath = FileUtil.createTempDirectory("inspection", "results").toPath();

    GlobalInspectionContextImpl context = (GlobalInspectionContextImpl)im.createNewGlobalContext();

    InspectionToolsSupplier.Simple toolSupplier = new InspectionToolsSupplier.Simple(getTools());
    Disposer.register(getTestRootDisposable(), toolSupplier);
    InspectionProfileImpl profile = new InspectionProfileImpl("test", toolSupplier, (BaseInspectionProfileManager)InspectionProfileManager.getInstance());
    
    for (InspectionToolWrapper<?, ?> t : getTools()) {
      profile.enableTool(t.getShortName(), getProject());
    }

    context.setExternalProfile(profile);

    ActionUtil.underModalProgress(myFixture.getProject(), "", () -> {
      context.launchInspectionsOffline(scope, outputPath, false, resultFiles);
      return null;
    });
    assertSize(1, resultFiles);

    Path resultFile = resultFiles.getFirst();
    assertEquals("TestAggregator.xml", resultFile.getFileName().toString());
    Element aggregatorResult = InspectionResultExportTest.loadFile(resultFile);
    Map<Integer, String> expectedProblemClassByLine = Map.of(
      3, "MockInspection", 4, "TestAggregator"
    );
    verifyProblemClassId(aggregatorResult, expectedProblemClassByLine);
  }

  private static void verifyProblemClassId(@NotNull Element results, @NotNull Map<Integer, String> expectedProblemClassByLine) {
    Collection<Element> problems = results.getChildren("problem");
    Map<Integer, String> problemClassByLine = new HashMap<>();
    for (Element problem : problems) {
      String line = problem.getChild("line").getContent(0).getValue();
      String problemClassId = problem.getChild("problem_class").getAttributeValue("id");
      problemClassByLine.put(Integer.valueOf(line), problemClassId);
    }
    assertEquals(expectedProblemClassByLine, problemClassByLine);
  }

  private static @NotNull List<InspectionToolWrapper<?, ?>> getTools() {
    return Arrays.asList(
      new LocalInspectionToolWrapper(new TestAggregatorInspection()),
      new LocalInspectionToolWrapper(new MockInspection())
    );
  }

  private static class TestAggregatorInspection extends LocalInspectionTool implements AggregateResultsInspection {
    @Override
    public @NotNull String getShortName() {
      return "TestAggregator";
    }

    @Override
    public @NotNull String getDisplayName() {
      return "Test Aggregator";
    }

    @Override
    public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
      return new JavaElementVisitor() {
        @Override
        public void visitLiteralExpression(@NotNull PsiLiteralExpression literal) {
          super.visitLiteralExpression(literal);
          Object value = literal.getValue();
          
          if (value instanceof Integer) {
            ProblemDescriptor descriptor = holder.getManager().createProblemDescriptor(
              literal,
              "Integer literal (aggregated to MockInspection)",
              (LocalQuickFix)null,
              ProblemHighlightType.WEAK_WARNING,
              holder.isOnTheFly()
            );
            descriptor.setProblemGroup(new TestProblemGroup("MockInspection"));
            holder.registerProblem(descriptor);
          }
          else if (value instanceof String) {
            ProblemDescriptor descriptor = holder.getManager().createProblemDescriptor(
              literal,
              "Boolean literal (unknown problem group, fallback to TestAggregator)",
              (LocalQuickFix)null,
              ProblemHighlightType.WEAK_WARNING,
              holder.isOnTheFly()
            );
            descriptor.setProblemGroup(new TestProblemGroup("UnknownInspection"));
            holder.registerProblem(descriptor);
          }
        }
      };
    }
  }

  /**
   * Mock inspection - used for problem group aggregation testing.
   */
  private static class MockInspection extends LocalInspectionTool {
    @Override
    public @NotNull String getShortName() {
      return "MockInspection";
    }

    @Override
    public @NotNull String getDisplayName() {
      return "Mock Inspection";
    }

    @Override
    public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
  }

  /**
   * Simple ProblemGroup implementation for testing.
   */
  private static class TestProblemGroup implements ProblemGroup {
    private final String myName;

    TestProblemGroup(@NotNull String name) {
      myName = name;
    }

    @Override
    public @NotNull String getProblemName() {
      return myName;
    }
  }

  private record FinishedEvent(String toolId, int problemsCount, InspectListener.InspectionKind kind) {}

  public void testAggregateResultsExternalAnnotatorBatchInspectionEvents() throws Exception {
    PsiFile file = myFixture.addFileToProject("Bar.java", """
      class Bar {
          void m() {}
      }""");

    List<FinishedEvent> events = new CopyOnWriteArrayList<>();
    getProject().getMessageBus().connect(getTestRootDisposable()).subscribe(
      GlobalInspectionContextEx.INSPECT_TOPIC,
      new InspectListener() {
        @Override
        public void inspectionFinished(long duration, long threadId, int problemsCount, @NotNull InspectionToolWrapper<?, ?> tool, @NotNull InspectionKind kind, @Nullable PsiFile file, @NotNull Project project) {
          events.add(new FinishedEvent(tool.getShortName(), problemsCount, kind));
        }
      }
    );

    InspectionManager im = InspectionManager.getInstance(getProject());
    AnalysisScope scope = new AnalysisScope(file);
    List<Path> resultFiles = new ArrayList<>();
    Path outputPath = FileUtil.createTempDirectory("inspection", "results").toPath();

    GlobalInspectionContextImpl context = (GlobalInspectionContextImpl)im.createNewGlobalContext();
    List<InspectionToolWrapper<?, ?>> tools = List.of(
      new LocalInspectionToolWrapper(new TestBatchAggregatorInspection()),
      new LocalInspectionToolWrapper(new MockInspection())
    );
    InspectionToolsSupplier.Simple toolSupplier = new InspectionToolsSupplier.Simple(tools);
    Disposer.register(getTestRootDisposable(), toolSupplier);
    InspectionProfileImpl profile = new InspectionProfileImpl("testBatch", toolSupplier, (BaseInspectionProfileManager)InspectionProfileManager.getInstance());
    for (InspectionToolWrapper<?, ?> t : tools) {
      profile.enableTool(t.getShortName(), getProject());
    }
    context.setExternalProfile(profile);

    ActionUtil.underModalProgress(myFixture.getProject(), "", () -> {
      context.launchInspectionsOffline(scope, outputPath, false, resultFiles);
      return null;
    });

    assertTrue("Aggregator inspection finished event expected",
      events.stream().anyMatch(e -> e.toolId().equals("TestBatchAggregator") && e.problemsCount() == 3 && e.kind() == InspectListener.InspectionKind.LOCAL));
    assertTrue("Child rule inspection finished event expected",
      events.stream().anyMatch(e -> e.toolId().equals("MockInspection") && e.problemsCount() == 1 && e.kind() == InspectListener.InspectionKind.LOCAL));
    assertFalse("Unknown child rule should not be reported",
      events.stream().anyMatch(e -> e.toolId().equals("UnknownInspection")));
  }

  public void testAggregateResultsGlobalInspectionEvents() throws Exception {
    PsiFile file = myFixture.addFileToProject("Baz.java", """
      class Baz {
          void m() {}
      }""");

    List<FinishedEvent> events = new CopyOnWriteArrayList<>();
    getProject().getMessageBus().connect(getTestRootDisposable()).subscribe(
      GlobalInspectionContextEx.INSPECT_TOPIC,
      new InspectListener() {
        @Override
        public void inspectionFinished(long duration, long threadId, int problemsCount, @NotNull InspectionToolWrapper<?, ?> tool, @NotNull InspectionKind kind, @Nullable PsiFile file, @NotNull Project project) {
          events.add(new FinishedEvent(tool.getShortName(), problemsCount, kind));
        }
      }
    );

    InspectionManager im = InspectionManager.getInstance(getProject());
    AnalysisScope scope = new AnalysisScope(file);
    List<Path> resultFiles = new ArrayList<>();
    Path outputPath = FileUtil.createTempDirectory("inspection", "results").toPath();

    GlobalInspectionContextImpl context = (GlobalInspectionContextImpl)im.createNewGlobalContext();
    List<InspectionToolWrapper<?, ?>> tools = List.of(
      new GlobalInspectionToolWrapper(new TestGlobalAggregatorInspection()),
      new LocalInspectionToolWrapper(new MockInspection())
    );
    InspectionToolsSupplier.Simple toolSupplier = new InspectionToolsSupplier.Simple(tools);
    Disposer.register(getTestRootDisposable(), toolSupplier);
    InspectionProfileImpl profile = new InspectionProfileImpl("testGlobal", toolSupplier, (BaseInspectionProfileManager)InspectionProfileManager.getInstance());
    for (InspectionToolWrapper<?, ?> t : tools) {
      profile.enableTool(t.getShortName(), getProject());
    }
    context.setExternalProfile(profile);

    ActionUtil.underModalProgress(myFixture.getProject(), "", () -> {
      context.launchInspectionsOffline(scope, outputPath, false, resultFiles);
      return null;
    });

    assertTrue("Global aggregator inspection finished event expected",
      events.stream().anyMatch(e -> e.toolId().equals("TestGlobalAggregator") && e.problemsCount() == 3 && e.kind() == InspectListener.InspectionKind.GLOBAL));
    assertTrue("Child rule inspection finished event expected",
      events.stream().anyMatch(e -> e.toolId().equals("MockInspection") && e.problemsCount() == 1 && e.kind() == InspectListener.InspectionKind.GLOBAL));
    assertFalse("Unknown child rule should not be reported",
      events.stream().anyMatch(e -> e.toolId().equals("UnknownInspection")));
  }

  private static class TestBatchAggregatorInspection extends LocalInspectionTool implements ExternalAnnotatorBatchInspection, AggregateResultsInspection {
    @Override
    public @NotNull String getShortName() {
      return "TestBatchAggregator";
    }

    @Override
    public ProblemDescriptor @NotNull [] checkFile(@NotNull PsiFile file, @NotNull GlobalInspectionContext context, @NotNull InspectionManager manager) {
      return ReadAction.computeBlocking(() -> {
        ProblemDescriptor d1 = manager.createProblemDescriptor(file, TextRange.EMPTY_RANGE, "Rule 1", ProblemHighlightType.GENERIC_ERROR_OR_WARNING, false);
        d1.setProblemGroup(new TestProblemGroup("MockInspection"));

        ProblemDescriptor d2 = manager.createProblemDescriptor(file, TextRange.EMPTY_RANGE, "Rule 2", ProblemHighlightType.GENERIC_ERROR_OR_WARNING, false);
        d2.setProblemGroup(new TestProblemGroup("UnknownInspection"));

        ProblemDescriptor d3 = manager.createProblemDescriptor(file, TextRange.EMPTY_RANGE, "Rule 3", ProblemHighlightType.GENERIC_ERROR_OR_WARNING, false);

        return new ProblemDescriptor[]{d1, d2, d3};
      });
    }
  }

  private static class TestGlobalAggregatorInspection extends GlobalInspectionTool implements AggregateResultsInspection {
    @Override
    public @NotNull String getShortName() {
      return "TestGlobalAggregator";
    }

    @Override
    public boolean isGraphNeeded() {
      return false;
    }

    @Override
    public void runInspection(@NotNull AnalysisScope scope,
                              @NotNull InspectionManager manager,
                              @NotNull GlobalInspectionContext globalContext,
                              @NotNull ProblemDescriptionsProcessor problemDescriptionsProcessor) {
      scope.accept(file -> {
        PsiFile psiFile = file.isValid() ? (file.isInLocalFileSystem() ? com.intellij.psi.PsiManager.getInstance(manager.getProject()).findFile(file) : null) : null;
        if (psiFile != null) {
          ReadAction.runBlocking(() -> {
            RefEntity refEntity = GlobalInspectionContextUtil.retrieveRefElement(psiFile, globalContext);
            ProblemDescriptor d1 = manager.createProblemDescriptor(psiFile, TextRange.EMPTY_RANGE, "Rule 1", ProblemHighlightType.GENERIC_ERROR_OR_WARNING, false);
            d1.setProblemGroup(new TestProblemGroup("MockInspection"));

            ProblemDescriptor d2 = manager.createProblemDescriptor(psiFile, TextRange.EMPTY_RANGE, "Rule 2", ProblemHighlightType.GENERIC_ERROR_OR_WARNING, false);
            d2.setProblemGroup(new TestProblemGroup("UnknownInspection"));

            ProblemDescriptor d3 = manager.createProblemDescriptor(psiFile, TextRange.EMPTY_RANGE, "Rule 3", ProblemHighlightType.GENERIC_ERROR_OR_WARNING, false);

            problemDescriptionsProcessor.addProblemElement(refEntity, d1);
            problemDescriptionsProcessor.addProblemElement(refEntity, d2);
            problemDescriptionsProcessor.addProblemElement(refEntity, d3);
          });
        }
        return true;
      });
    }
  }
}
