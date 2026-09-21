// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

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
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.LineColumn;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLambdaExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import one.util.streamex.EntryStream;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The shared part of the JSpecify sample corpus check.
 * <p>
 * {@link JSpecifyFilteredAnnotationTest} runs the corpus in the IDE. An LSP test runs the same corpus through the
 * language server. Both use this class, so one marker vocabulary, one set of inspection options and one waiver list
 * serve both.
 * <p>
 * This class is deliberately not a test case. A subclass of {@code UsefulTestCase} asserts in its static initializer
 * that the logger is a test logger, which fails in a language server test, because the server installs its own logger.
 */
public final class JSpecifyCorpus {
  private JSpecifyCorpus() { }

  public static final Pattern JSPECIFY_PATTERN = Pattern.compile("// jspecify_\\w+");
  public static final Pattern TEST_CANNOT_CONVERT = Pattern.compile("// test:cannot-convert.*!>?");

  //each case has its own reason (line number starts from 0)
  public static final Set<Pair<String, Integer>> SKIPPED_PLACES =
      Set.of(
        new Pair<>("WildcardCapturesToBoundOfTypeParameterNotToTypeVariableItself.java", 24),// see: IDEA-377699

        //this set was reverted because it is mostly about unspecified annotation, which we don't support, because it is not in the spec
        new Pair<>("CaptureConvertedUnspecToObject.java", 77), // see: IDEA-380143
        new Pair<>("CaptureConvertedUnspecToOther.java", 77), // see: IDEA-380143
        new Pair<>("DereferenceTypeVariable.java", 123), // see: IDEA-380143
        new Pair<>("MultiBoundTypeVariableUnspecToObject.java", 63), // see: IDEA-380143
        new Pair<>("MultiBoundTypeVariableUnspecToOther.java", 63), // see: IDEA-380143
        new Pair<>("TypeVariableToObject.java", 109), // see: IDEA-380143
        new Pair<>("TypeVariableUnspecToObject.java", 58), // see: IDEA-380143
        new Pair<>("TypeVariableUnspecToObject.java", 78), // see: IDEA-380143
        new Pair<>("TypeVariableUnspecToObject.java", 98), // see: IDEA-380143
        new Pair<>("TypeVariableUnspecToObject.java", 103), // see: IDEA-380143
        new Pair<>("TypeVariableUnspecToObject.java", 108), // see: IDEA-380143
        new Pair<>("TypeVariableUnspecToObject.java", 113), // see: IDEA-380143
        new Pair<>("TypeVariableUnspecToObject.java", 118), // see: IDEA-380143
        new Pair<>("TypeVariableUnspecToParent.java", 53), // see: IDEA-380143
        new Pair<>("TypeVariableUnspecToParent.java", 68), // see: IDEA-380143
        new Pair<>("TypeVariableUnspecToParent.java", 83), // see: IDEA-380143
        new Pair<>("TypeVariableUnspecToParent.java", 98), // see: IDEA-380143
        new Pair<>("UnionTypeArgumentWithUseSite.java", 95) // see: IDEA-380143
      );

  /**
   * Runs the JSpecify inspections on {@code file} and returns the anchor of each marker.
   * <p>
   * This method holds every inspection option that the corpus needs, so that the IDE run and the language server run
   * cannot drift apart. Call it inside a read action.
   */
  @NotNull
  public static Map<PsiElement, String> collectMarkers(@NotNull Project project, @NotNull PsiFile file) {
    Map<PsiElement, String> warnings = new LinkedHashMap<>();
    var dfaInspection = new JSpecifyDataFlowInspection(warnings);
    dfaInspection.TREAT_UNKNOWN_MEMBERS_AS_NULLABLE = true;
    dfaInspection.REPORT_UNSPECIFIED_PARAMETRIC_NULLNESS = true;
    var nullableStuffInspection = new JSpecifyNullableStuffInspection(warnings);
    nullableStuffInspection.REPORT_NOT_NULL_TO_NULLABLE_CONFLICTS_IN_ASSIGNMENTS = true;
    // JSpecify deviates from the JLS here on purpose, see jspecify/jspecify#49
    nullableStuffInspection.REPORT_NULLABLE_PARAMETER_OVERRIDES_NOTNULL = true;
    var notNullFieldNotInitializedInspection = new JSpecifyNotNullFieldNotInitializedInspection(warnings);
    List<LocalInspectionTool> inspections = List.of(dfaInspection, nullableStuffInspection, notNullFieldNotInitializedInspection);

    ProblemsHolder holder = new ProblemsHolder(new InspectionManagerEx(project), file, false);
    for (LocalInspectionTool inspection : inspections) {
      PsiElementVisitor visitor = inspection.buildVisitor(holder, false);
      PsiTreeUtil.processElements(file, e -> {
        e.accept(visitor);
        return true;
      });
    }
    return warnings;
  }

  /**
   * Renders the reported markers into {@code stripped}, in the layout of the corpus.
   *
   * @param actual the start offset of an anchor, mapped to the marker that the analysis reported there
   */
  public static String getActualText(@NotNull String fileName,
                                     @NotNull Map<Integer, String> actual,
                                     @NotNull String stripped,
                                     @NotNull List<ErrorFilter> filters) {
    StringBuilder sb = new StringBuilder();
    int pos = 0;
    TreeMap<Integer, List<String>> map = EntryStream.of(actual)
      .filter(e ->
                !ContainerUtil.exists(filters, filter ->
                  filter.filterExpected(fileName, StringUtil.offsetToLineColumn(stripped, e.getKey()).line, e.getValue())))
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

  /** Restores the markers that {@code filters} keep, and counts every marker in {@code statistic}. */
  @NotNull
  public static String getExpectedText(@NotNull String fileName,
                                       @NotNull String displayPath,
                                       @NotNull String stripped,
                                       @NotNull ErrorContainer container,
                                       @NotNull List<ErrorFilter> filters,
                                       @NotNull Statistic statistic) {
    List<ErrorInfo> errors = new ArrayList<>(container.errors());
    statistic.total.add(errors.size());
    errors.removeIf(er ->
                      ContainerUtil.exists(filters,
                                           m -> !m.shouldCount() &&
                                                m.filterActual(fileName, stripped, er.lineNumber, er.startLineOffset,
                                                               er.message)));
    statistic.valuable.add(errors.size());
    errors.removeIf(er -> {
      return ContainerUtil.exists(filters,
                                  m -> {
                                    boolean matched = m.shouldCount() &&
                                                      m.filterActual(fileName, stripped, er.lineNumber, er.startLineOffset,
                                                                     er.message);
                                    if (matched) {
                                      statistic.skipped.putValue(m.getClass().getName(), new Place(displayPath, er.lineNumber));
                                    }
                                    return matched;
                                  });
    });
    statistic.checked.add(errors.size());
    return restoreWithErrors(stripped, new ErrorContainer(errors));
  }

  @NotNull
  public static String restoreWithErrors(@NotNull String stripped, @NotNull ErrorContainer container) {
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
  public static ErrorContainer createErrorContainer(@NotNull String text) {
    ErrorContainer container = new ErrorContainer(new ArrayList<>());
    JSPECIFY_PATTERN.matcher(text).results().forEach(m -> {
      String message = m.group();
      int start = m.start();
      LineColumn column = StringUtil.offsetToLineColumn(text, start);
      container.errors.add(new ErrorInfo(column.line, column.column, message));
    });

    return container;
  }

  public interface ErrorFilter {
    boolean filterActual(@NotNull String fileName,
                         @NotNull String strippedText,
                         int lineNumber,
                         int startLineOffset,
                         @NotNull String errorMessage);

    /** @param lineNumber the 0-based line of the anchor, not of the marker comment */
    boolean filterExpected(@NotNull String fileName, int lineNumber, @NotNull String errorMessage);

    default boolean shouldCount() {
      return true;
    }

    default void reportUnused() {
    }
  }

  public static class SkipIndividuallyFilter implements ErrorFilter {
    private final Set<Pair<String, Integer>> places;
    private final Set<Pair<String, Integer>> unusedPlaces;
    private final Map<Pair<String, Integer>, Integer> bothUsedPlaces;

    public SkipIndividuallyFilter(Set<Pair<String, Integer>> places) {
      this.places = places;
      this.unusedPlaces = new HashSet<>(places);
      this.bothUsedPlaces = StreamEx.of(places).toMap(t -> t, t -> 2);
    }

    @Override
    public boolean filterActual(@NotNull String fileName,
                                @NotNull String strippedText,
                                int lineNumber,
                                int startLineOffset,
                                @NotNull String errorMessage) {
      if (!errorMessage.contains("jspecify_nullness_mismatch")) return false;
      return filter(Pair.create(fileName, lineNumber));
    }

    @Override
    public boolean filterExpected(@NotNull String fileName, int lineNumber, @NotNull String errorMessage) {
      if (!errorMessage.contains("jspecify_nullness_mismatch")) return false;
      // the marker comment sits on the line above the anchor
      return filter(Pair.create(fileName, lineNumber - 1));
    }

    private boolean filter(Pair<@NotNull @NlsSafe String, Integer> pair) {
      if (places.contains(pair)) {
        unusedPlaces.remove(pair);
        bothUsedPlaces.merge(pair, -1, Integer::sum);
        return true;
      }
      return false;
    }

    @Override
    public void reportUnused() {
      for (Map.Entry<Pair<String, Integer>, Integer> entry : bothUsedPlaces.entrySet()) {
        if(entry.getValue() == 0) {
          unusedPlaces.add(entry.getKey());
        }
      }
      if (unusedPlaces.isEmpty()) return;
      System.out.println("Some filters were unused; probably they are not actual anymore and should be excluded:\n"
                         + StringUtil.join(unusedPlaces, "\n"));
    }
  }

  public static class SkipErrorFilter implements ErrorFilter {
    private final String myMessage;

    public SkipErrorFilter(@NotNull String message) {
      myMessage = message;
    }

    @Override
    public boolean shouldCount() {
      return false;
    }

    @Override
    public boolean filterActual(@NotNull String fileName,
                                @NotNull String strippedText,
                                int lineNumber,
                                int startLineOffset,
                                @NotNull String errorMessage) {
      return errorMessage.contains(myMessage);
    }

    @Override
    public boolean filterExpected(@NotNull String fileName, int lineNumber, @NotNull String errorMessage) {
      return errorMessage.contains(myMessage);
    }
  }

  public static class JSpecifyNullableStuffInspection extends NullableStuffInspection {
    private final Map<PsiElement, String> warnings;

    public JSpecifyNullableStuffInspection(Map<PsiElement, String> warnings) {
      this.warnings = warnings;
    }

    @Override
    protected void reportProblem(@NotNull ProblemsHolder holder, @NotNull PsiElement anchor, @NotNull LocalQuickFix @NotNull [] fixes,
                                 @NotNull @PropertyKey(resourceBundle = JavaAnalysisBundle.BUNDLE) String descriptionKey, @NotNull Object @NotNull[] descriptionArgs,
                                 @NotNull @PropertyKey(resourceBundle = JavaAnalysisBundle.BUNDLE) String tooltipKey, @NotNull Object @NotNull[] tooltipArgs) {
      switch (descriptionKey) {
        case "inspection.nullable.problems.primitive.type.annotation", "inspection.nullable.problems.receiver.annotation",
             "inspection.nullable.problems.outer.type", "inspection.nullable.problems.at.reference.list",
             "inspection.nullable.problems.at.constructor", "inspection.nullable.problems.at.enum.constant" ->
          warnings.put(anchor, "jspecify_nullness_intrinsically_not_nullable");
        case "inspection.nullable.problems.at.wildcard", "inspection.nullable.problems.at.type.parameter",
             "inspection.nullable.problems.at.local.variable" -> warnings.put(anchor, "jspecify_unrecognized_location");
        case "inspection.nullable.problems.Nullable.method.overrides.NotNull",
             "inspection.nullable.problems.NotNull.parameter.overrides.Nullable",
             "inspection.nullable.problems.Nullable.parameter.overrides.NotNull",
             "inspection.nullable.problems.NotNull.type.parameter.bound.overrides.Nullable",
             "complex.problem.with.nullability",
             "assigning.a.class.with.nullable.elements",
             "assigning.a.class.with.notnull.elements",
             "returning.a.class.with.nullable.arguments",
             "returning.a.class.with.notnull.arguments",
             "overriding.a.class.with.nullable.elements",
             "overriding.a.class.with.notnull.elements"
          -> warnings.put(anchor, "jspecify_nullness_mismatch");
        case "non.null.type.argument.is.expected" -> {
          if (anchor instanceof PsiTypeElement typeElement) {
            warnings.put(anchor, typeElement.getType().getNullability().nullability() == Nullability.NULLABLE
                                 ? "jspecify_nullness_mismatch"
                                 : "jspecify_nullness_not_enough_information");
          }
        }
        case "inspection.nullable.problems.method.overrides.NotNull", "inspection.nullable.problems.parameter.overrides.NotNull",
             "inspection.nullable.problems.unspecified.type.parameter.bound.overrides.Nullable" ->
          warnings.put(anchor, "jspecify_nullness_not_enough_information");
        case "inspection.nullable.problems.Nullable.NotNull.conflict" -> warnings.put(anchor, "jspecify_conflicting_annotations");
      }
    }
  }

  public static class JSpecifyNotNullFieldNotInitializedInspection extends NotNullFieldNotInitializedInspection {
    private final Map<PsiElement, String> warnings;

    public JSpecifyNotNullFieldNotInitializedInspection(Map<PsiElement, String> warnings) {
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
  public static class JSpecifyDataFlowInspection extends DataFlowInspectionBase {
    private final Map<PsiElement, String> warnings;

    public JSpecifyDataFlowInspection(Map<PsiElement, String> warnings) {
      this.warnings = warnings;
    }

    @Override
    protected void reportNullabilityProblems(ProblemReporter reporter,
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
                                                 boolean parametricReturn,
                                                 PsiAnnotation anno,
                                                 NullableNotNullManager manager) {
      for (NullabilityProblemKind.NullabilityProblem<?> problem : problems) {
        if (parametricReturn) {
          // Returning a nullable value from a parametric type-variable return type is a mismatch.
          PsiExpression expression = problem.getDereferencedExpression();
          if (expression != null) {
            warnings.put(expression, "jspecify_nullness_mismatch");
          }
          continue;
        }
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
          if (info == null || info.isInferred()) {
            info = DfaPsiUtil.getTypeNullabilityInfo(PsiTypesUtil.getMethodReturnType(method.getBody()));
          }
          Nullability nullability = info == null ? Nullability.UNKNOWN : info.getNullability();
          if (nullability == Nullability.NULLABLE) return null;
          if (nullability == Nullability.UNKNOWN) return "jspecify_nullness_not_enough_information";
        }
      }
      return problem.hasUnknownNullability() ? "jspecify_nullness_not_enough_information" : "jspecify_nullness_mismatch";
    }
  }

  public record ErrorContainer(List<ErrorInfo> errors) {
  }

  public record ErrorInfo(int lineNumber, int startLineOffset, String message) {
  }

  public record Statistic(LongAdder total, LongAdder valuable, LongAdder checked, MultiMap<String, Place> skipped) {
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

  public record Place(String fileName, int lineNumber) {
  }

}