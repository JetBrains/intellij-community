// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.intention.CommonIntentionAction;
import com.intellij.java.codeserver.highlighting.errors.JavaCompilationError;
import com.intellij.java.codeserver.highlighting.errors.JavaErrorKind;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A helper abstract class to simplify the implementation of {@link JavaErrorFixProvider}. The implementors should call
 * methods {@link #fix(JavaErrorKind, JavaFixProvider)}, {@link #multi(JavaErrorKind, JavaFixesProvider)},
 * or {@link #fixes(JavaErrorKind, JavaFixesPusher)} in default constructor for every error. The rest will be done automatically.
 * <p>
 * Experimental: may be moved to another package later
 */
@ApiStatus.Experimental
public class AbstractJavaErrorFixProvider implements JavaErrorFixProvider {
  private final Map<JavaErrorKind<?, ?>, List<JavaFixesPusher<?, ?>>> myFixes = new HashMap<>();

  protected <Psi extends PsiElement, Context> void fix(@NotNull JavaErrorKind<Psi, Context> kind,
                                                       @NotNull JavaFixProvider<? super Psi, ? super Context> fixProvider) {
    fixes(kind, fixProvider.asPusher());
  }

  protected <Psi extends PsiElement, Context> void multi(@NotNull JavaErrorKind<Psi, Context> kind,
                                                         @NotNull JavaFixesProvider<? super Psi, ? super Context> fixProvider) {
    fixes(kind, fixProvider.asPusher());
  }

  protected <Psi extends PsiElement, Context> void fixes(@NotNull JavaErrorKind<Psi, Context> kind,
                                                         @NotNull JavaFixesPusher<? super Psi, ? super Context> fixProvider) {
    myFixes.computeIfAbsent(kind, k -> new ArrayList<>()).add(fixProvider);
  }

  @Override
  public final void registerFixes(@NotNull JavaCompilationError<?, ?> error,
                                  @NotNull Consumer<? super @NotNull CommonIntentionAction> sink) {
    var providers = myFixes.get(error.kind());
    if (providers == null) return;
    for (var provider : providers) {
      @SuppressWarnings("unchecked") var fn = (JavaFixesPusher<PsiElement, Object>)provider;
      fn.provide(error, fix -> {
        if (fix != null) {
          sink.accept(fix);
        }
      });
    }
  }

  @FunctionalInterface
  protected interface JavaFixProvider<Psi extends PsiElement, Context> {
    @Nullable CommonIntentionAction provide(@NotNull JavaCompilationError<? extends Psi, ? extends Context> error);

    default JavaFixesPusher<Psi, Context> asPusher() {
      return (error, registrar) -> registrar.accept(provide(error));
    }
  }

  @FunctionalInterface
  protected interface JavaFixesProvider<Psi extends PsiElement, Context> {
    @NotNull List<? extends @NotNull CommonIntentionAction> provide(@NotNull JavaCompilationError<? extends Psi, ? extends Context> error);

    default JavaFixesPusher<Psi, Context> asPusher() {
      return (error, registrar) -> provide(error).forEach(registrar);
    }
  }

  @FunctionalInterface
  protected interface JavaFixesPusher<Psi extends PsiElement, Context> {
    /**
     * @param error error to register fixes for
     * @param sink  a sink where fixes should be submitted. Submitting null is allowed and treated as null-op
     */
    void provide(@NotNull JavaCompilationError<? extends Psi, ? extends Context> error,
                 @NotNull Consumer<? super @Nullable CommonIntentionAction> sink);
  }
}
