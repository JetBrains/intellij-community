// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.navigation;

import com.intellij.openapi.editor.markup.TextAttributes;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.awt.*;

@ApiStatus.Experimental
@ApiStatus.NonExtendable
public interface TargetPresentationBuilder {

  @Contract(value = "-> new", pure = true)
  @NotNull TargetPresentation presentation();

  /**
   * @see com.intellij.openapi.vfs.newvfs.VfsPresentationUtil#getFileBackgroundColor
   */
  @Contract(value = "_ -> new", pure = true)
  @NotNull TargetPresentationBuilder backgroundColor(@Nullable Color color);

  @Contract(value = "_ -> new", pure = true)
  @NotNull TargetPresentationBuilder icon(@Nullable Icon icon);

  @Contract(value = "_ -> new", pure = true)
  @NotNull TargetPresentationBuilder presentableTextAttributes(@Nullable TextAttributes attributes);

  @Contract(value = "_ -> new", pure = true)
  @NotNull TargetPresentationBuilder containerText(@Nls @Nullable String text);

  @Contract(value = "_, _ -> new", pure = true)
  @NotNull TargetPresentationBuilder containerText(@Nls @Nullable String text, @Nullable TextAttributes attributes);

  /**
   * @see com.intellij.codeInsight.navigation.UtilKt#fileStatusAttributes
   */
  @Contract(value = "_ -> new", pure = true)
  @NotNull TargetPresentationBuilder containerTextAttributes(@Nullable TextAttributes attributes);

  @Contract(value = "_ -> new", pure = true)
  @NotNull TargetPresentationBuilder locationText(@Nls @Nullable String text);

  /**
   * @see com.intellij.codeInsight.navigation.UtilKt#fileLocation
   */
  @Contract(value = "_, _ -> new", pure = true)
  @NotNull TargetPresentationBuilder locationText(@Nls @Nullable String text, @Nullable Icon icon);
}
