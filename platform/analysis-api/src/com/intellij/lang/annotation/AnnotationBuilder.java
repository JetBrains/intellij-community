// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.annotation;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

@ApiStatus.NonExtendable
public interface AnnotationBuilder {
  /**
   * Specify annotation range. When not called, the current element range is used,
   * i.e. of the element your {@link Annotator#annotate(PsiElement, AnnotationHolder)} method is called with.
   * The passed {@code range} must be inside the range of the current element being annotated. An empty range will be highlighted as
   * {@code endOffset = startOffset + 1}.
   * This is an intermediate method in the creating new annotation pipeline.
   */
  @Contract(pure = true)
  @NotNull AnnotationBuilder range(@NotNull TextRange range);

  /**
   * Specify annotation range is equal to the {@code element.getTextRange()}.
   * When not called, the current element range is used, i.e. of the element your {@link Annotator#annotate(PsiElement, AnnotationHolder)} method is called with.
   * The range of the {@code element} must be inside the range of the current element being annotated.
   * This is an intermediate method in the creating new annotation pipeline.
   */
  @Contract(pure = true)
  @NotNull AnnotationBuilder range(@NotNull ASTNode element);

  /**
   * Specify annotation range is equal to the {@code element.getTextRange()}.
   * When not called, the current element range is used, i.e. of the element your {@link Annotator#annotate(PsiElement, AnnotationHolder)} method is called with.
   * The range of the {@code element} must be inside the range of the current element being annotated.
   * This is an intermediate method in the creating new annotation pipeline.
   */
  @Contract(pure = true)
  @NotNull AnnotationBuilder range(@NotNull PsiElement element);

  /**
   * Specify annotation should be shown after the end of line. Useful for creating warnings of the type "unterminated string literal".
   * This is an intermediate method in the creating new annotation pipeline.
   */
  @Contract(pure = true)
  @NotNull AnnotationBuilder afterEndOfLine();

  /**
   * Specify annotation should be shown differently - as a sticky popup at the top of the file.
   * Useful for file-wide messages like "This file is in the wrong directory".
   * This is an intermediate method in the creating new annotation pipeline.
   */
  @Contract(pure = true)
  @NotNull AnnotationBuilder fileLevel();

  /**
   * Specify annotation should have an icon at the gutter.
   * Useful for distinguish annotations linked to additional resources like "this is a test method. Click on the icon gutter to run".
   * This is an intermediate method in the creating new annotation pipeline.
   */
  @Contract(pure = true)
  @NotNull AnnotationBuilder gutterIconRenderer(@NotNull GutterIconRenderer gutterIconRenderer);

  /**
   * Specify problem group for the annotation to group corresponding inspections.
   * This is an intermediate method in the creating new annotation pipeline.
   */
  @Contract(pure = true)
  @NotNull AnnotationBuilder problemGroup(@NotNull ProblemGroup problemGroup);

  /**
   * Override text attributes for the annotation to change the defaults specified for the given severity.
   * This is an intermediate method in the creating new annotation pipeline.
   */
  @Contract(pure = true)
  @NotNull AnnotationBuilder enforcedTextAttributes(@NotNull TextAttributes enforcedAttributes);

  /**
   * Specify text attributes for the annotation to change the defaults specified for the given severity.
   * This is an intermediate method in the creating new annotation pipeline.
   */
  @Contract(pure = true)
  @NotNull AnnotationBuilder textAttributes(@NotNull TextAttributesKey enforcedAttributes);

  /**
   * Specify the problem highlight type for the annotation. If not specified, the default type for the severity is used.
   * This is an intermediate method in the creating new annotation pipeline.
   */
  @Contract(pure = true)
  @NotNull AnnotationBuilder highlightType(@NotNull ProblemHighlightType highlightType);

  /**
   * Specify tooltip for the annotation to popup on mouse hover.
   * This is an intermediate method in the creating new annotation pipeline.
   */
  @Contract(pure = true)
  @NotNull AnnotationBuilder tooltip(@NotNull @NlsContexts.Tooltip String tooltip);

  /**
   * Optimization method specifying whether the annotation should be re-calculated when the user types in it.
   * This is an intermediate method in the creating new annotation pipeline.
   */
  @Contract(pure = true)
  @NotNull AnnotationBuilder needsUpdateOnTyping();

  /**
   * Optimization method which explicitly specifies whether the annotation should be re-calculated when the user types in it.
   * This is an intermediate method in the creating new annotation pipeline.
   */
  @Contract(pure = true)
  @NotNull AnnotationBuilder needsUpdateOnTyping(boolean value);

  /**
   * Registers quick fix for this annotation.
   * If you want to tweak the fix, e.g. modify its range, please use {@link #newFix(IntentionAction)} instead.
   * This is an intermediate method in the creating new annotation pipeline.
   */
  @Contract(pure = true)
  @NotNull AnnotationBuilder withFix(@NotNull IntentionAction fix);

  /**
   * Begin registration of the new quickfix associated with the annotation.
   * A typical code looks like this: <p>{@code holder.newFix(action).range(fixRange).registerFix()}</p>
   *
   * @param fix an intention action to be shown for the annotation as a quick fix
   */
  @Contract(pure = true)
  @NotNull FixBuilder newFix(@NotNull IntentionAction fix);

  /**
   * Begin registration of the new quickfix associated with the annotation.
   * A typical code looks like this: <p>{@code holder.newLocalQuickFix(fix).range(fixRange).registerFix()}</p>
   *
   * @param fix               to be shown for the annotation as a quick fix
   * @param problemDescriptor to be passed to {@link LocalQuickFix#applyFix(Project, CommonProblemDescriptor)}
   */
  @Contract(pure = true)
  @NotNull FixBuilder newLocalQuickFix(@NotNull LocalQuickFix fix, @NotNull ProblemDescriptor problemDescriptor);

  interface FixBuilder {
    /**
     * Specify the range for this quick fix. If not specified, the annotation range is used.
     * This is an intermediate method in the registering new quick fix pipeline.
     */
    @Contract(pure = true)
    @NotNull FixBuilder range(@NotNull TextRange range);

    @Contract(pure = true)
    @NotNull FixBuilder key(@NotNull HighlightDisplayKey key);

    /**
     * Specify that the quickfix will be available during batch mode only.
     * This is an intermediate method in the registering new quick fix pipeline.
     */
    @Contract(pure = true)
    @NotNull FixBuilder batch();

    /**
     * Specify that the quickfix will be available both during batch mode and on-the-fly.
     * This is an intermediate method in the registering new quick fix pipeline.
     */
    @Contract(pure = true)
    @NotNull FixBuilder universal();

    /**
     * Finish registration of the new quickfix associated with the annotation.
     * After calling this method you can continue constructing the annotation - e.g. register new fixes.
     * For example:
     * <pre>{@code holder.newAnnotation(range, WARNING, "Illegal element")
     *   .newFix(myRenameFix).key(DISPLAY_KEY).registerFix()
     *   .newFix(myDeleteFix).range(deleteRange).registerFix()
     *   .create();
     * }</pre>
     */
    @Contract(pure = true)
    @NotNull AnnotationBuilder registerFix();
  }

  /**
   * Finish creating new annotation.
   * Calling this method means you've completed your annotation, and it is ready to be shown on screen.
   */
  void create();

  /** @deprecated Use {@link #create()} instead */
  @Deprecated(forRemoval = true)
  Annotation createAnnotation();
}
