// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.navigation;

import com.intellij.openapi.editor.markup.TextAttributes;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.awt.*;

@ApiStatus.Experimental
@ApiStatus.NonExtendable
public interface TargetPopupPresentationBuilder {

  @Contract(value = "-> new", pure = true)
  @NotNull TargetPopupPresentation presentation();

  /**
   * @see com.intellij.openapi.vfs.newvfs.VfsPresentationUtil#getFileBackgroundColor
   */
  @Contract(value = "_ -> new", pure = true)
  @NotNull TargetPopupPresentationBuilder backgroundColor(@Nullable Color color);

  @Contract(value = "_ -> new", pure = true)
  @NotNull TargetPopupPresentationBuilder icon(@Nullable Icon icon);

  @Contract(value = "_ -> new", pure = true)
  @NotNull TargetPopupPresentationBuilder presentableTextAttributes(@Nullable TextAttributes attributes);

  @Contract(value = "_ -> new", pure = true)
  @NotNull TargetPopupPresentationBuilder containerText(@Nls @Nullable String text);

  @Contract(value = "_, _ -> new", pure = true)
  @NotNull TargetPopupPresentationBuilder containerText(@Nls @Nullable String text, @Nullable TextAttributes attributes);

  /**
   * @see com.intellij.codeInsight.navigation.UtilKt#fileStatusAttributes
   */
  @Contract(value = "_ -> new", pure = true)
  @NotNull TargetPopupPresentationBuilder containerTextAttributes(@Nullable TextAttributes attributes);

  @Contract(value = "_ -> new", pure = true)
  @NotNull TargetPopupPresentationBuilder locationText(@Nls @Nullable String text);

  /**
   * @see com.intellij.codeInsight.navigation.UtilKt#fileLocation
   */
  @Contract(value = "_, _ -> new", pure = true)
  @NotNull TargetPopupPresentationBuilder locationText(@Nls @Nullable String text, @Nullable Icon icon);
}
