// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm.actions;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.jvm.JvmClass;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface JvmGroupIntentionAction extends IntentionAction {

  /**
   * @return full action text, e.g. <i>Create abstract method 'foo' in 'SomeClass'</i>
   */
  @NotNull
  String getText();

  /**
   * Given two actions, <i>Create method 'foo' in 'SomeJavaClass'</i>
   * and <i>Create function 'foo' in 'SomeKotlinClass'</i>,
   * we want to display them as a single action with ability to choose target class later.
   * In this case these actions should have {@link #equals equal} action groups.
   */
  @NotNull
  JvmActionGroup getActionGroup();

  /**
   * Returned value is used as the name of the group when actions are {@link #getActionGroup grouped}.
   * <p>
   * This text use terms of the target language,
   * e.g. <i>Create method 'foo'</i> for target class in Java
   * and <i>Create function 'foo'</i> for target class in Kotlin
   * <p>
   * This method is accessed only if {@link #isAvailable)} returned {@code true}.
   */
  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  default String getGroupDisplayText() {
    return getActionGroup().getDisplayText(getRenderData());
  }

  /**
   * Additional data which is passed to JvmActionGroup in cases
   * when there is no way to choose {@link #getGroupDisplayText group display text}
   * from {@link #getActionGroup grouped} actions.
   * <p>
   * This method is accessed only if {@link #isAvailable} returned {@code true}.
   */
  @Nullable
  default JvmActionGroup.RenderData getRenderData() {
    return null;
  }

  /**
   * Returned class in used for showing <i>Choose Target Class</i> popup
   * if the {@link #getActionGroup same action} is available for more than one target class.
   * <p>
   * This method is accessed only if {@link #isAvailable} returned {@code true}.
   */
  @NotNull
  JvmClass getTarget();
}
