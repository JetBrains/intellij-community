// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection;

import com.intellij.codeInspection.dataFlow.StandardDataFlowRunner;
import com.intellij.codeInspection.dataFlow.java.ControlFlowAnalyzer;
import com.intellij.codeInspection.dataFlow.lang.DfaListener;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.BombedProgressIndicator;
import com.intellij.testFramework.LightProjectDescriptor;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

import java.util.function.Predicate;

public class DataFlowInspectionCancellingTest extends DataFlowInspectionTestCase {
  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8_ANNOTATED;
  }

  public void testCancelledStreamInlining() {
    @Language("Java") String content = "import java.util.*;\n" +
                                       "import java.util.function.*;\n" +
                                       "import java.util.stream.*;\n" +
                                       "\n" +
                                       "/** @noinspection ConstantConditions*/ class X {\n" +
                                       "  List<Integer> testTryCatchInStream(List<String> input) {\n" +
                                       "    return input.stream().map(a -> {\n" +
                                       "      try {\n" +
                                       "        return Integer.parseInt(a);\n" +
                                       "      }\n" +
                                       "      catch (NumberFormatException ex) {\n" +
                                       "        return -1;\n" +
                                       "      }\n" +
                                       "    }).filter(Objects::nonNull).collect(Collectors.toList());\n" +
                                       "  }\n" +
                                       "}\n";
    PsiJavaFile file = (PsiJavaFile)PsiFileFactory.getInstance(getProject()).createFileFromText("X.java", JavaFileType.INSTANCE, content);
    PsiMethod method = file.getClasses()[0].getMethods()[0];
    PsiCodeBlock body = method.getBody();
    assertNotNull(body);
    var runner = new StandardDataFlowRunner(getProject());

    Predicate<StackTraceElement> stackTraceElementCondition =
      ste -> ste.getClassName().equals(ControlFlowAnalyzer.class.getName()) && ste.getMethodName().equals("processTryWithResources");
    BombedProgressIndicator.explodeOnStackElement(stackTraceElementCondition).runBombed(() -> {
      runner.analyzeMethod(body, DfaListener.EMPTY);
      fail("Should not be reachable");
    });
  }
}