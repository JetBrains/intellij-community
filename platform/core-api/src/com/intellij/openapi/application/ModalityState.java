/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.application;

import org.jetbrains.annotations.NotNull;

import java.awt.*;
import javax.swing.SwingUtilities;
import com.intellij.openapi.project.DumbService;
/**
 * Represents the stack of active modal dialogs. Used in calls to {@link Application#invokeLater} to specify
 * that the corresponding runnable is to be executed within the given modality state, i.e., when the same set modal dialogs is present, or its subset.<p/>
 *
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
 *
 * Normally clients of yes/no question dialogs aren't prepared for this at all, so exceptions are likely to arise.
 * Worse than that, there'll be no indication on why a particular change has occurred, because the runnable that was incorrectly invoked-later will
 * in many cases leave no trace of itself.<p/>
 *
 * For these reasons, it's strongly advised to use {@link Application#invokeLater} methods everywhere instead of
 * {@link SwingUtilities#invokeLater(Runnable)} and {@link com.intellij.util.ui.UIUtil} convenience methods.<p/>
 *
 * {@link SwingUtilities#invokeLater(Runnable)}, {@link #any()} and {@link com.intellij.util.ui.UIUtil} convenience methods may be used in the
 * purely UI-related code, but not with anything that deals with PSI or VFS.
 */
public abstract class ModalityState {
  /**
   * State when no modal dialogs are open.
   * @see Application#getNoneModalityState()
   */
  @NotNull public static final ModalityState NON_MODAL;

  static {
    try {
      @SuppressWarnings("unchecked")
      final Class<? extends ModalityState> ex = (Class<? extends ModalityState>)Class.forName("com.intellij.openapi.application.impl.ModalityStateEx");
      NON_MODAL = ex.newInstance();
    }
    catch (ClassNotFoundException | IllegalAccessException | InstantiationException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * @return the modality state corresponding to the currently opened modal dialogs. Can only be invoked on AWT thread.
   */
  @NotNull
  public static ModalityState current() {
    return ApplicationManager.getApplication().getCurrentModalityState();
  }

  /**
   * @return a special modality state that's equivalent to using no modality state at all in invokeLater.
   * Please don't use it unless absolutely needed. The code under this modality can only perform purely UI operations,
   * it shouldn't access any PSI, VFS or project model.
   */
  @NotNull
  public static ModalityState any() {
    return ApplicationManager.getApplication().getAnyModalityState();
  }

  /**
   * @return state corresponding to the modal dialog containing the given component.
   * @see Application#getModalityStateForComponent(Component)
   */
  @NotNull
  public static ModalityState stateForComponent(@NotNull Component component){
    return ApplicationManager.getApplication().getModalityStateForComponent(component);
  }

  /**
   * When invoked on AWT thread, returns {@link #current()}. When invoked in the thread of some modal progress, returns modality state
   * corresponding to that progress' dialog. Otherwise, returns {@link #NON_MODAL}.
   */
  @NotNull
  public static ModalityState defaultModalityState() {
    return ApplicationManager.getApplication().getDefaultModalityState();
  }

  /**
   * @return whether {@code this} modality state is strictly more specific than {@code anotherState},
   * so that {@code invokeLater} runnables with {@code anotherState} won't be executed until {@code this} modality state ends.
   */
  public abstract boolean dominates(@NotNull ModalityState anotherState);

  @Override
  public abstract String toString();
}
