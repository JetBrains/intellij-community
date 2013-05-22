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

/**
 * @author Sergey Evdokimov
 */
public class DisposeAwareRunnable extends WeakReference<Object> implements Runnable {

  protected final Runnable myDelegate;

  public static Runnable create(@NotNull Runnable delegate, @Nullable PsiElement disposable) {
    return create(delegate, (Object)disposable);
  }

  public static Runnable create(@NotNull Runnable delegate, @Nullable Project disposable) {
    return create(delegate, (Object)disposable);
  }

  public static Runnable create(@NotNull Runnable delegate, @Nullable Module disposable) {
    return create(delegate, (Object)disposable);
  }

  private static Runnable create(@NotNull Runnable delegate, @Nullable Object disposable) {
    if (disposable == null) {
      return delegate;
    }

    if (delegate instanceof DumbAware) {
      return new DumbAwareRunnable(delegate, disposable);
    }

    if (delegate instanceof PossiblyDumbAware) {
      return new PossiblyDumbAwareRunnable(delegate, disposable);
    }

    return new DisposeAwareRunnable(delegate, disposable);
  }

  private DisposeAwareRunnable(Runnable delegate, Object disposable) {
    super(disposable);
    myDelegate = delegate;
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

  private static class DumbAwareRunnable extends DisposeAwareRunnable implements DumbAware {
    DumbAwareRunnable(Runnable delegate, Object disposable) {
      super(delegate, disposable);
    }
  }

  private static class PossiblyDumbAwareRunnable extends DisposeAwareRunnable implements PossiblyDumbAware {
    PossiblyDumbAwareRunnable(Runnable delegate, Object disposable) {
      super(delegate, disposable);
    }

    @Override
    public boolean isDumbAware() {
      return ((PossiblyDumbAware)myDelegate).isDumbAware();
    }
  }

}
