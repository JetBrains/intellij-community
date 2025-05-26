// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeserver.highlighting.errors;

import com.intellij.java.codeserver.highlighting.JavaCompilationErrorBundle;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.*;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.ToIntBiFunction;
import java.util.function.ToIntFunction;

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
   * @return user-readable localized error description (plain text)
   */
  default @NotNull @Nls String description(@NotNull Psi psi, Context context) {
    return JavaCompilationErrorBundle.message(key());
  }

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
   * @param context a context in which the error should be rendered
   * @return range within the file to highlight
   */
  default @NotNull TextRange range(@NotNull Psi psi, Context context) {
    return psi.getTextRange();
  }

  /**
   * @param psi PSI element associated with an error
   * @param context a context in which the error should be rendered
   * @return navigation shift (non-negative) relative to the start of reported {@link #range(PsiElement, Object)}.
   * If navigation to an error is supported, this could be used to navigate to a specific offset withing the range,
   * instead of its beginning.
   */
  default @Range(from = 0, to = Integer.MAX_VALUE) int navigationShift(@NotNull Psi psi, Context context) {
    return 0;
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
    private final @Nullable Function<? super Psi, @Nls String> myDescription;
    private final @Nullable Function<? super Psi, ? extends HtmlChunk> myTooltip;
    private final @Nullable Function<? super Psi, ? extends @NotNull TextRange> myRange;
    private final @Nullable Function<? super Psi, JavaErrorHighlightType> myHighlightType;
    private final @Nullable ToIntFunction<? super Psi> myNavigationShift;

    private Simple(@NotNull @PropertyKey(resourceBundle = JavaCompilationErrorBundle.BUNDLE) String key,
                   @Nullable Function<? super Psi, @Nls String> description,
                   @Nullable Function<? super Psi, ? extends HtmlChunk> tooltip,
                   @Nullable Function<? super Psi, ? extends TextRange> range,
                   @Nullable Function<? super Psi, JavaErrorHighlightType> type,
                   @Nullable ToIntFunction<? super Psi> navigationShift) {
      myKey = key;
      myDescription = description;
      myTooltip = tooltip;
      myRange = range;
      myHighlightType = type;
      myNavigationShift = navigationShift;
    }

    Simple(@NotNull @PropertyKey(resourceBundle = JavaCompilationErrorBundle.BUNDLE) String key) {
      this(key, null, null, null, null, null);
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
    public @NotNull String description(@NotNull Psi psi, Void unused) {
      if (myDescription == null) {
        return JavaErrorKind.super.description(psi, null);
      }
      return checkNotNull(myDescription.apply(psi), "description");
    }

    @Override
    public @NotNull HtmlChunk tooltip(@NotNull Psi psi, Void unused) {
      if (myTooltip == null) {
        return JavaErrorKind.super.tooltip(psi, null);
      }
      return checkNotNull(myTooltip.apply(psi), "tooltip");
    }

    @Override
    public @NotNull TextRange range(@NotNull Psi psi, Void unused) {
      if (myRange == null) {
        return JavaErrorKind.super.range(psi, null);
      }
      return myRange.apply(psi);
    }

    @Override
    public @Range(from = 0, to = Integer.MAX_VALUE) int navigationShift(@NotNull Psi psi, Void unused) {
      if (myNavigationShift == null) {
        return 0;
      }
      return myNavigationShift.applyAsInt(psi); 
    }

    @Override
    public @NotNull JavaErrorHighlightType highlightType(@NotNull Psi psi, Void unused) {
      if (myHighlightType == null) {
        return JavaErrorKind.super.highlightType(psi, null);
      }
      return checkNotNull(myHighlightType.apply(psi), "highlightType");
    }

    /**
     * Creates a new instance of Simple with the specified anchor function.
     *
     * @param anchor a function that determines the {@link PsiElement} to be used 
     *               as an anchor for a given Psi object.
     *               Anchor can be used as a more convenient way to define a reporting range.
     * @return a new Simple instance with the updated anchor function.
     */
    Simple<Psi> withAnchor(@NotNull Function<? super Psi, ? extends PsiElement> anchor) {
      return withAbsoluteRange(psi -> anchor.apply(psi).getTextRange());
    }

    /**
     * Creates a new instance of Simple with the specified range function.
     *
     * @param range a function that determines the {@link TextRange} for a given Psi object.
     *              The range is absolute in the current file.
     * @return a new Simple instance with the updated range function.
     */
    Simple<Psi> withAbsoluteRange(@NotNull Function<? super Psi, ? extends TextRange> range) {
      if (myRange != null) {
        throw new IllegalStateException("Range function is already set for " + key());
      }
      return new Simple<>(myKey, myDescription, myTooltip, range, myHighlightType, myNavigationShift);
    }

    /**
     * Creates a new instance of Simple with the specified range function.
     *
     * @param range a function that determines the {@link TextRange} for a given Psi object.
     *              The range is relative to the input PSI element.
     *              Returning null assumes that the whole range of the PSI element should be used.
     * @return a new Simple instance with the updated range function.
     */
    Simple<Psi> withRange(@NotNull Function<? super Psi, ? extends @Nullable TextRange> range) {
      return withAbsoluteRange(psi -> {
        TextRange res = range.apply(psi);
        return res == null ? psi.getTextRange() : res.shiftRight(psi.getTextRange().getStartOffset());
      });
    }
    
    

    /**
     * Creates a new instance of Simple with the specified highlight type function.
     *
     * @param type a function that determines the {@link JavaErrorHighlightType} for a given Psi object.
     * @return a new Simple instance with the updated highlight type function.
     */
    Simple<Psi> withHighlightType(@NotNull Function<? super Psi, JavaErrorHighlightType> type) {
      if (myHighlightType != null) {
        throw new IllegalStateException("Highlight type function is already set for " + key());
      }
      return new Simple<>(myKey, myDescription, myTooltip, myRange, type, myNavigationShift);
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
     * Creates a new instance of Simple with the specified navigation shift function.
     *
     * @param navigationShift a function that determines the navigation shift for a given Psi object.
     * @return a new Simple instance with the updated navigation shift function.
     */
    Simple<Psi> withNavigationShift(@NotNull ToIntFunction<? super Psi> navigationShift) {
      if (myNavigationShift != null) {
        throw new IllegalStateException("Navigation shift function is already set for " + key());
      }
      return new Simple<>(myKey, myDescription, myTooltip, myRange, myHighlightType, navigationShift);
    }

    /**
     * Creates a new instance of Simple with the specified navigation shift.
     *
     * @param navigationShift the constant navigation shift.
     * @return a new Simple instance with the updated navigation shift.
     */
    Simple<Psi> withNavigationShift(int navigationShift) {
      return withNavigationShift(psi -> navigationShift);
    }

    /**
     * Creates a new instance of Simple with a specified description function.
     *
     * @param description a Function that computes a description based on the given Psi and Context.
     * @return a new Simple instance with the specified description function.
     */
    Simple<Psi> withDescription(@NotNull Function<? super Psi, @Nls String> description) {
      if (myDescription != null) {
        throw new IllegalStateException("Description function is already set for " + key());
      }
      return new Simple<>(myKey, description, myTooltip, myRange, myHighlightType, myNavigationShift);
    }

    <Context> Parameterized<Psi, Context> parameterized() {
      return new Parameterized<>(myKey, myDescription == null ? null : (psi, ctx) -> myDescription.apply(psi),
                                 myTooltip == null ? null : (psi, ctx) -> myTooltip.apply(psi),
                                 myRange == null ? null : (psi, ctx) -> myRange.apply(psi),
                                 myHighlightType == null ? null : (psi, ctx) -> myHighlightType.apply(psi),
                                 myNavigationShift == null ? null : (psi, ctx) -> myNavigationShift.applyAsInt(psi));
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
    private final @Nullable BiFunction<? super Psi, ? super Context, @Nls String> myDescription;
    private final @Nullable BiFunction<? super Psi, ? super Context, ? extends HtmlChunk> myTooltip;
    private final @Nullable BiFunction<? super Psi, ? super Context, ? extends TextRange> myRange;
    private final @Nullable BiFunction<? super Psi, ? super Context, JavaErrorHighlightType> myHighlightType;
    private final @Nullable ToIntBiFunction<? super Psi, ? super Context> myNavigationShift;

    private Parameterized(@NotNull @PropertyKey(resourceBundle = JavaCompilationErrorBundle.BUNDLE) String key,
                          @Nullable BiFunction<? super Psi, ? super Context, @Nls String> description,
                          @Nullable BiFunction<? super Psi, ? super Context, ? extends HtmlChunk> tooltip,
                          @Nullable BiFunction<? super Psi, ? super Context, ? extends TextRange> range,
                          @Nullable BiFunction<? super Psi, ? super Context, JavaErrorHighlightType> type,
                          @Nullable ToIntBiFunction<? super Psi, ? super Context> navigationShift) {
      myKey = key;
      myDescription = description;
      myTooltip = tooltip;
      myRange = range;
      myHighlightType = type;
      myNavigationShift = navigationShift;
    }

    Parameterized(@NotNull @PropertyKey(resourceBundle = JavaCompilationErrorBundle.BUNDLE) String key) {
      this(key, null, null, null, null, null);
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
    public @NotNull String description(@NotNull Psi psi, Context context) {
      if (myDescription == null) {
        return JavaErrorKind.super.description(psi, context);
      }
      return checkNotNull(myDescription.apply(psi, context), "description");
    }

    @Override
    public @NotNull HtmlChunk tooltip(@NotNull Psi psi, Context context) {
      if (myTooltip == null) {
        return JavaErrorKind.super.tooltip(psi, context);
      }
      return checkNotNull(myTooltip.apply(psi, context), "tooltip");
    }

    @Override
    public @NotNull TextRange range(@NotNull Psi psi, Context context) {
      if (myRange == null) {
        return JavaErrorKind.super.range(psi, context);
      }
      return myRange.apply(psi, context);
    }

    @Override
    public @Range(from = 0, to = Integer.MAX_VALUE) int navigationShift(@NotNull Psi psi, Context context) {
      if (myNavigationShift == null) {
        return 0;
      }
      return myNavigationShift.applyAsInt(psi, context);
    }

    @Override
    public @NotNull JavaErrorHighlightType highlightType(@NotNull Psi psi, Context context) {
      if (myHighlightType == null) {
        return JavaErrorKind.super.highlightType(psi, context);
      }
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
     * @param anchor a Function that computes an anchor based on the given Psi and Context.
     *               Anchor can be used as a more convenient way to define a reporting range.
     * @return a new Parameterized instance with the specified anchor function
     */
    Parameterized<Psi, Context> withAnchor(@NotNull Function<? super Psi, ? extends PsiElement> anchor) {
      return withAbsoluteRange((psi, ctx) -> anchor.apply(psi).getTextRange());
    }

    /**
     * Creates a new instance of Parameterized with the specified range function.
     *
     * @param range a BiFunction that determines the {@link TextRange} for a given Psi object.
     *              The range is absolute in the current file.
     * @return a new Parameterized instance with the updated range function.
     */
    Parameterized<Psi, Context> withAbsoluteRange(@NotNull BiFunction<? super Psi, ? super Context, ? extends @NotNull TextRange> range) {
      if (myRange != null) {
        throw new IllegalStateException("Range function is already set for " + key());
      }
      return new Parameterized<>(myKey, myDescription, myTooltip, range, myHighlightType, myNavigationShift);
    }

    /**
     * Creates a new instance of Parameterized with the specified range function.
     *
     * @param range a BiFunction that determines the {@link TextRange} for a given Psi object.
     *              The range is relative to the input PSI element.
     *              Returning null assumes that the whole range of the PSI element should be used.
     * @return a new Parameterized instance with the updated range function.
     */
    Parameterized<Psi, Context> withRange(@NotNull BiFunction<? super Psi, ? super Context, ? extends TextRange> range) {
      return withAbsoluteRange((psi, ctx) -> {
        TextRange res = range.apply(psi, ctx);
        return res == null ? psi.getTextRange() : res.shiftRight(psi.getTextRange().getStartOffset());
      });
    }

    /**
     * Creates a new instance of Parameterized with the specified navigation shift function.
     *
     * @param navigationShift a function that determines the navigation shift for a given Psi and Context object.
     * @return a new Parameterized instance with the updated navigation shift function.
     */
    Parameterized<Psi, Context> withNavigationShift(@NotNull ToIntBiFunction<? super Psi, ? super Context> navigationShift) {
      if (myNavigationShift != null) {
        throw new IllegalStateException("Navigation shift function is already set for " + key());
      }
      return new Parameterized<>(myKey, myDescription, myTooltip, myRange, myHighlightType, navigationShift);
    }

    /**
     * Creates a new instance of Parameterized with the specified navigation shift.
     *
     * @param navigationShift the constant navigation shift.
     * @return a new Parameterized instance with the updated navigation shift.
     */
    Parameterized<Psi, Context> withNavigationShift(int navigationShift) {
      return withNavigationShift((psi, ctx) -> navigationShift);
    }

    /**
     * Creates a new instance of Parameterized with the specified highlight type function.
     *
     * @param type a function that determines the {@link JavaErrorHighlightType} for a given Psi object.
     * @return a new Parameterized instance with the updated highlight type function.
     */
    Parameterized<Psi, Context> withHighlightType(@NotNull BiFunction<? super Psi, ? super Context, JavaErrorHighlightType> type) {
      if (myHighlightType != null) {
        throw new IllegalStateException("Highlight type function is already set for " + key());
      }
      return new Parameterized<>(myKey, myDescription, myTooltip, myRange, type, myNavigationShift);
    }

    /**
     * Creates a new instance of Parameterized with the specified constant highlight type.
     *
     * @param type a {@link JavaErrorHighlightType} for a given error kind.
     * @return a new Parameterized instance with the updated highlight type function.
     */
    Parameterized<Psi, Context> withHighlightType(@SuppressWarnings("SameParameterValue") @NotNull JavaErrorHighlightType type) {
      return withHighlightType((psi, ctx) -> type);
    }

    /**
     * Creates a new instance of Parameterized with a specified tooltip function.
     *
     * @param tooltip a BiFunction that computes a tooltip based on the given Psi and Context.
     * @return a new Parameterized instance with the specified tooltip function.
     */
    Parameterized<Psi, Context> withTooltip(@NotNull BiFunction<? super Psi, ? super Context, ? extends HtmlChunk> tooltip) {
      if (myTooltip != null) {
        throw new IllegalStateException("Tooltip function is already set for " + key());
      }
      return new Parameterized<>(myKey, myDescription, tooltip, myRange, myHighlightType, myNavigationShift);
    }

    /**
     * Creates a new instance of Parameterized with a raw description function.
     *
     * @param description a BiFunction that computes a raw description (localized string containing HTML)
     *                    based on the given Psi and Context.
     * @return a new Parameterized instance with the specified raw description function.
     */
    Parameterized<Psi, Context> withDescription(@NotNull BiFunction<? super Psi, ? super Context, @Nls String> description) {
      if (myDescription != null) {
        throw new IllegalStateException("Description function is already set for " + key());
      }
      return new Parameterized<>(myKey, description, myTooltip, myRange, myHighlightType, myNavigationShift);
    }

    @Override
    public String toString() {
      return "JavaErrorKind[" + myKey + "]";
    }
  }
}
