// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.Nullability;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.dataFlow.DataFlowInspectionBase;
import com.intellij.codeInspection.dataFlow.DfaPsiUtil;
import com.intellij.codeInspection.dataFlow.NullabilityProblemKind;
import com.intellij.codeInspection.ex.InspectionManagerEx;
import com.intellij.codeInspection.nullable.NotNullFieldNotInitializedInspection;
import com.intellij.codeInspection.nullable.NullableStuffInspection;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.LanguageLevelModuleExtension;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.rt.execution.junit.FileComparisonFailure;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import one.util.streamex.EntryStream;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

@RunWith(Parameterized.class)
public class JSpecifyAnnotationTest extends LightJavaCodeInsightFixtureTestCase {
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
  
  private static final String PACKAGE_NAME = "org.jspecify.nullness";
  private static final Path PATH = Paths.get(JavaTestUtil.getJavaTestDataPath(), "/inspection/dataFlow/jspecify/");
  @Parameterized.Parameter
  public String myFileName;

  @Parameterized.Parameters(name = "{0}")
  public static List<String> getData() throws IOException {
    return Files.list(PATH).filter(f -> !f.getFileName().toString().startsWith("."))
      .filter(f -> Files.isDirectory(f) || f.toString().endsWith(".java"))
      .map(PATH::relativize).map(Path::toString).collect(Collectors.toList());
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    // Cannot share project descriptor with previous test, as NullableNotNullManager caches the supported annotations
    // so it won't be updated after Registry.setValue()
    return PROJECT_DESCRIPTOR;
  }

  @Before
  public void before() {
    Registry.get("java.jspecify.annotations.available").setValue(true, getTestRootDisposable());
    mockAnnotations();
  }

  private void mockAnnotations() {
    String template = "package " + PACKAGE_NAME + ";import java.lang.annotation.*;\n\n@Target(%s)public @interface %s {}";
    myFixture.addClass(String.format(Locale.ROOT, template, "ElementType.TYPE_USE", "NonNull"));
    myFixture.addClass(String.format(Locale.ROOT, template, "ElementType.TYPE_USE", "Nullable"));
    myFixture.addClass(String.format(Locale.ROOT, template, "ElementType.TYPE_USE", "NullnessUnspecified"));
    myFixture.addClass(String.format(Locale.ROOT, template, "ElementType.TYPE, ElementType.PACKAGE", "NullMarked"));
  }

  @Test
  public void test() throws IOException {
    Path path = PATH.resolve(myFileName);
    boolean dirMode = Files.isDirectory(path);
    List<Path> files = Files.walk(path)
      .filter(p -> Files.isRegularFile(p)).filter(p -> p.getFileName().toString().endsWith(".java"))
      .collect(Collectors.toList());
    if (files.isEmpty()) {
      throw new IllegalStateException("No Java files");
    }
    class FileData {
      final Path path;
      final String fileText;
      final String stripped;
      final PsiFile psiFile;

      FileData(Path path, String fileText, String stripped, PsiFile psiFile) {
        this.path = path;
        this.fileText = fileText;
        this.stripped = stripped;
        this.psiFile = psiFile;
      }
    }
    List<FileData> fileData = new ArrayList<>();
    for (Path file : files) {
      String fileText = Files.readString(file).replace("\r\n", "\n");
      String stripped = fileText.replaceAll("// jspecify_\\w+", "");
      String relativeFile = FileUtil.toSystemIndependentName((dirMode ? path.relativize(file) : file.getFileName()).toString());
      PsiFile psiFile = myFixture.addFileToProject(relativeFile, stripped);
      fileData.add(new FileData(file, fileText, stripped, psiFile));
    }
    for (FileData data : fileData) {
      String fileText = data.fileText;
      String stripped = data.stripped;
      PsiFile file = data.psiFile;

      Map<PsiElement, String> actual = new LinkedHashMap<>();
      var dfaInspection = new JSpecifyDataFlowInspection(actual);
      dfaInspection.TREAT_UNKNOWN_MEMBERS_AS_NULLABLE = true;
      var nullableStuffInspection = new JSpecifyNullableStuffInspection(actual);
      var notNullFieldNotInitializedInspection = new JSpecifyNotNullFieldNotInitializedInspection(actual);
      List<LocalInspectionTool> inspections = List.of(dfaInspection, nullableStuffInspection, notNullFieldNotInitializedInspection);
      ReadAction.run(() -> {
        ProblemsHolder holder = new ProblemsHolder(new InspectionManagerEx(getProject()), file, false);
        for (LocalInspectionTool inspection : inspections) {
          PsiElementVisitor visitor = inspection.buildVisitor(holder, false);
          PsiTreeUtil.processElements(file, e -> {
            e.accept(visitor);
            return true;
          });
        }
        String actualText = getActualText(actual, stripped);
        if (!fileText.equals(actualText)) {
          throw new FileComparisonFailure("Messages don't match ("+data.path.getFileName().toString()+")", 
                                          fileText, actualText, data.path.toString());
        }
      });
    }
  }
  
  private static class JSpecifyNullableStuffInspection extends NullableStuffInspection {
    private final Map<PsiElement, String> warnings;

    JSpecifyNullableStuffInspection(Map<PsiElement, String> warnings) {
      this.warnings = warnings;
    }

    @Override
    protected void reportProblem(@NotNull ProblemsHolder holder,
                                 @NotNull PsiElement anchor,
                                 LocalQuickFix @NotNull [] fixes,
                                 @NotNull String messageKey, Object... args) {
      switch (messageKey) {
        case "inspection.nullable.problems.primitive.type.annotation":
        case "inspection.nullable.problems.receiver.annotation":
        case "inspection.nullable.problems.outer.type":
        case "inspection.nullable.problems.at.reference.list":
        case "inspection.nullable.problems.at.constructor":
        case "inspection.nullable.problems.at.enum.constant":
          warnings.put(anchor, "jspecify_nullness_intrinsically_not_nullable");
          break;
        case "inspection.nullable.problems.at.wildcard":
        case "inspection.nullable.problems.at.type.parameter":
        case "inspection.nullable.problems.at.local.variable":
          warnings.put(anchor, "jspecify_unrecognized_location");
          break;
        case "inspection.nullable.problems.Nullable.method.overrides.NotNull":
        case "inspection.nullable.problems.NotNull.parameter.overrides.Nullable":
          warnings.put(anchor, "jspecify_nullness_mismatch");
          break;
        case "inspection.nullable.problems.method.overrides.NotNull":
        case "inspection.nullable.problems.parameter.overrides.NotNull":
          warnings.put(anchor, "jspecify_nullness_not_enough_information");
          break;
        case "inspection.nullable.problems.Nullable.NotNull.conflict":
          warnings.put(anchor, "jspecify_conflicting_annotations");
          break;
      }
    }
  }

  private static class JSpecifyNotNullFieldNotInitializedInspection extends NotNullFieldNotInitializedInspection {
    private final Map<PsiElement, String> warnings;

    JSpecifyNotNullFieldNotInitializedInspection(Map<PsiElement, String> warnings) {
      this.warnings = warnings;
    }

    @Override
    protected void reportProblem(@NotNull ProblemsHolder holder,
                                 PsiElement anchor,
                                 String message,
                                 List<LocalQuickFix> fixes) {
      warnings.put(anchor, "jspecify_nullness_mismatch");
    }
  }

  // Reports dataflow problems in code-analysis-conformant way
  private static class JSpecifyDataFlowInspection extends DataFlowInspectionBase {
    private final Map<PsiElement, String> warnings;

    JSpecifyDataFlowInspection(Map<PsiElement, String> warnings) {
      this.warnings = warnings;
    }

    @Override
    protected void reportNullabilityProblems(DataFlowInspectionBase.ProblemReporter reporter,
                                             List<NullabilityProblemKind.NullabilityProblem<?>> problems,
                                             Map<PsiExpression, DataFlowInspectionBase.ConstantResult> expressions) {
      for (NullabilityProblemKind.NullabilityProblem<?> problem : problems) {
        String warning = getJSpecifyWarning(problem);
        if (warning != null) {
          warnings.put(problem.getDereferencedExpression(), warning);
        }
      }
    }
    
    private static @Nullable String getJSpecifyWarning(NullabilityProblemKind.NullabilityProblem<?> problem) {
      PsiExpression expression = problem.getDereferencedExpression();
      if (expression == null) return null;
      if (problem.getKind() == NullabilityProblemKind.passingToNonAnnotatedParameter) return null;
      if (problem.getKind() == NullabilityProblemKind.nullableReturn) {
        PsiType returnType = PsiTypesUtil.getMethodReturnType(expression);
        Nullability nullability = DfaPsiUtil.getTypeNullability(returnType);
        if (nullability == Nullability.NULLABLE) return null;
        if (nullability == Nullability.UNKNOWN) return "jspecify_nullness_not_enough_information";
      }
      return problem.hasUnknownNullability() ? "jspecify_nullness_not_enough_information" : "jspecify_nullness_mismatch";
    }
  }
  
  String getActualText(Map<PsiElement, String> actual, String stripped) {
    StringBuilder sb = new StringBuilder();
    int pos = 0;
    TreeMap<Integer, List<String>> map = EntryStream.of(actual)
      .mapKeys(e -> e.getTextRange().getStartOffset())
      .grouping(TreeMap::new, Collectors.toList());
    for (String str : stripped.split("\n", -1)) {
      int endPos = pos + str.length() + 1;
      String warnings = StreamEx.of(map.subMap(pos, endPos).values()).flatMap(List::stream).distinct().joining(" & ");
      if (!warnings.isEmpty()) {
        sb.append("// ").append(warnings);
      }
      if (sb.length() > 0) sb.append("\n");
      pos = endPos;
      sb.append(str);
    }
    return sb.toString();
  }
}
