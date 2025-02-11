// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeserver.highlighting.errors;

import com.intellij.java.codeserver.highlighting.JavaCompilationErrorBundle;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.*;

import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * A kind of Java compilation error
 * 
 * @param <Psi> type of context PSI element required for this error
 * @param <Context> additional context required for a particular kind, if any
 */
public sealed interface JavaErrorKind<Psi extends PsiElement, Context> {
  /**
   * @return a key, which uniquely identifies the error kind
   */
  @NotNull @PropertyKey(resourceBundle = JavaCompilationErrorBundle.BUNDLE) String key();

  /**
   * @param psi PSI element associated with an error
   * @param context a context in which the error should be rendered
   * @return rendered localized error description
   */
  @NotNull HtmlChunk description(@NotNull Psi psi, Context context);

  /**
   * @param psi PSI element associated with an error
   * @param context a context in which the error should be rendered
   * @return rendered localized error tooltip; empty chunk if description could be reused
   */
  default @NotNull HtmlChunk tooltip(@NotNull Psi psi, Context context) {
    return HtmlChunk.empty();
  }

  /**
   * @param psi PSI element associated with an error
   * @return error message anchor (must be within the psi). Note that anchor does not depend on context, only on psi
   */
  default @NotNull PsiElement anchor(@NotNull Psi psi) {
    return psi;
  }

  /**
   * @param psi PSI element associated with an error
   * @param context a context in which the error should be rendered
   * @return range within anchor to highlight; or null if the whole anchor should be highlighted
   */
  default @Nullable TextRange range(@NotNull Psi psi, Context context) {
    return null;
  }
  
  /**
   * @param psi PSI element associated with an error
   * @param context a context in which the error should be rendered
   * @return preferred type of highlighting
   */
  default @NotNull JavaErrorHighlightType highlightType(@NotNull Psi psi, Context context) {
    return JavaErrorHighlightType.ERROR;
  }

  /**
   * Simple kind of error without context
   * @param <Psi> type of PSI element where the error could be attached
   */
  final class Simple<Psi extends PsiElement> implements JavaErrorKind<Psi, Void> {
    private final @NotNull @PropertyKey(resourceBundle = JavaCompilationErrorBundle.BUNDLE) String myKey;
    private final @NotNull Function<? super Psi, ? extends HtmlChunk> myDescription;
    private final @NotNull Function<? super Psi, ? extends HtmlChunk> myTooltip;
    private final @NotNull Function<? super Psi, ? extends PsiElement> myAnchor;
    private final @NotNull Function<? super Psi, ? extends TextRange> myRange;
    private final @NotNull Function<? super Psi, JavaErrorHighlightType> myHighlightType;

    private Simple(@NotNull @PropertyKey(resourceBundle = JavaCompilationErrorBundle.BUNDLE) String key,
                   @NotNull Function<? super Psi, ? extends HtmlChunk> description,
                   @NotNull Function<? super Psi, ? extends HtmlChunk> tooltip,
                   @NotNull Function<? super Psi, ? extends PsiElement> anchor,
                   @NotNull Function<? super Psi, ? extends TextRange> range,
                   @NotNull Function<? super Psi, JavaErrorHighlightType> type) {
      myKey = key;
      myDescription = description;
      myTooltip = tooltip;
      myAnchor = anchor;
      myRange = range;
      myHighlightType = type;
    }

    Simple(@NotNull @PropertyKey(resourceBundle = JavaCompilationErrorBundle.BUNDLE) String key) {
      this(key,
           psi -> HtmlChunk.raw(JavaCompilationErrorBundle.message(key)),
           psi -> HtmlChunk.empty(),
           Function.identity(),
           psi -> null,
           psi -> JavaErrorHighlightType.ERROR
      );
    }

    private <T> @NotNull T checkNotNull(T val, String name) {
      if (val == null) {
        throw new NullPointerException("Function '" + name + "' returns null for key " + key());
      }
      return val;
    }

    @Override
    public @NotNull @PropertyKey(resourceBundle = JavaCompilationErrorBundle.BUNDLE) String key() {
      return myKey;
    }

    @Override
    public @NotNull HtmlChunk description(@NotNull Psi psi, Void unused) {
      return checkNotNull(myDescription.apply(psi), "description");
    }

    @Override
    public @NotNull HtmlChunk tooltip(@NotNull Psi psi, Void unused) {
      return checkNotNull(myTooltip.apply(psi), "tooltip");
    }

    @Override
    public @NotNull PsiElement anchor(@NotNull Psi psi) {
      return checkNotNull(myAnchor.apply(psi), "anchor");
    }

    @Override
    public @Nullable TextRange range(@NotNull Psi psi, Void unused) {
      return myRange.apply(psi);
    }

    @Override
    public @NotNull JavaErrorHighlightType highlightType(@NotNull Psi psi, Void unused) {
      return checkNotNull(myHighlightType.apply(psi), "highlightType");
    }

    /**
     * Creates a new instance of Simple with the specified anchor function.
     *
     * @param anchor a function that determines the {@link PsiElement} to be used 
     *               as an anchor for a given Psi object.
     * @return a new Simple instance with the updated anchor function.
     */
    Simple<Psi> withAnchor(@NotNull Function<? super Psi, ? extends PsiElement> anchor) {
      return new Simple<>(myKey, myDescription, myTooltip, anchor, myRange, myHighlightType);
    }

    /**
     * Creates a new instance of Simple with the specified range function.
     *
     * @param range a function that determines the {@link TextRange} for a given Psi object.
     *              The range is relative to anchor returned from {@link #anchor(PsiElement)}
     * @return a new Simple instance with the updated range function.
     */
    Simple<Psi> withRange(@NotNull Function<? super Psi, ? extends TextRange> range) {
      return new Simple<>(myKey, myDescription, myTooltip, myAnchor, range, myHighlightType);
    }

    /**
     * Creates a new instance of Simple with the specified highlight type function.
     *
     * @param type a function that determines the {@link JavaErrorHighlightType} for a given Psi object.
     * @return a new Simple instance with the updated highlight type function.
     */
    Simple<Psi> withHighlightType(@NotNull Function<? super Psi, JavaErrorHighlightType> type) {
      return new Simple<>(myKey, myDescription, myTooltip, myAnchor, myRange, type);
    }

    /**
     * Creates a new instance of Simple with the specified constant highlight type.
     *
     * @param type a {@link JavaErrorHighlightType} for this error kind.
     * @return a new Simple instance with the updated highlight type function.
     */
    Simple<Psi> withHighlightType(@NotNull JavaErrorHighlightType type) {
      return withHighlightType(psi -> type);
    }

    /**
     * Creates a new instance of Simple with the specified description function.
     *
     * @param description a Function that generates a description as an HtmlChunk 
     *                    based on the provided Psi object.
     * @return a new Simple instance with the updated description function.
     */
    Simple<Psi> withDescription(@NotNull Function<? super Psi, ? extends HtmlChunk> description) {
      return new Simple<>(myKey, description, myTooltip, myAnchor, myRange, myHighlightType);
    }

    /**
     * Creates a new instance of Simple with a specified description function.
     *
     * @param description a Function that computes a description based on the given Psi and Context.
     * @return a new Simple instance with the specified description function.
     */
    Simple<Psi> withRawDescription(@NotNull Function<? super Psi, @Nls String> description) {
      return withDescription(psi -> HtmlChunk.raw(description.apply(psi)));
    }

    <Context> Parameterized<Psi, Context> parameterized() {
      return new Parameterized<>(myKey, (psi, ctx) -> myDescription.apply(psi),
                                 (psi, ctx) -> myTooltip.apply(psi),
                                 myAnchor,
                                 (psi, ctx) -> myRange.apply(psi),
                                 (psi, ctx) -> myHighlightType.apply(psi));
    }

    /**
     * @param psi psi element to bind an error instance to
     * @return an instance of this error
     */
    @Contract(pure = true)
    public @NotNull JavaCompilationError<Psi, Void> create(@NotNull Psi psi) {
      return new JavaCompilationError<>(this, psi, null);
    }

    @Override
    public String toString() {
      return "JavaErrorKind[" + myKey + "]";
    }
  }

  /**
   * Kind of error with context
   * @param <Psi> type of PSI element where the error could be attached
   * @param <Context> type of context
   */
  final class Parameterized<Psi extends PsiElement, Context> implements JavaErrorKind<Psi, Context> {
    private final @NotNull @PropertyKey(resourceBundle = JavaCompilationErrorBundle.BUNDLE) String myKey;
    private final @NotNull BiFunction<? super Psi, ? super Context, ? extends HtmlChunk> myDescription;
    private final @NotNull BiFunction<? super Psi, ? super Context, ? extends HtmlChunk> myTooltip;
    private final @NotNull Function<? super Psi, ? extends PsiElement> myAnchor;
    private final @NotNull BiFunction<? super Psi, ? super Context, ? extends TextRange> myRange;
    private final @NotNull BiFunction<? super Psi, ? super Context, JavaErrorHighlightType> myHighlightType;

    private Parameterized(@NotNull @PropertyKey(resourceBundle = JavaCompilationErrorBundle.BUNDLE) String key,
                          @NotNull BiFunction<? super Psi, ? super Context, ? extends HtmlChunk> description,
                          @NotNull BiFunction<? super Psi, ? super Context, ? extends HtmlChunk> tooltip,
                          @NotNull Function<? super Psi, ? extends PsiElement> anchor,
                          @NotNull BiFunction<? super Psi, ? super Context, ? extends TextRange> range,
                          @NotNull BiFunction<? super Psi, ? super Context, JavaErrorHighlightType> type) {
      myKey = key;
      myDescription = description;
      myTooltip = tooltip;
      myAnchor = anchor;
      myRange = range;
      myHighlightType = type;
    }

    private <T> @NotNull T checkNotNull(T val, String name) {
      if (val == null) {
        throw new NullPointerException("Function '" + name + "' returns null for key " + key());
      }
      return val;
    }
    
    Parameterized(@NotNull @PropertyKey(resourceBundle = JavaCompilationErrorBundle.BUNDLE) String key) {
      this(key,
           (psi, ctx) -> HtmlChunk.raw(JavaCompilationErrorBundle.message(key)),
           (psi, ctx) -> HtmlChunk.empty(),
           Function.identity(),
           (psi, ctx) -> null,
           (psi, ctx) -> JavaErrorHighlightType.ERROR);
    }

    @Override
    public @NotNull @PropertyKey(resourceBundle = JavaCompilationErrorBundle.BUNDLE) String key() {
      return myKey;
    }

    @Override
    public @NotNull HtmlChunk description(@NotNull Psi psi, Context context) {
      return checkNotNull(myDescription.apply(psi, context), "description");
    }

    @Override
    public @NotNull HtmlChunk tooltip(@NotNull Psi psi, Context context) {
        return checkNotNull(myTooltip.apply(psi, context), "tooltip");
    }

    @Override
    public @NotNull PsiElement anchor(@NotNull Psi psi) {
      return checkNotNull(myAnchor.apply(psi), "anchor");
    }

    @Override
    public @Nullable TextRange range(@NotNull Psi psi, Context context) {
      return myRange.apply(psi, context);
    }

    @Override
    public @NotNull JavaErrorHighlightType highlightType(@NotNull Psi psi, Context context) {
      return checkNotNull(myHighlightType.apply(psi, context), "highlightType");
    }

    /**
     * @param psi psi element to bind an error instance to
     * @param context context to bind an error instance to
     * @return an instance of this error
     */
    @Contract(pure = true)
    public @NotNull JavaCompilationError<Psi, Context> create(@NotNull Psi psi, Context context) {
      return new JavaCompilationError<>(this, psi, context);
    }

    /**
     * Creates a new instance of Parameterized with a specified anchor function.
     *
     * @param anchor a BiFunction that computes an anchor based on the given Psi and Context
     * @return a new Parameterized instance with the specified anchor function
     */
    Parameterized<Psi, Context> withAnchor(@NotNull Function<? super Psi, ? extends PsiElement> anchor) {
      return new Parameterized<>(myKey, myDescription, myTooltip, anchor, myRange, myHighlightType);
    }

    /**
     * Creates a new instance of Parameterized with the specified range function.
     *
     * @param range a BiFunction that determines the {@link TextRange} for a given Psi object.
     *              The range is relative to anchor returned from {@link #anchor(PsiElement)}
     * @return a new Parameterized instance with the updated range function.
     */
    Parameterized<Psi, Context> withRange(@NotNull BiFunction<? super Psi, ? super Context, ? extends TextRange> range) {
      return new Parameterized<>(myKey, myDescription, myTooltip, myAnchor, range, myHighlightType);
    }

    /**
     * Creates a new instance of Parameterized with the specified highlight type function.
     *
     * @param type a function that determines the {@link JavaErrorHighlightType} for a given Psi object.
     * @return a new Parameterized instance with the updated highlight type function.
     */
    Parameterized<Psi, Context> withHighlightType(@NotNull BiFunction<? super Psi, ? super Context, JavaErrorHighlightType> type) {
      return new Parameterized<>(myKey, myDescription, myTooltip, myAnchor, myRange, type);
    }

    /**
     * Creates a new instance of Parameterized with the specified constant highlight type.
     *
     * @param type a {@link JavaErrorHighlightType} for a given error kind.
     * @return a new Parameterized instance with the updated highlight type function.
     */
    Parameterized<Psi, Context> withHighlightType(@NotNull JavaErrorHighlightType type) {
      return withHighlightType((psi, ctx) -> type);
    }

    /**
     * Creates a new instance of Parameterized with a specified description function.
     *
     * @param description a BiFunction that computes a description based on the given Psi and Context.
     * @return a new Parameterized instance with the specified description function.
     */
    Parameterized<Psi, Context> withDescription(@NotNull BiFunction<? super Psi, ? super Context, ? extends HtmlChunk> description) {
      return new Parameterized<>(myKey, description, myTooltip, myAnchor, myRange, myHighlightType);
    }

    /**
     * Creates a new instance of Parameterized with a specified tooltip function.
     *
     * @param tooltip a BiFunction that computes a tooltip based on the given Psi and Context.
     * @return a new Parameterized instance with the specified tooltip function.
     */
    Parameterized<Psi, Context> withTooltip(@NotNull BiFunction<? super Psi, ? super Context, ? extends HtmlChunk> tooltip) {
      return new Parameterized<>(myKey, myDescription, tooltip, myAnchor, myRange, myHighlightType);
    }

    /**
     * Creates a new instance of Parameterized with a raw description function.
     *
     * @param description a BiFunction that computes a raw description (localized string containing HTML)
     *                    based on the given Psi and Context.
     * @return a new Parameterized instance with the specified raw description function.
     */
    Parameterized<Psi, Context> withRawDescription(@NotNull BiFunction<? super Psi, ? super Context, @Nls String> description) {
      return withDescription((psi, context) -> HtmlChunk.raw(description.apply(psi, context)));
    }

    @Override
    public String toString() {
      return "JavaErrorKind[" + myKey + "]";
    }
  }
}
