// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application;

import com.intellij.openapi.project.DumbService;
import com.intellij.util.concurrency.annotations.RequiresBlockingContext;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * Represents the stack of active modal dialogs. Used in calls to {@link Application#invokeLater} to specify
 * that the corresponding runnable is to be executed within the given modality state, i.e., when the same set modal dialogs is present, or its subset.<p/>
 * <p>
 * The primary purpose of the modality state is to guarantee code model (PSI/VFS/etc) correctness during user interaction.
 * Consider the following scenario:
 * <ul>
 *   <li>Some code invokes {@code SwingUtilities#invokeLater}</li>
 *   <li>Before that, the user action is processed which shows a dialog (e.g., asking a yes/no question)</li>
 *   <li>While this dialog is shown, the event scheduled before is processed and does something very dramatic, e.g., removes a module from the project, deletes some files,
 *   invalidates PSI</li>
 *   <li>The user closes the dialog</li>
 *   <li>The code that invoked that dialog now has to deal with the completely
 *   changed world, where PSI that it worked with might be already invalid, dumb mode (see {@link DumbService})
 *   might have unexpectedly begun, etc.</li>
 * </ul>
 * <p>
 * Normally clients of yes/no question dialogs aren't prepared for this at all, so exceptions are likely to arise.
 * Worse than that, there'll be no indication on why a particular change has occurred, because the runnable that was incorrectly invoked-later will
 * in many cases leave no trace of itself.<p/>
 * <p>
 * For these reasons, it's strongly advised to use {@link Application#invokeLater} methods everywhere instead of
 * {@link SwingUtilities#invokeLater(Runnable)} and {@link com.intellij.util.ui.UIUtil} convenience methods.<p/>
 * <p>
 * {@link SwingUtilities#invokeLater(Runnable)}, {@link #any()} and {@link com.intellij.util.ui.UIUtil} convenience methods may be used in the
 * purely UI-related code, but not with anything that deals with PSI or VFS.
 */
public abstract class ModalityState {
  /**
   * @deprecated use {@link #nonModal()} instead
   */
  @Deprecated public static final @NotNull ModalityState NON_MODAL;

  static {
    try {
      @SuppressWarnings("unchecked")
      Class<? extends ModalityState> ex = (Class<? extends ModalityState>)Class.forName("com.intellij.openapi.application.impl.ModalityStateEx");
      NON_MODAL = ex.newInstance();
    }
    catch (ClassNotFoundException | IllegalAccessException | InstantiationException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * @return state when no modal dialogs are open
   */
  public static @NotNull ModalityState nonModal() {
    return NON_MODAL;
  }

  /**
   * @return the modality state corresponding to the currently opened modal dialogs. Can only be invoked on AWT thread.
   */
  @SuppressWarnings("deprecation")
  @RequiresEdt
  public static @NotNull ModalityState current() {
    return ApplicationManager.getApplication().getCurrentModalityState();
  }

  /**
   * @return a special modality state that's equivalent to using no modality state at all in invokeLater.
   * Please don't use it unless absolutely needed. The code under this modality can only perform purely UI operations,
   * it shouldn't access any PSI, VFS or project model.
   */
  @SuppressWarnings("deprecation")
  public static @NotNull ModalityState any() {
    return ApplicationManager.getApplication().getAnyModalityState();
  }

  /**
   * @return state corresponding to the modal dialog containing the given component.
   * @see Application#getModalityStateForComponent(Component)
   */
  @RequiresEdt(generateAssertion = false)
  public static @NotNull ModalityState stateForComponent(@NotNull Component component) {
    return ApplicationManager.getApplication().getModalityStateForComponent(component);
  }

  /**
   * When invoked on AWT thread, returns {@link #current()}. When invoked in the thread of some modal progress, returns modality state
   * corresponding to that progress' dialog. Otherwise, returns {@link #nonModal()}.
   */
  @RequiresBlockingContext
  public static @NotNull ModalityState defaultModalityState() {
    return ApplicationManager.getApplication().getDefaultModalityState();
  }

  /**
   * Checks whether a computation with {@code requestedModality} can run during this modality.
   * Returns {@code true} when {@code requestedModality} is {@link #any()}.
   *
   * @return `true` when the computation with {@code requestedModality} can run during this modality,
   * or `false` when the computation with {@code requestedModality} should be delayed at least
   * until this modality ends (for instance, by closing of a dialog)
   */
  public abstract boolean accepts(@NotNull ModalityState requestedModality);

  /**
   * @return whether {@code this} modality state is strictly more specific than {@code anotherState},
   * so that {@code invokeLater} runnables with {@code anotherState} won't be executed until {@code this} modality state ends.
   * @deprecated use {@link #accepts}
   */
  @Deprecated
  public final boolean dominates(@NotNull ModalityState anotherState) {
    return !accepts(anotherState);
  }

  @Override
  public abstract String toString();
}
