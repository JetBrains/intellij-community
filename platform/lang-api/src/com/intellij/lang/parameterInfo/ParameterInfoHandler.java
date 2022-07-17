// Copyright 2000-2017 JetBrains s.r.o.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.intellij.lang.parameterInfo;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * This extension is used to implement support for 'Parameter Info' action for a specific language. That action shows a popup with
 * information about formal method parameters, when caret is located at method invocation site.<p>
 * The extension should implement methods finding target method's PSI element based on caret position
 * ({@link #findElementForParameterInfo(CreateParameterInfoContext)} and
 * {@link #findElementForUpdatingParameterInfo(UpdateParameterInfoContext)}, the former also should specify information about all method
 * overloads to display), method to select currently used overload and parameter caret is currently on
 * ({@link #updateParameterInfo(Object, UpdateParameterInfoContext)}), method defining the presentation of popup elements
 * ({@link #updateUI(Object, ParameterInfoUIContext)} and method to actually show the popup
 * ({@link #showParameterInfo(Object, CreateParameterInfoContext)});
 */
public interface ParameterInfoHandler <ParameterOwner extends Object & PsiElement, ParameterType> {

  /**
   * <p>Find psiElement for parameter info should also set ItemsToShow in context and may set highlighted element</p>
   *
   * <p>Note: it is executed on non UI thread</p>
   */
  @Nullable
  ParameterOwner findElementForParameterInfo(@NotNull CreateParameterInfoContext context);

  /**
   * This method is called to show parameter info popup. Usually it just invokes
   * {@link CreateParameterInfoContext#showHint(PsiElement, int, ParameterInfoHandler)}.
   */
  void showParameterInfo(@NotNull final ParameterOwner element, @NotNull CreateParameterInfoContext context);

  /**
   * <p>Hint has to be removed if method returns <code>null</code>.</p>
   *
   * <p>Note: it is executed on non-UI thread</p>
   */
  @Nullable
  ParameterOwner findElementForUpdatingParameterInfo(@NotNull UpdateParameterInfoContext context);

  /**
   * This method performs some extra action (e.g. show hints) with a result of execution of
   * {@link #findElementForUpdatingParameterInfo(UpdateParameterInfoContext)} on UI thread.
   */
  default void processFoundElementForUpdatingParameterInfo(@Nullable ParameterOwner parameterOwner,
                                                           @NotNull UpdateParameterInfoContext context) {

  }

  /**
   * <p>Updates parameter info context due to change of caret position.</p>
   *
   * <p>It could update context and state of {@link UpdateParameterInfoContext#getObjectsToView()}</p>
   *
   * <p>Note: <code>context.getParameterOwner()</code> equals to <code>parameterOwner</code> or <code>null</code></p>
   *
   * <p>Note: it is executed on non UI thread.</p>
   */
  void updateParameterInfo(@NotNull final ParameterOwner parameterOwner, @NotNull UpdateParameterInfoContext context);

  /**
   * <p>This method is executed on UI thread and supposed only to update UI representation using
   * {@link ParameterInfoUIContext#setUIComponentEnabled(boolean)} or {@link ParameterInfoUIContext#setupUIComponentPresentation(String, int, int, boolean, boolean, boolean, Color)}.</p>
   *
   * <p>Don't perform any heavy calculations like resolve here: move it to {@link #findElementForParameterInfo(CreateParameterInfoContext)} or
   * {@link #updateParameterInfo(Object, UpdateParameterInfoContext)}.</p>
   */
  void updateUI(ParameterType p, @NotNull ParameterInfoUIContext context);

  default boolean supportsOverloadSwitching() { return false; }
  default void dispose(@NotNull DeleteParameterInfoContext context) {}

  default boolean isWhitespaceSensitive() {
    return false;
  }
  default void syncUpdateOnCaretMove(@NotNull UpdateParameterInfoContext context) {}

  /** @deprecated not used */
  @Deprecated(forRemoval = true)
  default Object @Nullable [] getParametersForDocumentation(ParameterType p, ParameterInfoContext context) { return null; }

  /** @deprecated not used */
  @Deprecated(forRemoval = true)
  default @Nullable String getParameterCloseChars() { return null; }

  /** @deprecated not used */
  @Deprecated(forRemoval = true)
  default boolean tracksParameterIndex() { return false; }

  /** @deprecated unused */
  @Deprecated(forRemoval = true)
  default boolean couldShowInLookup() { return false; }

  /** @deprecated unused */
  @Deprecated(forRemoval = true)
  default Object @Nullable [] getParametersForLookup(LookupElement item, ParameterInfoContext context) {
    return null;
  }

}
