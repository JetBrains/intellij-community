// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.psi;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.controlFlow.AllVariablesControlFlowPolicy;
import com.intellij.psi.controlFlow.ControlFlow;
import com.intellij.psi.controlFlow.ControlFlowFactory;
import com.intellij.psi.controlFlow.ControlFlowPolicy;
import com.intellij.psi.controlFlow.ControlFlowUtil;
import com.intellij.psi.controlFlow.LocalsControlFlowPolicy;
import com.intellij.psi.controlFlow.LocalsOrMyInstanceFieldsControlFlowPolicy;
import com.intellij.psi.controlFlow.WriteVariableInstruction;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NonNls;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture.lightDaemonAnalyzerFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class ControlFlowTest {
  @NonNls private static final String BASE_PATH = "/psi/controlFlow";

  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightDaemonAnalyzerFixture> daemonFixture = lightDaemonAnalyzerFixture(projectFixture);

  private void doTestFor(final File file) throws Exception {
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    String contents = StringUtil.convertLineSeparators(FileUtil.loadFile(file));
    daemonFixture.get().configureFromFileText(file.getName(), contents);
    // extract factory policy class name
    Pattern pattern = Pattern.compile("^// (\\S*).*", Pattern.DOTALL);
    Matcher matcher = pattern.matcher(contents);
    assertTrue(matcher.matches());
    final String policyClassName = matcher.group(1);
    final ControlFlowPolicy policy;
    if ("LocalsOrMyInstanceFieldsControlFlowPolicy".equals(policyClassName)) {
      policy = LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance();
    }
    else {
      policy = null;
    }

    String result = ReadAction.computeBlocking(() -> {
      final int offset = myFixture.getEditor().getCaretModel().getOffset();
      PsiCodeBlock element = PsiTreeUtil.getParentOfType(myFixture.getFile().findElementAt(offset), PsiCodeBlock.class, false);
      assertNotNull(element, "Selected element: " + element);

      ControlFlow controlFlow = ControlFlowFactory.getInstance(myFixture.getProject()).getControlFlow(element, policy);
      return controlFlow.toString().trim();
    });

    final String expectedFullPath = StringUtil.trimEnd(file.getPath(),".java") + ".txt";
    PlatformTestUtil.assertSameLinesWithFile(expectedFullPath, result);
  }

  private void doAllTests() throws Exception {
    final String testDirPath = JavaTestUtil.getJavaTestDataPath().replace(File.separatorChar, '/') + BASE_PATH;
    File testDir = new File(testDirPath);
    final File[] files = testDir.listFiles((dir, name) -> name.endsWith(".java"));
    for (File file : files) {
      doTestFor(file);

      System.out.print(file.getName() + " ");
    }
    System.out.println();
  }

  @Test
  public void test() throws Exception { doAllTests(); }

  @Test
  public void testMethodWithOnlyDoWhileStatementHasExitPoints() throws Exception {
    @Language("JAVA")
    String text = """
      public class Foo {
        public void foo() {
          boolean f;
          do {
            f = something();
          } while (f);
        }
      }""";
    PsiJavaFile file = (PsiJavaFile)daemonFixture.get().configureFromFileText("a.java", text);
    ReadAction.runBlocking(() -> {
      final PsiCodeBlock body = file.getClasses()[0].getMethods()[0].getBody();
      ControlFlow flow = ControlFlowFactory.getInstance(file.getProject()).getControlFlow(body, new LocalsControlFlowPolicy(body), false);
      IntList exitPoints = new IntArrayList();
      ControlFlowUtil.findExitPointsAndStatements(flow, 0, flow.getSize() -1 , exitPoints, ControlFlowUtil.DEFAULT_EXIT_STATEMENTS_CLASSES);
      assertEquals(1, exitPoints.size());
    });
  }

  @Test
  public void testWriteToFieldByInitializer() throws Exception {
    @Language("JAVA")
    String text = """
      public class Foo {
        int i = 3;
      }""";
    PsiJavaFile file = (PsiJavaFile)daemonFixture.get().configureFromFileText("a.java", text);
    ReadAction.runBlocking(() -> {
      final PsiField field = file.getClasses()[0].getFields()[0];
      ControlFlow flow = ControlFlowFactory.getInstance(file.getProject()).getControlFlow(field, AllVariablesControlFlowPolicy.getInstance());
      assertEquals(1, flow.getInstructions().size());
      assertInstanceOf(WriteVariableInstruction.class, flow.getInstructions().get(0));
    });
  }
}
