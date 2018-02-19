// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm.actions;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface JvmActionGroup {

  /**
   * @return default group text, idependent of target language
   */
  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  String getDisplayText(@Nullable RenderData data);

  interface RenderData {

    /**
     * The action usually is bound to a request,
     * which in turn is bound to a PSI element in call site source code.
     * In cases when PSI element changes, the name is changing too, while the action remains.
     * This property should return an up-to-date element name to be displayed.
     */
    @NonNls
    @Nullable
    String getEntityName();
  }
}
