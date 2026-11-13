// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection;

import com.intellij.codeInspection.dataFlow.StandardDataFlowRunner;
import com.intellij.codeInspection.dataFlow.java.ControlFlowAnalyzer;
import com.intellij.codeInspection.dataFlow.lang.DfaListener;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.BombedProgressIndicator;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

import java.util.function.Predicate;

import static com.intellij.java.codeInspection.DataFlowInspectionFixtureKt.dataFlowInspectionFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

@TestApplication
public class DataFlowInspectionCancellingTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_8_ANNOTATED);
  private final TestFixture<DataFlowInspectionFixture> dataFlow = dataFlowInspectionFixture(projectFixture);

  @Test
  public void testCancelledStreamInlining() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    @Language("Java") String content = """
      import java.util.*;
      import java.util.function.*;
      import java.util.stream.*;

      /** @noinspection ConstantConditions*/ class X {
        List<Integer> testTryCatchInStream(List<String> input) {
          return input.stream().map(a -> {
            try {
              return Integer.parseInt(a);
            }
            catch (NumberFormatException ex) {
              return -1;
            }
          }).filter(Objects::nonNull).collect(Collectors.toList());
        }
      }
      """;
    ReadAction.runBlocking(() -> {
      PsiJavaFile file = (PsiJavaFile)PsiFileFactory.getInstance(myFixture.getProject()).createFileFromText("X.java", JavaFileType.INSTANCE, content);
      PsiMethod method = file.getClasses()[0].getMethods()[0];
      PsiCodeBlock body = method.getBody();
      assertNotNull(body);
      var runner = new StandardDataFlowRunner(myFixture.getProject());

      Predicate<StackTraceElement> stackTraceElementCondition =
        ste -> ste.getClassName().equals(ControlFlowAnalyzer.class.getName()) && ste.getMethodName().equals("processTryWithResources");
      BombedProgressIndicator.explodeOnStackElement(stackTraceElementCondition).runBombed(() -> {
        runner.analyzeMethod(body, DfaListener.EMPTY);
        fail("Should not be reachable");
      });
    });
  }
}