// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.impl.nullaway;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassInitializer;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.java.impl.nullaway.NullAwayProblem.Kind.TargetKind.FIELD;
import static com.intellij.java.impl.nullaway.NullAwayProblem.Kind.TargetKind.METHOD;


/// NullAway problem reported in the console.
///
/// @param filePath   path to the file containing the problem
/// @param lineNumber zero-based line number containing the problem
/// @param kind       type of the problem
@NotNullByDefault
@VisibleForTesting
public record NullAwayProblem(Path filePath, int lineNumber, Kind kind) {
  private static final Pattern NULLAWAY_LOG_LINE_PATTERN =
    Pattern.compile(
      "^(\\[(?:ERROR|WARNING|INFO)] )?(?<path>.+?):[(\\[]?(?<line>\\d+)([:,](?<column>\\d++))?[)\\]]?(?:: | )(?:(?:error|warning): )?\\[NullAway] (?<message>.+)");

  @VisibleForTesting
  public static @Nullable NullAwayProblem fromLogLine(String line) {
    if (!line.contains("[NullAway]")) return null;
    Matcher matcher = NULLAWAY_LOG_LINE_PATTERN.matcher(line);
    if (!matcher.find()) return null;
    String path = matcher.group("path");
    Kind kind = Kind.fromMessage(matcher.group("message"));
    if (kind == null) return null;
    try {
      int oneBasedLineNumber = Integer.parseInt(matcher.group("line"));
      return new NullAwayProblem(Path.of(path), oneBasedLineNumber - 1, kind);
    }
    catch (NumberFormatException e) {
      return null;
    }
  }

  @VisibleForTesting
  public @Nullable PsiModifierListOwner findSuppressionTarget(Project project) {
    var psiJavaFile = findFileWithProblem(project);
    if (psiJavaFile == null) return null;
    return findFaultyElement(psiJavaFile);
  }

  private @Nullable PsiJavaFile findFileWithProblem(Project project) {
    Path path = resolvedPath(project);
    if (path == null) return null;
    var virtualFile = LocalFileSystem.getInstance().findFileByNioFile(path);
    if (virtualFile == null) return null;
    var psiFile = PsiManager.getInstance(project).findFile(virtualFile);
    if (!(psiFile instanceof PsiJavaFile psiJavaFile) || psiFile instanceof PsiCompiledElement) return null;
    return psiJavaFile;
  }

  private @Nullable Path resolvedPath(Project project) {
    if (filePath.isAbsolute()) return filePath;
    String basePath = project.getBasePath();
    if (basePath == null) return null;
    return Path.of(basePath).resolve(filePath);
  }

  @Nullable
  private PsiModifierListOwner findFaultyElement(PsiJavaFile file) {
    Project project = file.getProject();
    Document document = PsiDocumentManager.getInstance(project).getDocument(file);
    if (document == null) return null;

    int lineIndex = Math.max(0, lineNumber);
    if (lineIndex >= document.getLineCount()) return null;
    int lineStartOffset = document.getLineStartOffset(lineIndex);
    int lineEndOffset = document.getLineEndOffset(lineIndex);
    var lineRange = TextRange.create(lineStartOffset, lineEndOffset);

    PsiElement atLineStart = file.findElementAt(lineStartOffset);
    PsiClass psiClass = PsiTreeUtil.getParentOfType(atLineStart, PsiClass.class, false);
    if (psiClass == null) return null;
    if (kind.targetKinds().contains(FIELD)) {
      PsiField[] fields = psiClass.getFields();
      var field = findElementInLine(fields, lineRange);
      if (field != null) return field;
    }
    if (kind.targetKinds().contains(METHOD)) {
      var method = findElementInLine(psiClass.getMethods(), lineRange);
      if (method != null) return method;
      method = findElementInLine(psiClass.getConstructors(), lineRange);
      if (method != null) return method;

      PsiClassInitializer classInitializer = PsiTreeUtil.getParentOfType(atLineStart, PsiClassInitializer.class, false);
      if (classInitializer != null && PsiTreeUtil.getParentOfType(classInitializer, PsiClass.class, false) == psiClass) {
        return psiClass;
      }
    }
    return null;
  }

  private static <T extends PsiElement> @Nullable T findElementInLine(T[] psiElements,
                                                                      TextRange lineRange) {
    for (T psiElement : psiElements) {
      if (lineRange.intersects(psiElement.getTextRange())) {
        return psiElement;
      }
    }
    return null;
  }

  @ApiStatus.Internal
  public record Kind(Predicate<String> matchingPredicate, String nameToSuppress, EnumSet<TargetKind> targetKinds) {

    Kind(String matchingRegex, String nameToSuppress, TargetKind elementType, TargetKind... rest) {
      this(Pattern.compile(matchingRegex).asMatchPredicate(), nameToSuppress, EnumSet.of(elementType, rest));
    }

    public enum TargetKind {
      METHOD, FIELD
    }

    public static final Kind NON_NULL_FIELD_NOT_INITIALIZED = new Kind(
      "^@NonNull field (.+) not initialized.*",
      "NullAway.Init",
      FIELD
    );

    public static final Kind INITIALIZER_DOES_NOT_GUARANTEE_INITIALIZATION = new Kind(
      "^initializer method does not guarantee @NonNull field (.+) \\(line \\d+\\) is initialized along all control-flow paths.*",
      "NullAway.Init",
      METHOD
    );

    public static final Kind DEREFERENCED_EXPRESSION_IS_NULLABLE = new Kind(
      "^dereferenced expression .* is @Nullable.*",
      "NullAway",
      METHOD,
      FIELD
    );

    public static final Kind RETURNING_NULLABLE_FROM_NONNULL_METHOD = new Kind(
      "^returning @Nullable expression from method with @NonNull return type.*",
      "NullAway",
      METHOD
    );

    public static final Kind PASSING_NULLABLE_PARAMETER_WHERE_NONNULL_REQUIRED = new Kind(
      "^passing @Nullable parameter '.+' where @NonNull is required.*",
      "NullAway",
      METHOD,
      FIELD
    );

    public static final Kind METHOD_RETURNS_NULLABLE_BUT_SUPERCLASS_METHOD_NONNULL = new Kind(
      "^method returns @Nullable, but superclass method .* returns @NonNull.*",
      "NullAway",
      METHOD
    );

    public static final Kind ASSIGNING_NULLABLE_TO_NONNULL_FIELD = new Kind(
      "^assigning @Nullable expression to @NonNull field.*",
      "NullAway",
      METHOD,
      FIELD
    );

    public static final Kind REFERENCED_METHOD_RETURNS_NULLABLE = new Kind(
      "^referenced method returns @Nullable, but functional interface method .* returns @NonNull.*",
      "NullAway",
      METHOD
    );

    public static final Kind UNBOUND_INSTANCE_METHOD_REFERENCE_FIRST_PARAMETER_NULLABLE = new Kind(
      "^unbound instance method reference cannot be used, as first parameter .* is @Nullable.*",
      "NullAway",
      METHOD
    );

    public static final Kind PARAMETER_IS_NONNULL_BUT_PARAMETER_IN_SUPERCLASS_NULLABLE = new Kind(
      "^parameter .* is @NonNull, but parameter in .* is @Nullable.*",
      "NullAway",
      METHOD
    );

    public static final Kind UNBOXING_OF_NULLABLE_VALUE = new Kind(
      "^unboxing of a @Nullable value.*",
      "NullAway",
      METHOD
    );

    public static final Kind READ_OF_NONNULL_FIELD_BEFORE_INIT = new Kind(
      "^read of @NonNull field .* before initialization.*",
      "NullAway",
      METHOD
    );

    public static final Kind ENHANCED_FOR_EXPRESSION_NULLABLE = new Kind(
      "^enhanced-for expression .* is @Nullable.*",
      "NullAway",
      METHOD
    );

    public static final Kind SYNCHRONIZED_BLOCK_EXPRESSION_NULLABLE = new Kind(
      "^synchronized block expression \".+\" is @Nullable.*",
      "NullAway",
      METHOD
    );

    public static final Kind NONNULL_STATIC_FIELD_NOT_INITIALIZED = new Kind(
      "^@NonNull static field .* not initialized.*",
      "NullAway.Init",
      FIELD
    );

    public static final Kind PASSING_NONNULL_TO_CAST_TO_NONNULL = new Kind(
      "^passing known @NonNull parameter '.+' to CastToNonNullMethod \\(.*\\) .*",
      "NullAway",
      METHOD
    );

    public static final Kind INVOKING_GET_ON_EMPTY_OPTIONAL = new Kind(
      "^Invoking get\\(\\) on possibly empty Optional .*",
      "NullAway",
      METHOD
    );

    public static final Kind SWITCH_EXPRESSION_NULLABLE = new Kind(
      "^switch expression .* is @Nullable.*",
      "NullAway",
      METHOD
    );

    public static final Kind METHOD_ANNOTATED_WITH_ENSURES_NONNULL_BUT_FAILS = new Kind(
      "^Method is annotated with @EnsuresNonNull but fails to ensure .*",
      "NullAway",
      METHOD
    );

    public static final Kind METHOD_ANNOTATED_WITH_ENSURES_NONNULL_IF_BUT_DOES_NOT_ENSURE = new Kind(
      "^Method is annotated with @EnsuresNonNullIf but does not ensure fields .*",
      "NullAway",
      METHOD
    );

    public static final Kind EXPECTED_STATIC_FIELD_NONNULL_DUE_TO_REQUIRES_NONNULL = new Kind(
      "^Expected static field .* to be non-null at call site due to @RequiresNonNull annotation on invoked method.*",
      "NullAway",
      METHOD
    );

    public static final Kind EXPECTED_FIELD_NONNULL_DUE_TO_REQUIRES_NONNULL = new Kind(
      "^Expected field .* to be non-null at call site due to @RequiresNonNull annotation on invoked method.*",
      "NullAway",
      METHOD
    );

    public static final Kind POSTCONDITION_INHERITANCE_VIOLATED = new Kind(
      "^postcondition inheritance is violated, this method must guarantee that all fields .* are @NonNull .*",
      "NullAway",
      METHOD
    );

    public static final Kind PRECONDITION_INHERITANCE_VIOLATED = new Kind(
      "^precondition inheritance is violated, method in child class cannot have a stricter precondition .*",
      "NullAway",
      METHOD
    );

    public static final Kind TYPE_ARGUMENT_CANNOT_BE_NULLABLE = new Kind(
      "^Type argument cannot be @Nullable, as method .*'s type variable .* is not @Nullable.*",
      "NullAway",
      METHOD
    );

    public static final Kind GENERIC_TYPE_PARAMETER_CANNOT_BE_NULLABLE = new Kind(
      "^Generic type parameter cannot be @Nullable, as type variable .* of type .* does not have a @Nullable upper bound.*",
      "NullAway",
      METHOD
    );

    public static final Kind INCOMPATIBLE_TYPES_GENERIC = new Kind(
      "^incompatible types: .* cannot be converted to .*",
      "NullAway",
      METHOD
    );

    public static final Kind CONDITIONAL_EXPRESSION_TYPE_MISMATCH = new Kind(
      "^Conditional expression must have type .* but the sub-expression has type .*",
      "NullAway",
      METHOD
    );

    public static final Kind METHOD_RETURNS_GENERIC_WITH_MISMATCHED_NULLABILITY = new Kind(
      "^Method returns .*, but overridden method returns .*, which has mismatched type parameter nullability.*",
      "NullAway",
      METHOD
    );

    public static final Kind PARAMETER_TYPE_GENERIC_WITH_MISMATCHED_NULLABILITY = new Kind(
      "^Parameter has type .*, but overridden method has parameter type .*, which has mismatched type parameter nullability.*",
      "NullAway",
      METHOD
    );

    public static final Kind WRITING_NULLABLE_INTO_NONNULL_ARRAY = new Kind(
      "^Writing @Nullable expression into array with @NonNull contents.*",
      "NullAway",
      METHOD
    );

    public static final Kind FAILED_TO_INFER_TYPE_ARGUMENT_NULLABILITY = new Kind(
      "^Failed to infer type argument nullability for call .*",
      "NullAway",
      METHOD
    );

    public static final Kind TYPE_USE_NULLABILITY_ON_WRONG_NESTED_CLASS_LEVEL = new Kind(
      "^Type-use nullability annotations should be applied on inner class.*",
      "NullAway",
      METHOD
    );

    private static final List<Kind> ALL_KINDS =
      List.of(NON_NULL_FIELD_NOT_INITIALIZED,
              INITIALIZER_DOES_NOT_GUARANTEE_INITIALIZATION,
              DEREFERENCED_EXPRESSION_IS_NULLABLE,
              RETURNING_NULLABLE_FROM_NONNULL_METHOD,
              PASSING_NULLABLE_PARAMETER_WHERE_NONNULL_REQUIRED,
              METHOD_RETURNS_NULLABLE_BUT_SUPERCLASS_METHOD_NONNULL,
              ASSIGNING_NULLABLE_TO_NONNULL_FIELD,
              REFERENCED_METHOD_RETURNS_NULLABLE,
              UNBOUND_INSTANCE_METHOD_REFERENCE_FIRST_PARAMETER_NULLABLE,
              PARAMETER_IS_NONNULL_BUT_PARAMETER_IN_SUPERCLASS_NULLABLE,
              UNBOXING_OF_NULLABLE_VALUE,
              READ_OF_NONNULL_FIELD_BEFORE_INIT,
              ENHANCED_FOR_EXPRESSION_NULLABLE,
              SYNCHRONIZED_BLOCK_EXPRESSION_NULLABLE,
              NONNULL_STATIC_FIELD_NOT_INITIALIZED,
              PASSING_NONNULL_TO_CAST_TO_NONNULL,
              INVOKING_GET_ON_EMPTY_OPTIONAL,
              SWITCH_EXPRESSION_NULLABLE,
              METHOD_ANNOTATED_WITH_ENSURES_NONNULL_BUT_FAILS,
              METHOD_ANNOTATED_WITH_ENSURES_NONNULL_IF_BUT_DOES_NOT_ENSURE,
              EXPECTED_STATIC_FIELD_NONNULL_DUE_TO_REQUIRES_NONNULL,
              EXPECTED_FIELD_NONNULL_DUE_TO_REQUIRES_NONNULL,
              POSTCONDITION_INHERITANCE_VIOLATED,
              PRECONDITION_INHERITANCE_VIOLATED,
              TYPE_ARGUMENT_CANNOT_BE_NULLABLE,
              GENERIC_TYPE_PARAMETER_CANNOT_BE_NULLABLE,
              INCOMPATIBLE_TYPES_GENERIC,
              CONDITIONAL_EXPRESSION_TYPE_MISMATCH,
              METHOD_RETURNS_GENERIC_WITH_MISMATCHED_NULLABILITY,
              PARAMETER_TYPE_GENERIC_WITH_MISMATCHED_NULLABILITY,
              WRITING_NULLABLE_INTO_NONNULL_ARRAY,
              FAILED_TO_INFER_TYPE_ARGUMENT_NULLABILITY,
              TYPE_USE_NULLABILITY_ON_WRONG_NESTED_CLASS_LEVEL);

    private static @Nullable Kind fromMessage(String message) {
      for (Kind kind : ALL_KINDS) {
        if (kind.matchingPredicate.test(message)) {
          return kind;
        }
      }
      return null;
    }
  }
}
