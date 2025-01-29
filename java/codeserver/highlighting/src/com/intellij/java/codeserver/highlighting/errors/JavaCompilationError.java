// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeserver.highlighting.errors;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * A concrete instance of a Java compilation error. Note that the instance is bound to a PSI element, so it should not
 * outlive the read-action. Otherwise it may become invalid.
 * 
 * @param kind error kind
 * @param psi PSI element where the error occurred
 * @param context additional context necessary to properly render the error and the corresponding quick-fixes
 * @param <Context> type of the context
 */
public record JavaCompilationError<Psi extends PsiElement, Context>(@NotNull JavaErrorKind<Psi, Context> kind, 
                                                                    @NotNull Psi psi, 
                                                                    Context context) {
  public static final String JAVA_DISPLAY_INFORMATION = "--java-display-information";
  public static final String JAVA_DISPLAY_GRAYED = "--java-display-grayed";
  public static final String JAVA_DISPLAY_PARAMETER = "--java-display-parameter";
  public static final String JAVA_DISPLAY_ERROR = "--java-display-error";
  
  public JavaCompilationError {
    kind.validate(psi, context);
  }
  
  public @NotNull Project project() {
    return psi.getProject();
  }

  /**
   * @return a desired anchor to put the error message at
   */
  public @NotNull PsiElement anchor() {
    return kind.anchor(psi, context);
  }

  /**
   * @return range within anchor to highlight; or null if the whole anchor should be highlighted
   */
  public @Nullable TextRange range() {
    return kind.range(psi, context);
  }

  /**
   * @return a desired highlighting type to display the error
   */
  public @NotNull JavaErrorHighlightType highlightType() {
    return kind.highlightType(psi, context);
  }

  /**
   * @return a user-readable localized error description
   */
  public @NotNull HtmlChunk description() {
    return kind.description(psi, context);
  }

  /**
   * A tooltip may contain formatting classes, including:
   * <ul>
   *   <li>"--java-display-information" for informational formatting
   *   <li>"--java-display-grayed" for grayed formatting
   *   <li>"--java-display-error" for error formatting (typically red text or background)</li>
   * </ul>
   * 
   * @return a user-readable localized error tooltip.
   * 
   */
  public @NotNull HtmlChunk tooltip() {
    return kind.tooltip(psi, context);
  }

  /**
   * A helper method to match wanted error message and extract PSI without doing unchecked casts in client code.
   * 
   * @param kinds wanted error kinds
   * @return an optional containing typed {@link #psi()} if the current error is one of supplied kinds;
   * empty optional otherwise
   * @param <WantedPsi> the common PSI type of wanted kinds
   */
  @SafeVarargs
  public final <WantedPsi extends PsiElement> @NotNull Optional<WantedPsi> psiForKind(JavaErrorKind<? extends WantedPsi, ?>... kinds) {
    for (JavaErrorKind<? extends WantedPsi, ?> errorKind : kinds) {
      if (errorKind.equals(kind)) {
        //noinspection unchecked
        return Optional.of((WantedPsi)psi);
      }
    }
    return Optional.empty();
  } 
}
