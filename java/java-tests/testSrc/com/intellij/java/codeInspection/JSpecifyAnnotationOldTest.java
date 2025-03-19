// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.Nullability;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.dataFlow.DataFlowInspectionBase;
import com.intellij.codeInspection.dataFlow.DfaPsiUtil;
import com.intellij.codeInspection.dataFlow.NullabilityProblemKind;
import com.intellij.codeInspection.ex.InspectionManagerEx;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.LanguageLevelModuleExtension;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.platform.testFramework.core.FileComparisonFailedError;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import one.util.streamex.EntryStream;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
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
public class JSpecifyAnnotationOldTest extends LightJavaCodeInsightFixtureTestCase {
  private static final LightProjectDescriptor PROJECT_DESCRIPTOR = new DefaultLightProjectDescriptor() {
    @Override
    public Sdk getSdk() {
      return IdeaTestUtil.getMockJdk18();
    }

    @Override
    public void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry) {
      model.getModuleExtension(LanguageLevelModuleExtension.class).setLanguageLevel(LanguageLevel.JDK_1_8);
    }
  }; 
  
  private static final String PACKAGE_NAME = "org.jspecify.annotations";
  private static final Path PATH = Paths.get(JavaTestUtil.getJavaTestDataPath(), "/inspection/dataFlow/codeanalysis/");
  @Parameterized.Parameter
  public String myFileName;

  @Parameterized.Parameters(name = "{0}")
  public static List<String> getData() throws IOException {
    return Files.walk(PATH).filter(Files::isRegularFile).map(PATH::relativize).map(Path::toString).collect(Collectors.toList());
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    // Cannot share project descriptor with previous test, as NullableNotNullManager caches the supported annotations
    // so it won't be updated after Registry.setValue()
    return PROJECT_DESCRIPTOR;
  }

  @Before
  public void before() {
    mockAnnotations();
  }

  private void mockAnnotations() {
    String template = "package " + PACKAGE_NAME + ";import java.lang.annotation.*;\n\n@Target(%s)public @interface %s {}";
    myFixture.addClass(String.format(Locale.ROOT, template, "ElementType.TYPE_USE", "NonNull"));
    myFixture.addClass(String.format(Locale.ROOT, template, "ElementType.TYPE_USE", "Nullable"));
    myFixture.addClass(String.format(Locale.ROOT, template, "ElementType.TYPE", "NullMarked"));
  }

  @Test
  public void test() throws IOException {
    Path path = PATH.resolve(myFileName);
    String fileText = Files.readString(path).replace("\r\n", "\n");
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
        throw new FileComparisonFailedError("Messages don't match", fileText, actualText, path.toString());
      }
    });
  }

  // Reports dataflow problems in code-analysis-conformant way
  private static class CodeAnalysisDataFlowInspection extends DataFlowInspectionBase {
    private final Map<PsiElement, String> actual = new LinkedHashMap<>();

    @Override
    protected void reportNullabilityProblems(ProblemReporter reporter,
                                             List<NullabilityProblemKind.NullabilityProblem<?>> problems) {
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
