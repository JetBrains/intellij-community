// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.quickfix;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.QuickFixActionRegistrar;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.util.ReflectionUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Register implementation of this class as {@code com.intellij.codeInsight.unresolvedReferenceQuickFixProvider} extension to provide additional
 * quick fixes for 'Unresolved reference' problems.<p>
 * For example, this line in the {@code plugin.xml} file:
 * <p>
 *   {@code <codeInsight.unresolvedReferenceQuickFixProvider implementation="com.intellij.jarFinder.FindJarQuickFixProvider"/>}
 * </p>
 * registers class {@code com.intellij.jarFinder.FindJarQuickFixProvider"} as an unresolved reference quick fix.
 *
 * @param <T> type of element you want register quick fixes for; for example, in Java language it may be {@link com.intellij.psi.PsiJavaCodeReferenceElement}
 */
public abstract class UnresolvedReferenceQuickFixProvider<T extends PsiReference> {
  /**
   * Call each registered {@link UnresolvedReferenceQuickFixProvider} for its quick fixes.
   * Please don't use because it might be very expensive.
   * return true if at least one quick fix was registered
   */
  @ApiStatus.Internal
  public static <T extends PsiReference> boolean registerReferenceFixes(@NotNull T ref, @NotNull QuickFixActionRegistrar registrar) {
    ApplicationManager.getApplication().assertIsNonDispatchThread();
    ApplicationManager.getApplication().assertReadAccessAllowed();
    DumbService dumbService = DumbService.getInstance(ref.getElement().getProject());
    Class<? extends PsiReference> referenceClass = ref.getClass();
    AtomicBoolean registered = new AtomicBoolean();
    // for counting registered references
    QuickFixActionRegistrar registrarDelegate = new QuickFixActionRegistrar() {
      @Override
      public void register(@NotNull IntentionAction action) {
        registrar.register(action);
        registered.set(true);
      }

      @Override
      public void register(@NotNull TextRange fixRange, @NotNull IntentionAction action, @Nullable HighlightDisplayKey key) {
        registrar.register(fixRange, action, key);
        registered.set(true);
      }
    };
    for (UnresolvedReferenceQuickFixProvider<?> each : EP_NAME.getExtensionList()) {
      if (!dumbService.isUsableInCurrentContext(each)) {
        continue;
      }
      if (ReflectionUtil.isAssignable(each.getReferenceClass(), referenceClass)) {
        //noinspection unchecked
        ((UnresolvedReferenceQuickFixProvider<T>)each).registerFixes(ref, registrarDelegate);
      }
    }
    return registered.get();
  }

  /**
   * Tell highlighting subsystem that this {@link HighlightInfo} (to be eventually built from {@param builder}) is going to show quick fixes for unresolved reference {@param reference}.
   * These fixes are the ones obtained from {@link #registerFixes(PsiReference, QuickFixActionRegistrar)} called on this reference.
   * These quick fixes are to be computed lazily, only when they are needed, e.g., when the user pressed Alt-Enter on this info, or this info is scrolled into a focus.
   */
  @ApiStatus.Internal
  public static void registerUnresolvedReferenceLazyQuickFixes(@NotNull PsiReference reference, @NotNull HighlightInfo.Builder builder) {
    Consumer<? super QuickFixActionRegistrar> consumer = registrar -> {
      ApplicationManager.getApplication().assertIsNonDispatchThread();
      ApplicationManager.getApplication().assertReadAccessAllowed();
      PsiElement referenceElement = reference.getElement();
      Project myProject;

      if (!referenceElement.isValid() || (myProject = referenceElement.getProject()).isDisposed()
          || DumbService.getInstance(myProject).isDumb()) {
        // this will be restarted anyway on smart mode switch
        return;
      }
      boolean wasRegistered = registerReferenceFixes(reference, registrar);
      if (wasRegistered && !ApplicationManager.getApplication().isHeadlessEnvironment()) {
        DaemonCodeAnalyzer.getInstance(myProject).restart(reference.getElement().getContainingFile());
      }
    };

    builder.registerLazyFixes(consumer);
  }

  private static final ExtensionPointName<UnresolvedReferenceQuickFixProvider<?>> EP_NAME = ExtensionPointName.create("com.intellij.codeInsight.unresolvedReferenceQuickFixProvider");

  public abstract void registerFixes(@NotNull T ref, @NotNull QuickFixActionRegistrar registrar);

  public abstract @NotNull Class<T> getReferenceClass();
}
