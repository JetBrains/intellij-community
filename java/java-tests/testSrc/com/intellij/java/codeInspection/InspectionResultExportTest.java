// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.dataFlow.ConstantValueInspection;
import com.intellij.codeInspection.dataFlow.DataFlowInspection;
import com.intellij.codeInspection.ex.*;
import com.intellij.java.testFramework.fixtures.LightJava9ModulesCodeInsightFixtureTestCase;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.profile.codeInspection.BaseInspectionProfileManager;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.testFramework.InspectionTestUtil;
import com.siyeh.ig.controlflow.SimplifiableConditionalExpressionInspection;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class InspectionResultExportTest extends LightJava9ModulesCodeInsightFixtureTestCase {
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

  public void testExport() throws Exception {

    addTestFile("Foo.java", """
      class Foo {

          void m() {
              int i = 0 == 0 ? 0 : 0;
              int i2 = 0 == 0 ? 0 : 0;
              int i3 = 0 == 0 ? 0 : 0;
              int i4 = 0 == 0 ? 0 : 0;
              int i5 = 0 == 0 ? 0 : 0;
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

    ProgressManager.getInstance().runProcess(() -> context.launchInspectionsOffline(scope, outputPath, false, resultFiles), new ProgressWindow(false, getProject()));
    assertSize(2, resultFiles);

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
                                                       <description>&lt;code&gt;0 == 0 ? 0 : 0&lt;/code&gt; can be simplified to '0' #loc</description>
                                                     </problem>
                                                     <problem>
                                                       <file>Foo.java</file>
                                                       <line>5</line>
                                                       <problem_class>Simplifiable conditional expression</problem_class>
                                                       <description>&lt;code&gt;0 == 0 ? 0 : 0&lt;/code&gt; can be simplified to '0' #loc</description>
                                                     </problem>
                                                     <problem>
                                                       <file>Foo.java</file>
                                                       <line>6</line>
                                                       <problem_class>Simplifiable conditional expression</problem_class>
                                                       <description>&lt;code&gt;0 == 0 ? 0 : 0&lt;/code&gt; can be simplified to '0' #loc</description>
                                                     </problem>
                                                     <problem>
                                                       <file>Foo.java</file>
                                                       <line>7</line>
                                                       <problem_class>Simplifiable conditional expression</problem_class>
                                                       <description>&lt;code&gt;0 == 0 ? 0 : 0&lt;/code&gt; can be simplified to '0' #loc</description>
                                                     </problem>
                                                     <problem>
                                                       <file>Foo.java</file>
                                                       <line>8</line>
                                                       <problem_class>Simplifiable conditional expression</problem_class>
                                                       <description>&lt;code&gt;0 == 0 ? 0 : 0&lt;/code&gt; can be simplified to '0' #loc</description>
                                                     </problem></problems>""");

    InspectionTestUtil.compareWithExpected(expectedDfaResults, dfaResults, false);
    InspectionTestUtil.compareWithExpected(expectedUnnCondResults, unnCondResults, false);
  }

  private static @NotNull Element loadFile(@NotNull Path file) {
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
