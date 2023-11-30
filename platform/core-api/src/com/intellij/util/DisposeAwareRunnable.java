// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.PossiblyDumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;

public class DisposeAwareRunnable<T extends Runnable> extends WeakReference<Object> implements Runnable {
  protected final T myDelegate;

  private DisposeAwareRunnable(@NotNull T delegate, @NotNull Object disposable) {
    super(disposable);
    myDelegate = delegate;
    assert disposable instanceof PsiElement || disposable instanceof ComponentManager : "Unknown type of "+disposable;
  }

  public static @NotNull Runnable create(@NotNull Runnable delegate, @Nullable PsiElement disposable) {
    return create(delegate, (Object)disposable);
  }

  public static @NotNull Runnable create(@NotNull Runnable delegate, @Nullable Project disposable) {
    return create(delegate, (Object)disposable);
  }

  public static @NotNull Runnable create(@NotNull Runnable delegate, @Nullable Module disposable) {
    return create(delegate, (Object)disposable);
  }

  private static @NotNull Runnable create(@NotNull Runnable delegate, @Nullable Object disposable) {
    if (disposable == null) {
      return delegate;
    }

    //noinspection SSBasedInspection
    if (delegate instanceof DumbAware) {
      return DumbAwareRunnable.create((Runnable & DumbAware)delegate, disposable);
    }

    if (delegate instanceof PossiblyDumbAware) {
      return PossiblyDumbAwareRunnable.create((Runnable & PossiblyDumbAware)delegate, disposable);
    }

    return new DisposeAwareRunnable<>(delegate, disposable);
  }

  @Override
  public void run() {
    Object res = get();
    if (res == null) return;

    if (res instanceof PsiElement) {
      if (!((PsiElement)res).isValid()) return;
    }
    else if (res instanceof ComponentManager) {
      if (((ComponentManager)res).isDisposed()) return;
    }

    myDelegate.run();
  }

  private static final class DumbAwareRunnable<T extends Runnable & DumbAware> extends DisposeAwareRunnable<T> implements DumbAware {
    private static @NotNull <T extends Runnable & DumbAware> DumbAwareRunnable<T> create(@NotNull T delegate, Object o) {
      return new DumbAwareRunnable<>(delegate, o);
    }
    private DumbAwareRunnable(@NotNull T delegate, Object disposable) {
      super(delegate, disposable);
    }
  }

  private static final class PossiblyDumbAwareRunnable<T extends Runnable & PossiblyDumbAware> extends DisposeAwareRunnable<T> implements PossiblyDumbAware {
    private static @NotNull <T extends Runnable & PossiblyDumbAware> PossiblyDumbAwareRunnable<T> create(@NotNull T delegate, Object o) {
      return new PossiblyDumbAwareRunnable<>(delegate, o);
    }
    private PossiblyDumbAwareRunnable(T delegate, Object disposable) {
      super(delegate, disposable);
    }

    @Override
    public boolean isDumbAware() {
      return myDelegate.isDumbAware();
    }
  }
}
