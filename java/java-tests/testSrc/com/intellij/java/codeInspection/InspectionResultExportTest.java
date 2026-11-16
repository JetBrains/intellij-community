// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.dataFlow.ConstantValueInspection;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.codeInspection.ex.GlobalInspectionContextImpl;
import com.intellij.codeInspection.ex.GlobalInspectionToolWrapper;
import com.intellij.codeInspection.ex.InspectionManagerEx;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ex.InspectionToolsSupplier;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.java.testFramework.fixtures.LightJava9ModulesFixture;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.profile.codeInspection.BaseInspectionProfileManager;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.InspectionTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.controlflow.SimplifiableConditionalExpressionInspection;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.intellij.java.testFramework.fixtures.LightJava9ModulesFixtureKt.lightJava9ModulesFixture;
import static com.intellij.java.testFramework.fixtures.LightJava9ModulesFixtureKt.lightJava9ModulesProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class InspectionResultExportTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJava9ModulesProjectFixture();
  private final TestFixture<LightJava9ModulesFixture> java9 = lightJava9ModulesFixture(projectFixture);

  @TempDir
  Path tempDir;

  @BeforeEach
  void setUp() {
    InspectionProfileImpl.INIT_INSPECTIONS = true;
  }

  @AfterEach
  void tearDown() {
    InspectionProfileImpl.INIT_INSPECTIONS = false;
  }

  @Test
  public void testExport() throws Exception {
    doTestExport(true, getTools());
  }

  @Test
  public void testExportWithoutResultsView() throws Exception {
    doTestExportWithoutResultsView(false, getTools());
  }

  @Test
  public void testOfflineExportWithoutResultsView() throws Exception {
    doTestExportWithoutResultsView(true, getTools());
  }

  @Test
  public void testExportWithGlobalToolWithoutResultsView() throws Exception {
    var tools = new ArrayList<>(getTools());
    tools.add(new GlobalInspectionToolWrapper(new UnusedDeclarationInspection()));
    doTestExportWithoutResultsView(false, tools);
  }

  @Test
  public void testUiRerunKeepsContextRegistered() {
    LightJavaCodeInsightTestFixture myFixture = java9.get().getFixture();
    java9.get().addTestFile("Foo.java", "class Foo { boolean m() { return 0 == 0; } }");
    var context = createContext(getTools());
    var manager = (InspectionManagerEx)InspectionManager.getInstance(myFixture.getProject());
    var previous = GlobalInspectionContextImpl.TESTING_VIEW;
    GlobalInspectionContextImpl.TESTING_VIEW = true;
    EdtTestUtil.runInEdtAndWait(() -> {
      try {
        context.doInspections(new AnalysisScope(myFixture.getProject()));
        waitForResultsView(context);
        var view = context.getView();
        assertTrue(view.hasProblems());
        assertTrue(manager.getRunningContexts().contains(context));

        view.rerun();
        assertTrue(manager.getRunningContexts().contains(context), "A UI rerun must keep its context registered");
        waitForResultsView(context);
        assertNotSame(view, context.getView());
        assertTrue(context.getView().hasProblems());
        assertTrue(context.areToolsInitialized());
        assertTrue(manager.getRunningContexts().contains(context));

        context.close(false);
        assertFalse(manager.getRunningContexts().contains(context));
        assertFalse(context.areToolsInitialized());
      }
      finally {
        GlobalInspectionContextImpl.TESTING_VIEW = previous;
      }
    });
  }

  private static void waitForResultsView(GlobalInspectionContextImpl context) {
    PlatformTestUtil.waitWithEventsDispatching("Inspection results did not finish updating", () -> {
      var view = context.getView();
      return view != null && !view.isUpdating();
    }, 30);
  }

  private GlobalInspectionContextImpl createContext(List<InspectionToolWrapper<?, ?>> tools) {
    LightJavaCodeInsightTestFixture myFixture = java9.get().getFixture();
    var context = (GlobalInspectionContextImpl)InspectionManager.getInstance(myFixture.getProject()).createNewGlobalContext();
    Disposer.register(myFixture.getTestRootDisposable(), () -> context.close(true));
    var toolSupplier = new InspectionToolsSupplier.Simple(tools);
    Disposer.register(myFixture.getTestRootDisposable(), toolSupplier);
    var profile = new InspectionProfileImpl("test", toolSupplier,
                                            (BaseInspectionProfileManager)InspectionProfileManager.getInstance());
    for (var tool : tools) {
      profile.enableTool(tool.getShortName(), myFixture.getProject());
    }
    context.setExternalProfile(profile);
    return context;
  }

  private void doTestExportWithoutResultsView(boolean offline, List<InspectionToolWrapper<?, ?>> tools) throws Exception {
    var previous = GlobalInspectionContextImpl.TESTING_NON_HEADLESS;
    GlobalInspectionContextImpl.TESTING_NON_HEADLESS = true;
    try {
      doTestExport(offline, tools);
    }
    finally {
      GlobalInspectionContextImpl.TESTING_NON_HEADLESS = previous;
    }
  }

  private void doTestExport(boolean offline, List<InspectionToolWrapper<?, ?>> tools) throws Exception {
    LightJavaCodeInsightTestFixture myFixture = java9.get().getFixture();
    java9.get().addTestFile("Foo.java", """
      class Foo {

          void m() {
              int i = 0 == 0 ? 0 : 0;
              int i2 = 0 == 0 ? 0 : 0;
              int i3 = 0 == 0 ? 0 : 0;
              int i4 = 0 == 0 ? 0 : 0;
              int i5 = 0 == 0 ? 0 : 0;
          }
      }""");

    InspectionManager im = InspectionManager.getInstance(myFixture.getProject());
    AnalysisScope scope = new AnalysisScope(myFixture.getProject());
    List<Path> resultFiles = new ArrayList<>();
    Path outputPath = tempDir;

    var context = createContext(tools);
    assertTrue(context.isViewClosed());
    assertNull(context.getView());

    EdtTestUtil.runInEdtAndWait(() -> {
      ProgressManager.getInstance().run(new Task.Modal(myFixture.getProject(), "", true) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          if (offline) {
            context.launchInspectionsOffline(scope, outputPath, false, resultFiles);
          }
          else {
            context.performInspectionsWithProgressAndExportResults(scope, false, false, outputPath, resultFiles);
          }
        }
      });
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
    });
    assertTrue(context.isViewClosed());
    assertNull(context.getView());
    assertThat(resultFiles).hasSize(tools.size());
    for (var tool : tools) {
      var resultFile = resultFiles.stream()
        .filter(f -> f.getFileName().toString().equals(tool.getShortName() + ".xml"))
        .findAny().orElseThrow(AssertionError::new);
      assertFalse(loadFile(resultFile).getChildren("problem").isEmpty());
    }

    Element dfaResults = resultFiles.stream().filter(f -> f.getFileName().toString().equals("ConstantValue.xml")).findAny().map(InspectionResultExportTest::loadFile).orElseThrow(AssertionError::new);
    Element unnCondResults = resultFiles.stream().filter(f -> f.getFileName().toString().equals("SimplifiableConditionalExpression.xml")).findAny().map(InspectionResultExportTest::loadFile).orElseThrow(AssertionError::new);

    Element expectedDfaResults = JDOMUtil.load("""
                                                 <problems><problem>
                                                   <file>Foo.java</file>
                                                   <line>6</line>
                                                   <problem_class>Constant values</problem_class>
                                                   <description>Condition &lt;code&gt;0 == 0&lt;/code&gt; is always &lt;code&gt;true&lt;/code&gt;</description>
                                                 </problem>
                                                 <problem>
                                                   <file>Foo.java</file>
                                                   <line>7</line>
                                                   <problem_class>Constant values</problem_class>
                                                   <description>Condition &lt;code&gt;0 == 0&lt;/code&gt; is always &lt;code&gt;true&lt;/code&gt;</description>
                                                 </problem>
                                                 <problem>
                                                   <file>Foo.java</file>
                                                   <line>8</line>
                                                   <problem_class>Constant values</problem_class>
                                                   <description>Condition &lt;code&gt;0 == 0&lt;/code&gt; is always &lt;code&gt;true&lt;/code&gt;</description>
                                                 </problem>
                                                 <problem>
                                                   <file>Foo.java</file>
                                                   <line>4</line>
                                                   <problem_class>Constant values</problem_class>
                                                   <description>Condition &lt;code&gt;0 == 0&lt;/code&gt; is always &lt;code&gt;true&lt;/code&gt;</description>
                                                 </problem>
                                                 <problem>
                                                   <file>Foo.java</file>
                                                   <line>5</line>
                                                   <problem_class>Constant values</problem_class>
                                                   <description>Condition &lt;code&gt;0 == 0&lt;/code&gt; is always &lt;code&gt;true&lt;/code&gt;</description>
                                                 </problem></problems>""");
    Element expectedUnnCondResults = JDOMUtil.load("""
                                                     <problems><problem>
                                                       <file>Foo.java</file>
                                                       <line>4</line>
                                                       <problem_class>Simplifiable conditional expression</problem_class>
                                                       <description>&lt;code&gt;0 == 0 ? 0 : 0&lt;/code&gt; can be simplified to '0'</description>
                                                     </problem>
                                                     <problem>
                                                       <file>Foo.java</file>
                                                       <line>5</line>
                                                       <problem_class>Simplifiable conditional expression</problem_class>
                                                       <description>&lt;code&gt;0 == 0 ? 0 : 0&lt;/code&gt; can be simplified to '0'</description>
                                                     </problem>
                                                     <problem>
                                                       <file>Foo.java</file>
                                                       <line>6</line>
                                                       <problem_class>Simplifiable conditional expression</problem_class>
                                                       <description>&lt;code&gt;0 == 0 ? 0 : 0&lt;/code&gt; can be simplified to '0'</description>
                                                     </problem>
                                                     <problem>
                                                       <file>Foo.java</file>
                                                       <line>7</line>
                                                       <problem_class>Simplifiable conditional expression</problem_class>
                                                       <description>&lt;code&gt;0 == 0 ? 0 : 0&lt;/code&gt; can be simplified to '0'</description>
                                                     </problem>
                                                     <problem>
                                                       <file>Foo.java</file>
                                                       <line>8</line>
                                                       <problem_class>Simplifiable conditional expression</problem_class>
                                                       <description>&lt;code&gt;0 == 0 ? 0 : 0&lt;/code&gt; can be simplified to '0'</description>
                                                     </problem></problems>""");

    InspectionTestUtil.compareWithExpected(expectedDfaResults, dfaResults, false);
    InspectionTestUtil.compareWithExpected(expectedUnnCondResults, unnCondResults, false);
    assertTrue(((InspectionManagerEx)im).getRunningContexts().contains(context));
    assertTrue(context.areToolsInitialized());
    var wrappers = context.getTools().values().stream().map(tool -> tool.getTool()).toList();
    var presentations = wrappers.stream().map(context::getPresentation).toList();
    context.close(false);
    assertFalse(((InspectionManagerEx)im).getRunningContexts().contains(context), "A closed export context must leave the running set");
    assertFalse(context.areToolsInitialized(), "A closed export context must release its tools");
    for (var i = 0; i < wrappers.size(); i++) {
      assertNotSame(presentations.get(i), context.getPresentation(wrappers.get(i)));
    }
  }

  static @NotNull Element loadFile(@NotNull Path file) {
    try {
      return JDOMUtil.load(file);
    }
    catch (IOException | JDOMException e) {
      String content = null;
      try {
        content = Files.readString(file);
      }
      catch (IOException ignored) {}
      throw new AssertionError("cannot parse: " + content, e);
    }
  }

  private static @NotNull List<InspectionToolWrapper<?, ?>> getTools() {
    return Arrays.asList(new LocalInspectionToolWrapper(new ConstantValueInspection()), new LocalInspectionToolWrapper(new SimplifiableConditionalExpressionInspection()));
  }
}
