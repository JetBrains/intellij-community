// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.psi;

import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.LightJavaCodeInsightTestCase;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ControlFlowTest extends LightJavaCodeInsightTestCase {
  @NonNls private static final String BASE_PATH = "/psi/controlFlow";

  private void doTestFor(final File file) throws Exception {
    String contents = StringUtil.convertLineSeparators(FileUtil.loadFile(file));
    configureFromFileText(file.getName(), contents);
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

    final int offset = getEditor().getCaretModel().getOffset();
    PsiCodeBlock element = PsiTreeUtil.getParentOfType(getFile().findElementAt(offset), PsiCodeBlock.class, false);
    assertNotNull("Selected element: " + element, element);

    ControlFlow controlFlow = ControlFlowFactory.getInstance(getProject()).getControlFlow(element, policy);
    String result = controlFlow.toString().trim();

    final String expectedFullPath = StringUtil.trimEnd(file.getPath(),".java") + ".txt";
    assertSameLinesWithFile(expectedFullPath, result);
  }

  private void doAllTests() throws Exception {
    final String testDirPath = PathManagerEx.getTestDataPath().replace(File.separatorChar, '/') + BASE_PATH;
    File testDir = new File(testDirPath);
    final File[] files = testDir.listFiles((dir, name) -> name.endsWith(".java"));
    for (File file : files) {
      doTestFor(file);

      System.out.print(file.getName() + " ");
    }
    System.out.println();
  }

  public void test() throws Exception { doAllTests(); }

  public void testMethodWithOnlyDoWhileStatementHasExitPoints() throws Exception {
    @Language("JAVA")
    String text = "public class Foo {\n" +
                  "  public void foo() {\n" +
                  "    boolean f;\n" +
                  "    do {\n" +
                  "      f = something();\n" +
                  "    } while (f);\n" +
                  "  }\n" +
                  "}";
    configureFromFileText("a.java", text);
    final PsiCodeBlock body = ((PsiJavaFile)getFile()).getClasses()[0].getMethods()[0].getBody();
    ControlFlow flow = ControlFlowFactory.getInstance(getProject()).getControlFlow(body, new LocalsControlFlowPolicy(body), false);
    IntArrayList exitPoints = new IntArrayList();
    ControlFlowUtil.findExitPointsAndStatements(flow, 0, flow.getSize() -1 , exitPoints, ControlFlowUtil.DEFAULT_EXIT_STATEMENTS_CLASSES);
    assertEquals(1, exitPoints.size());
  }
}
