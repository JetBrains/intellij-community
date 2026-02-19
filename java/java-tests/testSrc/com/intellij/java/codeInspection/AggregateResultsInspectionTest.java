// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.AggregateResultsInspection;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.ex.GlobalInspectionContextImpl;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ex.InspectionToolsSupplier;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.java.testFramework.fixtures.LightJava9ModulesCodeInsightFixtureTestCase;
import com.intellij.lang.annotation.ProblemGroup;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.profile.codeInspection.BaseInspectionProfileManager;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiLiteralExpression;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
}
