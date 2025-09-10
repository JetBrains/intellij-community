// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.Nullability;
import com.intellij.codeInsight.NullabilityAnnotationInfo;
import com.intellij.codeInsight.NullableNotNullManager;
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
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.LineColumn;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.platform.testFramework.core.FileComparisonFailedError;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import one.util.streamex.EntryStream;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.LongAdder;
import java.util.regex.Pattern;
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

  private static final String PACKAGE_NAME = "org.jspecify.annotations";
  private static final Path PATH = Paths.get(JavaTestUtil.getJavaTestDataPath(), "/inspection/dataFlow/jspecify/");
  private static final Pattern JSPECIFY_PATTERN = Pattern.compile("// jspecify_\\w+");
  private static final Pattern TEST_CANNOT_CONVERT = Pattern.compile("// test:cannot-convert.*!>?");
  @Parameterized.Parameter
  public String myFileName;

  private static final Statistic STATISTIC = new Statistic(new LongAdder(), new LongAdder(), new LongAdder(), MultiMap.create());

  @AfterClass
  public static void afterClass() {
    System.out.println(STATISTIC);
  }

  @Parameterized.Parameters(name = "{0}")
  public static List<String> getData() throws IOException {
    return Files.list(PATH).filter(f -> !f.getFileName().toString().startsWith("."))
      .filter(f -> Files.isDirectory(f) || f.toString().endsWith(".java"))
      .map(PATH::relativize).map(Path::toString).collect(Collectors.toList());
  }

  protected List<ErrorFilter> getErrorFilter() {
    return Collections.emptyList();
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
    myFixture.addClass(String.format(Locale.ROOT, template, "ElementType.TYPE_USE", "NullnessUnspecified"));
    myFixture.addClass(String.format(Locale.ROOT, template, "ElementType.TYPE, ElementType.PACKAGE", "NullMarked"));
    myFixture.addClass(String.format(Locale.ROOT, template, "ElementType.TYPE, ElementType.PACKAGE", "NullUnmarked"));
  }

  @Test
  public void test() throws IOException {
    Path path = PATH.resolve(myFileName);
    boolean dirMode = Files.isDirectory(path);
    List<Path> files = Files.walk(path)
      .filter(p -> Files.isRegularFile(p)).filter(p -> p.getFileName().toString().endsWith(".java"))
      .toList();
    if (files.isEmpty()) {
      throw new IllegalStateException("No Java files");
    }
    List<FileData> fileData = new ArrayList<>();
    for (Path file : files) {
      String fileText = Files.readString(file).replace("\r\n", "\n");
      fileText = TEST_CANNOT_CONVERT.matcher(fileText).replaceAll("// jspecify_nullness_mismatch");
      String stripped = JSPECIFY_PATTERN.matcher(fileText).replaceAll("");
      String relativeFile = FileUtil.toSystemIndependentName((dirMode ? path.relativize(file) : file.getFileName()).toString());
      PsiFile psiFile = myFixture.addFileToProject(relativeFile, stripped);
      fileData.add(new FileData(file, fileText, stripped, psiFile, createErrorContainer(fileText)));
    }
    for (FileData data : fileData) {
      String fileText = getExpectedTest(data, getErrorFilter());
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
        if (!getErrorFilter().isEmpty()) {
          assertEquals("Messages don't match (" + data.path.getFileName().toString() + ")",
                       fileText, actualText);
        }
        if (getErrorFilter().isEmpty() && !fileText.equals(actualText)) {
          throw new FileComparisonFailedError("Messages don't match (" + data.path.getFileName().toString() + ")",
                                              fileText, actualText, data.path.toString());
        }
      });
    }
  }

  @NotNull
  private static String getExpectedTest(@NotNull FileData data,
                                        @NotNull List<ErrorFilter> modificators) {
    ErrorContainer container = data.errorContainer;
    List<ErrorInfo> errors = new ArrayList<>(container.errors());
    STATISTIC.total.add(errors.size());
    errors.removeIf(er ->
                                  ContainerUtil.exists(modificators,
                                                       m -> !m.shouldCount() && m.filterActual(data.psiFile, data.stripped, er.lineNumber, er.startLineOffset, er.message)));
    STATISTIC.valuable.add(errors.size());
    errors.removeIf(er -> {
      return ContainerUtil.exists(modificators,
                                  m -> {
                                    boolean matched = m.shouldCount() &&
                                                      m.filterActual(data.psiFile, data.stripped, er.lineNumber, er.startLineOffset,
                                                                     er.message);
                                    if (matched) {
                                      STATISTIC.skipped.putValue(m.getClass().getName(), new Place(data.path.toString(), er.lineNumber));
                                    }
                                    return matched;
                                  });
    });
    STATISTIC.checked.add(errors.size());
    String restoredText = restoreWithErrors(data.stripped, new ErrorContainer(errors));
    if (modificators.isEmpty()) {
      assertEquals("incorrect restored file", data.fileText, restoredText);
    }
    return restoredText;
  }

  @NotNull
  private static String restoreWithErrors(@NotNull String stripped, @NotNull ErrorContainer container) {
    List<Pair<Integer, String>> indexToText = ContainerUtil.map(container.errors(), error -> Pair.create(
      StringUtil.lineColToOffset(stripped, error.lineNumber, error.startLineOffset), error.message));
    StringBuilder sb = new StringBuilder(stripped);
    int additionalOffset = 0;
    for (Pair<Integer, String> pair : indexToText) {
      String additionalText = pair.second;
      sb.insert(pair.first + additionalOffset, additionalText);
      additionalOffset += additionalText.length();
    }
    return sb.toString();
  }

  @NotNull
  private static ErrorContainer createErrorContainer(@NotNull String text) {
    ErrorContainer container = new ErrorContainer(new ArrayList<>());
    JSPECIFY_PATTERN.matcher(text).results().forEach(m -> {
      String message = m.group();
      int start = m.start();
      LineColumn column = StringUtil.offsetToLineColumn(text, start);
      container.errors.add(new ErrorInfo(column.line, column.column, message));
    });

    return container;
  }

  static class JSpecifyNullableStuffInspection extends NullableStuffInspection {
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
        case "inspection.nullable.problems.primitive.type.annotation", "inspection.nullable.problems.receiver.annotation",
          "inspection.nullable.problems.outer.type", "inspection.nullable.problems.at.reference.list",
          "inspection.nullable.problems.at.constructor", "inspection.nullable.problems.at.enum.constant" ->
          warnings.put(anchor, "jspecify_nullness_intrinsically_not_nullable");
        case "inspection.nullable.problems.at.wildcard", "inspection.nullable.problems.at.type.parameter",
          "inspection.nullable.problems.at.local.variable" ->
          warnings.put(anchor, "jspecify_unrecognized_location");
        case "inspection.nullable.problems.Nullable.method.overrides.NotNull",
             "inspection.nullable.problems.NotNull.parameter.overrides.Nullable",
             "assigning.a.collection.of.nullable.elements"
        //,  "non.null.type.argument.is.expected"  //todo see IDEA-377707
          ->
          warnings.put(anchor, "jspecify_nullness_mismatch");
        case "inspection.nullable.problems.method.overrides.NotNull", "inspection.nullable.problems.parameter.overrides.NotNull" ->
          warnings.put(anchor, "jspecify_nullness_not_enough_information");
        case "inspection.nullable.problems.Nullable.NotNull.conflict" -> warnings.put(anchor, "jspecify_conflicting_annotations");
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
  static class JSpecifyDataFlowInspection extends DataFlowInspectionBase {
    private final Map<PsiElement, String> warnings;

    JSpecifyDataFlowInspection(Map<PsiElement, String> warnings) {
      this.warnings = warnings;
    }

    @Override
    protected void reportNullabilityProblems(DataFlowInspectionBase.ProblemReporter reporter,
                                             List<NullabilityProblemKind.NullabilityProblem<?>> problems) {
      for (NullabilityProblemKind.NullabilityProblem<?> problem : problems) {
        String warning = getJSpecifyWarning(problem);
        if (warning != null) {
          warnings.put(problem.getDereferencedExpression(), warning);
        }
      }
    }

    @Override
    protected void reportNullableReturnsProblems(ProblemReporter reporter,
                                                 List<NullabilityProblemKind.NullabilityProblem<?>> problems,
                                                 Nullability nullability,
                                                 PsiAnnotation anno,
                                                 NullableNotNullManager manager) {
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
        final PsiElement methodOrLambda = PsiTreeUtil.getParentOfType(expression, PsiMethod.class, PsiLambdaExpression.class);
        if (methodOrLambda instanceof PsiMethod method) {
          NullabilityAnnotationInfo info =
            NullableNotNullManager.getInstance(methodOrLambda.getProject()).findEffectiveNullabilityInfo(method);
          if (info == null || info.isInferred()) info = DfaPsiUtil.getTypeNullabilityInfo(PsiTypesUtil.getMethodReturnType(method.getBody()));
          Nullability nullability = info == null ? Nullability.UNKNOWN : info.getNullability();
          if (nullability == Nullability.NULLABLE) return null;
          if (nullability == Nullability.UNKNOWN) return "jspecify_nullness_not_enough_information";
        }
      }
      return problem.hasUnknownNullability() ? "jspecify_nullness_not_enough_information" : "jspecify_nullness_mismatch";
    }
  }

  String getActualText(Map<PsiElement, String> actual, String stripped) {
    StringBuilder sb = new StringBuilder();
    int pos = 0;
    TreeMap<Integer, List<String>> map = EntryStream.of(actual)
      .filter(e ->
                !ContainerUtil.exists(getErrorFilter(), filter ->
                  filter.filterExpected(e.getKey(), e.getValue())))
      .mapKeys(e -> e.getTextRange().getStartOffset())
      .grouping(TreeMap::new, Collectors.toList());
    for (String str : stripped.split("\n", -1)) {
      int endPos = pos + str.length() + 1;
      String warnings = StreamEx.of(map.subMap(pos, endPos).values()).flatMap(List::stream).distinct().joining(" & ");
      if (!warnings.isEmpty()) {
        sb.append("// ").append(warnings);
      }
      if (!sb.isEmpty()) sb.append("\n");
      pos = endPos;
      sb.append(str);
    }
    return sb.toString();
  }

  private record FileData(Path path, String fileText, String stripped, PsiFile psiFile, ErrorContainer errorContainer) {
  }

  private record ErrorContainer(List<ErrorInfo> errors) {
  }

  private record ErrorInfo(int lineNumber, int startLineOffset, String message) {
  }

  protected interface ErrorFilter {
    boolean filterActual(@NotNull PsiFile file,
                         @NotNull String strippedText,
                         int lineNumber,
                         int startLineOffset,
                         @NotNull String errorMessage);

    boolean filterExpected(@NotNull PsiElement psiElement, @NotNull String errorMessage);

    default boolean shouldCount() {
      return true;
    }
  }

  record Statistic(LongAdder total, LongAdder valuable, LongAdder checked, MultiMap<String, Place> skipped) {
    @Override
    public String toString() {
      return "Statistic{" +
             "total=" + total +
             ", valuable=" + valuable +
             ", checked=" + checked +
             ", skipped=\n" + prepareToString(skipped) +
             '}';
    }

    private static String prepareToString(MultiMap<String, Place> map) {
      StringBuilder sb = new StringBuilder();
      for (Map.Entry<String, Collection<Place>> entry : map.entrySet()) {
        sb.append(entry.getKey()).append(" (").append(entry.getValue().size()).append(")").append(": \n");
        for (Place place : entry.getValue()) {
          sb.append("  ").append(place.fileName).append(":").append(place.lineNumber).append("\n");
        }
        sb.append("\n");
      }
      return sb.toString();
    }
  }

  record Place(String fileName, int lineNumber){}
}
