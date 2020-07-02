// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.Nullability;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.dataFlow.DataFlowInspectionBase;
import com.intellij.codeInspection.dataFlow.DfaPsiUtil;
import com.intellij.codeInspection.dataFlow.NullabilityProblemKind;
import com.intellij.codeInspection.ex.InspectionManagerEx;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.rt.execution.junit.FileComparisonFailure;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import one.util.streamex.EntryStream;
import one.util.streamex.StreamEx;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@RunWith(Parameterized.class)
public class CodeAnalysisAnnotationsTest extends LightJavaCodeInsightFixtureTestCase {
  private static final String PACKAGE_NAME = "codeanalysis.experimental.annotations";
  private static final Path PATH = Paths.get(JavaTestUtil.getJavaTestDataPath(), "/inspection/dataFlow/codeanalysis/");
  @Parameterized.Parameter
  public String myFileName;

  @Parameterized.Parameters(name = "{0}")
  public static List<String> getData() throws IOException {
    return Files.walk(PATH).filter(Files::isRegularFile).map(PATH::relativize).map(Path::toString).collect(Collectors.toList());
  }

  @Before
  public void before() {
    Registry.get("java.codeanalysis.annotations.available").setValue(true, getTestRootDisposable());
    mockAnnotations();
  }

  private void mockAnnotations() {
    String template = "package " + PACKAGE_NAME + ";import java.lang.annotation.*;\n\n@Target(%s)public @interface %s {}";
    myFixture.addClass(String.format(Locale.ROOT, template, "ElementType.TYPE_USE", "NotNull"));
    myFixture.addClass(String.format(Locale.ROOT, template, "ElementType.TYPE_USE", "Nullable"));
    myFixture.addClass(String.format(Locale.ROOT, template, "ElementType.TYPE", "DefaultNotNull"));
  }

  @Test
  public void test() throws IOException {
    Path path = PATH.resolve(myFileName);
    String fileText = new String(Files.readAllBytes(path), CharsetToolkit.UTF8_CHARSET).replace("\r\n", "\n");
    String stripped = fileText.replaceAll("/\\*ca-[a-z\\-]+\\*/", "");
    PsiFile file = myFixture.configureByText(path.getFileName().toString(), stripped);

    CodeAnalysisDataFlowInspection inspection = new CodeAnalysisDataFlowInspection();
    ReadAction.run(() -> {
      PsiElementVisitor visitor = inspection.buildVisitor(new ProblemsHolder(new InspectionManagerEx(getProject()), file, false), false);
      PsiTreeUtil.processElements(file, e -> {
        e.accept(visitor);
        return true;
      });
      String actualText = inspection.getActualText(stripped);
      if (!fileText.equals(actualText)) {
        throw new FileComparisonFailure("Messages don't match", fileText, actualText, path.toString());
      }
    });
  }

  // Reports dataflow problems in code-analysis-conformant way
  private static class CodeAnalysisDataFlowInspection extends DataFlowInspectionBase {
    private final Map<PsiElement, String> actual = new LinkedHashMap<>();

    @Override
    protected void reportNullabilityProblems(DataFlowInspectionBase.ProblemReporter reporter,
                                             List<NullabilityProblemKind.NullabilityProblem<?>> problems,
                                             Map<PsiExpression, DataFlowInspectionBase.ConstantResult> expressions) {
      for (NullabilityProblemKind.NullabilityProblem<?> problem : problems) {
        PsiExpression expression = problem.getDereferencedExpression();
        if (expression != null) {
          if (problem.getKind() == NullabilityProblemKind.nullableReturn) {
            PsiType returnType = PsiTypesUtil.getMethodReturnType(expression);
            if (DfaPsiUtil.getTypeNullability(returnType) == Nullability.NULLABLE) continue;
          }
          actual.put(expression, "ca-nullable-to-not-null");
        }
      }
    }

    String getActualText(String stripped) {
      Map<Integer, String> map = EntryStream.of(this.actual)
        .mapKeys(e -> e.getTextRange().getStartOffset())
        .mapValues(v -> "/*" + v + "*/")
        .grouping(Collectors.joining());
      return StreamEx.ofKeys(map).sorted().prepend(0).append(stripped.length())
        .pairMap((prev, next) -> stripped.substring(prev, next) + map.getOrDefault(next, ""))
        .joining();
    }
  }
}
